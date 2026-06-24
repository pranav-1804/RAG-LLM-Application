# Knowledge Notes

Background on the chunking and retrieval techniques used in this RAG service. For
*where* things live in the code, see `CLAUDE.md`; for *what* the service can do and
how to extend it, see `SKILLS.md`; this file explains the *why* and the *math*.

## Chunking: why and how

Retrieval works on chunks, not whole documents. Chunk too large and a single hit
drags in unrelated text (coarse retrieval, diluted relevance); chunk too small and you
fragment ideas across hits and lose context. The goal is chunks that each hold one
coherent idea. The strategy is selected by `rag.chunking.strategy`.

- **`semantic` (default)** — `SemanticChunker` embeds every sentence, then walks them
  in order keeping a running group; when the **cosine similarity between adjacent
  sentences drops below `similarity-threshold` (0.5)** the meaning has shifted, so it
  closes the current chunk and starts a new one. This groups text by *meaning* rather
  than at arbitrary character offsets, so a chunk boundary lands where the topic
  actually changes. A `chunk-size` character budget still caps runaway chunks. Cost:
  one extra batched embedding call over the sentences at ingest time (those sentence
  vectors are only used to find boundaries — the resulting chunks are re-embedded for
  storage).
- **`sentence`** — `SentenceChunker` packs whole sentences up to `chunk-size` without
  ever splitting mid-sentence. No embeddings, so it's cheap, but boundaries are
  size-driven, not meaning-driven.
- **`fixed`** — `FixedSizeChunker` slices fixed-size windows with a configurable
  `overlap`. Simplest and fully deterministic; `overlap` re-includes the tail of one
  chunk at the head of the next so an idea spanning a boundary still appears intact in
  at least one chunk.

**Intuition:** larger chunks give the LLM more context per hit but make retrieval
coarser (more off-topic text per match); smaller chunks retrieve more precisely but
risk cutting an idea in half. Semantic chunking tries to get precise boundaries
*without* an arbitrary size, falling back on the size cap only as a safety valve.

## Why hybrid retrieval

Dense vector search (embeddings + cosine/`VectorDistance`) matches on **meaning**, so it
finds relevant passages even when the wording differs. But it tends to **miss exact
tokens** — error codes, IDs, product names, acronyms, rare technical terms — because
those get blurred into a low-dimensional vector.

Lexical search (BM25) is the opposite: it matches on **exact terms** and nails
keyword/rare-token queries, but it's blind to synonyms and paraphrase.

**Hybrid retrieval** runs both and fuses the results, getting semantic recall *and*
keyword precision. In this project the modes are selected by `rag.retrieval.mode`
(`hybrid` default | `vector` | `lexical`) in `retrieval/RetrievalConfig.java`.

## BM25 (Okapi BM25)

BM25 is a bag-of-words ranking function that scores how well a document matches a query,
based on term frequency and term rarity. For a query `Q` and document `D`:

```
score(D, Q) = Σ_t∈Q  IDF(t) · ( f(t,D) · (k1 + 1) ) / ( f(t,D) + k1 · (1 - b + b · |D|/avgdl) )
```

- `f(t, D)` — how many times term `t` appears in `D` (term frequency).
- `IDF(t)` — inverse document frequency; rare terms across the corpus weigh more:
  `IDF(t) = ln(1 + (N - n(t) + 0.5) / (n(t) + 0.5))`, where `N` = total docs and
  `n(t)` = docs containing `t`. The `+1` inside `ln` keeps IDF non-negative.
- `|D|` — document length in tokens; `avgdl` — average document length.
- **`k1` (here 1.2)** — term-frequency saturation: once a term appears "enough" times,
  extra occurrences add little. Higher `k1` = slower saturation.
- **`b` (here 0.75)** — length normalisation: `b=1` fully penalises long docs, `b=0`
  ignores length. 0.75 is the classic default.

Intuition: a term contributes more when it's **frequent in this doc**, **rare across the
corpus**, and the doc **isn't padded with unrelated text**.

**Implementation:** `retrieval/InMemoryBm25Index.java`. It keeps an in-memory inverted
index — per-document term frequencies, document lengths, and per-term document
frequencies — and computes the formula above at query time. Tokenisation is lowercase,
split on `[^a-z0-9]+`, with a small stopword list. Zero external dependencies, so it runs
fully offline alongside `InMemoryVectorStore`. (It is *not* wired into the Cosmos path:
with the Cosmos store, hybrid/lexical are delegated to Cosmos's own server-side full-text
search — `FullTextScore` + `VectorDistance` fused by the native `RRF` rank function — via
`CosmosHybridRetriever`/`CosmosLexicalRetriever`, which needs a full-text index on `/text`.)

## Reciprocal Rank Fusion (RRF)

The problem when combining two result lists: vector scores (cosine, ~0–1) and BM25 scores
(unbounded, corpus-dependent) live on **incompatible scales**, so you can't just add them.
You could normalise scores, but that's fragile (sensitive to outliers and distribution
shape).

RRF sidesteps this by ignoring raw scores and using only each item's **rank** within a
list:

```
RRF(d) = Σ_lists  1 / (k + rank_list(d))
```

- `rank` — position of document `d` in a given list (best = rank 0 in the code, so the
  denominator is `k + rank + 1`).
- **`k` (here 60)** — a smoothing constant. Larger `k` flattens the contribution gap
  between top ranks (rank 1 vs rank 5 matter less); 60 is the value from the original
  Cormack et al. paper and a common default.
- A document gets credit from **every** list it appears in, so items ranked decently in
  *both* the vector and lexical lists bubble to the top — exactly the consensus signal we
  want.

Example with `k=60`: a doc ranked #1 in both lists scores `1/61 + 1/61 ≈ 0.0328`; a doc
ranked #1 in only one list scores `1/61 ≈ 0.0164`. Appearing in both lists wins.

**Implementation:** `retrieval/Rrf.java`, called by `retrieval/HybridRetriever.java`,
which pulls a wider **candidate pool** (`rag.retrieval.candidate-pool`, default 20) from
each retriever before fusing and cutting to `topK`. Pulling more than `topK` per list
gives fusion room to promote dual-list consensus items.

### Note on citation scores

In hybrid mode the `score` returned in `/api/chat` citations is the **fused RRF score**
(small values, ~0.01–0.03), not a cosine similarity. That's expected — RRF scores are only
meaningful as a *relative ordering*, not as an absolute relevance percentage. In `vector`
mode the score is the cosine-style similarity as before. With the **Cosmos** store,
hybrid/lexical run as an `ORDER BY RANK` query whose fused score Cosmos does not project, so
`CosmosVectorStore` synthesises a descending score (`1/(position+1)`) purely to preserve the
server-returned ordering — again only meaningful as a relative ranking.

## Tuning cheat-sheet

| Setting | Env var | Effect |
| --- | --- | --- |
| `rag.retrieval.mode` | `RAG_RETRIEVAL_MODE` | `hybrid` / `vector` / `lexical` |
| `rag.retrieval.top-k` | — | final number of chunks fed to the LLM |
| `rag.retrieval.candidate-pool` | — | candidates pulled from each retriever before fusion |
| `rag.retrieval.rrf-k` | — | RRF smoothing; higher = flatter rank weighting |

## Quick reference: enabling each configuration

- Fully local/free: `RAG_EMBEDDING_PROVIDER=ollama`, `RAG_VECTOR_STORE=memory`,
  `RAG_LLM_PROVIDER=ollama`.
- Azure-backed retrieval, local generation: `RAG_EMBEDDING_PROVIDER=azure`,
  `RAG_VECTOR_STORE=cosmos`, `RAG_LLM_PROVIDER=ollama`.
- Remote generation: add `ANTHROPIC_API_KEY` and pass `"provider":"claude"`.
