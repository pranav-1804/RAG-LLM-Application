package com.example.rag;

import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;
import com.example.rag.retrieval.HybridRetriever;
import com.example.rag.retrieval.InMemoryBm25Index;
import com.example.rag.store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises BM25 lexical retrieval and RRF-fused hybrid retrieval fully offline,
 * using a deterministic bag-of-words embedder (no Ollama/Azure), mirroring the
 * fixture style in {@link RagPipelineTest}.
 */
class HybridRetrievalTest {

    private static List<Chunk> chunks(EmbeddingClient embedder, List<String> docs) {
        List<List<Float>> vectors = embedder.embedAll(docs);
        return IntStream.range(0, docs.size())
                .mapToObj(i -> new Chunk("d" + i, "src", docs.get(i), vectors.get(i)))
                .toList();
    }

    @Test
    void bm25RanksExactKeywordDocFirst() {
        var index = new InMemoryBm25Index();
        index.index(chunks(new FakeEmbeddingClient(256), List.of(
                "The order was shipped on Tuesday and arrived on time.",
                "Payment failed with error code ERR4096 during checkout.",
                "Customers can update their shipping address in settings.")));

        List<ScoredChunk> hits = index.search("ERR4096", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).text()).contains("ERR4096");
    }

    @Test
    void rrfFusionPromotesDocRankedHighInBothLists() {
        var embedder = new FakeEmbeddingClient(256);
        var store = new InMemoryVectorStore();
        var index = new InMemoryBm25Index();

        List<Chunk> corpus = chunks(embedder, List.of(
                "Azure Cosmos DB supports vector search using the VectorDistance function.",
                "Spring Boot makes it easy to build production-grade Java services.",
                "Bananas are a good source of potassium and dietary fibre."));
        store.upsert(corpus);
        index.index(corpus);

        var hybrid = new HybridRetriever(store, index, 60, 20);
        var query = embedder.embed("How do I run vector search in Cosmos DB?");
        List<ScoredChunk> hits = hybrid.retrieve("vector search Cosmos DB", query, 2);

        assertThat(hits).isNotEmpty().hasSizeLessThanOrEqualTo(2);
        // The Cosmos doc ranks top in both the vector and lexical lists, so RRF puts it first.
        assertThat(hits.get(0).text()).contains("Cosmos DB");
    }

    @Test
    void hybridSurfacesKeywordOnlyMatchVectorWouldRankLower() {
        var embedder = new FakeEmbeddingClient(256);
        var store = new InMemoryVectorStore();
        var index = new InMemoryBm25Index();

        List<Chunk> corpus = chunks(embedder, List.of(
                "General notes about logistics, delivery windows and warehousing.",
                "Incident report: the service returned status SVC_TIMEOUT under load.",
                "An overview of distributed systems and consistency models."));
        store.upsert(corpus);
        index.index(corpus);

        var hybrid = new HybridRetriever(store, index, 60, 20);
        var query = embedder.embed("SVC_TIMEOUT");
        List<ScoredChunk> hits = hybrid.retrieve("SVC_TIMEOUT", query, 3);

        assertThat(hits).extracting(ScoredChunk::text)
                .anyMatch(t -> t.contains("SVC_TIMEOUT"));
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
