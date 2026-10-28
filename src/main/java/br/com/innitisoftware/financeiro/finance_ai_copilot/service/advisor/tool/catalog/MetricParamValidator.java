package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class MetricParamValidator {

    private static final Map<String, String> SNAKE_TO_CAMEL = Map.of(
        "data_inicio", "dataInicio",
        "data_fim",    "dataFim",
        "data_referencia", "dataReferencia",
        "categoria",   "categoria"
    );

    public Map<String, Object> validate(MetricDefinition metric,
                                        Map<String, Object> rawParams) {
        // 1. Rejeita parâmetros desconhecidos
        Set<String> allowed = metric.params().stream()
            .map(MetricParam::name)
            .collect(Collectors.toSet());

        for (String key : rawParams.keySet()) {
            if (!allowed.contains(key)) {
                throw new InvalidMetricParamException(
                    "Parâmetro não permitido: " + key);
            }
        }

        // 2. Valida obrigatórios e tipos
        Map<String, Object> translated = new HashMap<>();

        for (MetricParam param : metric.params()) {
            Object raw = rawParams.get(param.name());

            if (raw == null) {
                if (param.required()) {
                    throw new InvalidMetricParamException(
                        "Parâmetro obrigatório ausente: " + param.name());
                }
                translated.put(toCamel(param.name()), null);
                continue;
            }

            translated.put(toCamel(param.name()), coerce(param, raw));
        }

        return translated;
    }

    private Object coerce(MetricParam param, Object raw) {
        return switch (param.type()) {
            case DATE    -> LocalDate.parse(raw.toString());
            case LONG    -> Long.parseLong(raw.toString());
            case DECIMAL -> new BigDecimal(raw.toString());
            case STRING, ENUM -> raw.toString();
        };
    }

    private String toCamel(String snake) {
        return SNAKE_TO_CAMEL.getOrDefault(snake, snake);
    }
}