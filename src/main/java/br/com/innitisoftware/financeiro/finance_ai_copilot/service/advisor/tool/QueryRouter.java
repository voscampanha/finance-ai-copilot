package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(QueryRouter.class);
    
    private static final Set<String> ANALYTICAL_STRONG = Set.of(
            "total", "soma", "somatório", "somatorio",
            "média", "media", "mediana",
            "quanto", "quantos", "quantas",
            "contagem", "contar",
            "percentual", "porcentagem",
            "frequência", "frequencia",
            "acumulado", "acumulada",
            "saldo", "balanço", "balanco"
    );

    private static final Set<String> RELATIONAL_STRONG = Set.of(
            "último", "ultimo", "última", "ultima",
            "últimos", "ultimos", "últimas", "ultimas",
            "mais recente", "mais recentes",
            "primeiro", "primeira", "primeiros", "primeiras",
            "mais caro", "mais cara", "mais caros", "mais caras",
            "mais barato", "mais barata", "mais baratos", "mais baratas",
            "maior", "menor",
            "liste", "listar", "listagem",
            "mostre", "mostrar", "exiba", "exibir",
            "detalhe", "detalhes", "detalhado", "detalhada",
            "quais foram", "qual foi"
    );

    private static final Set<String> CONTEXTUAL_STRONG = Set.of(
            "o que era", "o que foi", "o que significa",
            "aquele", "aquela", "aquilo",
            "que eu anotei", "que eu registrei", "que eu esqueci",
            "resuma", "resumo", "resumir",
            "me fale sobre", "me conta sobre",
            "explique", "explicar",
            "parecido com", "similar a", "do mesmo tipo",
            "relacionado a", "referente a"
    );

    private static final Set<String> RELATIONAL_WEAK = Set.of(
            "ontem", "hoje", "anteontem",
            "semana", "mes", "mês", "ano",
            "recente", "recentes",
            "antigo", "antiga", "antigos", "antigas",
            "começo", "comeco", "início", "inicio", "fim"
    );

    private static final Set<String> FINANCIAL_ANCHORS = Set.of(
            // substantivos
            "lançamento", "lancamento", "lançamentos", "lancamentos",
            "transação", "transacao", "transações", "transacoes",
            "gasto", "gastos", "despesa", "despesas",
            "compra", "compras",
            "receita", "receitas", "entrada", "entradas",
            "conta", "contas",
            "saldo", "valor", "valores",
            "categoria", "categorias",
            "pagamento", "pagamentos",
            // verbos (radicais, ver stemming abaixo)
            "gast", "compr", "receb", "pag", "entr", "sai"
    );

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("besteira", "lazer"),
            Map.entry("supérfluo", "lazer"),
            Map.entry("superfluo", "lazer"),
            Map.entry("rolê", "lazer"),
            Map.entry("role", "lazer"),
            Map.entry("diversão", "lazer"),
            Map.entry("diversao", "lazer"),
            Map.entry("corrida", "uber")
    );


    /** Tamanho da janela de proximidade (tokens antes e depois). */
    private static final int CONTEXT_WINDOW = 4;

    /** Marcadores de negação que invalidam keywords positivas. */
    private static final Set<String> NEGATION_MARKERS = Set.of(
            "não", "nao", "nunca", "jamais", "sem"
    );

    public QueryIntent classify(String question) {
        return classifyWithDetails(question).intent();
    }

    public ClassificationResult classifyWithDetails(String question) {
        if (question == null || question.isBlank()) {
            return new ClassificationResult(
                    QueryIntent.CONTEXTUAL, 0.0, "pergunta vazia");
        }

        String normalized = normalize(question);
        normalized = applySynonyms(normalized);

        List<String> tokens = tokenize(normalized);

        // 1. Negação explícita → rebaixa para CONTEXTUAL
        if (containsNegation(normalized)) {
            log.debug("Negação detectada, rebaixando para CONTEXTUAL: \"{}\"", question);
            return new ClassificationResult(
                    QueryIntent.CONTEXTUAL, 0.5, "negação detectada");
        }

        // 2. Keywords RELATIONAL fortes
        if (containsAny(normalized, RELATIONAL_STRONG)) {
            // Mas se também contém keyword analítica forte, ANALYTICAL ganha
            if (containsAny(normalized, ANALYTICAL_STRONG)) {
                return new ClassificationResult(
                        QueryIntent.ANALYTICAL, 0.9,
                        "ANALYTICAL_STRONG + RELATIONAL_STRONG (analytical tem prioridade)");
            }
            return new ClassificationResult(
                    QueryIntent.RELATIONAL, 0.95, "RELATIONAL_STRONG");
        }

        // 3. Keywords ANALYTICAL fortes
        if (containsAny(normalized, ANALYTICAL_STRONG)) {
            return new ClassificationResult(
                    QueryIntent.ANALYTICAL, 0.95, "ANALYTICAL_STRONG");
        }

        // 4. Keywords RELATIONAL fracas + substantivo financeiro por perto
        if (hasWeakRelationalWithFinancialAnchor(tokens)) {
            return new ClassificationResult(
                    QueryIntent.RELATIONAL, 0.8,
                    "RELATIONAL_WEAK + FINANCIAL_ANCHOR em ±" + CONTEXT_WINDOW + " tokens");
        }

        // 5. Keywords CONTEXTUAL fortes
        if (containsAny(normalized, CONTEXTUAL_STRONG)) {
            return new ClassificationResult(
                    QueryIntent.CONTEXTUAL, 0.85, "CONTEXTUAL_STRONG");
        }

        // 6. Fallback
        return new ClassificationResult(
                QueryIntent.CONTEXTUAL, 0.4, "fallback (nenhuma keyword forte)");
    }

    /**
     * Verifica se alguma keyword RELATIONAL fraca está próxima de uma âncora financeira.
     *
     * Considera stemming leve para casar variações (ex: "comprei" casa com "compr").
     */
    private boolean hasWeakRelationalWithFinancialAnchor(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!RELATIONAL_WEAK.contains(token)) continue;

            int start = Math.max(0, i - CONTEXT_WINDOW);
            int end = Math.min(tokens.size(), i + CONTEXT_WINDOW + 1);

            for (int j = start; j < end; j++) {
                if (i == j) continue;
                String neighbor = tokens.get(j);
                if (matchesFinancialAnchor(neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Casa um token com uma âncora financeira, aplicando stemming leve.
     * Ex: "comprei" → "compr" → casa com "compr" em FINANCIAL_ANCHORS.
     */
    private boolean matchesFinancialAnchor(String token) {
        if (FINANCIAL_ANCHORS.contains(token)) return true;
        String stemmed = stem(token);
        return FINANCIAL_ANCHORS.contains(stemmed);
    }

    /**
     * Remove sufixos verbais comuns do português para reduzir ao radical.
     *
     * Exemplos:
     *   "comprei"  → "compr"
     *   "comprou"  → "compr"
     *   "gastei"   → "gast"
     *   "gastou"   → "gast"
     *   "recebi"   → "receb"
     *   "paguei"   → "pag"
     *
     * NÃO é um stemmer completo (tipo Snowball), mas cobre o vocabulário
     * financeiro comum sem dependência externa.
     */
    private String stem(String token) {
        if (token == null || token.length() < 4) return token;

        String[] suffixes = {
                "ando", "endo", "indo",
                "ado", "ido",
                "ei", "ou", "am",
                "ar", "er", "ir",
                "s"
        };

        for (String suffix : suffixes) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= 3) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    private String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private String applySynonyms(String normalized) {
        String result = normalized;
        for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
            result = result.replaceAll(
                    "\\b" + entry.getKey() + "\\b",
                    entry.getValue()
            );
        }
        return result;
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.split("[\\s,.!?;:]+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean containsNegation(String text) {
        List<String> tokens = tokenize(text);
        return tokens.stream().anyMatch(NEGATION_MARKERS::contains);
    }

    /**
     * Resultado da classificação com metadados para observabilidade e fallback.
     *
     * @param intent     rota escolhida
     * @param confidence confiança (0.0 a 1.0)
     * @param reason     motivo legível — útil para logs e debugging
     */
    public record ClassificationResult(
            QueryIntent intent,
            double confidence,
            String reason
    ) {
        /**
         * Indica se a confiança é baixa o suficiente para acionar fallback
         * (ex: classificador LLM mais caro, ou pedido de esclarecimento).
         */
        public boolean isLowConfidence() {
            return confidence < 0.6;
        }
    }

    public enum QueryIntent {
        ANALYTICAL,
        RELATIONAL,
        CONTEXTUAL
    }
}