package com.test.rag.service.crawl;

import com.test.rag.config.RagProperties;
import com.test.rag.exception.CrawlException;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.model.ParsedDocument;
import com.test.rag.service.chunking.ChunkingService;
import com.test.rag.service.embedding.EmbeddingService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

@Service
public class JsoupWebCrawlerService implements WebCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(JsoupWebCrawlerService.class);

    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final CrawlJobStore crawlJobStore;
    private final int maxPages;
    private final int connectTimeoutMs;

    public JsoupWebCrawlerService(ChunkingService chunkingService,
                                   EmbeddingService embeddingService,
                                   VectorStoreService vectorStoreService,
                                   CrawlJobStore crawlJobStore,
                                   RagProperties properties) {
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.crawlJobStore = crawlJobStore;
        this.maxPages = properties.getCrawlMaxPages();
        this.connectTimeoutMs = properties.getCrawlConnectTimeoutMs();
    }

    @Override
    @Async
    public void crawl(String url, String jobId) {
        try {
            crawlInternal(url, jobId);
        } catch (Exception e) {
            log.error("Crawl failed for url='{}' jobId='{}': {}", url, jobId, e.getMessage(), e);
            crawlJobStore.update(jobId, "FAILED", 0, 0, 0, e.getMessage());
        }
    }

    private void crawlInternal(String url, String jobId) {
        String rootDomain = extractDomain(url);

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(url);
        visited.add(url);

        int pagesVisited = 0;
        int pagesIngested = 0;
        int totalChunks = 0;

        while (!queue.isEmpty()) {
            String pageUrl = queue.poll();
            pagesVisited++;

            Document doc;
            try {
                doc = fetchPage(pageUrl);
            } catch (IOException e) {
                if (pageUrl.equals(url)) {
                    throw new CrawlException("Cannot reach URL: " + url + " — " + e.getMessage(), e);
                }
                log.warn("Skipping page='{}': {}", pageUrl, e.getMessage());
                crawlJobStore.update(jobId, "RUNNING", pagesVisited, pagesIngested, totalChunks, null);
                continue;
            }

            // Enqueue new same-domain links found on this page (BFS, strips URL fragments)
            doc.select("a[href]").stream()
                    .map(a -> a.absUrl("href"))
                    .map(href -> href.contains("#") ? href.substring(0, href.indexOf('#')) : href)
                    .filter(href -> !href.isBlank())
                    .filter(href -> extractDomain(href).equals(rootDomain))
                    .filter(href -> !visited.contains(href))
                    .distinct()
                    .forEach(href -> {
                        if (visited.size() < maxPages) {
                            visited.add(href);
                            queue.add(href);
                        }
                    });

            try {
                ParsedDocument parsed = buildParsedDocument(pageUrl, url, doc);
                List<DocumentChunk> chunks = chunkingService.chunk(parsed);
                List<EmbeddedChunk> embedded = embeddingService.embed(chunks);
                vectorStoreService.upsert(embedded);
                pagesIngested++;
                totalChunks += embedded.size();
                log.info("Crawled page='{}' chunks={}", pageUrl, embedded.size());
            } catch (Exception e) {
                log.warn("Skipping ingest for page='{}': {}", pageUrl, e.getMessage());
            }

            crawlJobStore.update(jobId, "RUNNING", pagesVisited, pagesIngested, totalChunks, null);
        }

        crawlJobStore.update(jobId, "DONE", pagesVisited, pagesIngested, totalChunks, null);
        log.info("Crawl done jobId='{}' pagesIngested={} totalChunks={}", jobId, pagesIngested, totalChunks);
    }

    protected Document fetchPage(String url) throws IOException {
        return Jsoup.connect(url).timeout(connectTimeoutMs).get();
    }

    private ParsedDocument buildParsedDocument(String pageUrl, String rootUrl, Document doc) {
        String title = doc.title();
        String filename = title.isBlank() ? pageUrl : title;
        String content = doc.body().text();

        String sourceId = computeSourceId(pageUrl);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("filename", filename);
        metadata.put("source_id", sourceId);
        metadata.put("page-url", pageUrl);
        metadata.put("crawl-root-url", rootUrl);
        metadata.put("content-type", "text/html");
        metadata.put("upload-timestamp", Instant.now().toString());

        return new ParsedDocument(content, Collections.unmodifiableMap(metadata), sourceId);
    }

    private String extractDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            return Objects.isNull(host) ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String computeSourceId(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
