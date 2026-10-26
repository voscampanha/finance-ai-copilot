package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.innitisoftware.financeiro.finance_ai_copilot.security.TenantContext;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricCatalog;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDefinition;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricDescriptor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricParamValidator;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.MetricResult;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.ParamDescriptor;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog.UnknownMetricException;

@Component
public class SemanticLayerTools {

    private final MetricCatalog catalog;
    private final MetricParamValidator validator;
    private final MetricExecutor executor;
    private final TenantContext tenantContext;

    public SemanticLayerTools(MetricCatalog catalog,
                              MetricParamValidator validator,
                              MetricExecutor executor,
                              TenantContext tenantContext) {
        this.catalog = catalog;
        this.validator = validator;
        this.executor = executor;
        this.tenantContext = tenantContext;
    }

    public MetricResult execute(String metricName, Map<String, Object> rawParams) {
        MetricDefinition metric = catalog.findByName(metricName)
            .orElseThrow(() -> new UnknownMetricException(metricName));

        Map<String, Object> params = validator.validate(metric, rawParams);
        params.put("propertyId", tenantContext.requirePropertyId());

        return executor.execute(metric, params);
    }

    public List<MetricDescriptor> describeCatalog() {
        return catalog.all().stream()
            .map(m -> new MetricDescriptor(
                m.name(),
                m.description(),
                m.params().stream()
                    .map(p -> new ParamDescriptor(p.name(), p.type(), p.required(), p.description()))
                    .toList()
            ))
            .toList();
    }
}