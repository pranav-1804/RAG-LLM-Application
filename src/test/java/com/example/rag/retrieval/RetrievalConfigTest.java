package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;
import com.example.rag.store.HybridSearchStore;
import com.example.rag.store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link RetrievalConfig} selects retrievers based on the store's
 * capabilities: a store implementing {@link HybridSearchStore} (e.g. Cosmos) gets the
 * native server-side retrievers for lexical/hybrid, while a plain {@link VectorStore}
 * (e.g. in-memory) gets the local BM25-fused retrievers — with no fallback to vector-only.
 */
class RetrievalConfigTest {

    private final RetrievalConfig config = new RetrievalConfig();
    private final LexicalIndex lexicalIndex = new InMemoryBm25Index();

    private static RagProperties props(String store, String mode) {
        RagProperties p = new RagProperties();
        p.setVectorStore(store);
        p.getRetrieval().setMode(mode);
        return p;
    }

    @Test
    void cosmosHybridUsesNativeServerSideRetriever() {
        Retriever r = config.retriever(props("cosmos", "hybrid"), new FakeHybridStore(), lexicalIndex);
        assertThat(r).isInstanceOf(CosmosHybridRetriever.class);
    }

    @Test
    void cosmosLexicalUsesNativeServerSideRetriever() {
        Retriever r = config.retriever(props("cosmos", "lexical"), new FakeHybridStore(), lexicalIndex);
        assertThat(r).isInstanceOf(CosmosLexicalRetriever.class);
    }

    @Test
    void cosmosVectorStaysVectorOnly() {
        Retriever r = config.retriever(props("cosmos", "vector"), new FakeHybridStore(), lexicalIndex);
        assertThat(r).isInstanceOf(VectorRetriever.class);
    }

    @Test
    void memoryHybridUsesInMemoryBm25Fusion() {
        Retriever r = config.retriever(props("memory", "hybrid"), new FakePlainStore(), lexicalIndex);
        assertThat(r).isInstanceOf(HybridRetriever.class);
    }

    @Test
    void memoryLexicalUsesInMemoryBm25() {
        Retriever r = config.retriever(props("memory", "lexical"), new FakePlainStore(), lexicalIndex);
        assertThat(r).isInstanceOf(LexicalRetriever.class);
    }

    /** A store with native full-text/hybrid capability, like the Cosmos backend. */
    private static final class FakeHybridStore implements VectorStore, HybridSearchStore {
        @Override public void upsert(List<Chunk> chunks) { }
        @Override public List<ScoredChunk> search(List<Float> queryEmbedding, int topK) { return List.of(); }
        @Override public long count() { return 0; }
        @Override public List<ScoredChunk> hybridSearch(String q, List<Float> e, int k) { return List.of(); }
        @Override public List<ScoredChunk> lexicalSearch(String q, int k) { return List.of(); }
    }

    /** A plain vector-only store, like the in-memory backend. */
    private static final class FakePlainStore implements VectorStore {
        @Override public void upsert(List<Chunk> chunks) { }
        @Override public List<ScoredChunk> search(List<Float> queryEmbedding, int topK) { return List.of(); }
        @Override public long count() { return 0; }
    }
}
