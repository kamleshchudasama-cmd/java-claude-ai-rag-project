package com.test.rag.model;

import java.util.List;

public record BuiltContext(
        String systemPrompt,
        String userMessage,
        List<Citation> citations
) {}
