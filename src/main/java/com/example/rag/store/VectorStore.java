package com.example.rag.store;

import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;

import java.util.List;

/**
 * Storage + similarity search over embedded chunks.
 */
public interface VectorStore {

   
    void upsert(List<Chunk> chunks);
    List<ScoredChunk> search(List<Float> queryEmbedding, int topK);
    long count();
}
