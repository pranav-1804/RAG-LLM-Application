package com.example.rag.store;

import java.util.List;

/**
 * Small vector helpers shared by stores.
 */
public final class VectorMath {
    private VectorMath() {}

    /** Cosine similarity in [-1, 1]; higher is more similar. */
    public static double cosine(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: " + a.size() + " vs " + b.size());
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i), y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
