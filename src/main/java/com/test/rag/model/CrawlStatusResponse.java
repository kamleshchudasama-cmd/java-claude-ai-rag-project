package com.test.rag.model;

public record CrawlStatusResponse(
        String jobId,
        String status,
        int pagesVisited,
        int pagesIngested,
        int totalChunks,
        String errorMessage
) {}
