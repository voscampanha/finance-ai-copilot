package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantVectorStoreFilter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class FinancialAdvisorService {
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
        var documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(8)
                .filterExpression(tenantFilter.forAuthenticatedUser())
                .build());
        String context = documents.stream()
                .map(document -> document.getText())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("Nenhum registro financeiro relevante foi encontrado.");
        return chatClient.prompt()
                .tools(financialTools)
                .system("Você é um consultor financeiro. Responda em português, usando somente o contexto fornecido. "
                        + "Não invente dados, não exponha identificadores internos e deixe claro quando não houver evidência.")
                .user(user -> user.text("Contexto financeiro:\n{context}\n\nPergunta: {question}")
                        .param("context", context)
                        .param("question", question))
                .call()
                .content();
    }
}
