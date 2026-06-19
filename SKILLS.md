# SKILLS.md

The capabilities ("skills") this RAG service exposes, how each works, and how to
extend them. Each skill is backed by a small interface so implementations can be
swapped without touching callers.

## 1. Ingest knowledge

**What:** Take raw text, split it into overlapping chunks, embed each chunk, and
store the vectors for later retrieval.

**Endpoint:** `POST /api/ingest` — body `{ "text": "...", "source": "optional-label" }`
→ returns `{ "source", "chunksStored" }`.

**Pipeline:** `Chunker.chunk()` → `EmbeddingClient.embedAll()` →
`VectorStore.upsert()`, orchestrated in `RagService.ingest()`.

**Chunking strategy** (`rag.chunking.strategy`):
- `semantic` (default) — `SemanticChunker` embeds each sentence and starts a new chunk
  where consecutive-sentence similarity drops below `similarity-threshold` (0.5). Groups
  by meaning rather than arbitrary offsets.
- `sentence` — `SentenceChunker` packs whole sentences up to `chunk-size`. No embeddings.
- `fixed` — `FixedSizeChunker`: fixed-size windows with `overlap`.

**Tunable:** `chunk-size` (default 800 chars caps chunk length), `overlap` (fixed only),
`similarity-threshold` (semantic only). Larger chunks = more context per hit but coarser retrieval.

## 2. Embed text

**What:** Convert text into a vector. The active provider is chosen by
`rag.embedding-provider`.

| Provider | Impl | Notes |
|---|---|---|
| `ollama` | `OllamaEmbeddingClient` | local, free; e.g. `nomic-embed-text` (768-dim) |
| `azure` | `AzureOpenAiEmbeddingClient` | `text-embedding-3-small` (1536-dim) |
| `auto` | — | Azure if configured, otherwise Ollama |

**Invariant:** the embedding dimension must be identical at ingest and query time,
and must match the Cosmos container's vector policy.

## 3. Store & search vectors

**What:** Persist chunk vectors and return the top-k most similar to a query vector.

| Store | Impl | How it ranks |
|---|---|---|
| `memory` | `InMemoryVectorStore` | cosine similarity in-process |
| `cosmos` | `CosmosVectorStore` | Cosmos NoSQL `VectorDistance()` query |

Selected by `rag.vector-store`. The Cosmos store expects a pre-created container
(partition key `/id`, vector index on `/embedding`).

**Tunable:** `rag.retrieval.top-k` (default 4) or per-request `topK`.

## 4. Generate a grounded answer (with provider routing)

**What:** Build a prompt from the retrieved chunks and generate an answer, choosing
the model per request.

**Endpoint:** `POST /api/chat` — body
`{ "question": "...", "provider": "ollama|claude", "topK": 6 }` (last two optional)
→ returns `{ "answer", "provider", "sources": [{id, source, score, preview}] }`.

**Routing:** `LlmRouter.resolve(provider)` picks `OllamaLlmClient` (local, default) or
`ClaudeLlmClient` (remote), falling back to `rag.llm-provider` when no provider is
given. The system prompt instructs the model to answer only from context and cite
source numbers; if nothing is retrieved, the app returns a safe "no documents" message
without calling the LLM.

## 5. Health / introspection

`GET /api/health` → `{ "status": "ok" }`. The `sources` array on every chat response
is the retrieval trail (which chunks, with scores) — the main RAG-debugging signal.

## How to add a new skill / backend

1. **New embedding or LLM or vector backend:** implement the relevant interface
   (`EmbeddingClient`, `LlmClient`, or `VectorStore`), then register it — a `@Bean`
   in the matching config, or `@Component` with a `@ConditionalOnProperty` switch.
   Don't branch inside an existing client; add a sibling implementation.
2. **New API skill (e.g. delete/list documents, multi-turn chat):** add a method to
   `RagService`, then expose it from `RagController` with a request/response record in
   `Dtos`. Map new error cases in `ApiExceptionHandler`.
3. **Keep it swappable:** callers depend on interfaces, not concrete classes, so a new
   backend should require zero changes in `RagService` or the controller.

## Quick reference: enabling each configuration

- Fully local/free: `RAG_EMBEDDING_PROVIDER=ollama`, `RAG_VECTOR_STORE=memory`,
  `RAG_LLM_PROVIDER=ollama`.
- Azure-backed retrieval, local generation: `RAG_EMBEDDING_PROVIDER=azure`,
  `RAG_VECTOR_STORE=cosmos`, `RAG_LLM_PROVIDER=ollama`.
- Remote generation: add `ANTHROPIC_API_KEY` and pass `"provider":"claude"`.
