package com.example.rag.service;

import java.util.List;

/**
 * Fixed-size character windows with overlap. Cheap and deterministic, but can
 * cut sentences/ideas mid-way. Delegates to {@link TextChunker}.
 */
public class FixedSizeChunker implements Chunker {

    private final int chunkSize;
    private final int overlap;

    public FixedSizeChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> chunk(String text) {
        return TextChunker.chunk(text, chunkSize, overlap);
    }
}
