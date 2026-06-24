package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code rag.*} configuration block.
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private String vectorStore = "memory";

    private String llmProvider = "ollama";

    private Retrieval retrieval = new Retrieval();
    private Chunking chunking = new Chunking();

    public static class Retrieval {

        private int topK = 4;
        private String mode = "hybrid";
        private int rrfK = 60;
        private int candidatePool = 20;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
        public int getCandidatePool() { return candidatePool; }
        public void setCandidatePool(int candidatePool) { this.candidatePool = candidatePool; }
    }

    public static class Chunking {
       
        private String strategy = "semantic";
        private int chunkSize = 800;
        private int overlap = 120;
        private double similarityThreshold = 0.5;

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double t) { this.similarityThreshold = t; }
    }

    public String getVectorStore() { return vectorStore; }
    public void setVectorStore(String vectorStore) { this.vectorStore = vectorStore; }
    public String getLlmProvider() { return llmProvider; }
    public void setLlmProvider(String llmProvider) { this.llmProvider = llmProvider; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking chunking) { this.chunking = chunking; }
}
