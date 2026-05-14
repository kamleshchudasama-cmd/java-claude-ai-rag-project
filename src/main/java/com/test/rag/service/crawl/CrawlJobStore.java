package com.test.rag.service.crawl;

import com.test.rag.model.CrawlStatusResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrawlJobStore {

    private final ConcurrentHashMap<String, CrawlStatusResponse> jobs = new ConcurrentHashMap<>();

    public void create(String jobId, String status) {
        jobs.put(jobId, new CrawlStatusResponse(jobId, status, 0, 0, 0, null));
    }

    public void update(String jobId, String status, int pagesVisited,
                       int pagesIngested, int totalChunks, String errorMessage) {
        jobs.put(jobId, new CrawlStatusResponse(jobId, status, pagesVisited,
                pagesIngested, totalChunks, errorMessage));
    }

    public Optional<CrawlStatusResponse> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
