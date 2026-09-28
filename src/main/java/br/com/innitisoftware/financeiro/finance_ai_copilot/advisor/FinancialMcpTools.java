package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class FinancialMcpTools {
    private final VectorStore vectorStore;
    private final TenantVectorStoreFilter tenantFilter;

    public FinancialMcpTools(VectorStore vectorStore, TenantVectorStoreFilter tenantFilter) {
        this.vectorStore = vectorStore;
        this.tenantFilter = tenantFilter;
    }

    @Tool(description = "Busca registros financeiros do usuário autenticado por período ou categoria.")
    public String searchFinancialContext(String query, String categoria, String dataInicial, String dataFinal) {
        var documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(10)
                .filterExpression(tenantFilter.forAuthenticatedUserAndCategory(categoria)).build());
        return documents.stream().map(document -> document.getText()).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
