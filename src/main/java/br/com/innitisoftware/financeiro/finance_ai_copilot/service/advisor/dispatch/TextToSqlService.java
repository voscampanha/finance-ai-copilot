package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.DynamicSqlTool;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.FinancialTools;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter.QueryIntent;
import br.com.innitisoftware.financeiro.finance_ai_copilot.web.ChatResponse;

@Service
public class TextToSqlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextToSqlService.class);

    private final ChatClient chatClient;
    private final FinancialTools financialTools;
    private final DynamicSqlTool dynamicSqlTool;
    private final QueryRouter queryRouter;
    private final TenantContext tenantContext;

    public TextToSqlService(
            ChatClient chatClient,
            FinancialTools financialTools,
            DynamicSqlTool dynamicSqlTool,
            QueryRouter queryRouter,
            TenantContext tenantContext) {
        this.chatClient = chatClient;
        this.financialTools = financialTools;
        this.dynamicSqlTool = dynamicSqlTool;
        this.queryRouter = queryRouter;
        this.tenantContext = tenantContext;
    }

    public Object execute(String question) {
        long propertyId = tenantContext.requirePropertyId();
        Map<String, Object> toolContext = Map.of("propertyId", propertyId);
        QueryIntent intent = queryRouter.classifyWithDetails(question).intent();

        if (intent == QueryIntent.CONTEXTUAL) {
            return chatClient.prompt()
                    .system("""
                            Você é um consultor financeiro pessoal. Responda sempre em português do Brasil.
                            Use searchFinancialContext para perguntas sobre significado, descrição ou contexto.
                            Baseie-se somente nos resultados das ferramentas e não invente dados.
                            """)
                    .tools(financialTools, dynamicSqlTool)
                    .toolContext(toolContext)
                    .user(question)
                    .call()
                    .entity(ChatResponse.class);
        }

        String systemPrompt = intent == QueryIntent.ANALYTICAL
                ? """
                        Você é um consultor financeiro. Responda em português.
                        Gere SQL de agregação para esta pergunta. Use executeDynamicQuery e nunca calcule mentalmente.
                        """
                : """
                        Você é um consultor financeiro. Responda em português.
                        Gere SQL de listagem ordenada ou filtrada. Use executeDynamicQuery.
                        """;
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .tools(dynamicSqlTool)
                    .toolContext(toolContext)
                    .user(question)
                    .call()
                    .entity(ChatResponse.class, spec -> spec.validateSchema());
        } catch (RuntimeException exception) {
            LOGGER.warn("Text-to-SQL response generation failed | error={}",
                    exception.getClass().getSimpleName());
            return new ChatResponse(
                    "Desculpe, não consegui estruturar os dados no formato esperado.",
                    List.of("Ocorreu um erro de formatação na resposta do modelo."),
                    List.of());
        }
    }
}
