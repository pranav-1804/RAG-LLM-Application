package com.example.rag.store;

import com.example.rag.model.Chunk;
import com.example.rag.model.ScoredChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero-dependency fallback store: keeps chunks in memory and ranks them by
 * cosine similarity at query time. Great for local demos and tests without Azure.
 *
 * <p>Active when {@code rag.vector-store=memory} (the default).
 */
@Component
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, Chunk> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            store.put(c.id(), c);
        }
    }

    @Override
    public List<ScoredChunk> search(List<Float> queryEmbedding, int topK) {
        return store.values().stream()
                .map(c -> new ScoredChunk(c.id(), c.source(), c.text(),
                        VectorMath.cosine(queryEmbedding, c.embedding())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public long count() {
        return store.size();
    }
}
