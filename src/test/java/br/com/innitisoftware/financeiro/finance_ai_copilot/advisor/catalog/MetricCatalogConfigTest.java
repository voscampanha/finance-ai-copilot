package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.catalog;

import org.junit.jupiter.api.Test;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricCatalog;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricCatalogConfig;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class MetricCatalogConfigTest {

    private final MetricCatalog catalog = new MetricCatalogConfig().metricCatalog();

    @Test
    void registersRequestedMetricsWithDateAndTenantConstraints() {
        MetricDefinition monthlyAverage = metric("media_gasto_mensal");
        MetricDefinition accountBalance = metric("saldo_por_conta");
        MetricDefinition groupExpenses = metric("gastos_por_grupo");

        assertThat(monthlyAverage.params())
                .extracting(param -> param.name())
                .containsExactly("data_inicio", "data_fim");
        assertThat(monthlyAverage.sql())
                .contains("t.property_id = :propertyId", "c.type = 'debit'", "PERIOD_DIFF");

        assertThat(accountBalance.params())
                .extracting(param -> param.name())
                .containsExactly("data_referencia");
        assertThat(accountBalance.description()).contains("saldo inicial");
        assertThat(accountBalance.sql())
                .contains("fa.property_id = :propertyId", "WHEN c.type = 'credit'", "WHEN c.type = 'debit'",
                        "t.date <= :dataReferencia");

        assertThat(groupExpenses.params())
                .extracting(param -> param.name())
                .containsExactly("data_inicio", "data_fim", "grupo");
        assertThat(groupExpenses.sql())
                .contains("t.property_id = :propertyId", "c.type = 'debit'", "LEFT JOIN `groups`");
    }

    private MetricDefinition metric(String name) {
        return catalog.findByName(name).orElseThrow();
    }
}
