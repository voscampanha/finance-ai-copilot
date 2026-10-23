package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.util.Objects;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;

public sealed interface DispatchResult
        permits DispatchResult.Semantic, DispatchResult.Contextual, DispatchResult.Fallback {

    record Semantic(MetricResult result) implements DispatchResult {
        public Semantic {
            Objects.requireNonNull(result);
        }
    }

    record Contextual(String context) implements DispatchResult {
        public Contextual {
            Objects.requireNonNull(context);
        }
    }

    record Fallback(String result) implements DispatchResult {
        public Fallback {
            Objects.requireNonNull(result);
        }
    }
}
