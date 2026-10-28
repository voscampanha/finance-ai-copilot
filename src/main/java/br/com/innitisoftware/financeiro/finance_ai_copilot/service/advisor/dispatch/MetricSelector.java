package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDescriptor;

@Component
public class MetricSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricSelector.class);
    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() {};

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MetricSelector(ChatClient chatClient, ObjectMapper objectMapper, Clock clock) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<MetricSelection> trySelect(String question, List<MetricDescriptor> catalog) {
        try {
            String catalogJson = objectMapper.writeValueAsString(catalog);
            LocalDate today = LocalDate.now(clock);
            String response = chatClient.prompt()
                    .system("""
                            Você é um seletor de métricas. Dado o catálogo abaixo e a pergunta
                            do usuário, escolha EXATAMENTE UMA métrica da lista.
                            Antes de escolher, verifique se a pergunta está no bloco "NÃO USE QUANDO"
                            de alguma métrica. Se estiver, não escolha essa métrica.                            
                            Se nenhuma se aplicar com confiança, retorne
                            {"metric": "__none__", "params": {}}.
                            Nunca retorne null, nunca omita o campo 'metric',
                            nunca invente nomes de métricas ou parâmetros.
                            Data atual: %1$s.
                            Interprete datas relativas sempre em relação a essa data:
                            - "este mês", "esse mês" ou "mês atual": do primeiro dia do mês até hoje.
                            - "esta semana" ou "semana atual": de segunda-feira até hoje.
                            - "este ano", "esse ano" ou "ano atual": de 1º de janeiro até hoje.
                            - "mês passado" e "ano passado": período calendário anterior completo.
                            Um mês mencionado sem ano refere-se ao ano da data atual; nesse caso,
                            use o mês calendário completo. Se a pergunta informar o ano, respeite-o.
                            Para parâmetros de data, use o formato ISO YYYY-MM-DD.
                            Responda APENAS JSON, sem texto adicional.
                            Catálogo:
                            %2$s
                            """.formatted(today, catalogJson))
                    .user(question)
                    .options(OpenAiChatOptions.builder()
                            .temperature(0.0))
                    .call()
                    .content();
            SelectionParseResult parseResult = parseSelectionWithReason(response, catalog);
            Optional<MetricSelection> selection = parseResult.selection()
                    .map(selected -> applyRelativeDates(selected, question, catalog));
            if (selection.isPresent()) {
                MetricSelection selected = selection.get();
                LOGGER.info(
                        "Metric selection succeeded | metricName={} | params={} | responseLength={}",
                        selected.metricName(), selected.params(), response.length());
            } else {
                LOGGER.warn(
                        "Metric selection rejected | reason={} | candidateMetric={} | responseLength={} | catalogMetrics={}",
                        parseResult.rejectionReason(),
                        parseResult.candidateMetric(),
                        response == null ? null : response.length(),
                        catalog.stream().map(MetricDescriptor::name).toList());
            }
            return selection;
        } catch (JsonProcessingException | RuntimeException exception) {
            LOGGER.warn("Metric selection unavailable | error={} | message={}",
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    public Optional<MetricSelection> parseSelection(String response, List<MetricDescriptor> catalog) {
        return parseSelectionWithReason(response, catalog).selection();
    }

    private MetricSelection applyRelativeDates(
            MetricSelection selection, String question, List<MetricDescriptor> catalog) {
        String normalizedQuestion = Normalizer.normalize(question, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(clock);
        MetricDescriptor descriptor = catalog.stream()
                .filter(metric -> metric.name().equals(selection.metricName()))
                .findFirst()
                .orElse(null);
        if (descriptor != null && descriptor.params().stream()
                .anyMatch(param -> param.name().equals("data_referencia"))) {
            Map<String, Object> params = new LinkedHashMap<>(selection.params());
            Object referenceDate = params.get("data_referencia");
            if (normalizedQuestion.matches(".*\\bontem\\b.*")) {
                referenceDate = today.minusDays(1).toString();
            } else if (normalizedQuestion.matches(".*\\bhoje\\b.*") || referenceDate == null) {
                referenceDate = today.toString();
            }
            params.put("data_referencia", referenceDate);
            LOGGER.info("Reference date resolved | metricName={} | referenceDate={}",
                    selection.metricName(), referenceDate);
            return new MetricSelection(selection.metricName(), params);
        }

        LocalDate start;
        LocalDate end;
        String period;

        if (normalizedQuestion.matches(".*\\b(este mes|esse mes|neste mes|nesse mes|mes atual|mes corrente)\\b.*")) {
            start = today.withDayOfMonth(1);
            end = today;
            period = "current_month";
        } else if (normalizedQuestion.matches(".*\\b(mes passado|mes anterior)\\b.*")) {
            end = today.withDayOfMonth(1).minusDays(1);
            start = end.withDayOfMonth(1);
            period = "previous_month";
        } else if (normalizedQuestion.matches(".*\\b(esta semana|essa semana|nesta semana|nessa semana|semana atual|semana corrente)\\b.*")) {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = today;
            period = "current_week";
        } else if (normalizedQuestion.matches(".*\\b(semana passada|semana anterior)\\b.*")) {
            end = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
            start = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            period = "previous_week";
        } else if (normalizedQuestion.matches(".*\\b(este ano|esse ano|neste ano|nesse ano|ano atual|ano corrente)\\b.*")) {
            start = today.withDayOfYear(1);
            end = today;
            period = "current_year";
        } else if (normalizedQuestion.matches(".*\\b(ano passado|ano anterior)\\b.*")) {
            start = today.withDayOfYear(1).minusYears(1);
            end = start.withDayOfYear(start.lengthOfYear());
            period = "previous_year";
        } else if (normalizedQuestion.matches(".*\\b(ontem)\\b.*")) {
            start = today.minusDays(1);
            end = start;
            period = "yesterday";
        } else if (normalizedQuestion.matches(".*\\b(hoje)\\b.*")) {
            start = today;
            end = today;
            period = "today";
        } else {
            Month mentionedMonth = findPortugueseMonth(normalizedQuestion);
            if (mentionedMonth == null) {
                return selection;
            }
            int year = normalizedQuestion.matches(".*\\b(?:19|20)\\d{2}\\b.*")
                    ? Integer.parseInt(normalizedQuestion.replaceAll(".*\\b((?:19|20)\\d{2})\\b.*", "$1"))
                    : normalizedQuestion.matches(".*\\b(ano passado|ano anterior)\\b.*")
                            ? today.getYear() - 1
                            : today.getYear();
            start = LocalDate.of(year, mentionedMonth, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
            period = "explicit_month";
        }

        if (descriptor == null || descriptor.params().stream()
                .noneMatch(param -> param.name().equals("data_inicio"))
                || descriptor.params().stream()
                .noneMatch(param -> param.name().equals("data_fim"))) {
            return selection;
        }

        Map<String, Object> params = new LinkedHashMap<>(selection.params());
        params.put("data_inicio", start.toString());
        params.put("data_fim", end.toString());
        LOGGER.info("Relative date range resolved | metricName={} | period={} | start={} | end={}",
                selection.metricName(), period, start, end);
        return new MetricSelection(selection.metricName(), params);
    }

    private Month findPortugueseMonth(String normalizedQuestion) {
        Map<String, Month> months = Map.ofEntries(
                Map.entry("janeiro", Month.JANUARY),
                Map.entry("fevereiro", Month.FEBRUARY),
                Map.entry("marco", Month.MARCH),
                Map.entry("abril", Month.APRIL),
                Map.entry("maio", Month.MAY),
                Map.entry("junho", Month.JUNE),
                Map.entry("julho", Month.JULY),
                Map.entry("agosto", Month.AUGUST),
                Map.entry("setembro", Month.SEPTEMBER),
                Map.entry("outubro", Month.OCTOBER),
                Map.entry("novembro", Month.NOVEMBER),
                Map.entry("dezembro", Month.DECEMBER));
        return months.entrySet().stream()
                .filter(entry -> normalizedQuestion.matches(".*\\b" + entry.getKey() + "\\b.*"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private SelectionParseResult parseSelectionWithReason(
            String response, List<MetricDescriptor> catalog) {
                
        Logger logger = LoggerFactory.getLogger(MetricSelector.class);
        logger.info("Parsing metric selection response | response={} | catalogMetrics={}",
                response == null ? null : response,
                catalog.stream().map(MetricDescriptor::name).toList());
        if (response == null || response.isBlank()) {
            return SelectionParseResult.rejected("empty_response", null);
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            if (root == null) {
                return SelectionParseResult.rejected("empty_json", null);
            }
            if (!root.isObject()) {
                return SelectionParseResult.rejected("root_is_not_object", null);
            }
            if (!root.has("metric")) {
                return SelectionParseResult.rejected("metric_field_missing", null);
            }
            if (root.size() > 2) {
                return SelectionParseResult.rejected("unexpected_field_count", null);
            }
            if (root.has("params") && !root.get("params").isObject()) {
                return SelectionParseResult.rejected("params_is_not_object", null);
            }
            var fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (!fieldName.equals("metric") && !fieldName.equals("params")) {
                    return SelectionParseResult.rejected("unexpected_field", null);
                }
            }
            JsonNode metric = root.get("metric");
            if (metric.isNull()) {
                return SelectionParseResult.rejected("model_selected_no_metric", null);
            }
            if (metric.isTextual() && "__none__".equals(metric.asText())) {
                return SelectionParseResult.rejected("model_selected_none_sentinel", "__none__");
            }
            if (!metric.isTextual()) {
                return SelectionParseResult.rejected("metric_is_not_string", null);
            }

            String metricName = metric.asText();
            boolean knownMetric = catalog.stream().anyMatch(item -> item.name().equals(metricName));
            if (!knownMetric) {
                return SelectionParseResult.rejected("unknown_metric", metricName);
            }

            JsonNode paramsNode = root.get("params");
            Map<String, Object> params = paramsNode == null
                    ? Map.of()
                    : objectMapper.convertValue(paramsNode, PARAMS_TYPE);
            return SelectionParseResult.selected(new MetricSelection(metricName, params));
        } catch (JsonProcessingException exception) {
            return SelectionParseResult.rejected("invalid_json", null);
        } catch (IllegalArgumentException exception) {
            return SelectionParseResult.rejected("invalid_params", null);
        }
    }

    private record SelectionParseResult(
            Optional<MetricSelection> selection,
            String rejectionReason,
            String candidateMetric) {

        private static SelectionParseResult selected(MetricSelection selection) {
            return new SelectionParseResult(Optional.of(selection), null, selection.metricName());
        }

        private static SelectionParseResult rejected(String reason, String candidateMetric) {
            return new SelectionParseResult(Optional.empty(), reason, candidateMetric);
        }
    }
}
