package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class FinancialMcpTools {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialMcpTools.class);

    private final VectorStore vectorStore;
    private final TenantVectorStoreFilter tenantFilter;

    public FinancialMcpTools(VectorStore vectorStore, TenantVectorStoreFilter tenantFilter) {
        this.vectorStore = vectorStore;
        this.tenantFilter = tenantFilter;
    }

    @Tool(description = "Busca registros financeiros do usuário autenticado por período ou categoria.")
    public String searchFinancialContext(String query, String categoria, String dataInicial, String dataFinal) {
        long startedAt = System.nanoTime();
        LOGGER.info("Tool 'searchFinancialContext' iniciada");
        try {
            var documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(10)
                .filterExpression(tenantFilter.forAuthenticatedUserAndCategory(categoria)).build());
            String result = documents.stream().map(document -> document.getText())
                .reduce((left, right) -> left + "\n" + right).orElse("");
            LOGGER.info("Tool 'searchFinancialContext' concluída: documentos={}, tempo_ms={}",
                documents.size(), elapsedMillis(startedAt));
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("Tool 'searchFinancialContext' falhou: erro={}, tempo_ms={}",
                exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw exception;
        }
    }

        private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
        }
}
