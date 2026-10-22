package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

public class UnknownMetricException extends RuntimeException {
    public UnknownMetricException(String metricName) {
        super("Métrica desconhecida: " + metricName);
    }
}
