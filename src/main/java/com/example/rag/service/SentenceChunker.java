package com.example.rag.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure-aware chunker: packs whole sentences together up to a character
 * budget, never cutting a sentence in half. Cheap (no embeddings) and a solid
 * default — boundaries fall on real sentence ends instead of arbitrary offsets.
 */
public class SentenceChunker implements Chunker {

    private final int maxChars;

    public SentenceChunker(int maxChars) {
        this.maxChars = Math.max(1, maxChars);
    }

    @Override
    public List<String> chunk(String text) {
        List<String> sentences = Sentences.split(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() > 0 && current.length() + 1 + sentence.length() > maxChars) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
