# Finance AI Copilot

An intelligent, conversational interface for querying financial data using natural language. Built with **Java 25**, **Spring Boot 4**, and **Spring AI**, this project explores how to safely bridge the gap between Large Language Models (LLMs) and relational databases in multi-tenant systems.

## The problem

Standard RAG (Retrieval-Augmented Generation) is great for finding documents by semantic similarity. However, it fails miserably when users ask analytical questions like:
- *"Quanto eu gastei no mês passado?"*
- *"Qual foi minha última transação?"*

Retrieving a limited set of documents (e.g., `topK=8`) and letting the LLM do the math often results in **hallucinated values** and **incorrect aggregations**. 

## The solution

This project implements a **Hybrid Architecture** with an **Intent Router**. Before the LLM generates any response, the system classifies the user's intent and routes the request to the correct data source.

```text
User Question
      │
      ▼
┌─────────────────┐
│  Query Router   │  (Keyword & Proximity based)
└────────┬────────┘
         │
   ┌─────┴────────────────────────┐
   ▼                              ▼
┌──────────────────┐       ┌──────────────────┐
│  ANALYTICAL &    │       │   CONTEXTUAL     │
│   RELATIONAL     │       │                  │
│ (Text-to-SQL)    │       │      (RAG)       │
└────────┬─────────┘       └────────┬─────────┘
         │                          │
         ▼                          ▼
┌──────────────────┐       ┌──────────────────┐
│ SqlGenerator     │       │ VectorStore      │
│ SqlValidator     │       │ Similarity Search│
│ SqlExecutor      │       │ (Ollama)         │
└────────┬─────────┘       └────────┬─────────┘
         │                          │
         └──────────┬───────────────┘
                    ▼
         ┌────────────────────┐
         │   LLM (DeepSeek)   │
         │  Formats Response  │
         └────────────────────┘
```

### The three routes

1. ANALYTICAL: Asks for totals, sums, averages, counts. Routed directly to dynamic SQL generation.

2. RELATIONAL: Asks for lists, orderings, and temporal filters (e.g., "mais recente", "mais caro"). Routed to dynamic SQL generation.

3. CONTEXTUAL: Asks about meaning, descriptions, or memory (e.g., "Qual foi aquela compra na padaria?"). Routed to VectorStore (RAG). If the vector search returns empty, it falls back to SQL.

### Security first (text-to-sql layer)

Giving an LLM direct access to a database is dangerous. This project implements Defense in Depth:

1. Schema-Aware Prompting: The LLM only knows the whitelisted tables and columns.

2. Strict Parsing (JSqlParser): Raw text is never executed. The SQL is parsed, and only SELECT statements are allowed.

3. Tenant Isolation: Every generated query must include a property_id = :propertyId filter. The tenant ID is injected via ToolContext and never exposed to the LLM.

4. Function Blacklisting: Blocks dangerous functions (e.g., pg_sleep, load_file).

5. Read-Only Database User: The application connects to the database using a dedicated user with SELECT-only permissions and a 3-second query timeout.

## Tech stack

* Language: Java 25 (Records, Pattern Matching, Virtual Threads)
* Framework: Spring Boot 4 / Spring Framework 7
* AI Integration: Spring AI
* LLM (Chat): DeepSeek (via API)
* Embeddings: Qwen3-Embedding:0.6b (via Ollama - local)
* Database: PostgreSQL (Multi-tenant schema)
* SQL Parsing: JSqlParser
* Build Tool: Maven

## Key learnings & Trade-offs

* RAG vs. SQL: RAG is semantic; SQL is structural. Combining them via a lightweight router (keyword + proximity) reduced analytical errors from ~30% to <5% in a 40-question test suite.
* LLMs are bad at math: Never let the LLM do aggregations. Let the database do the SUM() and COUNT().
* Prompt Engineering is not Security: You cannot prompt your way out of SQL injection. You need a parser, a whitelist, and database-level constraints.
* Bleeding Edge Risks: Spring AI is evolving rapidly. This project required working around framework-specific bugs (e.g., DeepSeek tool-calling serialization) and distinguishing them from application bugs.

## How to test (local setup)

### Automated tests

Install JDK 25, then run the Maven test suite from the project root:

```bash
mvn test
```

The suite covers application context startup, query routing, SQL validation,
advisor behavior, and transaction ingestion. It does not make live requests to
DeepSeek or Ollama.

### Run the application locally

To exercise the API end to end, configure the external services first:

1. Copy `.env.example` to `.env` and set a random `JWT_SECRET` of at least 32
   bytes, your `DEEPSEEK_API_KEY`, and the PostgreSQL connection settings
   (`NEON_JDBC_URL`, `NEON_DB_USER`, and `NEON_DB_PASSWORD`).
2. Run `src/main/resources/sqls/script_neon.sql` against that PostgreSQL database
   to enable pgvector and create the vector-store and ingestion checkpoint tables.
3. Configure `LEGACY_DB_URL`, `LEGACY_DB_USER`, and `LEGACY_DB_PASSWORD` for a
   MySQL database containing the transaction tables queried by
   `src/main/resources/sqls/ingestion-transactions.sql`.
4. Install and start [Ollama](https://ollama.com), then download the configured
   embedding model:

   ```bash
   ollama pull qwen3-embedding:0.6b
   ```

Start the app:

```bash
mvn spring-boot:run
```

Use a valid bearer token from the identity system (issuer `sistema-a`, with
numeric `sub` and `property_id` claims) to test chat:

```bash
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Authorization: Bearer <valid-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"question":"Quanto eu gastei no mês passado?"}'
```

The `/api/v1/ai/sync` endpoint can be tested with the same bearer token after
both databases and Ollama are configured; it reads legacy transactions and
stores their embeddings in PostgreSQL.

## Next steps

□ Implement ChatMemory for conversational context (e.g., resolving "essa compra").

□ Migrate keyword-based routing to a lightweight LLM classifier for ambiguous cases.

□ Implement SQL caching for frequent analytical queries to reduce latency and token costs.

□ Add a Semantic Layer to pre-define core metrics, reducing reliance on dynamic Text-to-SQL.

## Author

Developed by Vanessa Campanha [LinkedIn](https://www.linkedin.com/in/voscampanha)

*This project is a study in enterprise-grade AI integration, focusing on safety, observability, and architectural trade-offs in the Java ecosystem.*
