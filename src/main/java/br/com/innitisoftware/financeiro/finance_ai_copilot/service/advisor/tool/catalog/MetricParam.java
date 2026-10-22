package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

public record MetricParam(
        String name,
        ParamType type,
        boolean required,
        String description
) {}
