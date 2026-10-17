package br.com.innitisoftware.financeiro.finance_ai_copilot.service.advisor.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gera SQL a partir de perguntas em linguagem natural, usando o LLM com prompt schema-aware.
 *
 * Fluxo:
 * 1. Recebe pergunta + schema curado + guia de JOIN.
 * 2. Monta prompt determinístico com regras rígidas.
 * 3. Chama o LLM e extrai o SQL da resposta.
 * 4. Sanitiza o SQL (remove markdown, comentários do LLM, etc.).
 * 5. Devolve SQL bruto para o SqlValidator.
 *
 * NÃO executa SQL. NÃO valida segurança. Essas responsabilidades são do SqlValidator e SqlExecutor.
 */
@Component
public class SqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerator.class);
    private static final Logger auditLog = LoggerFactory.getLogger("sql-audit");

    /** Padrão para extrair SQL de blocos markdown, caso o LLM ignore a instrução. */
    private static final Pattern SQL_BLOCK_PATTERN =
            Pattern.compile("```(?:sql)?\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Comprimento máximo aceitável antes de considerar resposta suspeita. */
    private static final int MAX_RAW_RESPONSE_LENGTH = 3000;

    private final ChatClient chatClient;
    private final SqlSchemaProvider schemaProvider;

    public SqlGenerator(ChatClient.Builder chatClientBuilder,
                        SqlSchemaProvider schemaProvider) {
        this.schemaProvider = schemaProvider;
        // ChatClient dedicado, com temperatura baixa para geração de SQL
        this.chatClient = chatClientBuilder
                .defaultSystem(buildSystemPrompt())
                .build();
    }

    /**
     * Gera SQL a partir da pergunta em linguagem natural.
     *
     * @param question   pergunta do usuário
     * @param propertyId identificador do tenant (usado para poupar uma chamada extra caso
     *                   o LLM precise saber o contexto — o filtro real é injetado pelo Executor)
     * @return SQL bruto (não validado) pronto para o SqlValidator
     */
    public String generate(String question, long propertyId) {
        long start = System.currentTimeMillis();

        try {
            String userPrompt = buildUserPrompt(question);

            String rawResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - start;

            if (rawResponse == null || rawResponse.isBlank()) {
                auditLog.warn("LLM retornou vazio | pergunta=\"{}\" | tempo={}ms",
                        question, elapsed);
                throw new SqlGenerationException(
                        "O modelo não retornou SQL para a pergunta.");
            }

            String sql = extractSql(rawResponse);

            auditLog.info(
                    "SQL gerado | propertyId={} | tempo={}ms | pergunta=\"{}\" | sql=\"{}\"",
                    propertyId, elapsed, question, sql
            );

            return sql;

        } catch (SqlGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao gerar SQL | pergunta=\"{}\"", question, e);
            throw new SqlGenerationException(
                    "Não foi possível gerar uma consulta para esta pergunta.", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // System prompt (fixo, montado uma vez no construtor)
    // ─────────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        LocalDate today = LocalDate.now();
        LocalDate previousMonthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate previousMonthEnd = today.withDayOfMonth(1).minusDays(1);

        return """
            Você é um gerador de SQL para um banco de dados financeiro pessoal multi-tenant.
            Sua única função é converter perguntas em linguagem natural em comandos SELECT.
            Gere consultas compatíveis com MySQL 8.x com sql_mode=ONLY_FULL_GROUP_BY.

            ═══════════════════════════════════════════
            REGRAS ABSOLUTAS (violação = resposta rejeitada)
            ═══════════════════════════════════════════
            1. Gere APENAS comandos SELECT. Nunca INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, GRANT, REVOKE ou qualquer DDL/DML.
            2. NUNCA use múltiplos statements. Uma pergunta = um SELECT.
            3. NUNCA use comentários SQL (-- ou /* */).
            4. SEMPRE inclua no WHERE um filtro independente para CADA tabela do domínio consultada, usando o alias da tabela: `<alias>.property_id = :propertyId`. Para `properties`, use `<alias>.id = :propertyId`. Todos esses filtros devem estar conectados por AND, nunca dentro de OR.
            5. NUNCA referencie a tabela `users` — ela é proibida por conter dados sensíveis.
            6. NUNCA use placeholders posicionais JDBC (`?`). Para isolamento, use exclusivamente o parâmetro nomeado `:propertyId`. O executor só fornece esse parâmetro; não invente outros placeholders nomeados. Para valores específicos da pergunta, use literais SQL válidos, com strings entre aspas simples.
            7. SEMPRE aplique LIMIT 100 quando a consulta NÃO tiver GROUP BY.
            8. TODA coluna não agregada exibida no SELECT DEVE aparecer também no GROUP BY. O servidor usa ONLY_FULL_GROUP_BY e rejeita o contrário.
            9. NUNCA agrupe apenas pela chave primária (`<alias>.id`) quando o SELECT exibir outras colunas não agregadas E a consulta usar `WITH ROLLUP`. Sem ROLLUP o MySQL aceita por dependência funcional da chave primária; com ROLLUP rejeita (erro 1055). Para obter a linha de total, agrupe pelas MESMAS colunas não agregadas exibidas, por exemplo: `GROUP BY t.date, t.description WITH ROLLUP`.
            10. Use `WITH ROLLUP` APENAS quando a pergunta pedir total ou subtotal; caso contrário não use ROLLUP. A linha de total vem com NULL nas colunas agrupadas, então identifique-a testando uma dessas colunas, por exemplo: `CASE WHEN t.date IS NULL THEN 'TOTAL' ELSE t.date END`.
            11. Use apenas sintaxe comum ao MySQL 8.x;
            12. `groups` é uma palavra reservada no MySQL. Sempre escreva o nome da tabela entre crases em toda referência SQL: FROM `groups` g, JOIN `groups` g, incluindo qualquer outro uso como `groups`.`name`. Nunca escreva FROM groups nem JOIN groups sem crases. O alias (por exemplo, g) não substitui as crases no nome da tabela.
            13. Para buscas textuais, use LIKE. Para comparação sem diferenciar maiúsculas e minúsculas, use LOWER(coluna) LIKE LOWER(padrão).
            14. NUNCA use funções perigosas como SLEEP, BENCHMARK ou LOAD_FILE.
            15. Retorne APENAS o SQL puro, sem markdown, sem explicações, sem comentários, sem cabeçalho.

            ═══════════════════════════════════════════
            SCHEMA DISPONÍVEL
            ═══════════════════════════════════════════
            %1$s

            ═══════════════════════════════════════════
            GUIA DE JOINS E FILTROS
            ═══════════════════════════════════════════
            %2$s

            ═══════════════════════════════════════════
            CONVENÇÕES DE NEGÓCIO
            ═══════════════════════════════════════════
            - Data de hoje: %3$s. Quando a pergunta mencionar uma data ou mês específico sem informar o ano, use o ano da data de hoje. Para períodos relativos, como "mês passado", use o intervalo calculado a partir da data de hoje, mesmo que o intervalo caia no ano anterior.
            - Datas: use o formato 'YYYY-MM-DD' entre aspas simples.
            - "este mês" = do dia 1 do mês atual até hoje.
            - "mês passado" = de %4$s até %5$s, inclusive. Use essas datas exatas; não escolha outro mês nem deduza o período apenas pelo ano.
            - "esta semana" = de segunda-feira até hoje.
            - "ontem" = data de ontem.
            - Os valores armazenados em `transactions.value` são sempre positivos. Categorias com tipo `debit-transfer`, `credit-transfer` representam transferências entre contas: são neutras, NÃO são gastos nem recebimentos e devem ser excluídas de agregações de despesas ou receitas. Para saldo líquido, trate-as como zero.
            - Para representar o sinal financeiro, faça JOIN com `categories` e use `CASE WHEN LOWER(c.type) IN ('debit-transfer', 'credit-transfer') THEN 0 WHEN LOWER(c.type) = 'debit' THEN -ABS(t.value) ELSE ABS(t.value) END`. Não classifique tipos de transferência como entrada por causa do ELSE. Para consultas específicas de gastos ou receitas, filtre também por `LOWER(c.type) = 'debit'` ou `LOWER(c.type) = 'credit'`, respectivamente.
            - Aplique a expressão de sinal antes de qualquer agregação, média, comparação ou ordenação. NUNCA some, faça média, compare ou exiba `t.value` diretamente. Em listagens de transferências, mostre `ABS(t.value)` e a categoria, sem classificá-las como gasto ou recebimento. Use a mesma regra de neutralidade em consultas detalhadas e agregadas.
            - "onde mais gasto" / "tipo de lugar" → agrupe por `categories.name` (via JOIN com transactions).
            - "último lançamento" → ORDER BY date DESC LIMIT 1.
            - "maior gasto" → ordene pela expressão de valor com sinal ASC (despesas são negativas, então a mais negativa vem primeiro); para "maior receita", use DESC.
            - A aplicação só vincula o parâmetro nomeado `:propertyId`. Nunca use `?` como placeholder, nem crie parâmetros como `:startDate`, `:category` ou outros que não são fornecidos pelo executor. Represente datas, categorias e demais valores extraídos da pergunta como literais SQL válidos; strings devem ficar entre aspas simples.

            ═══════════════════════════════════════════
            EXEMPLO DE SAÍDA ESPERADA
            ═══════════════════════════════════════════
            SELECT c.name AS categoria,
                   SUM(CASE WHEN LOWER(c.type) = 'debit'
                            THEN -ABS(t.value) ELSE 0 END) AS total_gasto
            FROM transactions t
            JOIN categories c ON t.category_id = c.id
            WHERE t.property_id = :propertyId
              AND c.property_id = :propertyId
              AND LOWER(c.type) = 'debit'
              AND t.date >= '%4$s'
              AND t.date <= '%5$s'
              AND LOWER(c.name) LIKE LOWER('%%alimentação%%')
            GROUP BY c.name
            ORDER BY total_gasto DESC
            """.formatted(
                schemaProvider.getSchemaForPrompt(),
                schemaProvider.getJoinGuide(),
                today,
                previousMonthStart,
                previousMonthEnd
            );
    }

    private String buildUserPrompt(String question) {
        return """
            Converta a pergunta abaixo em um comando SELECT.
            Lembre-se: retorne APENAS o SQL puro, sem markdown, sem explicações.

            Pergunta: %s
            """.formatted(question.trim());
    }

    /**
     * Extrai o SQL da resposta do LLM, cobrindo os casos comuns:
     * 1. Bloco markdown ```sql ... ```
     * 2. Bloco markdown ``` ... ```
     * 3. Texto puro (esperado)
     * 4. Texto com prefixo conversacional ("Aqui está o SQL: ...")
     */
    private String extractSql(String rawResponse) {
        if (rawResponse.length() > MAX_RAW_RESPONSE_LENGTH) {
            throw new SqlGenerationException(
                    "Resposta do modelo excedeu o tamanho máximo aceitável.");
        }

        String sql = rawResponse.trim();

        // 1. Tentar extrair de bloco markdown
        Matcher matcher = SQL_BLOCK_PATTERN.matcher(sql);
        if (matcher.find()) {
            sql = matcher.group(1).trim();
        }

        // 2. Remover prefixos conversacionais comuns
        sql = stripConversationalPrefix(sql);

        // 3. Remover `;` final (o Validator também remove, mas garantimos aqui)
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }

        // 4. Validação mínima: precisa começar com SELECT
        if (!sql.toLowerCase().startsWith("select")) {
            throw new SqlGenerationException(
                    "O modelo não gerou um comando SELECT válido.");
        }

        return sql;
    }

    /**
     * Remove prefixos como:
     * - "Aqui está o SQL:"
     * - "SQL:"
     * - "Query:"
     * - "```sql"
     * - Quebras de linha iniciais
     */
    private String stripConversationalPrefix(String sql) {
        String[] prefixes = {
                "aqui está o sql:", "aqui está a consulta:", "aqui está a query:",
                "sql:", "query:", "consulta:", "select:", "```sql", "```"
        };

        String lower = sql.toLowerCase();
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                return sql.substring(prefix.length()).trim();
            }
        }
        return sql;
    }

    public static class SqlGenerationException extends RuntimeException {
        public SqlGenerationException(String message) {
            super(message);
        }
        public SqlGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}