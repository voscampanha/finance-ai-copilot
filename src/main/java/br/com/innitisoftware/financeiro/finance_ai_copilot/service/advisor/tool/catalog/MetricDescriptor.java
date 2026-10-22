package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;

public record MetricDescriptor(String name, String description, List<ParamDescriptor> params) {}
