package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;
import com.example.rag.store.VectorStore;

import java.util.List;

/**
 * Dense-only retrieval: delegates straight to the {@link VectorStore}. This is the
 * original behaviour, used for {@code rag.retrieval.mode=vector} and as the fallback
 * when hybrid is requested against a backend that has no lexical index.
 */
public class VectorRetriever implements Retriever {

    private final VectorStore store;

    public VectorRetriever(VectorStore store) {
        this.store = store;
    }

    @Override
    public List<ScoredChunk> retrieve(String queryText, List<Float> queryEmbedding, int topK) {
        return store.search(queryEmbedding, topK);
    }
}
