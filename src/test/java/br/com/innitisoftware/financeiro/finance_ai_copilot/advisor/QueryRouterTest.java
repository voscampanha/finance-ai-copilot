package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryRouterTest {
    private final QueryRouter queryRouter = new QueryRouter();

    @Test
    void classifiesAnalyticalQuestionsUsingKeywords() {
        assertEquals(QueryRouter.QueryType.ANALYTICAL, queryRouter.classify("Qual foi o total gasto no mês?"));
        assertEquals(QueryRouter.QueryType.ANALYTICAL, queryRouter.classify("Qual a média de gastos por categoria?"));
        assertEquals(QueryRouter.QueryType.ANALYTICAL, queryRouter.classify("Quantas compras fiz?"));
        assertEquals(QueryRouter.QueryType.ANALYTICAL, queryRouter.classify("Quanto gastei com mercado?"));
    }

    @Test
    void classifiesTransactionQuestionsAsContextual() {
        assertEquals(QueryRouter.QueryType.CONTEXTUAL, queryRouter.classify("O que foi a compra na Padaria Central?"));
        assertEquals(QueryRouter.QueryType.CONTEXTUAL, queryRouter.classify("Mostre os detalhes do lançamento de ontem."));
    }

    @Test
    void treatsBlankQuestionsAsContextual() {
        assertEquals(QueryRouter.QueryType.CONTEXTUAL, queryRouter.classify("  "));
    }
}