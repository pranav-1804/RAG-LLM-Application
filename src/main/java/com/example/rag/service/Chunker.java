package com.example.rag.service;

import java.util.List;

/**
 * Splits a document into chunks for embedding + retrieval.
 * Implementations differ in how they decide chunk boundaries.
 */
public interface Chunker {

    /** Split text into chunks (order preserved, no empty chunks). */
    List<String> chunk(String text);
}
