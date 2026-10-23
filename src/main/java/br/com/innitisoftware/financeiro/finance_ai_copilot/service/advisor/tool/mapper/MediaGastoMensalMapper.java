package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResultMapper;

public class MediaGastoMensalMapper implements MetricResultMapper {

    @Override
    public Object map(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        return new MediaMensal(toBigDecimal(rows.getFirst().get("media_mensal")));
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
        throw new IllegalArgumentException("Média mensal ausente ou inválida no resultado da métrica.");
    }
}
