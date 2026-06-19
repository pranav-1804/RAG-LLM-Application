package com.example.rag;

import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;
import com.example.rag.service.TextChunker;
import com.example.rag.store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises chunking and in-memory retrieval without any external service,
 * so the core RAG retrieval path is covered offline. Uses a tiny deterministic
 * embedder defined below as a test fixture (no network, no Ollama/Azure).
 */
class RagPipelineTest {

    @Test
    void chunkerProducesOverlappingWindows() {
        String text = "a".repeat(2000);
        List<String> chunks = TextChunker.chunk(text, 800, 120);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).hasSize(800);
    }

    @Test
    void retrievalRanksRelevantChunkFirst() {
        var embedder = new FakeEmbeddingClient(256);
        var store = new InMemoryVectorStore();

        List<String> docs = List.of(
                "Azure Cosmos DB supports vector search using the VectorDistance function.",
                "Bananas are a good source of potassium and dietary fibre.",
                "Spring Boot makes it easy to build production-grade Java services.");

        List<List<Float>> vectors = embedder.embedAll(docs);
        store.upsert(IntStream.range(0, docs.size())
                .mapToObj(i -> new Chunk("d" + i, "src", docs.get(i), vectors.get(i)))
                .toList());

        var query = embedder.embed("How do I do vector search in Cosmos DB?");
        List<ScoredChunk> hits = store.search(query, 2);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).text()).contains("Cosmos DB");
    }

    /** Deterministic bag-of-words embedder — test fixture only. */
    private static final class FakeEmbeddingClient implements EmbeddingClient {
        private final int dim;

        FakeEmbeddingClient(int dim) {
            this.dim = dim;
        }

        @Override
        public int dimensions() {
            return dim;
        }

        @Override
        public List<List<Float>> embedAll(List<String> texts) {
            List<List<Float>> out = new ArrayList<>(texts.size());
            for (String text : texts) {
                float[] v = new float[dim];
                for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
                    if (!token.isEmpty()) {
                        v[Math.floorMod(token.hashCode(), dim)] += 1.0f;
                    }
                }
                double norm = 0;
                for (float x : v) {
                    norm += x * x;
                }
                norm = Math.sqrt(norm);
                List<Float> result = new ArrayList<>(dim);
                for (float x : v) {
                    result.add(norm == 0 ? 0f : (float) (x / norm));
                }
                out.add(result);
            }
            return out;
        }
    }
}
