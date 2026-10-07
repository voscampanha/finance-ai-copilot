package br.com.innitisoftware.financeiro.finance_ai_copilot.advisor.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

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
            6. SEMPRE use parâmetros nomeados (`:propertyId`) em vez de concatenar valores.
            7. SEMPRE aplique LIMIT 100 quando a consulta NÃO tiver GROUP BY.
            8. TODA coluna não agregada exibida no SELECT DEVE aparecer também no GROUP BY. O servidor usa ONLY_FULL_GROUP_BY e rejeita o contrário.
            9. NUNCA agrupe apenas pela chave primária (`<alias>.id`) quando o SELECT exibir outras colunas não agregadas E a consulta usar `WITH ROLLUP`. Sem ROLLUP o MySQL aceita por dependência funcional da chave primária; com ROLLUP rejeita (erro 1055). Para obter a linha de total, agrupe pelas MESMAS colunas não agregadas exibidas, por exemplo: `GROUP BY t.date, t.description WITH ROLLUP`.
            10. Use `WITH ROLLUP` APENAS quando a pergunta pedir total ou subtotal; caso contrário não use ROLLUP. A linha de total vem com NULL nas colunas agrupadas, então identifique-a testando uma dessas colunas, por exemplo: `CASE WHEN t.date IS NULL THEN 'TOTAL' ELSE t.date END`.
            11. Use apenas sintaxe comum ao MySQL 8.x;
            12. Para buscas textuais, use LIKE. Para comparação sem diferenciar maiúsculas e minúsculas, use LOWER(coluna) LIKE LOWER(padrão).
            13. NUNCA use funções perigosas como SLEEP, BENCHMARK ou LOAD_FILE.
            14. Retorne APENAS o SQL puro, sem markdown, sem explicações, sem comentários, sem cabeçalho.

            ═══════════════════════════════════════════
            SCHEMA DISPONÍVEL
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            GUIA DE JOINS E FILTROS
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            CONVENÇÕES DE NEGÓCIO
            ═══════════════════════════════════════════
            - Datas: use o formato 'YYYY-MM-DD' entre aspas simples.
            - "este mês" = do dia 1 do mês atual até hoje.
            - "mês passado" = do dia 1 ao último dia do mês anterior.
            - "esta semana" = de segunda-feira até hoje.
            - "ontem" = data de ontem.
            - Valores monetários: positivos = receita, negativos = despesa (ou conforme convenção do banco; use SUM(value) para totais).
            - "onde mais gasto" / "tipo de lugar" → agrupe por `categories.name` (via JOIN com transactions).
            - "último lançamento" → ORDER BY date DESC LIMIT 1.
            - "maior gasto" → ORDER BY value DESC (ou ASC se for receita).
            - Use os parâmetros nomeados `:propertyId` exigidos pela aplicação; não os substitua por `?`.

            ═══════════════════════════════════════════
            EXEMPLO DE SAÍDA ESPERADA
            ═══════════════════════════════════════════
            SELECT c.name AS categoria, SUM(t.value) AS total_gasto
            FROM transactions t
            JOIN categories c ON t.category_id = c.id
            WHERE t.property_id = :propertyId
              AND t.date >= '2025-03-01'
              AND LOWER(c.name) LIKE LOWER('%%alimentação%%')
            GROUP BY c.name
            ORDER BY total_gasto DESC
            """.formatted(
                schemaProvider.getSchemaForPrompt(),
                schemaProvider.getJoinGuide()
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