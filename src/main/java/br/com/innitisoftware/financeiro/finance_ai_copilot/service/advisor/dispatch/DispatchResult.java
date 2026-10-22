package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.document.Document;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;

public sealed interface DispatchResult
        permits DispatchResult.Semantic, DispatchResult.Contextual, DispatchResult.Fallback {

    record Semantic(MetricResult result) implements DispatchResult {
        public Semantic {
            Objects.requireNonNull(result);
        }
    }

    record Contextual(List<Document> documents) implements DispatchResult {
        public Contextual {
            documents = List.copyOf(documents);
        }
    }

    record Fallback(Object result) implements DispatchResult {
    }
}
