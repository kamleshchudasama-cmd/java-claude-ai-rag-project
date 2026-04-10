package com.test.rag.model;

public record Citation(
        int ref,
        String filename,
        int chunkIndex,
        double score,
        String chunkText
) {}
