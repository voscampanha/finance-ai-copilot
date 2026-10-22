package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface MetricResult permits MetricResult.Success, MetricResult.Failure {

    String metricName();

    record Success(String metricName, List<Map<String, Object>> rows, int rowCount) implements MetricResult {
        public Success {
            Objects.requireNonNull(metricName);
        }
    }

    record Failure(String metricName, String reason) implements MetricResult {
        public Failure {
            Objects.requireNonNull(metricName);
            Objects.requireNonNull(reason);
        }
    }
}
