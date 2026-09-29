package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.LegacyTransactionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class LegacyTransactionReader {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String query;

    public LegacyTransactionReader(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${app.legacy.query}") String query) {
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }

    public List<LegacyTransactionDto> findUpdatedSince(Instant updatedSince) {
        return jdbcTemplate.query(
                query,
                Map.of("updatedSince", updatedSince),
                (rs, rowNum) -> new LegacyTransactionDto(
                        rs.getString("id"),
                        rs.getString("property_id"),
                        rs.getString("description"),
                        rs.getBigDecimal("value"),
                        rs.getObject("transaction_date", LocalDate.class),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class),
                        rs.getString("category_id"),
                        rs.getString("finance_account_id"),
                        rs.getString("tags"),
                        rs.getString("attachment")
                )
        );
    }
}