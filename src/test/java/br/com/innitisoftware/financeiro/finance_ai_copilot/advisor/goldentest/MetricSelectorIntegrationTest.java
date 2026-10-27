package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.goldentest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelection;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelector;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.SemanticLayerTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MetricSelector — integração DeepSeek (golden set)")
class MetricSelectorIntegrationTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 26);

    @Autowired
    MetricSelector metricSelector; // ChatClient real + ObjectMapper de produção + Clock fixo

    @Autowired
    SemanticLayerTools semanticLayerTools;

    private List<GoldenCase> goldenSet;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    REFERENCE_DATE.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    ZoneOffset.UTC);
        }
    }

    @BeforeAll
    void loadGoldenSet() throws Exception {
        // Mapper dedicado ao YAML — sem JavaTimeModule para não interpretar datas como Long.
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        try (InputStream in = MetricSelectorIntegrationTest.class
                .getResourceAsStream("/golden-set/metric-selector-golden.yaml")) {
            Objects.requireNonNull(in, "golden-set/metric-selector-golden.yaml não encontrado");
            GoldenSetFile file = yaml.readValue(in, GoldenSetFile.class);
            goldenSet = file.cases();
            assertThat(file.referenceDate()).isEqualTo(REFERENCE_DATE.toString());
        }
        assertThat(goldenSet).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("Golden set (integração): taxa de acerto global")
    void relatorioDeAcerto() {
        var catalog = semanticLayerTools.describeCatalog();
        int total = 0, acertos = 0;
        int totalNone = 0, acertosNone = 0;
        List<String> falhas = new ArrayList<>();

        for (GoldenCase g : goldenSet) {
            total++;
            boolean ehNone = g.esperaFallback();
            if (ehNone) totalNone++;

            Optional<MetricSelection> r = metricSelector.trySelect(g.pergunta(), catalog);

            boolean ok;
            String obtido;
            if (ehNone) {
                ok = r.isEmpty();
                obtido = r.map(MetricSelection::metricName).orElse("<empty>");
            } else {
                ok = r.isPresent()
                        && g.metricaEsperada().equals(r.get().metricName())
                        && Objects.equals(g.paramsEsperados(), r.get().params());
                obtido = r.map(s -> s.metricName() + s.params()).orElse("<empty>");
            }

            if (ok) {
                acertos++;
                if (ehNone) acertosNone++;
            } else {
                falhas.add("  - %s | esperado=%s | obtido=%s | pergunta='%s'"
                        .formatted(g.id(),
                                ehNone ? "__none__" : g.metricaEsperada() + g.paramsEsperados(),
                                obtido, g.pergunta()));
            }
        }

        double taxaGlobal = (double) acertos / total;
        double taxaNone = totalNone == 0 ? 1.0 : (double) acertosNone / totalNone;

        String relatorio = """
                ===== GOLDEN SET (integração DeepSeek) =====
                Total: %d | Acertos: %d | Taxa global: %.2f%%
                __none__: %d | Acertos __none__: %d | Taxa __none__: %.2f%%
                Falhas:
                %s
                """.formatted(total, acertos, taxaGlobal * 100,
                              totalNone, acertosNone, taxaNone * 100,
                              falhas.isEmpty() ? "  (nenhuma)" : String.join("\n", falhas));

        // Critério de aceitação
        assertThat(taxaNone)
                .as("100%% dos casos __none__ devem cair no fallback.%n%s", relatorio)
                .isEqualTo(1.0);

        assertThat(taxaGlobal)
                .as("Taxa global mínima de 85%% no golden set.%n%s", relatorio)
                .isGreaterThanOrEqualTo(0.85);
    }

    record GoldenSetFile(String referenceDate, List<GoldenCase> cases) {}
}