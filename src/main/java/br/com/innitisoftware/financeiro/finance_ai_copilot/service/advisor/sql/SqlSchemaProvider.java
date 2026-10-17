package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.sql;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fornece o schema curado do banco para o gerador de SQL (Text-to-SQL).
 * 
 * Regras de segurança:
 * - Expõe APENAS as tabelas e colunas necessárias para consultas financeiras.
 * - NUNCA expõe colunas sensíveis (password, tokens, emails de verificação).
 * - NUNCA expõe tabelas de autenticação (users.password, users.email_verification_token, etc.).
 * - O filtro de isolamento obrigatório é `property_id`, conforme o DER.
 */
@Component
@ConfigurationProperties(prefix = "chat.sql.schema")
public class SqlSchemaProvider {

    /**
     * Nome da coluna de isolamento (multi-tenant).
     * Todas as tabelas do domínio financeiro possuem esta coluna.
     */
    private String tenantColumn = "property_id";

    /**
     * Tabelas permitidas na whitelist. Qualquer SQL que referencie tabela fora desta lista é rejeitado.
     */
    private Set<String> allowedTables = Set.of(
            "transactions",
            "categories",
            "groups",
            "finance_accounts",
            "properties"
    );

    /**
     * Tabelas explicitamente proibidas (defesa em profundidade).
     * Mesmo que a whitelist seja mal configurada, estas nunca passam.
     */
    private Set<String> forbiddenTables = Set.of(
            "users"
    );

    /**
     * Mapeamento curado: tabela -> descrição + colunas expostas ao LLM.
     * Ordem importa para o prompt (mais relevantes primeiro).
     */
    private final Map<String, TableSchema> schemas = Map.of(
        "transactions", new TableSchema(
            "Lançamentos financeiros do usuário. Tabela principal para consultas de gastos, receitas, valores e datas.",
            List.of(
                new ColumnSchema("id", "BIGINT", "Identificador interno. NÃO expor nas respostas."),
                new ColumnSchema("property_id", "BIGINT", "Identificador da propriedade/tenant. SEMPRE filtrar."),
                new ColumnSchema("description", "VARCHAR", "Descrição textual do lançamento. Use para 'o que era aquela compra'."),
                new ColumnSchema("value", "DECIMAL", "Valor monetário. Positivo = receita, negativo = despesa (ou o contrário, conforme sua convenção)."),
                new ColumnSchema("date", "DATE", "Data do lançamento. Formato YYYY-MM-DD."),
                new ColumnSchema("category_id", "BIGINT", "FK para categories. Use JOIN para agrupar por categoria."),
                new ColumnSchema("finance_account_id", "BIGINT", "FK para finance_accounts. Use JOIN para filtrar por conta."),
                new ColumnSchema("tags", "VARCHAR", "Tags livres separadas por vírgula. Útil para buscas textuais."),
                new ColumnSchema("attachment", "VARCHAR", "Caminho do anexo. NÃO expor.")
            )
        ),
        "categories", new TableSchema(
            "Categorias de lançamentos (ex: Alimentação, Transporte). Pertencem a um grupo e a uma propriedade.",
            List.of(
                new ColumnSchema("id", "BIGINT", "Identificador interno."),
                new ColumnSchema("property_id", "BIGINT", "Identificador da propriedade. SEMPRE filtrar."),
                new ColumnSchema("name", "VARCHAR", "Nome da categoria. Use para 'quanto gastei em alimentação'."),
                new ColumnSchema("type", "VARCHAR", "Tipo da categoria. debit indica despesa e credit indica recebimento. debit-transfer, credit-transfer e credit-trasfer (grafia legada) indicam transferência entre contas e não são gasto nem recebimento. Os valores armazenados em transactions.value são sempre positivos."),
                new ColumnSchema("priority", "INT", "Prioridade de exibição."),
                new ColumnSchema("group_id", "BIGINT", "FK para groups."),
                new ColumnSchema("active", "BOOLEAN", "Se a categoria está ativa."),
                new ColumnSchema("limit", "DECIMAL", "Limite de gasto definido para a categoria."),
                new ColumnSchema("goal", "DECIMAL", "Meta financeira da categoria.")
            )
        ),
        "groups", new TableSchema(
            "Agrupamentos de categorias (ex: Essenciais, Lazer, Investimentos).",
            List.of(
                new ColumnSchema("id", "BIGINT", "Identificador interno."),
                new ColumnSchema("property_id", "BIGINT", "Identificador da propriedade. SEMPRE filtrar."),
                new ColumnSchema("name", "VARCHAR", "Nome do grupo."),
                new ColumnSchema("type", "VARCHAR", "Tipo do grupo."),
                new ColumnSchema("priority", "INT", "Prioridade de exibição.")
            )
        ),
        "finance_accounts", new TableSchema(
            "Contas financeiras do usuário (ex: conta corrente, carteira, cartão).",
            List.of(
                new ColumnSchema("id", "BIGINT", "Identificador interno."),
                new ColumnSchema("property_id", "BIGINT", "Identificador da propriedade. SEMPRE filtrar."),
                new ColumnSchema("user_id", "BIGINT", "FK para users. Use apenas se a pergunta exigir isolamento por usuário."),
                new ColumnSchema("name", "VARCHAR", "Nome da conta (ex: Nubank, Carteira)."),
                new ColumnSchema("priority", "INT", "Prioridade de exibição."),
                new ColumnSchema("active", "BOOLEAN", "Se a conta está ativa.")
            )
        ),
        "properties", new TableSchema(
            "Propriedades (tenants). Cada usuário pertence a uma ou mais propriedades.",
            List.of(
                new ColumnSchema("id", "BIGINT", "Identificador interno."),
                new ColumnSchema("name", "VARCHAR", "Nome da propriedade.")
            )
        )
    );

    /**
     * Retorna o schema formatado como texto para o prompt do LLM.
     * Ordem das tabelas: mais relevantes primeiro (transactions antes de properties).
     */
    public String getSchemaForPrompt() {
        StringBuilder sb = new StringBuilder();
        schemas.forEach((table, schema) -> {
            sb.append("Tabela: ").append(formatTableName(table)).append("\n");
            sb.append("Descrição: ").append(schema.description()).append("\n");
            sb.append("Colunas:\n");
            schema.columns().forEach(col ->
                sb.append("  - ").append(col.name())
                  .append(" (").append(col.type()).append("): ")
                  .append(col.description()).append("\n")
            );
            sb.append("\n");
        });
        return sb.toString();
    }

    private String formatTableName(String table) {
        return table.equals("groups") ? "`groups`" : table;
    }

    /**
     * Prompt específico com instruções de JOIN e filtro obrigatório,
     * baseado no DER anexado.
     */
    public String getJoinGuide() {
        return """
            GUIA DE JOINS (baseado no schema):
            - transactions.category_id -> categories.id
            - transactions.finance_account_id -> finance_accounts.id
            - categories.group_id -> `groups`.id (em SQL, sempre use FROM `groups` ou JOIN `groups`; nunca omita as crases).
            - finance_accounts.user_id -> users.id (evite expor dados de users)
            
            FILTRO OBRIGATÓRIO:
            - Para CADA tabela do domínio consultada, inclua no WHERE um filtro independente pelo alias: `<alias>.property_id = :propertyId`. Em consultas com JOIN, aplique em todas as tabelas.
            - Para `properties`, use `<alias>.id = :propertyId`.
            - Conecte todos os filtros de tenant por AND, nunca dentro de OR.
            - Para perguntas que precisam isolar por usuário dentro da property, faça JOIN com finance_accounts e filtre `finance_accounts.user_id = :userId`.
            """;
    }

    // Getters e setters para @ConfigurationProperties
    public String getTenantColumn() { return tenantColumn; }
    public void setTenantColumn(String tenantColumn) { this.tenantColumn = tenantColumn; }
    public Set<String> getAllowedTables() { return allowedTables; }
    public void setAllowedTables(Set<String> allowedTables) { this.allowedTables = allowedTables; }
    public Set<String> getForbiddenTables() { return forbiddenTables; }
    public void setForbiddenTables(Set<String> forbiddenTables) { this.forbiddenTables = forbiddenTables; }
    public Map<String, TableSchema> getSchemas() { return schemas; }

    // Records internos
    public record TableSchema(String description, List<ColumnSchema> columns) {}
    public record ColumnSchema(String name, String type, String description) {}
}
