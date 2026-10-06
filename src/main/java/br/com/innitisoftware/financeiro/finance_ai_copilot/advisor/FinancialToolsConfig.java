package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.text.NumberFormat;
import java.util.Locale;

@Component
public class FinancialToolsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialToolsConfig.class);
    private static final String PROPERTY_ID_CONTEXT_KEY = "propertyId";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinancialToolsConfig(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Calcula estatísticas financeiras sem expor a identidade do usuário ao modelo.
     */
    @Tool(description = "Calcula estatísticas agregadas de lançamentos por categoria e período: total gasto, soma, média, contagem e frequência. Use SEMPRE para perguntas analíticas como 'quanto gastei', 'total de', 'média de', 'quantas vezes' ou comparações numéricas.")
    public String calculateCategoryStats(
            @ToolParam(description = "Nome da categoria ou grupo, por exemplo Alimentação ou Transporte") String category,
            @ToolParam(description = "Data inicial do período no formato YYYY-MM-DD") String startDate,
            @ToolParam(description = "Data final do período no formato YYYY-MM-DD") String endDate,
            ToolContext toolContext) {
        long startedAt = System.nanoTime();
        LOGGER.info("Tool 'calculateCategoryStats' iniciada");
        long propertyId = authenticatedPropertyId(toolContext);
        String sql = """
                SELECT 
                    COUNT(t.id) AS total_count,
                    COALESCE(SUM(t.value), 0) AS total_sum,
                    COALESCE(AVG(t.value), 0) AS avg_value
                FROM transactions t
                LEFT JOIN categories c ON t.category_id = c.id
                LEFT JOIN `groups` g ON c.group_id = g.id
                LEFT JOIN finance_accounts fa ON t.finance_account_id = fa.id
                    WHERE t.property_id = :propertyId AND
                    (LOWER(c.name) LIKE LOWER(CONCAT('%', :categoryName, '%'))
                    OR LOWER(g.name) LIKE LOWER(CONCAT('%', :categoryName, '%')))
                  AND t.date BETWEEN :startDate AND :endDate
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
               .addValue("propertyId", propertyId)
                .addValue("categoryName", category)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);

        Stats stats;
        LOGGER.info("Executando consulta SQL para calcular estatísticas: propertyId={}, categoria='{}', período={} a {}", propertyId, category, startDate, endDate);
        try {
            stats = jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Stats(
                rs.getLong("total_count"),
                rs.getBigDecimal("total_sum"),
                rs.getBigDecimal("avg_value")));
        } catch (RuntimeException exception) {
            LOGGER.warn("Tool 'calculateCategoryStats' falhou: erro={}, tempo_ms={}",
                exception.getClass().getSimpleName(), (System.nanoTime() - startedAt) / 1_000_000);
            throw exception;
        }

        LOGGER.info("Tool 'calculateCategoryStats' concluída: contagem={}, tempo_ms={}",
                stats.totalCount(), (System.nanoTime() - startedAt) / 1_000_000);
        if (stats.totalCount() == 0) {
            return "Nenhum lançamento encontrado para a categoria e o período informados.";
        }
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        return "Categoria %s, período de %s a %s: %d lançamentos, total de %s e média de %s por lançamento."
            .formatted(category, startDate, endDate, stats.totalCount(),
                currencyFormat.format(stats.totalSum()), currencyFormat.format(stats.average()));
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

    private record Stats(long totalCount, java.math.BigDecimal totalSum, java.math.BigDecimal average) {}
}