package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;
import java.util.Objects;

public record MetricDefinition(
        String name,
        String description,
        List<MetricParam> params,
        String sql,
        MetricResultMapper mapper
) {
    public MetricDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        Objects.requireNonNull(params);
        Objects.requireNonNull(sql);
        Objects.requireNonNull(mapper);
        params = List.copyOf(params);
        if (name.isBlank()) {
            throw new IllegalArgumentException("metric name vazio");
        }
    }
}
