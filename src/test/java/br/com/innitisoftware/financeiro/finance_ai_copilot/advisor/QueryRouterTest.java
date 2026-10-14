package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.junit.jupiter.api.Test;

import br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.QueryRouter.QueryIntent;
import br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.QueryRouter.ClassificationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRouterTest {
    private final QueryRouter router = new QueryRouter();

    @Test
    void deveClassificarAgregacaoComoAnalytical() {
        assertEquals(QueryIntent.ANALYTICAL,
            router.classify("Quanto gastei este mês?"));
    }

    @Test
    void deveClassificarUltimoComoRelational() {
        assertEquals(QueryIntent.RELATIONAL,
            router.classify("Mostre o último lançamento"));
    }

    @Test
    void deveClassificarListagemComoRelational() {
        assertEquals(QueryIntent.RELATIONAL,
            router.classify("Liste os gastos de ontem"));
    }

    @Test
    void deveClassificarPerguntaSemanticaComoContextual() {
        assertEquals(QueryIntent.CONTEXTUAL,
            router.classify("O que era aquela compra na padaria?"));
    }

    @Test
    void deveExigirAncoraFinanceiraParaKeywordFraca() {
        // "ontem" sozinho NÃO deve ser RELATIONAL
        QueryIntent intent = router.classify("Ontem choveu");
        assertEquals(QueryIntent.CONTEXTUAL, intent);
    }

    @Test
    void deveDetectarRelationalComAncoraProxima() {
        // "comprei" (stem "compr") a 2 tokens de "ontem"
        assertEquals(QueryIntent.RELATIONAL,
            router.classify("O que comprei ontem?"));
    }

    @Test
    void deveAplicarSinonimo() {
        // "besteira" vira "lazer" via sinônimo — continua CONTEXTUAL
        ClassificationResult result =
            router.classifyWithDetails("Quanto gastei com besteira?");
        // "quanto" força ANALYTICAL, então cai em ANALYTICAL
        assertEquals(QueryIntent.ANALYTICAL, result.intent());
    }

    @Test
    void deveRebaixarNegacao() {
        ClassificationResult result =
            router.classifyWithDetails("Não me mostre os gastos de ontem");
        assertEquals(QueryIntent.CONTEXTUAL, result.intent());
        assertTrue(result.confidence() < 0.6);
    }

    @Test
    void deveRetornarBaixaConfiancaParaPerguntaVaga() {
        ClassificationResult result =
            router.classifyWithDetails("Como estão as coisas?");
        assertTrue(result.isLowConfidence());
    }

    @Test
    void deveDarPrioridadeAnalyticalSobreRelational() {
        // "maior" (RELATIONAL_STRONG) + "total" (ANALYTICAL_STRONG)
        assertEquals(QueryIntent.ANALYTICAL,
            router.classify("Qual o total do meu maior gasto?"));
    }

    @Test
    void deveCasarVerboComStem() {
        // "gastei" → "gast" (stem) → casa com FINANCIAL_ANCHORS
        assertEquals(QueryIntent.RELATIONAL,
            router.classify("O que eu gastei ontem?"));
    }
}