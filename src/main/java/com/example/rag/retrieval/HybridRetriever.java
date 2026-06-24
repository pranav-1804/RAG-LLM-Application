package com.example.rag.retrieval;

import com.example.rag.model.ScoredChunk;
import com.example.rag.store.VectorStore;

import java.util.List;

/**
 * Hybrid retrieval: pulls a candidate pool from both dense vector search and the BM25
 * lexical index, then fuses the two rankings with Reciprocal Rank Fusion and returns
 * the top-k. Combines semantic recall with exact-keyword precision.
 *
 * If the lexical index is empty (nothing ingested yet) RRF naturally reduces to the
 * vector ranking, so this degrades gracefully.
 */
public class HybridRetriever implements Retriever {

    private final VectorStore store;
    private final LexicalIndex lexicalIndex;
    private final int rrfK;
    private final int candidatePool;

    public HybridRetriever(VectorStore store, LexicalIndex lexicalIndex,
                           int rrfK, int candidatePool) {
        this.store = store;
        this.lexicalIndex = lexicalIndex;
        this.rrfK = rrfK;
        this.candidatePool = candidatePool;
    }

    @Override
    public List<ScoredChunk> retrieve(String queryText, List<Float> queryEmbedding, int topK) {
        int pool = Math.max(topK, candidatePool);
        List<ScoredChunk> vectorHits = store.search(queryEmbedding, pool);
        List<ScoredChunk> lexicalHits = lexicalIndex.search(queryText, pool);
        return Rrf.fuse(List.of(vectorHits, lexicalHits), rrfK, topK);
    }
}
