package com.example.rag.retrieval;

import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;

import java.util.List;

/**
 * A keyword (lexical) index over chunk text, complementary to the dense
 * {@link com.example.rag.store.VectorStore}. Fed at ingest time and queried for
 * exact-term / rare-token matches that vector similarity tends to miss.
 */
public interface LexicalIndex {

    void index(List<Chunk> chunks);

    List<ScoredChunk> search(String queryText, int topK);
}
