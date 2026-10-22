package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class MetricCatalog {

    private final Map<String, MetricDefinition> byName;

    public MetricCatalog(List<MetricDefinition> definitions) {
        this.byName = definitions.stream()
            .collect(Collectors.toUnmodifiableMap(MetricDefinition::name, Function.identity()));
    }

    public Optional<MetricDefinition> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<MetricDefinition> all() {
        return List.copyOf(byName.values());
    }
}