package com.test.rag.model;

import java.util.List;

public record RagResponse(
        String answer,
        List<Citation> citations,
        int totalTokens
) {}
