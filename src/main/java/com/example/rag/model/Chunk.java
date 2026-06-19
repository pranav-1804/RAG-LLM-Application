package com.example.rag.model;

import java.util.List;

/**
 * A stored unit of knowledge: a piece of text plus its embedding vector.
 *
 * @param id        unique id (also used as the Cosmos partition key)
 * @param source    where the text came from (filename, url, doc id...)
 * @param text      the chunk text
 * @param embedding the embedding vector for {@link #text}
 */
public record Chunk(String id, String source, String text, List<Float> embedding) {
}
