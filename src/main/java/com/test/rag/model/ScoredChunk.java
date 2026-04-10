package com.test.rag.model;

public record ScoredChunk(
        DocumentChunk chunk,
        double similarityScore
) {}
