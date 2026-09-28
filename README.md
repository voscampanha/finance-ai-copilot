# Finance AI Copilot

Sidecar de IA e RAG somente leitura para dados financeiros legados. O módulo fica em
`br.com.innitisoftware.financeiro.finance_ai_copilot.ai` e não possui entidades JPA nem
operações de escrita no banco legado.

## Fluxo

- `LegacyJdbcFinancialReader` lê lançamentos usando uma conexão separada e a query configurada em `app.legacy.query`.
- `PrivacySanitizer` remove CPF, CNPJ e sequências que possam representar conta/cartão antes da vetorização.
- `FinancialIngestionService` sincroniza por job agendado ou `POST /api/v1/ai/sync`.
- `FinancialAdvisorService` busca no Pgvector com o filtro obrigatório `usuarioId` e consulta o Gemini via `ChatClient`.
- `FinancialMcpTools` expõe busca contextual ao modelo; a ferramenta também aplica o tenant autenticado.

## Configuração

Copie `.env.example` para o ambiente de execução e forneça `GEMINI_API_KEY`, as credenciais Neon
e as credenciais de leitura do legado. A aplicação usa Java 25, virtual threads e
`text-embedding-004` por padrão.

O MCP é configurado por `spring.ai.mcp.client.streamable-http.connections.neon-postgres.url`.
Deixe `MCP_CLIENT_ENABLED=false` até que exista um servidor MCP compatível e autenticado. O
isolamento de tenant não deve ser delegado a uma ferramenta MCP genérica de SQL: toda busca
exposta por este módulo inclui o filtro do usuário autenticado.

## API

`POST /api/v1/ai/chat`:

```json
{"question":"Quanto gastei com alimentação este mês?"}
```

`POST /api/v1/ai/sync` dispara uma sincronização manual. Ambos os endpoints devem ser protegidos
pelo mecanismo de autenticação do sistema hospedeiro; o identificador usado no filtro vem do
`SecurityContext` e nunca do corpo da requisição.