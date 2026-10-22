package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record MetricSelection(String metricName, Map<String, Object> params) {
    public MetricSelection {
        Objects.requireNonNull(metricName);
        params = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(params)));
    }
}
