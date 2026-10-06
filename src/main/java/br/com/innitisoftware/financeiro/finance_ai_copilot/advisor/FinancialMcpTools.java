package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

    @Tool(description = "Busca registros financeiros do usuário autenticado por período ou categoria. Use a pergunta original do usuário como query, sem resumir ou substituir por termos genéricos.")
    public String searchFinancialContext(
                                         @ToolParam(description = "Pergunta original do usuário que deve orientar a busca semântica; preserve o texto e os termos usados pelo usuário") String query,
                                         @ToolParam(description = "Categoria ou grupo financeiro mencionado pelo usuário, se houver") String categoria,
                                         @ToolParam(description = "Data inicial do período mencionado, no formato YYYY-MM-DD, se houver") String dataInicial,
                                         @ToolParam(description = "Data final do período mencionado, no formato YYYY-MM-DD, se houver") String dataFinal,
                                         ToolContext toolContext) {
        long startedAt = System.nanoTime();
        LOGGER.info("Tool 'searchFinancialContext' iniciada");
        try {
            var documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(10)
                .filterExpression(tenantFilter.forUserCategoryAndDateRange(
                    String.valueOf(FinancialToolsConfig.authenticatedUserId(toolContext)), categoria,
                    dataInicial, dataFinal))
                .build());
            LOGGER.info("Query: {}, Categoria: {}, Data Inicial: {}, Data Final: {}, UserId: {}, Documentos encontrados: {}",
                query, categoria, dataInicial, dataFinal, FinancialToolsConfig.authenticatedUserId(toolContext), documents.size());
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
