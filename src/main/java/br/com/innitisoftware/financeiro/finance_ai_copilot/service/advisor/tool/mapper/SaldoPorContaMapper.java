package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResultMapper;

public class SaldoPorContaMapper implements MetricResultMapper {

    @Override
    public Object map(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new SaldoConta(
                        (String) row.get("conta"),
                        toBigDecimal(row.get("saldo_liquido"))))
                .toList();
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
        throw new IllegalArgumentException("Saldo ausente ou inválido no resultado da métrica.");
    }
}
