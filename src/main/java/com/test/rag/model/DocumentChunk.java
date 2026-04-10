package com.test.rag.model;

import java.util.Map;

public record DocumentChunk(
        String chunkId,
        String content,
        int chunkIndex,
        int tokenCount,
        Map<String, String> metadata
) {}
