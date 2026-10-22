package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDefinition;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;

@Component
public class MetricExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricExecutor.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenantContext;

    public MetricExecutor(
            @Qualifier("legacyNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            TenantContext tenantContext) {
        this.jdbc = jdbc;
        this.tenantContext = tenantContext;
    }

    public MetricResult execute(MetricDefinition metric, Map<String, Object> params) {
        long propertyId = tenantContext.requirePropertyId();
        Map<String, Object> boundParams = new HashMap<>(params);
        boundParams.put("propertyId", propertyId);

        try {
            List<Map<String, Object>> rows = jdbc.queryForList(metric.sql(), boundParams);
            Object payload = metric.mapper().map(rows);
            List<Map<String, Object>> mappedRows = payload == null
                    ? List.of()
                    : List.of(Map.of("payload", payload));
            return new MetricResult.Success(metric.name(), mappedRows, rows.size());
        } catch (DataAccessException exception) {
            LOGGER.error(
                    "Metric execution failed | metricName={} | propertyId={} | error={} | rootCause={}",
                    metric.name(),
                    propertyId,
                    exception.getClass().getSimpleName(),
                    exception.getMostSpecificCause().getMessage(),
                    exception);
            return new MetricResult.Failure(metric.name(), "Não foi possível consultar esta métrica.");
        }
    }
}
