package com.test.rag.model;

import java.math.BigDecimal;

public record Citation(
        int ref,
        String filename,
        int chunkIndex,
        BigDecimal score,
        String chunkText
) {}
