# CLAUDE.md

Guidance for AI coding agents (and humans) working in this repository.

## What this project is

A small Retrieval-Augmented Generation (RAG) service in **Java 17 + Spring Boot 3**.
Flow: ingest text → chunk → embed → store vectors → on a question, embed it →
vector-search → build a grounded prompt → generate an answer. Embeddings come from
**Azure OpenAI** or **local Ollama**; vectors live in **Azure Cosmos DB for NoSQL**
or an in-memory store; generation is routed to **local Ollama** or **remote Claude**.

Design goal: every external dependency has a local fallback so the app runs
end-to-end offline, and each major step is a swappable interface.

## Build, run, test

```bash
mvn spring-boot:run     # start the API on :8080
mvn test                # run unit tests
mvn clean package       # build a runnable jar in target/
```

Requires JDK 17+ and Maven. There is no Maven wrapper checked in.

## Architecture (packages under com.example.rag)

- `embedding/` — `EmbeddingClient` interface; impls: `AzureOpenAiEmbeddingClient`,
  `OllamaEmbeddingClient`. `EmbeddingConfig` picks one based on `rag.embedding-provider`.
- `store/` — `VectorStore` interface; impls: `CosmosVectorStore` (native
  `VectorDistance()`), `InMemoryVectorStore` (cosine). Selected by the
  `@ConditionalOnProperty("rag.vector-store")` annotations.
- `llm/` — `LlmClient` interface; impls: `OllamaLlmClient`, `ClaudeLlmClient`.
  `LlmRouter.resolve(provider)` chooses per request, falling back to the configured default.
- `service/` — `RagService` orchestrates ingest + query; `Chunker` interface splits text,
  with `SemanticChunker` (embedding breakpoints), `SentenceChunker`, and `FixedSizeChunker`
  impls selected by `ChunkingConfig` from `rag.chunking.strategy`.
- `web/` — `RagController` (`/api/ingest`, `/api/chat`, `/api/health`) and
  `ApiExceptionHandler`.
- `config/` — `RagProperties` binds the `rag.*` config block.
- `model/` — records: `Chunk`, `ScoredChunk`, and `Dtos` (request/response records).

## Configuration

All settings are in `src/main/resources/application.yml` and overridable by env vars.
Key switches:

- `RAG_VECTOR_STORE` = `memory` (default) | `cosmos`
- `RAG_EMBEDDING_PROVIDER` = `ollama` | `azure` | `auto` (Azure if configured, else Ollama)
- `RAG_LLM_PROVIDER` = `ollama` (default) | `claude`
- Azure OpenAI: `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY`,
  `AZURE_OPENAI_EMBEDDINGS_DEPLOYMENT`, `AZURE_OPENAI_EMBEDDING_DIMENSIONS`
- Cosmos: `COSMOS_ENDPOINT`, `COSMOS_KEY`, `COSMOS_DATABASE`, `COSMOS_CONTAINER`
- Ollama: `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_EMBED_MODEL`, `OLLAMA_EMBED_DIMENSIONS`
- Claude: `ANTHROPIC_API_KEY`, `CLAUDE_MODEL`

## Invariants an agent must preserve

- **Embedding dimensions must match between ingest and query, and match the Cosmos
  container's vector policy.** Changing the embedding model/provider means re-ingesting.
- **Cosmos partition key is `/id`.** `CosmosVectorStore` upserts with `PartitionKey(id)`.
  A new container must be created with partition key `/id`, a vector policy on
  `/embedding`, and `/embedding/*` in excluded paths.
- **Generation always needs a real LLM** (Ollama running or a Claude key). The in-memory
  store needs no setup, but embeddings always require Ollama or Azure.
- Keep each provider behind its interface; add new backends as new impls, not by
  branching inside existing clients.

## Conventions

- Java records for DTOs and value types; constructor injection (no field `@Autowired`).
- HTTP calls use Spring `RestClient`. No WebFlux.
- Throw `IllegalArgumentException` for bad input (→ 400) and `IllegalStateException`
  for misconfiguration/unavailable dependency (→ 503); `ApiExceptionHandler` maps these.
- Don't commit secrets. Configure via env vars; `.env`/keys are gitignored.

## Common tasks

- **Add an embedding/LLM/vector backend**: implement the interface, register it
  (a `@Bean` in the relevant config or `@Component` + conditional), wire selection.
- **Change chunking**: `rag.chunking.strategy` (`semantic`/`sentence`/`fixed`),
  `chunk-size`, `overlap`, `similarity-threshold`; or add a new `Chunker` impl.
- **Tune retrieval depth**: `rag.retrieval.top-k` or per-request `topK`.

## Manual smoke test

```bash
curl localhost:8080/api/health
# ingest then chat (see README for the exact JSON bodies)
```
