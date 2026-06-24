package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;

import java.util.List;

/**
 * Retrieves the most relevant chunks for a question. Implementations decide how:
 * dense vector similarity, lexical keyword (BM25), or a hybrid fusion of both.
 *
 * Both the raw query text and its embedding are supplied so an implementation can
 * use whichever it needs without re-embedding.
 */
public interface Retriever {

    /**
     * @param queryText      the raw question (used by lexical retrieval)
     * @param queryEmbedding the embedded question (used by vector retrieval)
     * @param topK           maximum number of chunks to return
     * @return scored chunks, best first, at most {@code topK}
     */
    List<ScoredChunk> retrieve(String queryText, List<Float> queryEmbedding, int topK);
}
