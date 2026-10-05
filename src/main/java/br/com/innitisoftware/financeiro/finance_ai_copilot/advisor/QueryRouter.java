package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
public class QueryRouter {
    private static final Set<String> ANALYTICAL_KEYWORDS = Set.of(
            "quanto", "total", "totais", "soma", "somatorio", "media", "medias",
            "quantas", "quantos", "contagem", "frequencia", "comparar", "compare",
            "comparacao", "maior", "menor", "vezes"
    );

    public QueryType classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryType.CONTEXTUAL;
        }

        String normalizedQuestion = normalize(question);
        for (String word : normalizedQuestion.split("[^\\p{L}\\p{N}]+")) {
            if (ANALYTICAL_KEYWORDS.contains(word)) {
                return QueryType.ANALYTICAL;
            }
        }
        return QueryType.CONTEXTUAL;
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public enum QueryType {
        ANALYTICAL,
        CONTEXTUAL
    }
}