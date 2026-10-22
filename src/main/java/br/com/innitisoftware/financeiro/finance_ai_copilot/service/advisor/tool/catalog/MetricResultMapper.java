package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface MetricResultMapper {
    Object map(List<Map<String, Object>> rows);
}
