package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialMcpToolsTest {
    @Test
    void searchUsesUserIdFromToolContext() {
        VectorStore vectorStore = mock(VectorStore.class);
        ToolContext toolContext = mock(ToolContext.class);
        when(toolContext.getContext()).thenReturn(Map.of("userId", 42L));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        FinancialMcpTools tools = new FinancialMcpTools(vectorStore,
                new TenantVectorStoreFilter(() -> "different-user"));

        tools.searchFinancialContext("lunch", "Food", "2026-01-01", "2026-01-31", toolContext);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        String filter = requestCaptor.getValue().getFilterExpression().toString();
        assertTrue(filter.contains("42"));
        assertTrue(filter.contains("Food"));
        assertFalse(filter.contains("different-user"));
    }
}