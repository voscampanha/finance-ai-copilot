package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.dispatch;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.SemanticLayerTools;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.UnknownMetricException;

@Component
public class QueryDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDispatcher.class);
    private static final Pattern CONTEXTUAL = Pattern.compile(
            "\\b(o que e|como funciona|significa|explica)\\b");

    private final SemanticLayerTools semanticLayer;
    private final MetricSelector metricSelector;
    private final RagService ragService;
    private final TextToSqlService textToSqlService;

    public QueryDispatcher(
            SemanticLayerTools semanticLayer,
            MetricSelector metricSelector,
            RagService ragService,
            TextToSqlService textToSqlService) {
        this.semanticLayer = semanticLayer;
        this.metricSelector = metricSelector;
        this.ragService = ragService;
        this.textToSqlService = textToSqlService;
    }

    public DispatchResult dispatch(String question) {
        var selection = metricSelector.trySelect(question, semanticLayer.describeCatalog());
        if (selection.isPresent()) {
            try {
                MetricResult result = semanticLayer.execute(
                        selection.get().metricName(),
                        selection.get().params());
                LOGGER.info("Query routed to semantic layer | metricName={}",
                        selection.get().metricName());
                return new DispatchResult.Semantic(result);
            } catch (UnknownMetricException exception) {
                LOGGER.warn("Selected metric is not in catalog | metricName={}",
                        selection.get().metricName());
            }
        }

        if (isContextual(question)) {
            LOGGER.info("Query routed to contextual retrieval");
            return new DispatchResult.Contextual(ragService.retrieve(question));
        }
        LOGGER.info("Query routed to text-to-sql | reason=no_metric_selected");
        return new DispatchResult.Fallback(textToSqlService.execute(question));
    }

    boolean isContextual(String question) {
        String normalized = Normalizer.normalize(question, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return CONTEXTUAL.matcher(normalized).find();
    }
}
