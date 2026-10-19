package br.com.innitisoftware.financeiro.finance_ai_copilot.service.ingestion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class ProcessingCheckpointRepository {
    private static final String JOB_NAME = "transaction_embedding_job";

    private final JdbcTemplate jdbcTemplate;

    public ProcessingCheckpointRepository(@Qualifier("vectorDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public ProcessingCheckpoint load() {
        return jdbcTemplate.queryForObject(
                """
                SELECT last_processed_at, last_processed_id
                FROM processing_checkpoint
                WHERE job_name = ?
                """,
                (rs, rowNum) -> new ProcessingCheckpoint(
                        rs.getObject("last_processed_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("last_processed_id")),
                JOB_NAME);
    }

    public void save(Instant lastProcessedAt, long lastProcessedId) {
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE processing_checkpoint
                SET last_processed_at = ?, last_processed_id = ?
                WHERE job_name = ?
                """,
                OffsetDateTime.ofInstant(lastProcessedAt, ZoneOffset.UTC),
                lastProcessedId,
                JOB_NAME);
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Não foi possível atualizar o checkpoint da ingestão; verifique se a linha do job existe");
        }
    }

    public record ProcessingCheckpoint(Instant lastProcessedAt, long lastProcessedId) {
    }
}
