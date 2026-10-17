package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executa SQL previamente validado pelo {@link SqlValidator}.
 *
 * Responsabilidades:
 * - Injetar parâmetros de isolamento (propertyId, userId) via NamedParameterJdbcTemplate.
 * - Aplicar timeout de statement e limite de linhas.
 * - Serializar o resultado em formato compacto e legível para o LLM.
 * - Nunca expor nomes de tabelas, colunas técnicas ou stacktraces ao usuário final.
 *
 * IMPORTANTE: esta classe usa um DataSource restrito (somente SELECT),
 * configurado separadamente do DataSource principal da aplicação.
 */
@Component
public class SqlExecutor {

    private static final Logger auditLog = LoggerFactory.getLogger("sql-audit");

    /** Timeout de execução em segundos. */
    private static final int QUERY_TIMEOUT_SECONDS = 3;

    /** Número máximo de linhas retornadas (defesa extra, além do LIMIT aplicado pelo validator). */
    private static final int MAX_ROWS = 100;

    /** Formato de data para serialização. */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SqlExecutor(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executa o SQL validado e retorna o resultado serializado.
     *
     * @param sql        SQL já validado pelo SqlValidator
     * @param propertyId identificador do tenant (obrigatório)
     * @return texto formatado com o resultado ou mensagem de "nenhum registro"
     */
    public String execute(String sql, long propertyId) {
        long start = System.currentTimeMillis();

        try {
            MapSqlParameterSource params = buildParams(propertyId);

            jdbcTemplate.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

            long elapsed = System.currentTimeMillis() - start;
            auditLog.info(
                "SQL executado | propertyId={} | linhas={} | tempo={}ms | sql=\"{}\"",
                propertyId, rows.size(), elapsed, sql
            );

            if (rows.isEmpty()) {
                return "Nenhum registro encontrado para os critérios informados.";
            }

            // Defesa extra: truncar se o banco ignorou o LIMIT (não deveria acontecer, mas...)
            if (rows.size() > MAX_ROWS) {
                rows = rows.subList(0, MAX_ROWS);
            }

            return formatResult(rows);

        } catch (org.springframework.dao.DataAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            auditLog.error(
                "Erro ao executar SQL | propertyId={} | tempo={}ms | sql=\"{}\" | erro={}",
                propertyId, elapsed, sql, e.getMessage()
            );
            // Mensagem genérica — nunca expor detalhes do banco
            throw new SqlExecutionException(
                "Houve um erro ao consultar os dados. Tente reformular a pergunta."
            );
        } catch (Exception e) {
            auditLog.error("Erro inesperado na execução de SQL", e);
            throw new SqlExecutionException(
                "Houve um erro inesperado ao consultar os dados."
            );
        }
    }

    /**
     * Monta o mapa de parâmetros nomeados.
     *
     * O LLM gera SQL com `:propertyId` e, quando necessário, `:userId`.
     * Estes valores vêm do ToolContext, NUNCA do LLM — o LLM não pode
     * vê-los nem modificá-los.
     */
    private MapSqlParameterSource buildParams(long propertyId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("propertyId", propertyId);
        return params;
    }

    /**
     * Formata o resultado em texto compacto para o LLM.
     *
     * Formato escolhido: uma linha por registro, campos separados por " | ".
     * Isso é mais econômico em tokens do que JSON e o LLM lida bem.
     *
     * Exemplo:
     *   1) Data: 15/03/2025 | Descrição: Padaria do Zé | Categoria: Alimentação | Valor: R$ 25,00
     *   2) Data: 14/03/2025 | Descrição: Uber | Categoria: Transporte | Valor: R$ 18,50
     */
    private String formatResult(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total de registros: ").append(rows.size()).append("\n\n");

        int index = 1;
        for (Map<String, Object> row : rows) {
            sb.append(index++).append(") ");
            String line = row.entrySet().stream()
                    .map(e -> formatColumn(e.getKey(), e.getValue()))
                    .collect(Collectors.joining(" | "));
            sb.append(line).append("\n");
        }

        return sb.toString();
    }

    /**
     * Formata uma coluna específica, com tratamento especial para:
     * - Valores monetários (colunas com "value", "total", "valor", "limite", "goal").
     * - Datas (LocalDate, LocalDateTime, Timestamp).
     * - Nulos.
     * - IDs internos (omitidos da saída por segurança).
     */
    private String formatColumn(String columnName, Object value) {
        // Nunca expor IDs internos ao LLM
        if (isInternalIdColumn(columnName)) {
            return null;
        }

        String label = humanize(columnName);

        if (value == null) {
            return label + ": -";
        }

        if (value instanceof LocalDate date) {
            return label + ": " + date.format(DATE_FMT);
        }
        if (value instanceof LocalDateTime dt) {
            return label + ": " + dt.format(DATE_FMT);
        }
        if (value instanceof Timestamp ts) {
            return label + ": " + ts.toLocalDateTime().format(DATE_FMT);
        }

        if (isMonetaryColumn(columnName) && value instanceof Number number) {
            return label + ": " + formatMoney(number);
        }

        return label + ": " + value.toString();
    }

    private String formatMoney(Number value) {
        BigDecimal bd = new BigDecimal(value.toString());
        return "R$ " + String.format("%,.2f", bd).replace(",", "X").replace(".", ",").replace("X", ".");
    }

    private boolean isInternalIdColumn(String column) {
        String lower = column.toLowerCase();
        return lower.equals("id")
                || lower.endsWith("_id")
                || lower.equals("property_id")
                || lower.equals("user_id");
    }

    private boolean isMonetaryColumn(String column) {
        String lower = column.toLowerCase();
        return lower.contains("value")
                || lower.contains("valor")
                || lower.contains("total")
                || lower.contains("limit")
                || lower.contains("limite")
                || lower.contains("goal")
                || lower.contains("meta")
                || lower.contains("sum")
                || lower.contains("avg");
    }

    /**
     * Converte "total_gasto" em "Total gasto".
     * Remove underscores e capitaliza a primeira palavra.
     */
    private String humanize(String column) {
        if (column == null || column.isBlank()) return "";
        String cleaned = column.replace("_", " ").trim().toLowerCase();
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    public static class SqlExecutionException extends RuntimeException {
        public SqlExecutionException(String message) {
            super(message);
        }
        public SqlExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}