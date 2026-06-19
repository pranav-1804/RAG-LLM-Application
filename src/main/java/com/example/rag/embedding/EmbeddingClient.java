package com.example.rag.embedding;

import java.util.List;

/**
 * Turns text into embedding vectors.
 */
public interface EmbeddingClient {

    /** Embed a single string. */
    default List<Float> embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    /** Embed a batch of strings, preserving order. */
    List<List<Float>> embedAll(List<String> texts);

    /** Dimensionality of the produced vectors. */
    int dimensions();
}
