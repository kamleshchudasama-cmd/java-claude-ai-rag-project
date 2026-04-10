package com.test.rag.model;

public record EmbeddedChunk(
        DocumentChunk chunk,
        float[] embedding
) {}
