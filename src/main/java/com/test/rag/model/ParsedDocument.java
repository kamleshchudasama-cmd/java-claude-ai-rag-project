package com.test.rag.model;

import java.util.Map;

public record ParsedDocument(
        String content,
        Map<String, String> metadata,
        String sourceId
) {}
