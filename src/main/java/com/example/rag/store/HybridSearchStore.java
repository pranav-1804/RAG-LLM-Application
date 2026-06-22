package com.example.rag.store;

import com.example.rag.model.ScoredChunk;

import java.util.List;

/**
 * Optional capability for a {@link VectorStore} that can perform keyword (full-text) and
 * hybrid retrieval <em>server-side</em>, rather than relying on the application's in-memory
 * BM25 index. Implemented by backends with native full-text + vector ranking
 * (e.g. {@link CosmosVectorStore} via Cosmos DB's {@code FullTextScore}/{@code RRF}).
 *
 * <p>When a store implements this, the retrieval layer delegates hybrid/lexical modes to it
 * instead of fusing locally, keeping search next to the data.
 */
public interface HybridSearchStore {

    /**
     * Server-side hybrid search: combines dense vector similarity with full-text (BM25)
     * scoring, fused by the store's own Reciprocal Rank Fusion. Returns the top-k, best
     * first.
     */
    List<ScoredChunk> hybridSearch(String queryText, List<Float> queryEmbedding, int topK);

    /** Server-side full-text (BM25) search only. Returns the top-k, best first. */
    List<ScoredChunk> lexicalSearch(String queryText, int topK);
}
