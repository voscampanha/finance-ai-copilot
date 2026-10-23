package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor;

import java.text.NumberFormat;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.DispatchResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch.QueryDispatcher;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.CategoriaTotal;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.Lancamento;
import br.com.innitisoftware.financeiro.finance_ai_copilot.web.ChatResponse;

@Service
public class FinancialAdvisorService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final QueryDispatcher dispatcher;
    private final ChatClient chatClient;
    private final TenantContext tenantContext;

    public FinancialAdvisorService(
            QueryDispatcher dispatcher,
            ChatClient chatClient,
            TenantContext tenantContext) {
        this.dispatcher = dispatcher;
        this.chatClient = chatClient;
        this.tenantContext = tenantContext;
    }

    public ChatResponse answer(String question, long propertyId) {
        tenantContext.set(propertyId);
        try {
            return toChatResponse(dispatcher.dispatch(question), question);
        } finally {
            tenantContext.clear();
        }
    }

    private ChatResponse toChatResponse(DispatchResult result, String question) {
        if (result instanceof DispatchResult.Semantic semantic) {
            return semanticResponse(semantic.result());
        }
        if (result instanceof DispatchResult.Contextual contextual) {
            if (contextual.context().isBlank()) {
                return new ChatResponse(
                        "Não encontrei informações suficientes nos seus registros para responder isso.",
                        List.of(),
                        List.of());
            }
            return chatClient.prompt()
                    .system("""
                            Responda em português do Brasil usando somente o contexto financeiro abaixo.
                            Se ele não for suficiente, diga isso explicitamente. Não invente dados.
                            """)
                    .user("""
                            Pergunta: %s

                            Contexto financeiro recuperado:
                            %s
                            """.formatted(question, contextual.context()))
                    .call()
                    .entity(ChatResponse.class);
        }
        String fallback = ((DispatchResult.Fallback) result).result();
        if (fallback.isBlank()) {
            return new ChatResponse("Nenhum registro encontrado.", List.of(), List.of());
        }
        return chatClient.prompt()
                .system("""
                        Responda em português do Brasil, de forma clara e concisa, usando somente os resultados
                        fornecidos. Não invente informações. Se não houver dados suficientes, diga isso explicitamente.
                        """)
                .user("""
                        Pergunta: %s

                        Resultado da consulta financeira:
                        %s
                        """.formatted(question, fallback))
                .call()
                .entity(ChatResponse.class, spec -> spec.validateSchema());
    }

    private ChatResponse semanticResponse(MetricResult result) {
        if (result instanceof MetricResult.Failure failure) {
            return new ChatResponse(failure.reason(), List.of(), List.of());
        }

        MetricResult.Success success = (MetricResult.Success) result;
        Object payload = success.rows().stream()
                .map(row -> row.get("payload"))
                .findFirst()
                .orElse(null);
        if (payload instanceof List<?> list
                && list.stream().allMatch(CategoriaTotal.class::isInstance)) {
            List<CategoriaTotal> categories = list.stream()
                    .map(CategoriaTotal.class::cast)
                    .toList();
            if (categories.isEmpty()) {
                return new ChatResponse("Nenhum registro encontrado.", List.of(), List.of());
            }

            List<ChatResponse.DadoFinanceiro> data = categories.stream()
                    .map(row -> new ChatResponse.DadoFinanceiro(
                            null, null, row.categoria(), row.total().doubleValue()))
                    .toList();
            List<String> insights = categories.stream()
                    .map(row -> "Total em %s no período: %s."
                            .formatted(row.categoria(), formatCurrency(row.total())))
                    .toList();
            String answer = categories.size() == 1
                    ? "O total gasto em %s no período foi %s."
                            .formatted(categories.getFirst().categoria(),
                                    formatCurrency(categories.getFirst().total()))
                    : "Aqui está o total gasto por categoria.";
            return new ChatResponse(answer, insights, data);
        }
        if (payload instanceof Lancamento lancamento) {
            ChatResponse.DadoFinanceiro data = new ChatResponse.DadoFinanceiro(
                    lancamento.data().format(DATE_FORMAT),
                    lancamento.descricao(),
                    lancamento.categoria(),
                    lancamento.valor().doubleValue());
            String answer = switch (success.metricName()) {
                case "maior_lancamento" -> "Este é o lançamento de maior valor.";
                case "ultimo_lancamento" -> "Este é o lançamento mais recente.";
                default -> throw new IllegalStateException(
                        "A métrica retornou um lançamento sem resposta configurada: " + success.metricName());
            };
            return new ChatResponse(answer, List.of(), List.of(data));
        }
        if (payload == null) {
            return new ChatResponse("Nenhum registro encontrado.", List.of(), List.of());
        }
        throw new IllegalStateException("A métrica retornou um payload não suportado.");
    }

    private String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(amount);
    }
}
