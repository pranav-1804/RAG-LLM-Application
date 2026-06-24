package com.example.rag.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight sentence segmentation shared by the structure-aware and semantic
 * chunkers. Splits on blank lines (paragraphs) and sentence terminators
 * (. ! ?). Not linguistically perfect, but dependency-free and good enough to
 * give chunkers natural boundaries to work with.
 */
public final class Sentences {
    private Sentences() {}

    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
      
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
          
            for (String sentence : p.split("(?<=[.!?])\\s+")) {
                String s = sentence.replaceAll("\\s+", " ").strip();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
