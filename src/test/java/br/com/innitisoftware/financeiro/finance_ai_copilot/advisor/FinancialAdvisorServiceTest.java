package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.FinancialAdvisorService;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.DynamicSqlTool;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.FinancialTools;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter.ClassificationResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter.QueryIntent;
import br.com.innitisoftware.financeiro.finance_ai_copilot.web.ChatResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialAdvisorServiceTest {
    private static final String QUESTION = "Pergunta financeira de teste";
    private static final long PROPERTY_ID = 17L;
    private static final String RESPONSE = "resposta";
    private static final ChatResponse RESPONSE_DTO = new ChatResponse(
            RESPONSE,
            List.of("Insight de teste"),
            List.of(new ChatResponse.DadoFinanceiro(
                    "21/10", "Compra de teste", "Alimentação", 25.50)));

    private final ChatClient chatClient = mock(ChatClient.class);
    private final FinancialTools financialTools = mock(FinancialTools.class);
    private final DynamicSqlTool dynamicSqlTool = mock(DynamicSqlTool.class);
    private final QueryRouter queryRouter = mock(QueryRouter.class);
    private final ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);

    private FinancialAdvisorService service;

    @BeforeEach
    void setUp() {
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(ChatResponse.class)).thenReturn(RESPONSE_DTO);
        when(responseSpec.entity(
                org.mockito.ArgumentMatchers.eq(ChatResponse.class), any()))
                .thenReturn(RESPONSE_DTO);
        service = new FinancialAdvisorService(
                chatClient, financialTools, dynamicSqlTool, queryRouter);
    }

    @Test
    void analyticalQuestionUsesAggregationPromptAndSqlTool() {
        when(queryRouter.classifyWithDetails(QUESTION))
                .thenReturn(new ClassificationResult(QueryIntent.ANALYTICAL, 0.9, "agregação"));

        assertEquals(RESPONSE_DTO, service.answer(QUESTION, PROPERTY_ID));

        verify(queryRouter).classifyWithDetails(QUESTION);
        verify(requestSpec).system(argThat((String prompt) ->
                prompt.contains("AGREGAÇÃO") && prompt.contains("NUNCA calcule mentalmente")));
        verify(requestSpec).tools(dynamicSqlTool);
        verify(requestSpec, never()).tools(financialTools, dynamicSqlTool);
        verify(requestSpec).toolContext(Map.of("propertyId", PROPERTY_ID, "userId", 4L));
        verify(requestSpec).user(QUESTION);
    }

    @Test
    void relationalQuestionUsesOrderedListingPromptAndSqlTool() {
        when(queryRouter.classifyWithDetails(QUESTION))
                .thenReturn(new ClassificationResult(QueryIntent.RELATIONAL, 0.9, "listagem"));

        assertEquals(RESPONSE_DTO, service.answer(QUESTION, PROPERTY_ID));

        verify(queryRouter).classifyWithDetails(QUESTION);
        verify(requestSpec).system(argThat((String prompt) ->
                prompt.contains("LISTAGEM ordenada/filtrada")
                        && prompt.contains("ORDER BY date DESC, LIMIT")));
        verify(requestSpec).tools(dynamicSqlTool);
        verify(requestSpec, never()).tools(financialTools, dynamicSqlTool);
        verify(requestSpec).toolContext(Map.of("propertyId", PROPERTY_ID, "userId", 4L));
        verify(requestSpec).user(QUESTION);
    }

    @Test
    void contextualQuestionUsesContextSearchAndBothTools() {
        when(queryRouter.classifyWithDetails(QUESTION))
                .thenReturn(new ClassificationResult(QueryIntent.CONTEXTUAL, 0.9, "contexto"));

        assertEquals(RESPONSE_DTO, service.answer(QUESTION, PROPERTY_ID));

        verify(queryRouter).classifyWithDetails(QUESTION);
        verify(requestSpec).system(argThat((String prompt) ->
                prompt.contains("searchFinancialContext")
                        && prompt.contains("NUNCA invente dados")));
        verify(requestSpec).tools(financialTools, dynamicSqlTool);
        verify(requestSpec, never()).tools(dynamicSqlTool);
        verify(requestSpec).toolContext(Map.of("propertyId", PROPERTY_ID, "userId", 4L));
        verify(requestSpec).user(QUESTION);
    }
}
