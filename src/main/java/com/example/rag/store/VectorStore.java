package com.example.rag.store;

import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;

import java.util.List;

/**
 * Storage + similarity search over embedded chunks.
 */
public interface VectorStore {

    /** Persist a batch of chunks (with embeddings already computed). */
    void upsert(List<Chunk> chunks);

    /** Return the {@code topK} chunks most similar to the query embedding. */
    List<ScoredChunk> search(List<Float> queryEmbedding, int topK);

    /** Total number of stored chunks (best-effort; for diagnostics). */
    long count();
}
