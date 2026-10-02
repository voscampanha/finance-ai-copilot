package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.FinancialToolsDTOs;

@Configuration
public class FinancialToolsConfig {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectProvider<ChatClient> chatClientProvider;

    public FinancialToolsConfig(NamedParameterJdbcTemplate jdbcTemplate, ObjectProvider<ChatClient> chatClientProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatClientProvider = chatClientProvider;
    }

    @Bean
    public Function<FinancialToolsDTOs.CategoryStatsRequest, FinancialToolsDTOs.CategoryStatsResponse> calculateCategoryStats() {
        return request -> {
            String sql = """
                SELECT 
                    COUNT(t.id) AS total_count,
                    COALESCE(SUM(t.value), 0) AS total_sum,
                    COALESCE(AVG(t.value), 0) AS avg_value
                FROM transactions t
                LEFT JOIN categories c ON t.category_id = c.id
                LEFT JOIN `groups` g ON c.group_id = g.id
                WHERE 
                -- t.property_id = :propertyId AND 
                (LOWER(c.name) LIKE LOWER(CONCAT('%', :categoryName, '%')) 
                    OR LOWER(g.name) LIKE LOWER(CONCAT('%', :categoryName, '%')))
                  AND t.date BETWEEN :startDate AND :endDate
            """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    // .addValue("propertyId", request.propertyId())
                    .addValue("categoryName", request.categoryName())
                    .addValue("startDate", request.startDate())
                    .addValue("endDate", request.endDate());

            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
                long count = rs.getLong("total_count");
                double sum = rs.getDouble("total_sum");
                double avg = rs.getDouble("avg_value");

                // Cálculo simples de padrão de frequência
                String pattern = count > 12 ? "Alta recorrência (múltiplas vezes por mês)" :
                                 count >= 4  ? "Recorrência mensal/semanal habitual" : "Pontual/Baixa frequência";

                String analysis = chatClientProvider.getObject().prompt()
                    .system("Você é um analista financeiro. Interprete os dados fornecidos em português, sem inventar informações e sem repetir identificadores internos.")
                    .user("Analise as estatísticas da categoria %s no período de %s a %s: %d transações, total de R$ %.2f, média de R$ %.2f por transação e padrão de frequência: %s."
                        .formatted(request.categoryName(), request.startDate(), request.endDate(), count, sum, avg, pattern))
                    .call()
                    .content();

                return new FinancialToolsDTOs.CategoryStatsResponse(
                        request.categoryName(),
                        count,
                        sum,
                        avg,
                    pattern,
                    analysis
                );
            });
        };
    }

    @Bean
    public FunctionToolCallback<FinancialToolsDTOs.CategoryStatsRequest, FinancialToolsDTOs.CategoryStatsResponse>
    calculateCategoryStatsToolCallback(
            Function<FinancialToolsDTOs.CategoryStatsRequest, FinancialToolsDTOs.CategoryStatsResponse> calculateCategoryStats) {
        return FunctionToolCallback.builder("calculateCategoryStats", calculateCategoryStats)
                .description("Calcula soma, quantidade, média e frequência dos gastos de uma categoria no período informado.")
                .inputType(FinancialToolsDTOs.CategoryStatsRequest.class)
                .build();
    }
}