package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.MetricExecutor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDefinition;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.CategoriaTotal;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.TotalGastoPorCategoriaMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricExecutorTest {

    @Test
    void executeMapsSqlRowsIntoSemanticPayload() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantContext tenantContext = new TenantContext();
        tenantContext.set(17L);
        MetricExecutor executor = new MetricExecutor(jdbc, tenantContext);
        MetricDefinition metric = new MetricDefinition(
                "total_gasto_por_categoria",
                "Total por categoria",
                List.of(),
                "SELECT category total",
                new TotalGastoPorCategoriaMapper());
        List<Map<String, Object>> sqlRows = List.of(Map.of(
                "categoria", "Mercado",
                "total", new BigDecimal("125.50")));
        when(jdbc.queryForList(eq(metric.sql()), anyMap())).thenReturn(sqlRows);

        MetricResult.Success result = (MetricResult.Success) executor.execute(metric, Map.of());

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows())
                .containsExactly(Map.of("payload", List.of(
                        new CategoriaTotal("Mercado", new BigDecimal("125.50")))));
    }
}
