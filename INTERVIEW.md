# Interview Cheat-Sheet — Local + Remote RAG Service

## 30-second pitch
A RAG service in **Java 17 / Spring Boot 3** where every external dependency has a local
fallback. Ingest → chunk → embed → store vectors; query → embed → vector-search →
grounded prompt → answer. **Provider-agnostic at every layer**: embeddings from Azure
OpenAI *or* local Ollama, vectors in Azure Cosmos DB *or* in-memory, generation routed to
local Llama *or* remote Claude — per request. Runs fully offline/free in dev, swaps to
managed Azure in prod with **config only, no code change**.

## Demo flow (3 moves)
1. `POST /api/ingest` a short document.
2. `POST /api/chat` — point at the `sources` array = the retrieval trail it grounded on.
3. Flip one env var (`RAG_LLM_PROVIDER` or `RAG_EMBEDDING_PROVIDER`) — same call, new backend.

## Architecture — 3 swappable seams (interfaces)
- `EmbeddingClient` → Ollama / Azure  (picked by `rag.embedding-provider`)
- `VectorStore` → Cosmos (`VectorDistance()`) / in-memory (cosine)  (`rag.vector-store`)
- `LlmClient` → Ollama / Claude, chosen by `LlmRouter.resolve()`  (`rag.llm-provider`)
- `RagService` orchestrates; `RagController` exposes `/api/ingest`, `/api/chat`, `/api/health`.

## Themes to volunteer
- **Offline-first / fallbacks** → testable, zero-cost dev loop.
- **Routing pattern** → cheap local model for routine queries, escalate to frontier model on demand (cost + privacy).
- **Grounding + citations** → system prompt says "answer only from context"; returns sources; safe message when retrieval is empty.
- **Secrets server-side** → keys in env vars, never the client.
- **Open/closed** → new backend = new class, not an `if`.

## Likely Q&A (one-liners)
- **Why chunk + size?** Model context limits; smaller = sharper retrieval. ~800 chars, 120 overlap so sentences aren't cut.
- **How does vector search work?** Embeddings place similar text nearby; cosine = angle. Cosmos does it natively; in-memory computes cosine.
- **Stop hallucination?** Grounding, "say I don't know," return sources, short-circuit on empty retrieval.
- **Why interfaces per provider?** Open/closed, easy fakes for tests.
- **Hardest invariant?** Embedding dims must match across ingest / query / Cosmos vector policy — change model ⇒ re-ingest.
- **Why Cosmos partition key `/id`?** Even distribution + point lookups; upsert by id.
- **What breaks at scale?** In-memory store is single-node/non-durable → Cosmos for prod. Embedding calls are the latency cost → batch (done) + cache; async ingest for large corpora.
- **How to evaluate quality?** Retrieval metrics (recall@k, MRR) + answer faithfulness/groundedness on a labeled set.

## Curveballs — have a sentence ready
- **Metadata filter / hybrid search** → add WHERE to the Cosmos query; combine BM25 keyword + vector.
- **Reranking** → cross-encoder rerank after top-k, before prompting.
- **Streaming** → both providers stream tokens; switch controller to SSE.
- **Auth / multi-tenancy / rate limiting** → partition by tenant, API gateway.

## Honest gaps (say these — reads senior)
- No automated eval/metrics harness yet (verify via `sources` for now).
- No reranking step yet — retrieval is pure top-k cosine; a cross-encoder rerank would lift precision.

## Tech stack name-drop
Java 17, Spring Boot 3, Spring `RestClient` (no WebFlux), Java records for DTOs,
constructor injection, `@ConditionalOnProperty` for backend selection,
Azure Cosmos DB NoSQL vector search, Azure OpenAI, Ollama, Anthropic Messages API.
