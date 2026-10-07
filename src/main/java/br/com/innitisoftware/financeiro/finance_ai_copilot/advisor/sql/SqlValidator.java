package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
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
     * Funções de agregação: colunas dentro delas são agregadas e, portanto,
     * não precisam aparecer no GROUP BY.
     */
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
            "sum", "count", "avg", "min", "max",
            "group_concat", "json_arrayagg", "json_objectagg",
            "std", "stddev", "stddev_pop", "stddev_samp",
            "variance", "var_pop", "var_samp",
            "bit_and", "bit_or", "bit_xor"
    );

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

            // 12. `WITH ROLLUP` exige que toda coluna não agregada exibida esteja no GROUP BY
            validateRollupGrouping(select);

            // 13. Aplicar LIMIT se necessário
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
            String table = normalizeTableName(rawTable);

            if (FORBIDDEN_TABLES.contains(table)) {
                throw new SqlValidationException("Tabela proibida: " + table);
            }
            if (!ALLOWED_TABLES.contains(table)) {
                throw new SqlValidationException("Tabela não autorizada: " + table);
            }
        }
    }

    private String normalizeTableName(String rawTable) {
        String table = rawTable.toLowerCase(Locale.ROOT).split("\\s+")[0];
        if (table.length() >= 2
                && ((table.startsWith("`") && table.endsWith("`"))
                || (table.startsWith("\"") && table.endsWith("\"")))) {
            return table.substring(1, table.length() - 1)
                    .replace("``", "`")
                    .replace("\"\"", "\"");
        }
        return table;
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
     * Every domain table must have its own tenant predicate, connected to the
     * WHERE clause with AND and bound to the trusted :propertyId parameter.
     */
    private void validateTenantFilter(Select select) {
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            throw new SqlValidationException("Consultas com CTE não são permitidas.");
        }
        if (select instanceof SetOperationList setOperationList) {
            for (Select branch : setOperationList.getSelects()) {
                validateTenantFilter(branch);
            }
            return;
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            throw new SqlValidationException("Formato de consulta não suportado para isolamento de tenant.");
        }

        validateNoNestedSelects(plainSelect);
        Expression where = plainSelect.getWhere();
        List<Table> tables = new java.util.ArrayList<>();
        tables.add(requireDirectTable(plainSelect.getFromItem()));
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                tables.add(requireDirectTable(join.getFromItem()));
            }
        }

        for (Table table : tables) {
            String tableName = table.getName().toLowerCase(Locale.ROOT);
            String columnName = tableName.equals("properties") ? "id" : TENANT_COLUMN;
            String qualifier = table.getAlias() == null
                    ? tableName
                    : table.getAlias().getName();
            if (!containsRequiredEquality(where, columnName, qualifier)) {
                throw new SqlValidationException(
                        "Filtro obrigatório de tenant ausente ou inválido para a tabela `" + tableName + "`.");
            }
        }
    }

    /**
     * Em consultas com `WITH ROLLUP`, o MySQL 8 (sql_mode=ONLY_FULL_GROUP_BY) rejeita
     * colunas não agregadas que não estejam no GROUP BY — inclusive as funcionalmente
     * dependentes da chave primária agrupada, exceção que só vale sem ROLLUP.
     * Antecipamos aqui para não gastar uma ida ao banco com SQL que ele já recusaria.
     */
    private void validateRollupGrouping(Select select) {
        if (select instanceof SetOperationList setOperationList) {
            setOperationList.getSelects().forEach(this::validateRollupGrouping);
            return;
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            return;
        }
        GroupByElement groupBy = plainSelect.getGroupBy();
        if (groupBy == null || !groupBy.isMysqlWithRollup()) {
            return;
        }

        Set<String> groupedColumns = new LinkedHashSet<>();
        List<Expression> groupByExpressions = groupBy.getGroupByExpressionList() == null
                ? List.of()
                : groupBy.getGroupByExpressionList().getExpressions();
        groupByExpressions.forEach(expression -> collectGroupedColumnKeys(groupedColumns, expression));

        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            String alias = item.getAliasName() == null
                    ? null
                    : item.getAliasName().toLowerCase(Locale.ROOT);
            // O MySQL aceita agrupar pelo alias do próprio item do SELECT.
            if (alias != null && groupedColumns.contains(alias)) {
                continue;
            }
            for (String column : nonAggregatedColumnKeys(item.getExpression())) {
                int separator = column.indexOf('.');
                String bareName = separator < 0 ? column : column.substring(separator + 1);
                if (groupedColumns.contains(column) || groupedColumns.contains(bareName)) {
                    continue;
                }
                throw new SqlValidationException(
                        "Em consultas com WITH ROLLUP, toda coluna não agregada do SELECT "
                                + "deve aparecer no GROUP BY (sql_mode=ONLY_FULL_GROUP_BY).");
            }
        }
    }

    /** Adiciona as chaves de cada coluna agrupada, com e sem o qualificador de tabela. */
    private void collectGroupedColumnKeys(Set<String> target, Expression expression) {
        if (expression == null) {
            return;
        }
        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S context) {
                target.add(columnKey(column));
                target.add(column.getColumnName().toLowerCase(Locale.ROOT));
                return super.visit(column, context);
            }
        }, null);
    }

    /** Coleta as colunas referenciadas fora de funções de agregação. */
    private Set<String> nonAggregatedColumnKeys(Expression expression) {
        Set<String> columns = new LinkedHashSet<>();
        if (expression == null) {
            return columns;
        }
        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Function function, S context) {
                String name = function.getName();
                if (name != null && AGGREGATE_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT))) {
                    return null;
                }
                return super.visit(function, context);
            }

            @Override
            public <S> Void visit(Column column, S context) {
                columns.add(columnKey(column));
                return super.visit(column, context);
            }
        }, null);
        return columns;
    }

    private String columnKey(Column column) {
        String name = column.getColumnName().toLowerCase(Locale.ROOT);
        return column.getTable() == null
                ? name
                : column.getTable().getName().toLowerCase(Locale.ROOT) + "." + name;
    }

    private Table requireDirectTable(FromItem fromItem) {
        if (fromItem instanceof Table table) {
            return table;
        }
        throw new SqlValidationException(
                "Fontes de consulta devem ser tabelas diretas para validar o isolamento de tenant.");
    }

    private void validateNoNestedSelects(PlainSelect select) {
        ExpressionVisitorAdapter<Void> visitor = new ExpressionVisitorAdapter<>() {
            @Override
            public <S> Void visit(ParenthesedSelect nestedSelect, S context) {
                throw new SqlValidationException(
                        "Subconsultas não são permitidas sem validação independente de tenant.");
            }

            @Override
            public <S> Void visit(ExistsExpression existsExpression, S context) {
                throw new SqlValidationException(
                        "Subconsultas não são permitidas sem validação independente de tenant.");
            }

            @Override
            public <S> Void visit(InExpression inExpression, S context) {
                if (inExpression.getRightExpression() instanceof ParenthesedSelect) {
                    throw new SqlValidationException(
                            "Subconsultas não são permitidas sem validação independente de tenant.");
                }
                return super.visit(inExpression, context);
            }
        };
        select.getSelectItems().forEach(item -> visitExpression(item.getExpression(), visitor));
        visitExpression(select.getWhere(), visitor);
        visitExpression(select.getHaving(), visitor);
        if (select.getGroupBy() != null && select.getGroupBy().getGroupByExpressionList() != null) {
            select.getGroupBy().getGroupByExpressionList().getExpressions()
                    .forEach(expression -> visitExpression(expression, visitor));
        }
        if (select.getOrderByElements() != null) {
            select.getOrderByElements().forEach(orderBy ->
                    visitExpression(orderBy.getExpression(), visitor));
        }
        if (select.getJoins() != null) {
            select.getJoins().stream()
                    .flatMap(join -> join.getOnExpressions().stream())
                    .forEach(expression -> visitExpression(expression, visitor));
        }
    }

    private void visitExpression(Expression expression, ExpressionVisitorAdapter<Void> visitor) {
        if (expression != null) {
            expression.accept(visitor, null);
        }
    }

    @SuppressWarnings("removal")
    private boolean containsRequiredEquality(Expression expression, String columnName, String qualifier) {
        if (expression instanceof Parenthesis parenthesis) {
            return containsRequiredEquality(parenthesis.getExpression(), columnName, qualifier);
        }
        if (expression instanceof AndExpression andExpression) {
            return containsRequiredEquality(andExpression.getLeftExpression(), columnName, qualifier)
                    || containsRequiredEquality(andExpression.getRightExpression(), columnName, qualifier);
        }
        if (!(expression instanceof EqualsTo equalsTo)) {
            return false;
        }

        return (isRequiredColumn(equalsTo.getLeftExpression(), columnName, qualifier)
                    && isTrustedTenantParameter(equalsTo.getRightExpression()))
                || (isRequiredColumn(equalsTo.getRightExpression(), columnName, qualifier)
                    && isTrustedTenantParameter(equalsTo.getLeftExpression()));
    }

    private boolean isRequiredColumn(Expression expression, String columnName, String qualifier) {
        if (!(expression instanceof Column column)
                || !column.getColumnName().equalsIgnoreCase(columnName)) {
            return false;
        }
        return column.getTable() != null
                && column.getTable().getName().equalsIgnoreCase(qualifier);
    }

    private boolean isTrustedTenantParameter(Expression expression) {
        return expression instanceof JdbcNamedParameter parameter
                && parameter.getName().equalsIgnoreCase("propertyId");
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