package com.test.rag.service.crawl;

import com.test.rag.config.RagProperties;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.EmbeddedChunk;
import com.test.rag.service.chunking.ChunkingService;
import com.test.rag.service.embedding.EmbeddingService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsoupWebCrawlerServiceTest {

    @Mock private ChunkingService chunkingService;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private CrawlJobStore crawlJobStore;

    private JsoupWebCrawlerService service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        props.setCrawlMaxPages(50);
        props.setCrawlConnectTimeoutMs(5000);
        service = new JsoupWebCrawlerService(
                chunkingService, embeddingService, vectorStoreService, crawlJobStore, props);
    }

    private Document makeDoc(String title, String body, String baseUrl, String... links) {
        StringBuilder html = new StringBuilder(
                "<html><head><title>" + title + "</title></head><body>" + body);
        for (String link : links) {
            html.append("<a href=\"").append(link).append("\">link</a>");
        }
        html.append("</body></html>");
        return Jsoup.parse(html.toString(), baseUrl);
    }

    @Test
    void crawl_sets_failed_when_root_url_unreachable() {
        JsoupWebCrawlerService failingService = new JsoupWebCrawlerService(
                chunkingService, embeddingService, vectorStoreService, crawlJobStore,
                new RagProperties()) {
            @Override
            protected Document fetchPage(String url) throws IOException {
                throw new IOException("Connection refused");
            }
        };

        failingService.crawl("https://example.com", "job1");

        verify(crawlJobStore).update(eq("job1"), eq("FAILED"),
                eq(0), eq(0), eq(0), contains("Connection refused"));
        verify(vectorStoreService, never()).upsert(any());
    }

    @Test
    void crawl_ingests_root_page_when_no_links() {
        Document doc = makeDoc("My Page", "Some content here", "https://example.com");
        DocumentChunk chunk = new DocumentChunk("chunk1", "Some content here", 0, 10,
                Map.of("source_id", "abc"));
        EmbeddedChunk embedded = new EmbeddedChunk(chunk, new float[1536]);

        when(chunkingService.chunk(any())).thenReturn(List.of(chunk));
        when(embeddingService.embed(any())).thenReturn(List.of(embedded));

        JsoupWebCrawlerService testService = new JsoupWebCrawlerService(
                chunkingService, embeddingService, vectorStoreService, crawlJobStore,
                new RagProperties()) {
            @Override
            protected Document fetchPage(String url) {
                return doc;
            }
        };

        testService.crawl("https://example.com", "job1");

        verify(vectorStoreService).upsert(List.of(embedded));
        verify(crawlJobStore).update(eq("job1"), eq("DONE"),
                eq(1), eq(1), eq(1), isNull());
    }

    @Test
    void crawl_follows_same_domain_links_only() {
        Document rootDoc = makeDoc("Root", "Root content", "https://example.com",
                "https://example.com/page2",
                "https://other.com/external");
        Document page2Doc = makeDoc("Page 2", "Page 2 content", "https://example.com/page2");
        DocumentChunk chunk = new DocumentChunk("c1", "content", 0, 5,
                Map.of("source_id", "x"));
        EmbeddedChunk embedded = new EmbeddedChunk(chunk, new float[1536]);

        when(chunkingService.chunk(any())).thenReturn(List.of(chunk));
        when(embeddingService.embed(any())).thenReturn(List.of(embedded));

        JsoupWebCrawlerService testService = new JsoupWebCrawlerService(
                chunkingService, embeddingService, vectorStoreService, crawlJobStore,
                new RagProperties()) {
            @Override
            protected Document fetchPage(String url) {
                if (url.equals("https://example.com")) return rootDoc;
                if (url.equals("https://example.com/page2")) return page2Doc;
                throw new RuntimeException("Should not fetch: " + url);
            }
        };

        testService.crawl("https://example.com", "job1");

        verify(vectorStoreService, times(2)).upsert(any());
        verify(crawlJobStore).update(eq("job1"), eq("DONE"),
                eq(2), eq(2), eq(2), isNull());
    }

    @Test
    void crawl_skips_failed_page_and_continues() {
        Document rootDoc = makeDoc("Root", "Root content", "https://example.com",
                "https://example.com/broken");
        DocumentChunk chunk = new DocumentChunk("c1", "content", 0, 5,
                Map.of("source_id", "x"));
        EmbeddedChunk embedded = new EmbeddedChunk(chunk, new float[1536]);

        when(chunkingService.chunk(any())).thenReturn(List.of(chunk));
        when(embeddingService.embed(any())).thenReturn(List.of(embedded));

        JsoupWebCrawlerService testService = new JsoupWebCrawlerService(
                chunkingService, embeddingService, vectorStoreService, crawlJobStore,
                new RagProperties()) {
            @Override
            protected Document fetchPage(String url) throws IOException {
                if (url.equals("https://example.com")) return rootDoc;
                throw new IOException("broken");
            }
        };

        testService.crawl("https://example.com", "job1");

        verify(vectorStoreService, times(1)).upsert(any());
        verify(crawlJobStore).update(eq("job1"), eq("DONE"),
                eq(2), eq(1), eq(1), isNull());
    }

    @Test
    void crawl_metadata_contains_page_url_and_crawl_root_url() {
        Document doc = makeDoc("Title", "Body text", "https://example.com");

        when(chunkingService.chunk(argThat(pd ->
                "https://example.com".equals(pd.metadata().get("page-url")) &&
                "https://example.com".equals(pd.metadata().get("crawl-root-url"))
        ))).thenReturn(List.of());
        when(embeddingService.embed(any())).thenReturn(List.of());

        JsoupWebCrawlerService testService = new JsoupWebCrawlerService(
                chunkingService, embeddingService, vectorStoreService, crawlJobStore,
                new RagProperties()) {
            @Override
            protected Document fetchPage(String url) {
                return doc;
            }
        };

        testService.crawl("https://example.com", "job1");

        verify(chunkingService).chunk(argThat(pd ->
                "https://example.com".equals(pd.metadata().get("page-url")) &&
                "https://example.com".equals(pd.metadata().get("crawl-root-url"))));
    }
}
