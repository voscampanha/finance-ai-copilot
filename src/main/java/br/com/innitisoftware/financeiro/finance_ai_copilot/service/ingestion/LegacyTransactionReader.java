package br.com.innitisoftware.financeiro.finance_ai_copilot.service.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.TransactionEnrichedDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class LegacyTransactionReader {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String query;

    public LegacyTransactionReader(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${app.legacy.query}") Resource queryResource) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        try (var inputStream = queryResource.getInputStream()) {
            this.query = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    public List<TransactionEnrichedDTO> findUpdatedSince(Instant updatedSince) {
        return jdbcTemplate.query(
                query,
                Map.of("updatedSince", updatedSince),
                (rs, rowNum) -> new TransactionEnrichedDTO(
                        getLong(rs, "transaction_id", "id"),
                        getLong(rs, "property_id"),
                        getLong(rs, "user_id"),
                        getString(rs, "user_name"),
                        getString(rs, "description"),
                        rs.getBigDecimal("value"),
                        getDate(rs, "transaction_date", "date"),
                        getString(rs, "tags"),
                        getString(rs, "category_name"),
                        getString(rs, "category_type"),
                        getString(rs, "group_name"),
                        getString(rs, "account_name"),
                        getUpdatedAt(rs)
                )
        );
    }

    private static Long getLong(ResultSet rs, String... columns) throws SQLException {
        Object value = getObject(rs, columns);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static String getString(ResultSet rs, String column) throws SQLException {
        Object value = getObject(rs, column);
        return value == null ? null : value.toString();
    }

    private static LocalDate getDate(ResultSet rs, String... columns) throws SQLException {
        Object value = getObject(rs, columns);
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return value == null ? null : LocalDate.parse(value.toString());
    }

    private static OffsetDateTime getUpdatedAt(ResultSet rs) throws SQLException {
        Object value = getObject(rs, "updated_at");
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC);
        }

        if (value instanceof String str) {
            LocalDateTime localDateTime = LocalDateTime.parse(str.toString().replace(" ", "T"));
            return localDateTime.atOffset(ZoneOffset.UTC);
        }

        return value == null ? null : OffsetDateTime.parse(value.toString());
    }

    private static Object getObject(ResultSet rs, String... columns) throws SQLException {
        for (String column : columns) {
            try {
                return rs.getObject(column);
            } catch (SQLException ignored) {
                // Try the next supported alias for legacy query compatibility.
            }
        }
        return null;
    }
}