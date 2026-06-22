package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;
import com.example.rag.store.HybridSearchStore;

import java.util.List;

/**
 * Hybrid retrieval delegated to the store's native server-side hybrid search (vector +
 * full-text BM25, RRF-fused). Used for {@code rag.retrieval.mode=hybrid} when the vector
 * store implements {@link HybridSearchStore} (e.g. Cosmos DB), so fusion happens next to
 * the data instead of via the in-memory BM25 index.
 */
public class CosmosHybridRetriever implements Retriever {

    private final HybridSearchStore store;

    public CosmosHybridRetriever(HybridSearchStore store) {
        this.store = store;
    }

    @Override
    public List<ScoredChunk> retrieve(String queryText, List<Float> queryEmbedding, int topK) {
        return store.hybridSearch(queryText, queryEmbedding, topK);
    }
}
