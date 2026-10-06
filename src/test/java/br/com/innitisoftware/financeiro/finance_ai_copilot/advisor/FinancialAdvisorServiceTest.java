package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialAdvisorServiceTest {
    private final ChatClient chatClient = mock(ChatClient.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final FinancialTools financialToolsConfig = mock(FinancialTools.class);
    private final UserIdentity userIdentity = mock(UserIdentity.class);

    private FinancialAdvisorService service;
        private ChatClient.ChatClientRequestSpec requestSpec;

    @BeforeEach
    void setUp() {
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("resposta");
        service = new FinancialAdvisorService(chatClient, vectorStore,
                financialToolsConfig, new QueryRouter());
    }

    @Test
    void analyticalQuestionsSkipVectorSearchAndSendEmptyContextWithTools() {
        service.answer("Quanto gastei com mercado?", 1);

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        verify(requestSpec).tools(financialToolsConfig);
        var systemPromptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPromptCaptor.capture());
        assertTrue(systemPromptCaptor.getValue().contains(
            "DEVE chamar a ferramenta 'calculateCategoryStats'"));
        verify(requestSpec).user(org.mockito.ArgumentMatchers
            .<Consumer<ChatClient.PromptUserSpec>>argThat(userPrompt -> {
                ChatClient.PromptUserSpec prompt = mock(ChatClient.PromptUserSpec.class, RETURNS_SELF);
                userPrompt.accept(prompt);
                verify(prompt).param(eq("context"), eq(""));
                return true;
            }));
    }

}