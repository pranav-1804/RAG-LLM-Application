package com.example.rag.model;

/**
 * A chunk returned from a vector search together with its similarity score.
 */
public record ScoredChunk(String id, String source, String text, double score) {
}
