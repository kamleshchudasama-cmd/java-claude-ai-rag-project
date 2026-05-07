package com.test.rag.model;

import java.math.BigDecimal;

public record ScoredChunk(
        DocumentChunk chunk,
        BigDecimal similarityScore
) {}
