package com.test.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Single source of truth for all tuneable RAG parameters.
 * All service classes must read params from here — no @Value for RAG params
 * outside this class, no magic numbers in service classes.
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    // --- Chunking ---
    private int chunkSize = 512;
    private int chunkOverlap = 50;
    private int minChunkSize = 50;

    // --- Embedding ---
    private int embeddingBatchSize = 32;
    private long embeddingRequestDelayMs = 0;

    // --- Retrieval ---
    private boolean retrievalEnabled = true;
    private int topK = 5;
    private BigDecimal minSimilarity = new BigDecimal("0.75");
    private boolean useMmr = false;

    // --- Generation ---
    private BigDecimal temperature = new BigDecimal("0.2");
    private int maxOutputTokens = 2048;

    // --- Document loading ---
    private int maxContentChars = 500_000;

    // --- Video ---
    private long maxVideoSizeBytes = 5_242_880L;

    // --- Crawl ---
    private int crawlMaxPages = 50;
    private int crawlConnectTimeoutMs = 10000;

    // --- Context ---
    private int maxContextTokens = 4096;

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getMinChunkSize() { return minChunkSize; }
    public void setMinChunkSize(int minChunkSize) { this.minChunkSize = minChunkSize; }

    public int getEmbeddingBatchSize() { return embeddingBatchSize; }
    public void setEmbeddingBatchSize(int embeddingBatchSize) { this.embeddingBatchSize = embeddingBatchSize; }

    public long getEmbeddingRequestDelayMs() { return embeddingRequestDelayMs; }
    public void setEmbeddingRequestDelayMs(long embeddingRequestDelayMs) { this.embeddingRequestDelayMs = embeddingRequestDelayMs; }

    public boolean isRetrievalEnabled() { return retrievalEnabled; }
    public void setRetrievalEnabled(boolean retrievalEnabled) { this.retrievalEnabled = retrievalEnabled; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public BigDecimal getMinSimilarity() { return minSimilarity; }
    public void setMinSimilarity(BigDecimal minSimilarity) { this.minSimilarity = minSimilarity; }

    public boolean isUseMmr() { return useMmr; }
    public void setUseMmr(boolean useMmr) { this.useMmr = useMmr; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }

    public int getMaxContentChars() { return maxContentChars; }
    public void setMaxContentChars(int maxContentChars) { this.maxContentChars = maxContentChars; }

    public long getMaxVideoSizeBytes() { return maxVideoSizeBytes; }
    public void setMaxVideoSizeBytes(long maxVideoSizeBytes) { this.maxVideoSizeBytes = maxVideoSizeBytes; }

    public int getCrawlMaxPages() { return crawlMaxPages; }
    public void setCrawlMaxPages(int crawlMaxPages) { this.crawlMaxPages = crawlMaxPages; }

    public int getCrawlConnectTimeoutMs() { return crawlConnectTimeoutMs; }
    public void setCrawlConnectTimeoutMs(int crawlConnectTimeoutMs) { this.crawlConnectTimeoutMs = crawlConnectTimeoutMs; }
}
