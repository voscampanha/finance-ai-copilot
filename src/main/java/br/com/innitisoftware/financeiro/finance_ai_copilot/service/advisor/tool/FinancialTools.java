package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;


@Component
public class FinancialTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialTools.class);
    private static final String PROPERTY_ID_CONTEXT_KEY = "propertyId";

    private final VectorStore vectorStore;
    private final TenantVectorStoreFilter tenantFilter;

    public FinancialTools(VectorStore vectorStore, TenantVectorStoreFilter tenantFilter) {
        this.vectorStore = vectorStore;
        this.tenantFilter = tenantFilter;
    }


    @Tool(description = """
        Busca documentos relevantes no histórico financeiro do usuário 
        usando similaridade semântica (embeddings). 
        Use para perguntas sobre SIGNIFICADO, DESCRIÇÃO ou CONTEXTO de 
        lançamentos — por exemplo: 'o que era aquela compra na padaria?', 
        'aquele gasto que anotei como urgente', 'resuma meus gastos com lazer'.
        NÃO use para perguntas que exijam agregação (total, soma, média) 
        ou listagem ordenada (último, mais recente).
        """)
    public String searchFinancialContext(
                                         @ToolParam(description = 
                                            "Pergunta original do usuário que deve orientar a busca semântica; "
                                            + "preserve o texto e os termos usados pelo usuário")
                                         String query,
                                         ToolContext toolContext) {
        long startedAt = System.nanoTime();
        LOGGER.info("Tool 'searchFinancialContext' iniciada");
        try {
            var documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(10)
                .filterExpression(tenantFilter.forUserProperty(
                    String.valueOf(FinancialTools.authenticatedPropertyId(toolContext))))
                .build());
            LOGGER.info("Query: {}, PropertyId: {}, Documentos encontrados: {}",
                query, FinancialTools.authenticatedPropertyId(toolContext), documents.size());
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

    static long authenticatedPropertyId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(PROPERTY_ID_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String propertyId && propertyId.matches("\\d{1,19}")) {
            try {
                return Long.parseLong(propertyId);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Identidade de propriedade inválida no contexto da ferramenta", exception);
            }
        }
        throw new IllegalStateException("Identidade de propriedade ausente ou inválida no contexto da ferramenta");
    }

}