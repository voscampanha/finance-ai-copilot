package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.goldentest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelection;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.MetricSelector;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDescriptor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.ParamDescriptor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.ParamType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste de regressão do MetricSelector contra o golden set.
 *
 * Roda sem rede (ChatClient mockado) e com temperatura 0.0.
 * A data de referência é fixa (2026-10-26) e injetada via Clock no
 * MetricSelector.
 */
@DisplayName("MetricSelector — golden set (mock, sem rede)")
class MetricSelectorGoldenSetTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 26);

    private static final Clock FIXED_CLOCK = Clock.fixed(REFERENCE_DATE.atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneOffset.UTC);

    /** Catálogo mínimo — o mesmo que a produção deve injetar. */
    private static final List<MetricDescriptor> CATALOG = buildCatalog();

    private static List<GoldenCase> goldenSet;

    private static ObjectMapper testObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @BeforeAll
    static void loadGoldenSet() throws Exception {
        // Mapper dedicado ao YAML do golden set — sem JavaTimeModule,
        // para que datas no YAML sejam lidas como String e não como Long/Date.
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        try (InputStream in = MetricSelectorGoldenSetTest.class
                .getResourceAsStream("/golden-set/metric-selector-golden.yaml")) {
            Objects.requireNonNull(in, "golden-set/metric-selector-golden.yaml não encontrado");
            GoldenSetFile file = yaml.readValue(in, GoldenSetFile.class);
            goldenSet = file.cases();
            assertThat(file.referenceDate())
                    .as("referenceDate no YAML deve ser a string ISO 2026-10-26 (com aspas)")
                    .isEqualTo(REFERENCE_DATE.toString());
        }
        assertThat(goldenSet).hasSizeGreaterThanOrEqualTo(15);
    }

    static Stream<GoldenCase> cases() {
        return goldenSet.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void deveSelecionarMetricaEEparamsEsperados(GoldenCase golden) {
        // MOCK: devolve o JSON que o LLM "produziria" para a resposta correta.
        // Este mock é a "forma correta" da resposta — o teste verifica que
        // o parsing + resolução de datas + validação de params no MetricSelector
        // preservam a intenção. Em CI é o que garante determinismo.
        //
        // Em conjunto com MetricSelectorIntegrationTest (@Tag("integration")),
        // cobre-se tanto a "cola" quanto o "cérebro".
        MetricSelector selector = newSelectorForMock(golden);

        Optional<MetricSelection> resultado = selector.trySelect(golden.pergunta(), CATALOG);

        if (golden.esperaFallback()) {
            assertThat(resultado)
                    .as("Caso %s: esperava fallback (__none__), veio %s",
                            golden.id(), resultado.map(MetricSelection::metricName).orElse("<empty>"))
                    .isEmpty();
            return;
        }

        assertThat(resultado)
                .as("Caso %s (%s): esperava métrica '%s'",
                        golden.id(), golden.pergunta(), golden.metricaEsperada())
                .isPresent();

        MetricSelection sel = resultado.get();
        assertThat(sel.metricName())
                .as("Caso %s: métrica errada. LLM escolheu '%s', esperado '%s'",
                        golden.id(), sel.metricName(), golden.metricaEsperada())
                .isEqualTo(golden.metricaEsperada());

        assertThat(sel.params())
                .as("Caso %s: params divergentes. Esperado %s, veio %s",
                        golden.id(), golden.paramsEsperados(), sel.params())
                .isEqualTo(golden.paramsEsperados());
    }

    /**
     * Constrói um MetricSelector cujo ChatClient é mockado para devolver a
     * resposta canônica do caso. O objetivo aqui NÃO é testar o LLM (isso é
     * papel do teste de integração), e sim:
     * - o parsing do JSON de resposta,
     * - a aplicação de datas relativas contra o Clock fixo,
     * - a validação de params contra o catálogo,
     * - a decisão de fallback quando o LLM retorna {"metric": null}.
     */
    private MetricSelector newSelectorForMock(GoldenCase golden) {
        ChatClient chatClient = mockChatClientReturning(canonicalLlmJson(golden));
        return new MetricSelector(chatClient, testObjectMapper(), FIXED_CLOCK);
    }

    /** JSON canônico que o LLM deveria produzir para acertar o caso. */
    private static String canonicalLlmJson(GoldenCase golden) {
        if (golden.esperaFallback()) {
            return "{\"metric\": null}";
        }
        // Nota: o MockChatClient do Spring AI retorna o texto bruto.
        // O MetricSelector deve aplicar datas relativas sobre os params crus;
        // por isso entregamos datas relativas em forma simbólica quando o YAML
        // usar termos relativos. Como aqui o YAML já traz datas resolvidas,
        // entregamos as datas finais — o teste então valida que o parser
        // NÃO as reinterpreta errado.
        String paramsJson = golden.paramsEsperados().entrySet().stream()
                .map(e -> "\"%s\":\"%s\"".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        return "{\"metric\":\"%s\",\"params\":%s}"
                .formatted(golden.metricaEsperada(), paramsJson);
    }

    private static ChatClient mockChatClientReturning(String rawJson) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(rawJson);

        return chatClient;
    }

    private static List<MetricDescriptor> buildCatalog() {
        return List.of(
                new MetricDescriptor("total_gasto_por_categoria",
                        "Soma de gastos no período, opcionalmente filtrado por categoria.",
                        List.of(param("data_inicio", ParamType.DATE, true, "ISO date, início do período"),
                                param("data_fim", ParamType.DATE, true, "ISO date, fim do período"),
                                param("categoria", ParamType.STRING, false, "opcional; nome da categoria"))),
                new MetricDescriptor("ultimo_lancamento",
                        "Lançamento mais recente, opcionalmente filtrado por categoria.",
                        List.of(param("categoria", ParamType.STRING, false, "opcional"))),
                new MetricDescriptor("maior_lancamento",
                        "Lançamento de maior valor no período, opcionalmente filtrado por categoria.",
                        List.of(param("data_inicio", ParamType.DATE, true, "ISO date"),
                                param("data_fim", ParamType.DATE, true, "ISO date"),
                                param("categoria", ParamType.STRING, false, "opcional"))),
                new MetricDescriptor("media_gasto_mensal",
                        "Média de gastos por mês no período.",
                        List.of(param("data_inicio", ParamType.DATE, true, "ISO date"),
                                param("data_fim", ParamType.DATE, true, "ISO date"))),
                new MetricDescriptor("saldo_por_conta",
                        "Saldo da conta na data de referência.",
                        List.of(param("data_referencia", ParamType.DATE, false, "ISO date, opcional; default hoje"))),
                new MetricDescriptor("gastos_por_grupo",
                        "Soma de gastos no período, agrupados por grupo.",
                        List.of(param("data_inicio", ParamType.DATE, true, "ISO date, início do período"),
                                param("data_fim", ParamType.DATE, true, "ISO date, fim do período")))
                    );
    }

    private static ParamDescriptor param(String name, ParamType type, boolean required, String desc) {
        return new ParamDescriptor(name, type, required, desc); // ajuste ao seu record real
    }

    /** Espelho do YAML. */
    record GoldenSetFile(String referenceDate, List<GoldenCase> cases) {
    }
}