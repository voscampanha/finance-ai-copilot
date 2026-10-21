package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import br.com.innitisoftware.financeiro.finance_ai_copilot.web.ChatResponse;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.DynamicSqlTool;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.FinancialTools;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.QueryRouter.ClassificationResult;

@Service
public class FinancialAdvisorService {
    private static final Logger log = LoggerFactory.getLogger(FinancialAdvisorService.class);

    private final ChatClient chatClient;
    private final FinancialTools financialTools;
    private final DynamicSqlTool dynamicSqlTool;
    private final QueryRouter queryRouter;

    public FinancialAdvisorService(ChatClient chatClient,
            FinancialTools financialTools,
            DynamicSqlTool dynamicSqlTool,
            QueryRouter queryRouter) {
        this.chatClient = chatClient;
        this.financialTools = financialTools;
        this.dynamicSqlTool = dynamicSqlTool;
        this.queryRouter = queryRouter;
    }

    public ChatResponse answer(String question, long propertyId) {
        ClassificationResult result = queryRouter.classifyWithDetails(question);

        log.info("Rota escolhida | intent={} | confiança={} | motivo=\"{}\"",
                result.intent(), result.confidence(), result.reason());

        var userId = 4L; // TODO: obter o ID do usuário autenticado
        return switch (result.intent()) {
            case ANALYTICAL -> executeWithSqlTool(question, propertyId, userId, "ANALYTICAL");
            case RELATIONAL -> executeWithSqlTool(question, propertyId, userId, "RELATIONAL");
            case CONTEXTUAL -> executeContextual(question, propertyId, userId);
        };
    }

    private ChatResponse executeWithSqlTool(String question,
            Long propertyId,
            Long userId,
            String intent) {

        String systemPrompt = switch (intent) {
            case "ANALYTICAL" -> """
                    Você é um consultor financeiro. Responda em português.
                    Para esta pergunta, gere SQL de AGREGAÇÃO (SUM, AVG, COUNT, GROUP BY).
                    Use a ferramenta executeDynamicQuery. NUNCA calcule mentalmente.
                    """;
            case "RELATIONAL" -> """
                    Você é um consultor financeiro. Responda em português.
                    Para esta pergunta, gere SQL de LISTAGEM ordenada/filtrada
                    (ORDER BY date DESC, LIMIT, filtros temporais).
                    Use a ferramenta executeDynamicQuery.
                    """;
            default -> throw new IllegalArgumentException("Intent inválido: " + intent);
        };

        return chatClient.prompt()
                .system(systemPrompt)
                .tools(dynamicSqlTool)
                .toolContext(Map.of(
                        "propertyId", propertyId,
                        "userId", userId))
                .user(question)
                .call()
                .entity(ChatResponse.class);
    }

    private ChatResponse executeContextual(String question, Long propertyId, Long userId) {
        return chatClient.prompt()
                .system("""
                        Você é um consultor financeiro pessoal. Responda SEMPRE em português do Brasil,
                        de forma objetiva e clara.

                        Você tem acesso à ferramenta `searchFinancialContext`, que busca no histórico financeiro
                        do usuário por similaridade semântica.

                        REGRAS CRÍTICAS:
                        1. Para perguntas sobre SIGNIFICADO, DESCRIÇÃO ou CONTEXTO de lançamentos
                           (ex: "o que era aquela compra na padaria?", "aquele gasto que anotei"),
                           você DEVE usar `searchFinancialContext` antes de responder.
                        2. Use SOMENTE as informações retornadas pela ferramenta. NUNCA invente dados.
                        3. Se a ferramenta retornar "Nenhum registro relevante", diga explicitamente
                           ao usuário: "Não encontrei informações suficientes nos seus registros
                           para responder isso."
                        4. Se a pergunta exigir AGREGAÇÃO (total, soma, média) ou LISTAGEM ORDENADA
                           (último, mais recente), informe ao usuário que essa pergunta precisa ser
                           feita de outra forma. NÃO tente calcular mentalmente.
                        5. NUNCA exponha IDs internos, nomes de tabelas ou colunas.
                        6. Formate valores monetários em R$ com duas casas decimais.
                        7. Se após usar `searchFinancialContext` você não encontrar dados suficientes E a pergunta 
                            puder ser respondida com uma consulta estruturada (listagem, ordenação), 
                            use a ferramenta `executeDynamicQuery` para tentar uma segunda abordagem.
                        8. PRIORIDADE: Para perguntas desta rota, tente SEMPRE `searchFinancialContext` 
                            primeiro. Só use `executeDynamicQuery` se `searchFinancialContext` retornar vazio 
                            E a pergunta for claramente sobre listagem ou ordenação.    
                        """)
                .tools(financialTools, dynamicSqlTool)
                .toolContext(Map.of(
                        "propertyId", propertyId,
                        "userId", userId))
                .user(question)
                .call()
                .entity(ChatResponse.class);
    }
}
