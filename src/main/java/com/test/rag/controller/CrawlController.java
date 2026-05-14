package com.test.rag.controller;

import com.test.rag.exception.CrawlException;
import com.test.rag.model.CrawlSiteSummary;
import com.test.rag.model.CrawlStartResponse;
import com.test.rag.model.CrawlStatusResponse;
import com.test.rag.service.crawl.CrawlJobStore;
import com.test.rag.service.crawl.WebCrawlerService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag/crawl")
public class CrawlController {

    private final WebCrawlerService webCrawlerService;
    private final CrawlJobStore crawlJobStore;
    private final VectorStoreService vectorStoreService;

    public CrawlController(WebCrawlerService webCrawlerService,
                           CrawlJobStore crawlJobStore,
                           VectorStoreService vectorStoreService) {
        this.webCrawlerService = webCrawlerService;
        this.crawlJobStore = crawlJobStore;
        this.vectorStoreService = vectorStoreService;
    }

    @PostMapping
    public ResponseEntity<CrawlStartResponse> startCrawl(@RequestParam("url") String url) {
        if (url.isBlank()) {
            throw new CrawlException("URL must not be blank");
        }
        try {
            URI.create(url).toURL();
        } catch (Exception e) {
            throw new CrawlException("Invalid URL: " + url);
        }
        String jobId = UUID.randomUUID().toString();
        crawlJobStore.create(jobId, "RUNNING");
        webCrawlerService.crawl(url, jobId);
        return ResponseEntity.accepted().body(new CrawlStartResponse(jobId));
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<CrawlStatusResponse> getStatus(@PathVariable String jobId) {
        return crawlJobStore.get(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sites")
    public ResponseEntity<List<CrawlSiteSummary>> listSites() {
        return ResponseEntity.ok(vectorStoreService.listCrawledSites());
    }

    @DeleteMapping("/sites")
    public ResponseEntity<Void> deleteSite(@RequestParam("rootUrl") String rootUrl) {
        boolean deleted = vectorStoreService.deleteByCrawlRoot(rootUrl);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
