package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.junit.jupiter.api.Test;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.FinancialAdvisorService;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.DispatchResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.QueryDispatcher;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.CategoriaTotal;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.Lancamento;
import br.com.innitisoftware.financeiro.finance_ai_copilot.web.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

class FinancialAdvisorServiceTest {

    @Test
    void semanticDispatchUsesAuthenticatedPropertyContextAndClearsItAfterward() {
        QueryDispatcher dispatcher = mock(QueryDispatcher.class);
        TenantContext tenantContext = new TenantContext();
        FinancialAdvisorService service = new FinancialAdvisorService(
                dispatcher, mock(org.springframework.ai.chat.client.ChatClient.class), tenantContext);
        when(dispatcher.dispatch("Pergunta financeira")).thenAnswer(invocation -> {
            assertThat(tenantContext.requirePropertyId()).isEqualTo(17L);
            return new DispatchResult.Semantic(
                    new MetricResult.Success(
                            "total_gasto_por_categoria",
                            List.of(Map.of("payload", List.of(
                                    new CategoriaTotal("Alimentação", new BigDecimal("125.50")),
                                    new CategoriaTotal("Transporte", new BigDecimal("42.75"))))),
                            2));
        });

        ChatResponse response = service.answer("Pergunta financeira", 17L);

        assertThat(response.answer()).isEqualTo("Aqui está o total gasto por categoria.");
        assertThat(response.data())
                .extracting(ChatResponse.DadoFinanceiro::categoria)
                .containsExactly("Alimentação", "Transporte");
        assertThat(response.data())
                .extracting(ChatResponse.DadoFinanceiro::valor)
                .containsExactly(125.50, 42.75);
        assertThat(response.insights()).containsExactly(
                "Total em Alimentação no período: R$ 125,50.",
                "Total em Transporte no período: R$ 42,75.");
        verify(dispatcher).dispatch("Pergunta financeira");
        assertThatThrownBy(tenantContext::requirePropertyId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semanticCategoryTotalIncludesSummaryAndLeavesTransactionFieldsEmpty() {
        QueryDispatcher dispatcher = mock(QueryDispatcher.class);
        FinancialAdvisorService service = new FinancialAdvisorService(
                dispatcher, mock(org.springframework.ai.chat.client.ChatClient.class), new TenantContext());
        when(dispatcher.dispatch("Qual total gasto com mercado este mês?"))
                .thenReturn(new DispatchResult.Semantic(
                        new MetricResult.Success(
                                "total_gasto_por_categoria",
                                List.of(Map.of("payload", List.of(
                                        new CategoriaTotal("Mercado", new BigDecimal("1733.83"))))),
                                1)));

        ChatResponse response = service.answer("Qual total gasto com mercado este mês?", 17L);

        assertThat(response.answer())
                .isEqualTo("O total gasto em Mercado no período foi R$ 1.733,83.");
        assertThat(response.insights())
                .containsExactly("Total em Mercado no período: R$ 1.733,83.");
        assertThat(response.data()).containsExactly(new ChatResponse.DadoFinanceiro(
                null, null, "Mercado", 1733.83));
    }

    @Test
    void semanticLatestTransactionUsesLatestSpecificAnswer() {
        ChatResponse response = transactionMetricResponse("ultimo_lancamento");

        assertThat(response.answer()).isEqualTo("Este é o lançamento mais recente.");
        assertThat(response.data()).containsExactly(new ChatResponse.DadoFinanceiro(
                "20/10/2026", "Mercado", "Alimentação", 89.90));
    }

    @Test
    void semanticLargestTransactionUsesLargestSpecificAnswer() {
        ChatResponse response = transactionMetricResponse("maior_lancamento");

        assertThat(response.answer()).isEqualTo("Este é o lançamento de maior valor.");
        assertThat(response.data()).containsExactly(new ChatResponse.DadoFinanceiro(
                "20/10/2026", "Mercado", "Alimentação", 89.90));
    }

    private ChatResponse transactionMetricResponse(String metricName) {
        QueryDispatcher dispatcher = mock(QueryDispatcher.class);
        FinancialAdvisorService service = new FinancialAdvisorService(
                dispatcher, mock(org.springframework.ai.chat.client.ChatClient.class), new TenantContext());
        when(dispatcher.dispatch("Pergunta financeira"))
                .thenReturn(new DispatchResult.Semantic(
                        new MetricResult.Success(
                                metricName,
                                List.of(Map.of("payload", new Lancamento(
                                        "Mercado",
                                        new BigDecimal("89.90"),
                                        LocalDate.of(2026, 10, 20),
                                        "Alimentação"))),
                                1)));

        return service.answer("Pergunta financeira", 17L);
    }
}
