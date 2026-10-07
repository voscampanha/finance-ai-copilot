package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.springframework.stereotype.Component;

import br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql.SqlExecutor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql.SqlGenerator;
import br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql.SqlValidator;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Component 
public class DynamicSqlTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSqlTool.class);

    private final SqlGenerator generator;
    private final SqlValidator validator;
    private final SqlExecutor executor;

    public DynamicSqlTool(SqlGenerator generator,
                          SqlValidator validator,
                          SqlExecutor executor) {
        this.generator = generator;
        this.validator = validator;
        this.executor = executor;
    }

    @Tool(description = "Executa consultas dinâmicas no banco financeiro do usuário...")
    public String executeDynamicQuery(
            @ToolParam(description = "Pergunta do usuário em linguagem natural") String question,
            ToolContext toolContext) {

        LOGGER.info("Tool 'executeDynamicQuery' iniciada");
        long propertyId = FinancialTools.authenticatedPropertyId(toolContext);

        try {
            String rawSql = generator.generate(question, propertyId);
            String safeSql = validator.validate(rawSql);
            return executor.execute(safeSql, propertyId);
        } catch (SqlValidator.SqlValidationException e) {
            // Não expor o motivo real ao LLM (evita prompt injection de retorno)
            return "Não foi possível gerar uma consulta válida para esta pergunta. Reformule ou seja mais específico.";
        } catch (SqlExecutor.SqlExecutionException e) {
            return e.getMessage();
        }
    }
}