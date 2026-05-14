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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        } catch (CrawlException e) {
            log.error("Crawl failed for url='{}' jobId='{}': {}", url, jobId, e.getMessage());
            crawlJobStore.update(jobId, "FAILED", 0, 0, 0, e.getMessage());
        }
    }

    private void crawlInternal(String url, String jobId) {
        Document rootDoc;
        try {
            rootDoc = fetchPage(url);
        } catch (IOException e) {
            throw new CrawlException("Cannot reach URL: " + url + " — " + e.getMessage(), e);
        }

        String rootDomain = extractDomain(url);
        List<String> urls = new ArrayList<>();
        urls.add(url);

        rootDoc.select("a[href]").stream()
                .map(a -> a.absUrl("href"))
                .filter(href -> !href.isBlank())
                .filter(href -> extractDomain(href).equals(rootDomain))
                .filter(href -> !href.equals(url))
                .distinct()
                .limit((long) maxPages - 1)
                .forEach(urls::add);

        int pagesVisited = 0;
        int pagesIngested = 0;
        int totalChunks = 0;

        for (String pageUrl : urls) {
            pagesVisited++;
            try {
                Document doc = pageUrl.equals(url) ? rootDoc : fetchPage(pageUrl);
                ParsedDocument parsed = buildParsedDocument(pageUrl, url, doc);
                List<DocumentChunk> chunks = chunkingService.chunk(parsed);
                List<EmbeddedChunk> embedded = embeddingService.embed(chunks);
                vectorStoreService.upsert(embedded);
                pagesIngested++;
                totalChunks += embedded.size();
                log.info("Crawled page='{}' chunks={}", pageUrl, embedded.size());
            } catch (Exception e) {
                log.warn("Skipping page='{}': {}", pageUrl, e.getMessage());
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
