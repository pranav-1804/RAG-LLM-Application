package com.example.rag.service;

import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.store.VectorMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic ("by meaning") chunker. Embeds each sentence, then walks through them
 * keeping a running group; when the meaning shifts — the cosine similarity
 * between the next sentence and the previous one drops below a threshold — it
 * starts a new chunk. A character budget caps runaway chunks.
 *
 * <p>Cost: one batched embedding call over all sentences at ingest time. Those
 * sentence embeddings are only used to find boundaries; the resulting chunks are
 * re-embedded by {@code RagService} for storage.
 */
public class SemanticChunker implements Chunker {

    private final EmbeddingClient embeddings;
    private final double similarityThreshold;
    private final int maxChars;

    public SemanticChunker(EmbeddingClient embeddings, double similarityThreshold, int maxChars) {
        this.embeddings = embeddings;
        this.similarityThreshold = similarityThreshold;
        this.maxChars = Math.max(1, maxChars);
    }

    @Override
    public List<String> chunk(String text) {
        List<String> sentences = Sentences.split(text);
        if (sentences.size() <= 1) {
            return new ArrayList<>(sentences);
        }

        List<List<Float>> vectors = embeddings.embedAll(sentences);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder(sentences.get(0));

        for (int i = 1; i < sentences.size(); i++) {
            double similarity = VectorMath.cosine(vectors.get(i - 1), vectors.get(i));
            boolean meaningShifted = similarity < similarityThreshold;
            boolean tooBig = current.length() + 1 + sentences.get(i).length() > maxChars;

            if (meaningShifted || tooBig) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(sentences.get(i));
            } else {
                current.append(' ').append(sentences.get(i));
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
