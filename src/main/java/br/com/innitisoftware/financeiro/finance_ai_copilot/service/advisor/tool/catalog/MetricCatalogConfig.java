package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.TotalGastoPorCategoriaMapper;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.UltimoLancamentoMapper;

@Configuration
public class MetricCatalogConfig {

    @Bean
    public MetricCatalog metricCatalog() {
        return new MetricCatalog(List.of(
            new MetricDefinition(
                "total_gasto_por_categoria",
                "Total gasto agrupado por categoria em um período. Use para perguntas " +
                "como 'quanto gastei', 'onde mais gastei', 'gastos por categoria'. " +
                "'Besteira' e 'diversão' mapeiam para a categoria Lazer.",
                List.of(
                    new MetricParam("data_inicio", ParamType.DATE, true, "Data inicial (inclusive)"),
                    new MetricParam("data_fim",    ParamType.DATE, true, "Data final (inclusive)"),
                    new MetricParam("categoria",   ParamType.STRING, false, "Nome da categoria (opcional)")
                ),
                """
                SELECT c.name AS categoria, SUM(t.value) AS total
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                WHERE t.property_id = :propertyId
                  AND t.date BETWEEN :dataInicio AND :dataFim
                  AND c.type = 'debit'
                  AND (:categoria IS NULL OR c.name = :categoria)
                GROUP BY c.name
                ORDER BY total DESC
                """,
                new TotalGastoPorCategoriaMapper()
            ),

            new MetricDefinition(
                "ultimo_lancamento",
                "Retorna o lançamento mais recente, opcionalmente filtrado por categoria.",
                List.of(
                    new MetricParam("categoria", ParamType.STRING, false, "Nome da categoria (opcional)")
                ),
                """
                SELECT t.description AS descricao, t.value AS valor,
                       t.date AS data, c.name AS categoria
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.property_id = :propertyId
                  AND (:categoria IS NULL OR c.name = :categoria)
                ORDER BY t.date DESC, t.id DESC
                LIMIT 1
                """,
                new UltimoLancamentoMapper()
            )
        ));
    }
}