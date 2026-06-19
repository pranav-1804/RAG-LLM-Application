package com.example.rag.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw text into overlapping character windows. Overlap preserves context
 * across chunk boundaries so retrieval doesn't cut sentences awkwardly.
 */
public final class TextChunker {
    private TextChunker() {}

    public static List<String> chunk(String text, int chunkSize, int overlap) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        List<String> chunks = new ArrayList<>();
        if (normalized.isEmpty()) {
            return chunks;
        }
        if (chunkSize <= 0) {
            chunks.add(normalized);
            return chunks;
        }
        int step = Math.max(1, chunkSize - Math.max(0, overlap));
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(normalized.length(), start + chunkSize);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
        return chunks;
    }
}
