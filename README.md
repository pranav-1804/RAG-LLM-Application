<video src="https://github.com/pranav-1804/RAG-LLM-Application/raw/main/assets/LLM.mp4" controls width="100%"></video>

> ▶️ **Demo video:** if the player above doesn't load, [watch LLM.mp4](https://github.com/pranav-1804/RAG-LLM-Application/raw/main/assets/LLM.mp4).

# RAG Assistant — Spring Boot

A small, Retrieval-Augmented Generation service. You ingest documents, it embeds them with **Azure OpenAI** (or local Ollama), stores the vectors in **Azure Cosmos DB for NoSQL** (with a zero-config in-memory fallback), and answers questions grounded in the retrieved text. Retrieval is **hybrid by default** — dense vector search fused with BM25 keyword search via Reciprocal Rank Fusion — so it catches both semantically similar and exact-keyword matches. Generation is routed to a **local LLM (Ollama)** by default, with a per-request toggle to the **remote Claude** API.

The whole thing runs **end-to-end offline** with no Azure account and no API keys — point it at local Ollama for embeddings and the in-memory store, and you can demo the pipeline immediately, then swap in the managed Azure services later by changing config only.


## Architecture

```
            ┌──────────────┐   embed    ┌─────────────────────┐
POST /ingest │ Text → chunks ├──────────►│ EmbeddingClient     │  Azure OpenAI
            └──────┬───────┘            │  (Azure or Ollama)  │  / local Ollama
                   │                    └─────────┬───────────┘
                   │ chunks                       │ vectors
                   ▼                              ▼
        ┌─────────────────────┐        ┌─────────────────────┐
        │ LexicalIndex (BM25) │        │ VectorStore         │  Cosmos DB (VectorDistance)
        │  (in-memory)        │        │ (or in-memory)      │  / in-memory cosine
        └─────────┬───────────┘        └─────────┬───────────┘
                  │ keyword hits                  │ vector hits
                  └──────────────┬────────────────┘
                                 ▼
                       ┌─────────────────────┐
                       │ Retriever           │  hybrid (RRF) | vector | lexical
                       │  (RetrievalConfig)  │
                       └─────────┬───────────┘
            ┌──────────────┐     │ fused top-k chunks
POST /chat  │ Question      ├─────┘
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
- `Retriever` — `HybridRetriever`, `VectorRetriever`, or `LexicalRetriever`, picked by `RetrievalConfig` from `rag.retrieval.mode` (with the Cosmos store the lexical/hybrid modes are served natively by `CosmosLexicalRetriever`/`CosmosHybridRetriever`)
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
| `RAG_RETRIEVAL_MODE` | `hybrid` (vector + BM25, RRF-fused), `vector`, or `lexical` | `hybrid` |
| `rag.retrieval.top-k` | chunks fed to the prompt (override per request with `topK`) | `4` |
| `rag.retrieval.candidate-pool` | candidates pulled from each retriever before fusion (in-memory hybrid only) | `20` |
| `rag.retrieval.rrf-k` | RRF constant `k`; larger flattens the rank weighting (in-memory hybrid only) | `60` |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | local LLM | `http://localhost:11434` / `llama3.2` |
| `OLLAMA_EMBED_MODEL` / `OLLAMA_EMBED_DIMENSIONS` | local embedding model | `nomic-embed-text` / `768` |
| `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_API_KEY` | Azure OpenAI (needed for `azure` embeddings) | — |
| `AZURE_OPENAI_EMBEDDINGS_DEPLOYMENT` | embeddings deployment name | `text-embedding-3-small` |
| `AZURE_OPENAI_EMBEDDING_DIMENSIONS` | vector size | `1536` |
| `COSMOS_ENDPOINT` / `COSMOS_KEY` | Cosmos DB account | — |
| `COSMOS_DATABASE` / `COSMOS_CONTAINER` | database + container names | `ragdb` / `documents` |
| `ANTHROPIC_API_KEY` | enables Claude routing | — |
| `CLAUDE_MODEL` | Claude model id | `claude-sonnet-4-6` |

## Retrieval modes (hybrid / vector / lexical)

Retrieval is governed by `rag.retrieval.mode` (`RAG_RETRIEVAL_MODE`) and defaults to **hybrid**. The mode is independent of which vector store you use — the same switch works with the in-memory store and with Cosmos.

- **`hybrid` (default)** — runs dense vector search and BM25 keyword search in parallel, then fuses the two rankings with **Reciprocal Rank Fusion (RRF)** and returns the top-k. This combines the *semantic recall* of embeddings (matches by meaning, even with no shared words) with the *exact-keyword precision* of BM25 (matches rare terms, names, codes, and acronyms that embeddings tend to blur).
- **`vector`** — dense vector similarity only (the original behaviour).
- **`lexical`** — BM25 keyword search only, no embeddings used at query time.

### How hybrid works

```
question ──► embed ──► vector search ─┐
        └──► tokenize ─► BM25 search ─┴─► RRF fuse ─► top-k chunks ─► prompt
```

1. The query is embedded and run against the `VectorStore`; the raw query text is run against the BM25 `LexicalIndex`. Each retriever returns a wider **candidate pool** (`rag.retrieval.candidate-pool`, default 20) than the final cut, so fusion has room to promote chunks that rank moderately in *both* lists.
2. **RRF** merges the two lists using only each chunk's *rank* within a list, not its raw score. A chunk at 0-based rank `r` in a list contributes `1 / (k + r + 1)` to its fused score (`k` = `rag.retrieval.rrf-k`, default 60), summed across lists. Using ranks rather than scores makes fusion robust to the incompatible scales of cosine similarity (0–1) and BM25 (unbounded), so neither signal needs normalising.
3. The fused list is sorted and trimmed to `top-k`. The `ScoredChunk.score` returned in the response is the fused RRF score.

Hybrid degrades gracefully: if nothing has been ingested into the lexical index, RRF naturally reduces to the vector ranking.

### BM25 in the in-memory store

With the in-memory store, the keyword side is a **zero-dependency `InMemoryBm25Index`** (classic Okapi BM25, `k1=1.2`, `b=0.75`) maintained alongside the vector store, so the full hybrid path runs offline with no extra services. Tokenisation lower-cases, splits on non-alphanumerics, and drops a small stop-word list. Re-ingesting a chunk id correctly rolls back its old term statistics before re-indexing.

### Hybrid on Cosmos DB

With `RAG_VECTOR_STORE=cosmos`, the in-memory BM25 index is **not** used. Instead, `lexical` and `hybrid` are delegated to Cosmos's **native server-side full-text + hybrid search** (`FullTextScore` + `VectorDistance`, fused with Cosmos's own RRF), so fusion happens next to the data. The `candidate-pool` / `rrf-k` settings apply only to the in-memory path. This requires a **full-text policy and full-text index on `/text`** (see below).

## Using Azure Cosmos DB (vector search)

Set `RAG_VECTOR_STORE=cosmos` plus the `COSMOS_*` variables. The app expects the container to already exist with a **vector embedding policy** and a **vector index** on `/embedding`. Create the account with the vector capability once:

```bash
az cosmosdb create --name <acct> --resource-group <rg> --capabilities EnableNoSQLVectorSearch
```

The container's vector policy should declare path `/embedding`, data type `float32`, distance function `cosine`, and dimensions matching `AZURE_OPENAI_EMBEDDING_DIMENSIONS`. Vector retrieval then uses Cosmos's native query:

```sql
SELECT TOP @k c.id, c.source, c.text, VectorDistance(c.embedding, @embedding) AS distance
FROM c ORDER BY VectorDistance(c.embedding, @embedding)
```

To use `RAG_RETRIEVAL_MODE=lexical` or `hybrid` with Cosmos, the container additionally needs a **full-text policy** (`fullTextPaths: [{ path: "/text", language: "en-US" }]`) and a **full-text index on `/text`**, and the account must have the vector/full-text search feature enabled. Vector and full-text policies/indexes are **immutable** — a container that was created without the full-text policy must be recreated to add it. This path requires `azure-cosmos` ≥ 4.65 (GA at 4.79). Cosmos then serves lexical search with `FullTextScore` and hybrid search by fusing `FullTextScore` + `VectorDistance` with its native RRF — no `candidate-pool` / `rrf-k` tuning applies.

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

The RAG logic sits behind a few small interfaces and a single `RagService` facade. To drop it into another Spring Boot app, copy the `embedding`, `store`, `retrieval`, `llm`, and `service` packages (plus `RagProperties`) and autowire `RagService`. The REST layer in `web` is a thin adapter you can keep or replace with your own controllers, a CLI, or a message listener.
