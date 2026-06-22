package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;
import com.example.rag.store.HybridSearchStore;

import java.util.List;

/**
 * Keyword-only retrieval delegated to the store's native full-text (BM25) search. Used for
 * {@code rag.retrieval.mode=lexical} when the vector store implements
 * {@link HybridSearchStore} (e.g. Cosmos DB's {@code FullTextScore}).
 */
public class CosmosLexicalRetriever implements Retriever {

    private final HybridSearchStore store;

    public CosmosLexicalRetriever(HybridSearchStore store) {
        this.store = store;
    }

    @Override
    public List<ScoredChunk> retrieve(String queryText, List<Float> queryEmbedding, int topK) {
        return store.lexicalSearch(queryText, topK);
    }
}
