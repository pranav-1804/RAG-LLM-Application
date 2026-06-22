package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;

import java.util.List;

/**
 * Keyword-only retrieval: delegates straight to the {@link LexicalIndex} (BM25).
 * Used for {@code rag.retrieval.mode=lexical}.
 */
public class LexicalRetriever implements Retriever {

    private final LexicalIndex index;

    public LexicalRetriever(LexicalIndex index) {
        this.index = index;
    }

    @Override
    public List<ScoredChunk> retrieve(String queryText, List<Float> queryEmbedding, int topK) {
        return index.search(queryText, topK);
    }
}
