package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final TenantContext tenantContext;
    private final TenantVectorStoreFilter tenantFilter;

    public RagService(
            VectorStore vectorStore,
            TenantContext tenantContext,
            TenantVectorStoreFilter tenantFilter) {
        this.vectorStore = vectorStore;
        this.tenantContext = tenantContext;
        this.tenantFilter = tenantFilter;
    }

    public List<Document> retrieve(String question) {
        long propertyId = tenantContext.requirePropertyId();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(10)
                .filterExpression(tenantFilter.forUserProperty(String.valueOf(propertyId)))
                .build());
    }
}
