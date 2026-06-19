# RAG Assistant — Spring Boot

A small, Retrieval-Augmented Generation service. You ingest documents, it embeds them with **Azure OpenAI**, stores the vectors in **Azure Cosmos DB for NoSQL** (with a zero-config in-memory fallback), and answers questions grounded in the retrieved text. Generation is routed to a **local LLM (Ollama)** by default, with a per-request toggle to the **remote Claude** API.

The whole thing runs **end-to-end offline** with no Azure account and no API keys — point it at local Ollama for embeddings and the in-memory store, and you can demo the pipeline immediately, then swap in the managed Azure services later by changing config only.


## Architecture

```
            ┌──────────────┐   embed    ┌─────────────────────┐
POST /ingest │ Text → chunks ├──────────►│ EmbeddingClient     │  Azure OpenAI
            └──────────────┘            │  (Azure or Ollama)  │  / local Ollama
                                        └─────────┬───────────┘
                                                  │ vectors
                                                  ▼
                                        ┌─────────────────────┐
                                        │ VectorStore         │  Cosmos DB (VectorDistance)
                                        │ (or in-memory)      │  / in-memory cosine
                                        └─────────┬───────────┘
            ┌──────────────┐   search            │ top-k chunks
POST /chat  │ Question      ├─────────────────────┘
            └──────┬───────┘
                   │ prompt (context + question)
                   ▼
            ┌─────────────────────┐   resolve(provider)
            │ LlmRouter           ├──────────► OllamaLlmClient  (local, default)
            └─────────────────────┘──────────► ClaudeLlmClient  (remote, toggle)
```

Each major step is an interface with swappable implementations:

- `EmbeddingClient` — `OllamaEmbeddingClient` or `AzureOpenAiEmbeddingClient`
- `VectorStore` — `CosmosVectorStore` or `InMemoryVectorStore`
- `LlmClient` — `OllamaLlmClient` or `ClaudeLlmClient`, picked at request time by `LlmRouter`

## Requirements

- Java 17+
- Maven 3.9+
- (Optional) [Ollama](https://ollama.com) running locally for real generation
- (Optional) Azure OpenAI + Azure Cosmos DB for production-grade embeddings/storage

## Quick start (works offline)

```bash
mvn spring-boot:run
```

By default the app uses the in-memory store. For a fully local, free setup, use Ollama for both generation and embeddings:

```bash
ollama pull llama3.2          # lightweight chat model (~2GB)
ollama pull nomic-embed-text  # embedding model (768-dim, ~274MB)
ollama serve                  # usually already running on :11434

export RAG_EMBEDDING_PROVIDER=ollama   # use Ollama for embeddings too (no Azure)
```

Even lighter chat models: `llama3.2:1b` or `qwen2.5:1.5b` (set `OLLAMA_MODEL`).

Then:

```bash
# Ingest a document
curl -s localhost:8080/api/ingest -H 'Content-Type: application/json' -d '{
  "source": "cosmos-notes",
  "text": "Azure Cosmos DB for NoSQL supports vector search via the VectorDistance() function and a vector index on the embedding path."
}'

# Ask a question (defaults to local Ollama)
curl -s localhost:8080/api/chat -H 'Content-Type: application/json' -d '{
  "question": "How does Cosmos DB do vector search?"
}'

# Force the remote model for this one request
curl -s localhost:8080/api/chat -H 'Content-Type: application/json' -d '{
  "question": "How does Cosmos DB do vector search?",
  "provider": "claude"
}'
```

## Configuration

Settings live in `src/main/resources/application.yml` and can be overridden by environment variables.

| Variable | Purpose | Default |
|---|---|---|
| `RAG_VECTOR_STORE` | `memory` or `cosmos` | `memory` |
| `RAG_EMBEDDING_PROVIDER` | `ollama`, `azure`, or `auto` (Azure if configured, else Ollama) | `ollama` |
| `RAG_LLM_PROVIDER` | default generator: `ollama` or `claude` | `ollama` |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | local LLM | `http://localhost:11434` / `llama3.2` |
| `OLLAMA_EMBED_MODEL` / `OLLAMA_EMBED_DIMENSIONS` | local embedding model | `nomic-embed-text` / `768` |
| `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_API_KEY` | Azure OpenAI (needed for `azure` embeddings) | — |
| `AZURE_OPENAI_EMBEDDINGS_DEPLOYMENT` | embeddings deployment name | `text-embedding-3-small` |
| `AZURE_OPENAI_EMBEDDING_DIMENSIONS` | vector size | `1536` |
| `COSMOS_ENDPOINT` / `COSMOS_KEY` | Cosmos DB account | — |
| `COSMOS_DATABASE` / `COSMOS_CONTAINER` | database + container names | `ragdb` / `documents` |
| `ANTHROPIC_API_KEY` | enables Claude routing | — |
| `CLAUDE_MODEL` | Claude model id | `claude-sonnet-4-6` |

## Using Azure Cosmos DB (vector search)

Set `RAG_VECTOR_STORE=cosmos` plus the `COSMOS_*` variables. The app expects the container to already exist with a **vector embedding policy** and a **vector index** on `/embedding`. Create the account with the vector capability once:

```bash
az cosmosdb create --name <acct> --resource-group <rg> --capabilities EnableNoSQLVectorSearch
```

The container's vector policy should declare path `/embedding`, data type `float32`, distance function `cosine`, and dimensions matching `AZURE_OPENAI_EMBEDDING_DIMENSIONS`. Retrieval then uses Cosmos's native query:

```sql
SELECT TOP @k c.id, c.source, c.text, VectorDistance(c.embedding, @embedding) AS distance
FROM c ORDER BY VectorDistance(c.embedding, @embedding)
```

## API

| Method | Path | Body | Description |
|---|---|---|---|
| `GET` | `/api/health` | — | liveness check |
| `POST` | `/api/ingest` | `{ "text", "source?" }` | chunk → embed → store |
| `POST` | `/api/chat` | `{ "question", "provider?", "topK?" }` | retrieve → generate, returns answer + sources |

`/api/chat` returns the answer, the provider that produced it, and the source chunks (id, source, similarity score, preview) used as context.

## Tests

```bash
mvn test
```

`RagPipelineTest` covers chunking and in-memory vector retrieval end-to-end with a tiny deterministic test embedder — no network or external services required.

## Reusing it as an extension in other projects

The RAG logic sits behind three small interfaces and a single `RagService` facade. To drop it into another Spring Boot app, copy the `embedding`, `store`, `llm`, and `service` packages (plus `RagProperties`) and autowire `RagService`. The REST layer in `web` is a thin adapter you can keep or replace with your own controllers, a CLI, or a message listener.
