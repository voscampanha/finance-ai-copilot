package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class FinancialAdvisorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialAdvisorService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final FinancialTools financialTools;
    private final DynamicSqlTool dynamicSqlTool;
    private final QueryRouter queryRouter;

    public FinancialAdvisorService(ChatClient chatClient,
                                   VectorStore vectorStore,
                                   FinancialTools financialTools,
                                   DynamicSqlTool dynamicSqlTool,
                                   QueryRouter queryRouter) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.financialTools = financialTools;
        this.dynamicSqlTool = dynamicSqlTool;
        this.queryRouter = queryRouter;
    }

    public String answer(String question, long propertyId) {
        QueryRouter.QueryType queryType = timedStage("Roteamento", () -> queryRouter.classify(question),
                result -> "tipo=" + result);
        // List<Document> documents = queryType == QueryRouter.QueryType.ANALYTICAL
        //         ? List.of()
        //         : timedStage("Busca vetorial", () -> vectorStore.similaritySearch(SearchRequest.builder()
        //                         .query(question)
        //                         .topK(8 )
        //                         // .filterExpression(tenantFilter.forAuthenticatedUser())
        //                         .build()).stream()
        //                 .toList(),
        //         result -> "documentos_relevantes=" + result.size());
        // String context = queryType == QueryRouter.QueryType.ANALYTICAL
        //         ? ""
        //         : timedStage("Montagem do contexto", () -> documents.stream()
        //                         .map(document -> document.getText())
        //                         .reduce((left, right) -> left + "\n" + right)
        //                         .orElse("Nenhum registro financeiro relevante foi encontrado."),
        //         result -> "contexto_montado, caracteres=" + result.length()
        //                 + ", fallback=" + documents.isEmpty());
        // LOGGER.info("Contexto final para a pergunta: '{}'\n{}", question, context);
        return timedStage("Geração da resposta", () -> chatClient.prompt()
                        .tools(financialTools, dynamicSqlTool)
                        .toolContext(Map.of("propertyId", propertyId))
                        .system(systemInstructions(queryType))
                        .user(user -> user.text("Contexto financeiro:\n{context}\n\nPergunta: {question}")
                                .param("context", "")
                                .param("question", question))
                        .call()
                        .content(),
                result -> "resposta_gerada, caracteres=" + (result == null ? 0 : result.length()));
    }

    private String systemInstructions(QueryRouter.QueryType queryType) {
        String routingInstruction = queryType == QueryRouter.QueryType.ANALYTICAL
                                ? "A pergunta é analítica. Chame 'executeDynamicQuery' antes de responder."
                                : "A pergunta é contextual. Use os documentos fornecidos no contexto e chame 'searchFinancialContext' quando eles forem insuficientes.";
        return "Você é um consultor financeiro pessoal. Responda SEMPRE em português do Brasil, de forma objetiva e clara. "
                + "Para QUALQUER pergunta sobre totais, somas, médias, contagens, frequências ou agregações numéricas, "
                + "DEVE chamar a ferramenta 'executeDynamicQuery' para obter os resultados e usá-los na resposta. "
                + "NUNCA calcule mentalmente nem invente valores. "
                + "Para perguntas sobre contexto, use os documentos fornecidos no contexto. "
                + "Se o contexto estiver vazio e a pergunta não for analítica, diga explicitamente que não há evidência suficiente. "
                + "NUNCA exponha IDs internos, nomes de tabelas, colunas ou detalhes técnicos do banco. "
                + "Se uma ferramenta retornar erro ou vazio, informe o usuário sem inventar dados. "
                + "Formate valores monetários em R$ com duas casas decimais. " + routingInstruction;
    }

    private <T> T timedStage(String stage, Supplier<T> operation, Function<T, String> resultSummary) {
        long startedAt = System.nanoTime();
        try {
            T result = operation.get();
            LOGGER.info("Etapa '{}' concluída: resultado={}, tempo_ms={}",
                    stage, resultSummary.apply(result), elapsedMillis(startedAt));
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("Etapa '{}' falhou: resultado=erro ({}), tempo_ms={}",
                    stage, exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
