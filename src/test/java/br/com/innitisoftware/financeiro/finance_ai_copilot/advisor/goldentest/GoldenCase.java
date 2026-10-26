package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.goldentest;

import java.util.Map;

public record GoldenCase(
        String id,
        String pergunta,
        String metricaEsperada,      // nome exato ou "__none__"
        Map<String, String> paramsEsperados,
        String justificativa
) {
    public static final String NONE = "__none__";

    public boolean esperaFallback() {
        return NONE.equals(metricaEsperada);
    }
}