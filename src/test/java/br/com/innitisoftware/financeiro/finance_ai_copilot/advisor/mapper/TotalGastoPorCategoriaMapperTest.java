package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.CategoriaTotal;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.TotalGastoPorCategoriaMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TotalGastoPorCategoriaMapperTest {

    private final TotalGastoPorCategoriaMapper mapper = new TotalGastoPorCategoriaMapper();

    @Test
    void mapsRowsToCategoryTotals() {
        Object result = mapper.map(List.of(
                Map.of("categoria", "Alimentação", "total", new BigDecimal("125.50")),
                Map.of("categoria", "Transporte", "total", 45.25)));

        assertThat(result).isEqualTo(List.of(
                new CategoriaTotal("Alimentação", new BigDecimal("125.50")),
                new CategoriaTotal("Transporte", new BigDecimal("45.25"))));
    }

    @Test
    void emptyRowsProduceEmptyList() {
        assertThat(mapper.map(List.of())).isEqualTo(List.of());
    }
}
