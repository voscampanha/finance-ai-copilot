package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.Lancamento;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.UltimoLancamentoMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UltimoLancamentoMapperTest {

    private final UltimoLancamentoMapper mapper = new UltimoLancamentoMapper();

    @Test
    void emptyRowsProduceNull() {
        assertThat(mapper.map(List.of())).isNull();
    }

    @Test
    void mapsSingleRowToLatestTransaction() {
        Object result = mapper.map(List.of(Map.of(
                "descricao", "Mercado",
                "valor", new BigDecimal("89.90"),
                "data", LocalDate.of(2026, 10, 20),
                "categoria", "Alimentação")));

        assertThat(result).isEqualTo(new Lancamento(
                "Mercado", new BigDecimal("89.90"), LocalDate.of(2026, 10, 20), "Alimentação"));
    }
}
