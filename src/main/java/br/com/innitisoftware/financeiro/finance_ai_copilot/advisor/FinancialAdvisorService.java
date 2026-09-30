package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class FinancialAdvisorService {
        private static final Logger LOGGER = LoggerFactory.getLogger(FinancialAdvisorService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final TenantVectorStoreFilter tenantFilter;
    private final FinancialMcpTools financialTools;

    public FinancialAdvisorService(ChatClient chatClient,
                                   VectorStore vectorStore,
                                   TenantVectorStoreFilter tenantFilter,
                                   FinancialMcpTools financialTools) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.tenantFilter = tenantFilter;
        this.financialTools = financialTools;
    }

    public String answer(String question) {
        var documents = timedStage("Busca vetorial", () -> vectorStore.similaritySearch(SearchRequest.builder()
                        .query(question)
                        .topK(8)
                        // .filterExpression(tenantFilter.forAuthenticatedUser())
                        .build()),
                result -> "documentos_encontrados=" + result.size());
        String context = timedStage("Montagem do contexto", () -> documents.stream()
                        .map(document -> document.getText())
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("Nenhum registro financeiro relevante foi encontrado."),
                result -> "contexto_montado, caracteres=" + result.length()
                        + ", fallback=" + documents.isEmpty());
        return timedStage("Geração da resposta", () -> chatClient.prompt()
                        .tools(financialTools)
                        .system("Você é um consultor financeiro. Responda em português, usando somente o contexto fornecido. "
                                + "Não invente dados, não exponha identificadores internos e deixe claro quando não houver evidência.")
                        .user(user -> user.text("Contexto financeiro:\n{context}\n\nPergunta: {question}")
                                .param("context", context)
                                .param("question", question))
                        .call()
                        .content(),
                result -> "resposta_gerada, caracteres=" + (result == null ? 0 : result.length()));
    }

    private <T> T timedStage(String stage, Supplier<T> operation, Function<T, String> resultSummary) {
        long startedAt = System.nanoTime();
        try {
            T result = operation.get();
            LOGGER.info("Etapa '{}' concluída: resultado={}, tempo_ms={}",
                    stage, resultSummary.apply(result), elapsedMillis(startedAt));
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("Etapa '{}' falhou: resultado=erro ({}), tempo_ms={}",
                    stage, exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
