package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResultMapper;

public class UltimoLancamentoMapper implements MetricResultMapper {

    @Override
    public Object map(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.getFirst();
        return new Lancamento(
                (String) row.get("descricao"),
                toBigDecimal(row.get("valor")),
                toLocalDate(row.get("data")),
                (String) row.get("categoria"));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string) {
            return new BigDecimal(string);
        }
        throw new IllegalArgumentException("Valor ausente ou inválido no resultado da métrica.");
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof String string) {
            return LocalDate.parse(string);
        }
        throw new IllegalArgumentException("Data ausente ou inválida no resultado da métrica.");
    }
}
