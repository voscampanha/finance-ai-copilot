package br.com.innitisoftware.financeiro.finance_ai_copilot.ingestion;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.FinancialEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class LegacyJdbcFinancialReader implements LegacyFinancialReader {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String query;

    public LegacyJdbcFinancialReader(NamedParameterJdbcTemplate jdbcTemplate,
                                     @Value("${app.legacy.query}") String query) {
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }

    @Override
    public List<FinancialEntry> findUpdatedSince(Instant updatedSince) {
        return jdbcTemplate.query(query, java.util.Map.of("updatedSince", updatedSince),
            (ResultSet rs, int rowNum) -> new FinancialEntry(
                rs.getObject("id", UUID.class),
                rs.getString("usuario_id"),
                rs.getObject("data", LocalDate.class),
                rs.getBigDecimal("valor"),
                FinancialEntry.EntryType.valueOf(rs.getString("tipo").toUpperCase()),
                rs.getString("categoria"),
                rs.getString("descricao")));
    }
}
