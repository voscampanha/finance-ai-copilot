package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.InvalidMetricParamException;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDefinition;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricParam;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricParamValidator;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.ParamType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricParamValidatorTest {

    private final MetricParamValidator validator = new MetricParamValidator();

    @Test
    void rejectsUnknownParameter() {
        assertThatThrownBy(() -> validator.validate(metric(), Map.of("desconhecido", "x")))
                .isInstanceOf(InvalidMetricParamException.class);
    }

    @Test
    void rejectsMissingRequiredParameter() {
        assertThatThrownBy(() -> validator.validate(metric(), Map.of("categoria", "mercado")))
                .isInstanceOf(InvalidMetricParamException.class);
    }

    @Test
    void convertsSnakeCaseNamesAndCoercesSupportedTypes() {
        Map<String, Object> params = validator.validate(metric(), Map.of(
                "data_inicio", "2026-10-01",
                "quantidade", "42",
                "limite", "99.95",
                "categoria", "mercado"));

        assertThat(params)
                .containsEntry("dataInicio", LocalDate.of(2026, 10, 1))
                .containsEntry("quantidade", 42L)
                .containsEntry("limite", new BigDecimal("99.95"))
                .containsEntry("categoria", "mercado");
    }

    @Test
    void acceptsOptionalNullValue() {
        Map<String, Object> params = validator.validate(metric(), Map.of("data_inicio", "2026-10-01"));

        assertThat(params)
                .containsEntry("categoria", null)
                .containsEntry("dataReferencia", null);
    }

    @Test
    void convertsOptionalReferenceDateToCamelCaseAndDateType() {
        Map<String, Object> params = validator.validate(metric(), Map.of(
                "data_inicio", "2026-10-01",
                "data_referencia", "2026-10-25"));

        assertThat(params).containsEntry("dataReferencia", LocalDate.of(2026, 10, 25));
    }


    private MetricDefinition metric() {
        return new MetricDefinition(
                "metric",
                "description",
                List.of(
                        new MetricParam("data_inicio", ParamType.DATE, true, "start"),
                        new MetricParam("data_referencia", ParamType.DATE, false, "reference date"),
                        new MetricParam("quantidade", ParamType.LONG, false, "count"),
                        new MetricParam("limite", ParamType.DECIMAL, false, "amount"),
                        new MetricParam("categoria", ParamType.STRING, false, "category")),
                "SELECT 1",
                rows -> rows);
    }
}
