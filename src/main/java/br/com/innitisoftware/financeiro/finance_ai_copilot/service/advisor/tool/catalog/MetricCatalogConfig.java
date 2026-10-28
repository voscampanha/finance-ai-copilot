package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.catalog;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.GastosPorGrupoMapper;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.MediaGastoMensalMapper;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.SaldoPorContaMapper;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.TotalGastoPorCategoriaMapper;
import br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.tool.mapper.UltimoLancamentoMapper;

@Configuration
public class MetricCatalogConfig {

    @Bean
    public MetricCatalog metricCatalog() {
        return new MetricCatalog(List.of(
            new MetricDefinition(
                "total_gasto_por_categoria",
                """
                    Retorna a SOMA (SUM) dos gastos agrupada por categoria, em um período.
                    Uma linha por categoria. NÃO retorna lançamento individual, NÃO retorna
                    o maior valor, NÃO retorna média, NÃO retorna contagem.

                    USE QUANDO o usuário pergunta o total acumulado, agrupado ou não:
                    - "quanto gastei este mês"
                    - "gastos por categoria em março"
                    - "onde mais gastei no ano passado"
                    - "total de gastos com lazer em setembro"

                    NÃO USE QUANDO:
                    - o usuário pede o MAIOR lançamento individual (ex: "meu maior gasto
                    com mercado", "a compra mais cara do mês") → use `maior_lancamento`
                    - o usuário pede a MÉDIA (ex: "gasto médio por mês") → use `media_gasto_mensal`
                    - o usuário pede o ÚLTIMO lançamento (ex: "última compra") → use `ultimo_lancamento`
                    - o usuário pede uma LISTAGEM de transações (ex: "liste os gastos com
                    mercado") → NÃO há métrica; use text-to-SQL

                    PALAVRAS QUE INDICAM ESTA MÉTRICA: total, quanto gastei, soma,
                    gastos por, onde mais gastei, acumulado.
                    PALAVRAS QUE INDICAM OUTRA ROTA: maior, mais caro, última, média,
                    liste, quais foram.
                """,
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
                "maior_lancamento",
                """
                Retorna o lançamento individual de MAIOR VALOR (MAX) em um período,
                opcionalmente filtrado por categoria. Uma linha.
                USE QUANDO o usuário pede "maior gasto", "compra mais cara",
                "lançamento mais alto".
                NÃO USE para total acumulado (use total_gasto_por_categoria)
                nem para o mais recente (use ultimo_lancamento).
                PALAVRAS: maior, mais caro, mais alto, máximo.
                """,
                List.of(
                    new MetricParam("data_inicio", ParamType.DATE, true, "Data inicial"),
                    new MetricParam("data_fim", ParamType.DATE, true, "Data final"),
                    new MetricParam("categoria", ParamType.STRING, false, "Categoria (opcional)")
                ),
                """
                  SELECT t.description AS descricao, t.value AS valor,
                      t.date AS data, c.name AS categoria
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.property_id = :propertyId
                AND t.date BETWEEN :dataInicio AND :dataFim
                AND (:categoria IS NULL OR c.name = :categoria)
                AND c.type = 'debit'
                ORDER BY t.value DESC, t.id DESC
                LIMIT 1
                """,
                new UltimoLancamentoMapper()
            ),

            new MetricDefinition(
                "media_gasto_mensal",
                """
                Retorna a média de despesas por mês no período informado, considerando
                todos os meses do intervalo, inclusive os sem lançamentos. Retorna um
                único valor, não uma linha por mês.

                USE QUANDO o usuário pergunta quanto gasta em média por mês em um período:
                - "qual meu gasto médio mensal este ano"
                - "em média, quanto gastei por mês de janeiro a junho"
                - "média de despesas no período"

                NÃO USE QUANDO:
                - o usuário pede o total acumulado no período → use `total_gasto_por_categoria`
                - o usuário pede uma média por categoria ou grupo → use text-to-SQL
                - o usuário pede o maior ou o último lançamento → use `maior_lancamento`
                ou `ultimo_lancamento`

                PALAVRAS: média, em média, gasto médio, despesas médias por mês.
                """,
                List.of(
                    new MetricParam("data_inicio", ParamType.DATE, true, "Data inicial (inclusive)"),
                    new MetricParam("data_fim",    ParamType.DATE, true, "Data final (inclusive)")
                ),
                """
                SELECT COALESCE(
                    SUM(t.value) /
                        (PERIOD_DIFF(DATE_FORMAT(:dataFim, '%Y%m'), DATE_FORMAT(:dataInicio, '%Y%m')) + 1),
                    0
                ) AS media_mensal
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                                 AND c.property_id = t.property_id
                WHERE t.property_id = :propertyId
                  AND t.date BETWEEN :dataInicio AND :dataFim
                  AND c.type = 'debit'
                """,
                new MediaGastoMensalMapper()
            ),

            new MetricDefinition(
                "saldo_por_conta",
                """
                Retorna a variação líquida acumulada por conta (créditos menos débitos),
                sem restringir a um período. Retorna uma linha por conta, inclusive contas
                sem lançamentos. Não representa o saldo bancário real, pois não considera
                o saldo inicial.

                USE QUANDO o usuário pergunta a movimentação líquida acumulada de cada conta:
                - "qual o saldo acumulado por conta"
                - "quanto entrou e saiu de cada conta"
                - "como está a variação líquida das minhas contas"

                NÃO USE QUANDO:
                - o usuário pede o saldo bancário real → esta métrica não considera saldo inicial
                - o usuário pede a movimentação em um período específico → use text-to-SQL
                - o usuário pede o total de despesas por categoria ou grupo → use
                `total_gasto_por_categoria` ou `gastos_por_grupo`

                PALAVRAS: saldo por conta, movimentação líquida, créditos menos débitos,
                variação acumulada.
                """,
                List.of(),
                """
                SELECT fa.name AS conta,
                       COALESCE(SUM(
                           CASE
                               WHEN c.type = 'credit' THEN t.value
                               WHEN c.type = 'debit' THEN -t.value
                               ELSE 0
                           END
                       ), 0) AS saldo_liquido
                FROM finance_accounts fa
                LEFT JOIN transactions t ON t.finance_account_id = fa.id
                                         AND t.property_id = fa.property_id
                LEFT JOIN categories c ON c.id = t.category_id
                                       AND c.property_id = fa.property_id
                WHERE fa.property_id = :propertyId
                GROUP BY fa.id, fa.name
                ORDER BY fa.name
                """,
                new SaldoPorContaMapper()
            ),

            new MetricDefinition(
                "gastos_por_grupo",
                """
                Retorna o total de despesas agrupado por grupo no período informado.
                Uma linha por grupo; lançamentos sem grupo aparecem como "Sem grupo".

                USE QUANDO o usuário pergunta quanto gastou por grupo em um período:
                - "gastos por grupo este mês"
                - "quanto gastei em cada grupo em março"
                - "quais grupos tiveram mais despesas no período"

                NÃO USE QUANDO:
                - o usuário pede o total por categoria → use `total_gasto_por_categoria`
                - o usuário pede a média mensal → use `media_gasto_mensal`
                - o usuário pede lançamentos individuais ou uma listagem → use text-to-SQL

                PALAVRAS: gastos por grupo, despesas por grupo, total por grupo.
                """,
                List.of(
                    new MetricParam("data_inicio", ParamType.DATE, true, "Data inicial (inclusive)"),
                    new MetricParam("data_fim",    ParamType.DATE, true, "Data final (inclusive)"),
                    new MetricParam("grupo", ParamType.STRING, false, "Nome do grupo (opcional)")
                ),
                """
                SELECT COALESCE(g.name, 'Sem grupo') AS grupo,
                       SUM(t.value) AS total
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                                 AND c.property_id = t.property_id
                LEFT JOIN `groups` g ON g.id = c.group_id
                                    AND g.property_id = c.property_id
                WHERE t.property_id = :propertyId
                  AND t.date BETWEEN :dataInicio AND :dataFim
                  AND c.type = 'debit'
                  AND (:grupo IS NULL OR g.name = :grupo)
                GROUP BY g.id, g.name
                ORDER BY total DESC
                """,
                new GastosPorGrupoMapper()
            ),

            new MetricDefinition(
                "ultimo_lancamento",
                """
                Retorna UM único lançamento: o mais recente por data (e por id em caso
                de empate), opcionalmente filtrado por categoria. NÃO retorna lista,
                NÃO retorna soma, NÃO retorna o maior valor.

                USE QUANDO o usuário pergunta o lançamento mais recente:
                - "qual foi meu último lançamento"
                - "última compra no mercado"
                - "o que gastei por último"

                NÃO USE QUANDO:
                - o usuário pede o MAIOR valor (ex: "maior gasto com mercado") →
                use `maior_lancamento`
                - o usuário pede o total (ex: "quanto gastei no mercado") →
                use `total_gasto_por_categoria`
                - o usuário pede uma LISTA (ex: "últimos 5 lançamentos") → text-to-SQL

                PALAVRAS: último, última, mais recente, por último.
                """,
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