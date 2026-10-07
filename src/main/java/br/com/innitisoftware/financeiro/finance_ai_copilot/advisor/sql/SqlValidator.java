package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valida SQL gerado dinamicamente pelo LLM antes da execução.
 *
 * Estratégia de defesa em camadas:
 * 1. Parse real via JSqlParser (não regex — regex é bypassável).
 * 2. Whitelist de tabelas + blacklist explícita.
 * 3. Apenas SELECT (raiz e subqueries).
 * 4. Filtro obrigatório de `property_id` (exceto tabela `properties`).
 * 5. LIMIT obrigatório em todas as consultas.
 * 6. Blacklist de funções perigosas.
 * 7. Limite de tamanho e proibição de múltiplos statements.
 *
 * IMPORTANTE: esta classe é a SEGUNDA linha de defesa.
 * A primeira é o usuário de banco com apenas SELECT (menor privilégio).
 */
@Component
public class SqlValidator {

    private static final Logger auditLog = LoggerFactory.getLogger("sql-audit");

    private static final int MAX_SQL_LENGTH = 5000;
    private static final int DEFAULT_LIMIT = 100;

    /** Coluna de isolamento multi-tenant. */
    private static final String TENANT_COLUMN = "property_id";

    /** Tabelas permitidas — alinhadas com SqlSchemaProvider. */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "transactions",
            "categories",
            "groups",
            "finance_accounts",
            "properties"
    );

    /** Tabelas proibidas — defesa em profundidade. */
    private static final Set<String> FORBIDDEN_TABLES = Set.of(
            "users",
            "information_schema",
            "pg_catalog",
            "pg_tables",
            "pg_user",
            "mysql",
            "sys",
            "flyway_schema_history"
    );

    /** Funções perigosas que podem causar DoS ou exfiltração. */
    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
            "pg_sleep", "sleep", "benchmark", "waitfor",
            "load_file", "xp_cmdshell", "sys_eval",
            "pg_read_file", "pg_ls_dir", "lo_import", "lo_export",
            "dblink", "pg_execute_server_program"
    );

    /** Padrão para detectar múltiplos statements separados por `;`. */
    private static final Pattern MULTIPLE_STATEMENTS = Pattern.compile(";\\s*\\S");

    /** Padrão para detectar comentários SQL (podem esconder payloads). */
    private static final Pattern SQL_COMMENTS = Pattern.compile("(--|/\\*|#)");

    /**
     * Valida o SQL e retorna a versão normalizada (com LIMIT aplicado se necessário).
     *
     * @param sql SQL bruto gerado pelo LLM
     * @return SQL validado e pronto para execução
     * @throws SqlValidationException se qualquer validação falhar
     */
    public String validate(String sql) {
        String original = sql;

        try {
            // 1. Sanitização inicial
            sql = sanitize(sql);

            // 2. Comprimento máximo
            if (sql.length() > MAX_SQL_LENGTH) {
                reject("SQL excede o tamanho máximo permitido", sql);
            }

            // 3. Sem múltiplos statements
            if (MULTIPLE_STATEMENTS.matcher(sql).find()) {
                reject("Múltiplos statements não são permitidos", sql);
            }

            // 4. Sem comentários (podem esconder payloads ou burlar validação)
            if (SQL_COMMENTS.matcher(sql).find()) {
                reject("Comentários SQL não são permitidos", sql);
            }

            // 5. Remover `;` final
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1).trim();
            }

            // 6. Parse via JSqlParser
            Statement statement = parse(sql);

            // 7. Apenas SELECT
            if (!(statement instanceof Select)) {
                reject("Apenas comandos SELECT são permitidos", sql);
            }
            Select select = (Select) statement;

            // 8. Whitelist + blacklist de tabelas
            validateTables(select, sql);

            // 9. Sem subqueries com DML/DDL
            validateSubqueries(select, sql);

            // 10. Sem funções perigosas
            validateNoForbiddenFunctions(sql);

            // 11. Filtro obrigatório de property_id
            validateTenantFilter(select);

            // 12. Aplicar LIMIT se necessário
            sql = applyLimitIfNeeded(select, sql);

            auditLog.info("SQL aprovado | original=\"{}\" | final=\"{}\"", original, sql);
            return sql;

        } catch (SqlValidationException e) {
            auditLog.warn("SQL rejeitado | motivo=\"{}\" | sql=\"{}\"", e.getMessage(), original);
            throw e;
        } catch (Exception e) {
            auditLog.error("Erro inesperado na validação | sql=\"{}\"", original, e);
            throw new SqlValidationException("Não foi possível validar a consulta gerada.", e);
        }
    }

    private String sanitize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlValidationException("SQL vazio.");
        }
        // Remove markdown residual que o LLM possa ter deixado
        return sql
                .replaceAll("```sql", "")
                .replaceAll("```", "")
                .trim();
    }

    private Statement parse(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            throw new SqlValidationException("SQL não é parseável.", e);
        }
    }

    private void validateTables(Select select, String sql) {
        TablesNamesFinder<Void> finder = new TablesNamesFinder<>();
        Set<String> tables = finder.getTables((Statement) select);

        if (tables.isEmpty()) {
            throw new SqlValidationException("Nenhuma tabela referenciada.");
        }

        for (String rawTable : tables) {
            String table = rawTable.toLowerCase(Locale.ROOT).split("\\s+")[0];

            if (FORBIDDEN_TABLES.contains(table)) {
                throw new SqlValidationException("Tabela proibida: " + table);
            }
            if (!ALLOWED_TABLES.contains(table)) {
                throw new SqlValidationException("Tabela não autorizada: " + table);
            }
        }
    }

    private void validateSubqueries(Select select, String sql) {
        String lower = sql.toLowerCase();
        // JSqlParser já bloqueia DML dentro de SELECT por parsing.
        // Defesa extra: rejeitar palavras-chave de DML/DDL no texto.
        List<String> forbiddenKeywords = List.of(
                " insert ", " update ", " delete ", " drop ",
                " alter ", " truncate ", " create ", " grant ",
                " revoke ", " merge ", " call ", " execute "
        );
        for (String keyword : forbiddenKeywords) {
            if (lower.contains(keyword)) {
                throw new SqlValidationException(
                        "Palavra-chave proibida detectada: " + keyword.trim());
            }
        }
    }

    private void validateNoForbiddenFunctions(String sql) {
        String lower = sql.toLowerCase();
        for (String fn : FORBIDDEN_FUNCTIONS) {
            if (lower.contains(fn + "(")) {
                throw new SqlValidationException("Função proibida detectada: " + fn);
            }
        }
    }

    /**
     * Regras:
     * - Se a query acessa apenas `properties`, exige filtro em `id`.
     * - Se acessa qualquer outra tabela, exige `property_id` no WHERE.
     * - Aceita tanto `property_id = :propertyId` quanto `t.property_id = :propertyId`
     *   (com ou sem alias).
     */
    private void validateTenantFilter(Select select) {
        if (select instanceof SetOperationList setOperationList) {
            for (Select branch : setOperationList.getSelects()) {
                validateTenantFilter(branch);
            }
            return;
        }

        TablesNamesFinder<Void> finder = new TablesNamesFinder<>();
        Set<String> tables = finder.getTables((Statement) select);
        List<String> normalizedTables = tables.stream()
                .map(t -> t.toLowerCase(Locale.ROOT).split("\\s+")[0])
                .toList();

        boolean onlyProperties = normalizedTables.size() == 1
            && normalizedTables.contains("properties");
        PlainSelect plainSelect = extractPlainSelect(select);
        Expression where = plainSelect == null ? null : plainSelect.getWhere();

        if (onlyProperties) {
            if (!containsRequiredEquality(where, "id")) {
                throw new SqlValidationException(
                        "Consulta em `properties` sem filtro de `id`.");
            }
            return;
        }

        if (!containsRequiredEquality(where, TENANT_COLUMN)) {
            throw new SqlValidationException(
                    "Filtro obrigatório ausente: `property_id`.");
        }
    }

    @SuppressWarnings("removal")
    private boolean containsRequiredEquality(Expression expression, String columnName) {
        if (expression instanceof Parenthesis parenthesis) {
            return containsRequiredEquality(parenthesis.getExpression(), columnName);
        }
        if (expression instanceof AndExpression andExpression) {
            return containsRequiredEquality(andExpression.getLeftExpression(), columnName)
                    || containsRequiredEquality(andExpression.getRightExpression(), columnName);
        }
        if (!(expression instanceof EqualsTo equalsTo)) {
            return false;
        }

        return (isRequiredColumn(equalsTo.getLeftExpression(), columnName)
                    && isAllowedFilterValue(equalsTo.getRightExpression()))
                || (isRequiredColumn(equalsTo.getRightExpression(), columnName)
                    && isAllowedFilterValue(equalsTo.getLeftExpression()));
    }

    private boolean isRequiredColumn(Expression expression, String columnName) {
        return expression instanceof Column column
                && column.getColumnName().equalsIgnoreCase(columnName);
    }

    private boolean isAllowedFilterValue(Expression expression) {
        return expression instanceof JdbcParameter
                || expression instanceof JdbcNamedParameter
                || expression instanceof LongValue;
    }

    /**
     * Aplica ou limita o LIMIT 100 em consultas simples e operações de conjunto.
     */
    private String applyLimitIfNeeded(Select select, String sql) {
        PlainSelect plainSelect = extractPlainSelect(select);
        if (plainSelect == null) {
            return applyLimit(select.getLimit(), select::setLimit, select::toString, sql);
        }

        return applyLimit(plainSelect.getLimit(), plainSelect::setLimit,
                plainSelect::toString, sql);
    }

    private String applyLimit(Limit existingLimit, java.util.function.Consumer<Limit> setLimit,
                              java.util.function.Supplier<String> toSql, String originalSql) {
        if (existingLimit != null) {
            Expression rowCount = existingLimit.getRowCount();
            if (rowCount instanceof LongValue longValue
                    && longValue.getValue() <= DEFAULT_LIMIT) {
                return originalSql;
            }
            existingLimit.setRowCount(new LongValue(DEFAULT_LIMIT));
            return toSql.get();
        }

        Limit limit = new Limit();
        limit.setRowCount(new LongValue(DEFAULT_LIMIT));
        setLimit.accept(limit);
        return toSql.get();
    }

    private PlainSelect extractPlainSelect(Select select) {
        if (select.getPlainSelect() != null) {
            return select.getPlainSelect();
        }
        if (select instanceof PlainSelect ps) {
            return ps;
        }
        if (select instanceof ParenthesedSelect ps) {
            return extractPlainSelect(ps.getSelect());
        }
        return null;
    }

    private void reject(String reason, String sql) {
        throw new SqlValidationException(reason);
    }

    public static class SqlValidationException extends RuntimeException {
        public SqlValidationException(String message) {
            super(message);
        }
        public SqlValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}