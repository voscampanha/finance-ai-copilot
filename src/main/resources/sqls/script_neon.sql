-- 1. Habilita a extensão pgvector no PostgreSQL do Neon
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Tabela principal para armazenamento de embeddings do RAG
CREATE TABLE IF NOT EXISTS transaction_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Vínculo com o id da transação no banco legado (evita duplicatas na sincronização)
    legacy_transaction_id BIGINT UNIQUE NOT NULL,
    
    -- O texto narrativo e sanitizado da transação
    content TEXT NOT NULL,
    
    -- O vetor gerado pelo Ollama qwen3-embedding:0.6b (1024 dimensões)
    embedding vector(1024) NOT NULL,
    
    -- Metadados em formato JSONB para filtros rápidos (usuario_id, category_id, data, etc.)
    metadata JSONB NOT NULL,
    
    -- Timestamps do registro no banco vetorial
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Índices de Performance e Segurança

-- Índice HNSW no vetor para buscas semânticas ultra-rápidas usando distância Cosine
CREATE INDEX IF NOT EXISTS idx_transaction_embeddings_vector 
ON transaction_embeddings USING hnsw (embedding vector_cosine_ops);

-- Índice GIN nos metadados JSONB para filtrar instantaneamente por usuario_id / property_id / datas
CREATE INDEX IF NOT EXISTS idx_transaction_embeddings_metadata 
ON transaction_embeddings USING gin (metadata);

-- Índice específico no property_id/usuario_id dentro do JSONB para acelerar a busca multi-tenant
CREATE INDEX IF NOT EXISTS idx_transaction_embeddings_property_id 
ON transaction_embeddings ((metadata->>'property_id'));


CREATE TABLE IF NOT EXISTS processing_checkpoint (
    job_name VARCHAR(100) PRIMARY KEY,
    last_processed_id BIGINT NOT NULL,
    last_processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO processing_checkpoint (job_name, last_processed_id, last_processed_at)
VALUES ('transaction_embedding_job', 0, '1970-01-01 00:00:00+00')
ON CONFLICT (job_name) DO NOTHING;
