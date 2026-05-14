package com.test.rag.model;

public record CrawlSiteSummary(
        String rootUrl,
        int pagesIngested,
        int totalChunks,
        String lastCrawledAt
) {}
