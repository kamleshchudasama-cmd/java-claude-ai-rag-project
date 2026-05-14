# Web Crawling Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add async website crawling (depth 1, max 50 pages) with a new `/crawl` Angular page that shows URL input, inline status, a deletable list of crawled sites, and a chat interface for querying crawled content.

**Architecture:** Jsoup fetches pages; `JsoupWebCrawlerService.crawl()` runs `@Async` and feeds each page through the existing chunk→embed→upsert pipeline unchanged. `CrawlJobStore` tracks in-flight job state in memory; crawled sites persist in PGVector via a new `crawl-root-url` metadata key. `CrawlController` exposes four endpoints. Angular `CrawlService` polls for status and manages the crawled-sites list.

**Tech Stack:** Jsoup 1.17.2 (HTML fetch/parse), Spring `@Async`, JdbcTemplate (SQL for list/delete by root URL), Angular signals + RxJS `interval`/`switchMap`/`takeWhile`, Angular Material List + Dialog.

**Spec:** `docs/superpowers/specs/2026-05-14-crawling-design.md`

---

## File Map

### New backend files
| File | Responsibility |
|------|---------------|
| `src/main/java/com/test/rag/exception/CrawlException.java` | Unchecked exception for crawl failures |
| `src/main/java/com/test/rag/model/CrawlStartResponse.java` | Record: `{ String jobId }` |
| `src/main/java/com/test/rag/model/CrawlStatusResponse.java` | Record: job status + page/chunk counts |
| `src/main/java/com/test/rag/model/CrawlSiteSummary.java` | Record: persisted site summary from PGVector |
| `src/main/java/com/test/rag/service/crawl/WebCrawlerService.java` | Interface: `crawl(url, jobId)` |
| `src/main/java/com/test/rag/service/crawl/JsoupWebCrawlerService.java` | Async crawl impl using Jsoup |
| `src/main/java/com/test/rag/service/crawl/CrawlJobStore.java` | In-memory job registry |
| `src/main/java/com/test/rag/controller/CrawlController.java` | Four crawl endpoints |
| `src/test/java/com/test/rag/service/crawl/CrawlJobStoreTest.java` | Unit tests |
| `src/test/java/com/test/rag/service/crawl/JsoupWebCrawlerServiceTest.java` | Unit tests |
| `src/test/java/com/test/rag/controller/CrawlControllerTest.java` | MockMvc tests |

### Modified backend files
| File | Change |
|------|--------|
| `pom.xml` | Add Jsoup dependency |
| `src/main/java/com/test/rag/RagApplication.java` | Add `@EnableAsync` |
| `src/main/java/com/test/rag/config/RagProperties.java` | Add `crawlMaxPages`, `crawlConnectTimeoutMs` |
| `src/main/resources/application.properties` | Add `rag.crawl-max-pages`, `rag.crawl-connect-timeout-ms` |
| `src/main/java/com/test/rag/service/vectorstore/VectorStoreService.java` | Add `listCrawledSites()`, `deleteByCrawlRoot()` |
| `src/main/java/com/test/rag/service/vectorstore/PgVectorStoreService.java` | Implement new methods |
| `src/main/java/com/test/rag/controller/GlobalExceptionHandler.java` | Handle `CrawlException` → 400 |

### New Angular files
| File | Responsibility |
|------|---------------|
| `angular-ui/src/app/features/crawl/crawl.service.ts` | Polling logic, crawl state signals, site list |
| `angular-ui/src/app/features/crawl/crawl.component.ts` | Component wiring |
| `angular-ui/src/app/features/crawl/crawl.component.html` | Template |
| `angular-ui/src/app/features/crawl/crawl.component.scss` | Styles |
| `angular-ui/src/app/features/crawl/crawl.service.spec.ts` | Unit tests |
| `angular-ui/src/app/features/crawl/crawl.component.spec.ts` | Component tests |

### Modified Angular files
| File | Change |
|------|--------|
| `angular-ui/src/app/core/models.ts` | Add `CrawlStartResponse`, `CrawlStatusResponse`, `CrawlSiteSummary` |
| `angular-ui/src/app/core/rag-api.service.ts` | Add four crawl API methods |
| `angular-ui/src/app/app.routes.ts` | Add `/crawl` route |
| `angular-ui/src/app/app.component.ts` | Add "Crawl" nav item |

---

## Task 1: Jsoup dependency, models, and CrawlException

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/test/rag/exception/CrawlException.java`
- Create: `src/main/java/com/test/rag/model/CrawlStartResponse.java`
- Create: `src/main/java/com/test/rag/model/CrawlStatusResponse.java`
- Create: `src/main/java/com/test/rag/model/CrawlSiteSummary.java`

- [ ] **Step 1: Add Jsoup to pom.xml**

In `pom.xml`, add after the Apache Tika dependency block:
```xml
<!-- Jsoup — HTML fetch and parse for web crawling -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

- [ ] **Step 2: Create CrawlException**

Create `src/main/java/com/test/rag/exception/CrawlException.java`:
```java
package com.test.rag.exception;

public class CrawlException extends RuntimeException {

    public CrawlException(String message) {
        super(message);
    }

    public CrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: Create model records**

Create `src/main/java/com/test/rag/model/CrawlStartResponse.java`:
```java
package com.test.rag.model;

public record CrawlStartResponse(String jobId) {}
```

Create `src/main/java/com/test/rag/model/CrawlStatusResponse.java`:
```java
package com.test.rag.model;

public record CrawlStatusResponse(
        String jobId,
        String status,
        int pagesVisited,
        int pagesIngested,
        int totalChunks,
        String errorMessage
) {}
```

Create `src/main/java/com/test/rag/model/CrawlSiteSummary.java`:
```java
package com.test.rag.model;

public record CrawlSiteSummary(
        String rootUrl,
        int pagesIngested,
        int totalChunks,
        String lastCrawledAt
) {}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/test/rag/exception/CrawlException.java src/main/java/com/test/rag/model/CrawlStartResponse.java src/main/java/com/test/rag/model/CrawlStatusResponse.java src/main/java/com/test/rag/model/CrawlSiteSummary.java
git commit -m "feat: add Jsoup dependency, crawl models and CrawlException"
```

---

## Task 2: RagProperties crawl fields and @EnableAsync

**Files:**
- Modify: `src/main/java/com/test/rag/config/RagProperties.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/test/rag/RagApplication.java`

- [ ] **Step 1: Add crawl fields to RagProperties**

In `RagProperties.java`, add after the `// --- Document loading ---` block:
```java
// --- Crawl ---
private int crawlMaxPages = 50;
private int crawlConnectTimeoutMs = 10000;
```

Add getters and setters after the existing `getMaxContentChars` / `setMaxContentChars` methods:
```java
public int getCrawlMaxPages() { return crawlMaxPages; }
public void setCrawlMaxPages(int crawlMaxPages) { this.crawlMaxPages = crawlMaxPages; }

public int getCrawlConnectTimeoutMs() { return crawlConnectTimeoutMs; }
public void setCrawlConnectTimeoutMs(int crawlConnectTimeoutMs) { this.crawlConnectTimeoutMs = crawlConnectTimeoutMs; }
```

- [ ] **Step 2: Add crawl config to application.properties**

Append to `src/main/resources/application.properties`:
```properties
# ── Web crawl ─────────────────────────────────────────────────────────────────
rag.crawl-max-pages=50
rag.crawl-connect-timeout-ms=10000
```

- [ ] **Step 3: Add @EnableAsync to RagApplication**

In `RagApplication.java`, add the import and annotation:
```java
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
@EnableRetry
@EnableAsync
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/test/rag/config/RagProperties.java src/main/resources/application.properties src/main/java/com/test/rag/RagApplication.java
git commit -m "feat: add crawl config properties and enable async"
```

---

## Task 3: CrawlJobStore

**Files:**
- Create: `src/main/java/com/test/rag/service/crawl/CrawlJobStore.java`
- Create: `src/test/java/com/test/rag/service/crawl/CrawlJobStoreTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/test/rag/service/crawl/CrawlJobStoreTest.java`:
```java
package com.test.rag.service.crawl;

import com.test.rag.model.CrawlStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CrawlJobStoreTest {

    private CrawlJobStore store;

    @BeforeEach
    void setUp() {
        store = new CrawlJobStore();
    }

    @Test
    void create_stores_running_state_with_zero_counts() {
        store.create("job1", "RUNNING");

        CrawlStatusResponse result = store.get("job1").orElseThrow();
        assertEquals("job1", result.jobId());
        assertEquals("RUNNING", result.status());
        assertEquals(0, result.pagesVisited());
        assertEquals(0, result.pagesIngested());
        assertEquals(0, result.totalChunks());
        assertNull(result.errorMessage());
    }

    @Test
    void update_replaces_entire_job_state() {
        store.create("job1", "RUNNING");
        store.update("job1", "DONE", 10, 9, 72, null);

        CrawlStatusResponse result = store.get("job1").orElseThrow();
        assertEquals("DONE", result.status());
        assertEquals(10, result.pagesVisited());
        assertEquals(9, result.pagesIngested());
        assertEquals(72, result.totalChunks());
        assertNull(result.errorMessage());
    }

    @Test
    void update_stores_error_message_on_failure() {
        store.create("job1", "RUNNING");
        store.update("job1", "FAILED", 0, 0, 0, "Cannot reach URL");

        CrawlStatusResponse result = store.get("job1").orElseThrow();
        assertEquals("FAILED", result.status());
        assertEquals("Cannot reach URL", result.errorMessage());
    }

    @Test
    void get_returns_empty_for_unknown_job_id() {
        Optional<CrawlStatusResponse> result = store.get("unknown");
        assertTrue(result.isEmpty());
    }

    @Test
    void multiple_jobs_are_stored_independently() {
        store.create("job1", "RUNNING");
        store.create("job2", "RUNNING");
        store.update("job1", "DONE", 5, 5, 40, null);

        assertEquals("DONE", store.get("job1").orElseThrow().status());
        assertEquals("RUNNING", store.get("job2").orElseThrow().status());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `mvn test -Dtest=CrawlJobStoreTest -q`
Expected: FAIL — `CrawlJobStore` does not exist yet.

- [ ] **Step 3: Implement CrawlJobStore**

Create `src/main/java/com/test/rag/service/crawl/CrawlJobStore.java`:
```java
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
```

- [ ] **Step 4: Run tests to confirm they pass**

Run: `mvn test -Dtest=CrawlJobStoreTest -q`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/test/rag/service/crawl/CrawlJobStore.java src/test/java/com/test/rag/service/crawl/CrawlJobStoreTest.java
git commit -m "feat: add CrawlJobStore for in-memory async job tracking"
```

---

## Task 4: VectorStoreService crawl methods

**Files:**
- Modify: `src/main/java/com/test/rag/service/vectorstore/VectorStoreService.java`
- Modify: `src/main/java/com/test/rag/service/vectorstore/PgVectorStoreService.java`
- Modify: `src/test/java/com/test/rag/service/vectorstore/PgVectorStoreServiceTest.java` (add new test methods)

- [ ] **Step 1: Add methods to the VectorStoreService interface**

In `VectorStoreService.java`, add after the `listDocuments()` method:
```java
import com.test.rag.model.CrawlSiteSummary;
import java.util.List;

List<CrawlSiteSummary> listCrawledSites();

boolean deleteByCrawlRoot(String rootUrl);
```

- [ ] **Step 2: Write failing tests for the new methods**

Open the existing `PgVectorStoreServiceTest.java` and add the following imports if not already present:
```java
import com.test.rag.model.CrawlSiteSummary;
import org.springframework.jdbc.core.RowMapper;
```

Add the following test methods (keep all existing tests, add these alongside them):
```java
@Test
void listCrawledSites_returns_grouped_summaries() {
    List<CrawlSiteSummary> expected = List.of(
            new CrawlSiteSummary("https://example.com", 3, 21, "2026-05-14T10:00:00Z"));

    when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(expected);

    List<CrawlSiteSummary> result = service.listCrawledSites();

    assertEquals(1, result.size());
    assertEquals("https://example.com", result.get(0).rootUrl());
    assertEquals(3, result.get(0).pagesIngested());
    assertEquals(21, result.get(0).totalChunks());
}

@Test
void listCrawledSites_returns_empty_when_no_crawled_pages() {
    when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

    List<CrawlSiteSummary> result = service.listCrawledSites();

    assertTrue(result.isEmpty());
}

@Test
void deleteByCrawlRoot_returns_true_when_rows_exist() {
    when(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vector_store WHERE metadata->>'crawl-root-url' = ?",
            Integer.class, "https://example.com"))
            .thenReturn(5);
    when(jdbcTemplate.update(
            "DELETE FROM vector_store WHERE metadata->>'crawl-root-url' = ?",
            "https://example.com"))
            .thenReturn(5);

    boolean result = service.deleteByCrawlRoot("https://example.com");

    assertTrue(result);
    verify(jdbcTemplate).update(
            "DELETE FROM vector_store WHERE metadata->>'crawl-root-url' = ?",
            "https://example.com");
}

@Test
void deleteByCrawlRoot_returns_false_when_no_rows_exist() {
    when(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vector_store WHERE metadata->>'crawl-root-url' = ?",
            Integer.class, "https://unknown.com"))
            .thenReturn(0);

    boolean result = service.deleteByCrawlRoot("https://unknown.com");

    assertFalse(result);
    verify(jdbcTemplate, never()).update(anyString(), eq("https://unknown.com"));
}
```

- [ ] **Step 3: Run tests to confirm new tests fail**

Run: `mvn test -Dtest=PgVectorStoreServiceTest -q`
Expected: compilation error — `listCrawledSites()` and `deleteByCrawlRoot()` not yet implemented.

- [ ] **Step 4: Implement the new methods in PgVectorStoreService**

Add the following imports to `PgVectorStoreService.java`:
```java
import com.test.rag.model.CrawlSiteSummary;
```

Add these two methods before the private helper methods:
```java
@Override
public List<CrawlSiteSummary> listCrawledSites() {
    String sql = """
            SELECT
                metadata->>'crawl-root-url' AS root_url,
                COUNT(DISTINCT metadata->>'source_id') AS pages_ingested,
                COUNT(*) AS total_chunks,
                MAX(metadata->>'upload-timestamp') AS last_crawled_at
            FROM vector_store
            WHERE metadata->>'crawl-root-url' IS NOT NULL
            GROUP BY metadata->>'crawl-root-url'
            ORDER BY last_crawled_at DESC
            """;
    return jdbcTemplate.query(sql, (rs, rowNum) -> new CrawlSiteSummary(
            rs.getString("root_url"),
            rs.getInt("pages_ingested"),
            rs.getInt("total_chunks"),
            rs.getString("last_crawled_at")
    ));
}

@Override
@Transactional
public boolean deleteByCrawlRoot(String rootUrl) {
    Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vector_store WHERE metadata->>'crawl-root-url' = ?",
            Integer.class, rootUrl);
    if (Objects.isNull(count) || count == 0) {
        return false;
    }
    jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'crawl-root-url' = ?", rootUrl);
    log.info("deleteByCrawlRoot: deleted {} chunks for rootUrl='{}'", count, rootUrl);
    return true;
}
```

- [ ] **Step 5: Run all VectorStoreService tests**

Run: `mvn test -Dtest=PgVectorStoreServiceTest -q`
Expected: all tests pass including the three new ones.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/vectorstore/VectorStoreService.java src/main/java/com/test/rag/service/vectorstore/PgVectorStoreService.java src/test/java/com/test/rag/service/vectorstore/PgVectorStoreServiceTest.java
git commit -m "feat: add listCrawledSites and deleteByCrawlRoot to VectorStoreService"
```

---

## Task 5: JsoupWebCrawlerService

**Files:**
- Create: `src/main/java/com/test/rag/service/crawl/WebCrawlerService.java`
- Create: `src/main/java/com/test/rag/service/crawl/JsoupWebCrawlerService.java`
- Create: `src/test/java/com/test/rag/service/crawl/JsoupWebCrawlerServiceTest.java`

- [ ] **Step 1: Create the WebCrawlerService interface**

Create `src/main/java/com/test/rag/service/crawl/WebCrawlerService.java`:
```java
package com.test.rag.service.crawl;

public interface WebCrawlerService {
    void crawl(String url, String jobId);
}
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/com/test/rag/service/crawl/JsoupWebCrawlerServiceTest.java`:
```java
package com.test.rag.service.crawl;

import com.test.rag.config.RagProperties;
import com.test.rag.model.CrawlStatusResponse;
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

import static org.junit.jupiter.api.Assertions.*;
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
                java.util.Map.of("source_id", "abc"));
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
                java.util.Map.of("source_id", "x"));
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
                java.util.Map.of("source_id", "x"));
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

        // Root ingested, broken page skipped — job still DONE
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
```

- [ ] **Step 3: Run tests to confirm they fail**

Run: `mvn test -Dtest=JsoupWebCrawlerServiceTest -q`
Expected: compilation error — `JsoupWebCrawlerService` does not exist yet.

- [ ] **Step 4: Implement JsoupWebCrawlerService**

Create `src/main/java/com/test/rag/service/crawl/JsoupWebCrawlerService.java`:
```java
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
        List<String> urls = new java.util.ArrayList<>();
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
```

- [ ] **Step 5: Run tests to confirm they pass**

Run: `mvn test -Dtest=JsoupWebCrawlerServiceTest -q`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/test/rag/service/crawl/WebCrawlerService.java src/main/java/com/test/rag/service/crawl/JsoupWebCrawlerService.java src/test/java/com/test/rag/service/crawl/JsoupWebCrawlerServiceTest.java
git commit -m "feat: implement JsoupWebCrawlerService with async depth-1 crawling"
```

---

## Task 6: CrawlController and GlobalExceptionHandler

**Files:**
- Create: `src/main/java/com/test/rag/controller/CrawlController.java`
- Create: `src/test/java/com/test/rag/controller/CrawlControllerTest.java`
- Modify: `src/main/java/com/test/rag/controller/GlobalExceptionHandler.java`

- [ ] **Step 1: Write failing controller tests**

Create `src/test/java/com/test/rag/controller/CrawlControllerTest.java`:
```java
package com.test.rag.controller;

import com.test.rag.model.CrawlSiteSummary;
import com.test.rag.model.CrawlStatusResponse;
import com.test.rag.service.crawl.CrawlJobStore;
import com.test.rag.service.crawl.WebCrawlerService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CrawlController.class)
class CrawlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private WebCrawlerService webCrawlerService;
    @MockBean private CrawlJobStore crawlJobStore;
    @MockBean private VectorStoreService vectorStoreService;

    @Test
    void startCrawl_returns_202_with_jobId() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "https://example.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());

        verify(webCrawlerService).crawl(eq("https://example.com"), anyString());
    }

    @Test
    void startCrawl_returns_400_for_invalid_url() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "not-a-url"))
                .andExpect(status().isBadRequest());

        verify(webCrawlerService, never()).crawl(any(), any());
    }

    @Test
    void startCrawl_returns_400_for_blank_url() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatus_returns_200_with_job_data() throws Exception {
        CrawlStatusResponse status = new CrawlStatusResponse(
                "job1", "RUNNING", 3, 2, 15, null);
        when(crawlJobStore.get("job1")).thenReturn(Optional.of(status));

        mockMvc.perform(get("/api/rag/crawl/job1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job1"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.pagesVisited").value(3));
    }

    @Test
    void getStatus_returns_404_for_unknown_jobId() throws Exception {
        when(crawlJobStore.get("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rag/crawl/unknown/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSites_returns_200_with_site_list() throws Exception {
        when(vectorStoreService.listCrawledSites()).thenReturn(List.of(
                new CrawlSiteSummary("https://example.com", 5, 40, "2026-05-14T10:00:00Z")));

        mockMvc.perform(get("/api/rag/crawl/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rootUrl").value("https://example.com"))
                .andExpect(jsonPath("$[0].pagesIngested").value(5));
    }

    @Test
    void deleteSite_returns_204_when_deleted() throws Exception {
        when(vectorStoreService.deleteByCrawlRoot("https://example.com")).thenReturn(true);

        mockMvc.perform(delete("/api/rag/crawl/sites").param("rootUrl", "https://example.com"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSite_returns_404_when_not_found() throws Exception {
        when(vectorStoreService.deleteByCrawlRoot("https://unknown.com")).thenReturn(false);

        mockMvc.perform(delete("/api/rag/crawl/sites").param("rootUrl", "https://unknown.com"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `mvn test -Dtest=CrawlControllerTest -q`
Expected: compilation error — `CrawlController` does not exist yet.

- [ ] **Step 3: Implement CrawlController**

Create `src/main/java/com/test/rag/controller/CrawlController.java`:
```java
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
```

- [ ] **Step 4: Update GlobalExceptionHandler to handle CrawlException as 400**

Replace the contents of `GlobalExceptionHandler.java`:
```java
package com.test.rag.controller;

import com.test.rag.exception.CrawlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CrawlException.class)
    public ResponseEntity<String> handleCrawlException(CrawlException ex) {
        log.warn("Crawl request error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An internal error occurred");
    }
}
```

- [ ] **Step 5: Run controller tests**

Run: `mvn test -Dtest=CrawlControllerTest -q`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Run full test suite**

Run: `mvn test -q`
Expected: `BUILD SUCCESS` — all existing tests still pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/test/rag/controller/CrawlController.java src/main/java/com/test/rag/controller/GlobalExceptionHandler.java src/test/java/com/test/rag/controller/CrawlControllerTest.java
git commit -m "feat: add CrawlController with start/status/list/delete endpoints"
```

---

## Task 7: Angular models and RagApiService

**Files:**
- Modify: `angular-ui/src/app/core/models.ts`
- Modify: `angular-ui/src/app/core/rag-api.service.ts`
- Modify: `angular-ui/src/app/core/rag-api.service.spec.ts`

- [ ] **Step 1: Add crawl interfaces to models.ts**

Append to `angular-ui/src/app/core/models.ts`:
```typescript
export interface CrawlStartResponse {
  jobId: string;
}

export interface CrawlStatusResponse {
  jobId: string;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  pagesVisited: number;
  pagesIngested: number;
  totalChunks: number;
  errorMessage: string | null;
}

export interface CrawlSiteSummary {
  rootUrl: string;
  pagesIngested: number;
  totalChunks: number;
  lastCrawledAt: string;
}
```

- [ ] **Step 2: Add crawl methods to RagApiService**

In `rag-api.service.ts`, add the import for new types and four new methods after `deleteDocument`:
```typescript
import { RagResponse, DocumentSummary, CrawlStartResponse, CrawlStatusResponse, CrawlSiteSummary } from './models';

// Add these methods to the RagApiService class:

startCrawl(url: string): Observable<CrawlStartResponse> {
  return this.http.post<CrawlStartResponse>(`${this.base}/api/rag/crawl`, null, {
    params: { url }
  });
}

getCrawlStatus(jobId: string): Observable<CrawlStatusResponse> {
  return this.http.get<CrawlStatusResponse>(`${this.base}/api/rag/crawl/${jobId}/status`);
}

listCrawledSites(): Observable<CrawlSiteSummary[]> {
  return this.http.get<CrawlSiteSummary[]>(`${this.base}/api/rag/crawl/sites`);
}

deleteCrawledSite(rootUrl: string): Observable<void> {
  return this.http.delete<void>(`${this.base}/api/rag/crawl/sites`, {
    params: { rootUrl }
  });
}
```

- [ ] **Step 3: Add tests for the new RagApiService methods**

In `rag-api.service.spec.ts`, add the following tests (keep all existing tests, add these alongside):
```typescript
describe('startCrawl', () => {
  it('posts to /api/rag/crawl with url param', () => {
    const mockResponse: CrawlStartResponse = { jobId: 'job123' };
    service.startCrawl('https://example.com').subscribe(res => {
      expect(res.jobId).toBe('job123');
    });
    const req = httpMock.expectOne(r =>
      r.url.includes('/api/rag/crawl') && r.params.get('url') === 'https://example.com');
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });
});

describe('getCrawlStatus', () => {
  it('gets status by jobId', () => {
    service.getCrawlStatus('job123').subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/rag/crawl/job123/status`);
    expect(req.request.method).toBe('GET');
    req.flush({ jobId: 'job123', status: 'RUNNING', pagesVisited: 2,
                pagesIngested: 2, totalChunks: 14, errorMessage: null });
  });
});

describe('listCrawledSites', () => {
  it('gets list from /api/rag/crawl/sites', () => {
    service.listCrawledSites().subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/rag/crawl/sites`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});

describe('deleteCrawledSite', () => {
  it('deletes by rootUrl param', () => {
    service.deleteCrawledSite('https://example.com').subscribe();
    const req = httpMock.expectOne(r =>
      r.url.includes('/api/rag/crawl/sites') &&
      r.params.get('rootUrl') === 'https://example.com');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

- [ ] **Step 4: Run Angular tests**

Run from `angular-ui/`: `npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/core/models.ts angular-ui/src/app/core/rag-api.service.ts angular-ui/src/app/core/rag-api.service.spec.ts
git commit -m "feat: add crawl types and API methods to Angular core"
```

---

## Task 8: CrawlService

**Files:**
- Create: `angular-ui/src/app/features/crawl/crawl.service.ts`
- Create: `angular-ui/src/app/features/crawl/crawl.service.spec.ts`

- [ ] **Step 1: Write failing tests**

Create `angular-ui/src/app/features/crawl/crawl.service.spec.ts`:
```typescript
import { TestBed } from '@angular/core/testing';
import { CrawlService } from './crawl.service';
import { RagApiService } from '../../core/rag-api.service';
import { of, throwError } from 'rxjs';
import { CrawlStatusResponse, CrawlSiteSummary } from '../../core/models';

describe('CrawlService', () => {
  let service: CrawlService;
  let ragApi: jasmine.SpyObj<RagApiService>;

  const donStatus: CrawlStatusResponse = {
    jobId: 'job1', status: 'DONE',
    pagesVisited: 5, pagesIngested: 5, totalChunks: 40, errorMessage: null
  };

  const sites: CrawlSiteSummary[] = [
    { rootUrl: 'https://example.com', pagesIngested: 5, totalChunks: 40, lastCrawledAt: '2026-05-14T10:00:00Z' }
  ];

  beforeEach(() => {
    ragApi = jasmine.createSpyObj('RagApiService', [
      'startCrawl', 'getCrawlStatus', 'listCrawledSites', 'deleteCrawledSite'
    ]);
    TestBed.configureTestingModule({
      providers: [
        CrawlService,
        { provide: RagApiService, useValue: ragApi }
      ]
    });
    service = TestBed.inject(CrawlService);
  });

  it('starts in idle state', () => {
    expect(service.crawlState()).toBe('idle');
    expect(service.pagesVisited()).toBe(0);
    expect(service.crawledSites()).toEqual([]);
  });

  it('sets crawlState to running after startCrawl succeeds', () => {
    ragApi.startCrawl.and.returnValue(of({ jobId: 'job1' }));
    ragApi.getCrawlStatus.and.returnValue(of(donStatus));
    ragApi.listCrawledSites.and.returnValue(of(sites));

    service.startCrawl('https://example.com');

    expect(ragApi.startCrawl).toHaveBeenCalledWith('https://example.com');
  });

  it('sets crawlState to failed when startCrawl errors', () => {
    ragApi.startCrawl.and.returnValue(throwError(() => new Error('Network error')));

    service.startCrawl('https://example.com');

    expect(service.crawlState()).toBe('failed');
    expect(service.errorMessage()).toBe('Failed to start crawl');
  });

  it('loadSites updates crawledSites signal', () => {
    ragApi.listCrawledSites.and.returnValue(of(sites));

    service.loadSites();

    expect(service.crawledSites()).toEqual(sites);
  });

  it('deleteSite calls api and reloads sites', () => {
    ragApi.deleteCrawledSite.and.returnValue(of(undefined));
    ragApi.listCrawledSites.and.returnValue(of([]));

    service.deleteSite('https://example.com');

    expect(ragApi.deleteCrawledSite).toHaveBeenCalledWith('https://example.com');
    expect(ragApi.listCrawledSites).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to confirm they fail**

Run from `angular-ui/`: `npm test -- --watch=false --browsers=ChromeHeadless --include="**/crawl.service.spec.ts"`
Expected: error — `CrawlService` cannot be found.

- [ ] **Step 3: Implement CrawlService**

Create `angular-ui/src/app/features/crawl/crawl.service.ts`:
```typescript
import { Injectable, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, Subscription } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { RagApiService } from '../../core/rag-api.service';
import { CrawlSiteSummary } from '../../core/models';

@Injectable()
export class CrawlService {
  private ragApi = inject(RagApiService);
  private destroyRef = inject(DestroyRef);

  readonly crawlState = signal<'idle' | 'running' | 'done' | 'failed'>('idle');
  readonly pagesVisited = signal(0);
  readonly pagesIngested = signal(0);
  readonly totalChunks = signal(0);
  readonly errorMessage = signal<string | null>(null);
  readonly crawledSites = signal<CrawlSiteSummary[]>([]);

  private pollSubscription: Subscription | null = null;

  startCrawl(url: string): void {
    this.ragApi.startCrawl(url).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ jobId }) => {
        this.crawlState.set('running');
        this.startPolling(jobId);
      },
      error: () => {
        this.crawlState.set('failed');
        this.errorMessage.set('Failed to start crawl');
      }
    });
  }

  private startPolling(jobId: string): void {
    this.pollSubscription = interval(3000).pipe(
      switchMap(() => this.ragApi.getCrawlStatus(jobId)),
      takeWhile(status => status.status === 'RUNNING', true),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: status => {
        this.pagesVisited.set(status.pagesVisited);
        this.pagesIngested.set(status.pagesIngested);
        this.totalChunks.set(status.totalChunks);
        if (status.status === 'DONE') {
          this.crawlState.set('done');
          this.loadSites();
        } else if (status.status === 'FAILED') {
          this.crawlState.set('failed');
          this.errorMessage.set(status.errorMessage ?? 'Crawl failed');
        }
      }
    });
  }

  loadSites(): void {
    this.ragApi.listCrawledSites().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: sites => this.crawledSites.set(sites)
    });
  }

  deleteSite(rootUrl: string): void {
    this.ragApi.deleteCrawledSite(rootUrl).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.loadSites()
    });
  }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

Run from `angular-ui/`: `npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/crawl/crawl.service.ts angular-ui/src/app/features/crawl/crawl.service.spec.ts
git commit -m "feat: add CrawlService with polling and site management"
```

---

## Task 9: CrawlComponent

**Files:**
- Create: `angular-ui/src/app/features/crawl/crawl.component.ts`
- Create: `angular-ui/src/app/features/crawl/crawl.component.html`
- Create: `angular-ui/src/app/features/crawl/crawl.component.scss`
- Create: `angular-ui/src/app/features/crawl/crawl.component.spec.ts`

- [ ] **Step 1: Write the failing component test**

Create `angular-ui/src/app/features/crawl/crawl.component.spec.ts`:
```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CrawlComponent } from './crawl.component';
import { CrawlService } from './crawl.service';
import { ChatService } from '../query/chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('CrawlComponent', () => {
  let fixture: ComponentFixture<CrawlComponent>;
  let component: CrawlComponent;
  let crawlService: jasmine.SpyObj<CrawlService>;
  let chatService: jasmine.SpyObj<ChatService>;

  beforeEach(async () => {
    crawlService = jasmine.createSpyObj('CrawlService',
      ['startCrawl', 'loadSites', 'deleteSite'],
      {
        crawlState: signal('idle'),
        pagesVisited: signal(0),
        pagesIngested: signal(0),
        totalChunks: signal(0),
        errorMessage: signal(null),
        crawledSites: signal([])
      }
    );
    chatService = jasmine.createSpyObj('ChatService',
      ['addUserMessage', 'addAssistantMessage', 'addErrorMessage'],
      { messages: signal([]) }
    );

    await TestBed.configureTestingModule({
      imports: [CrawlComponent, NoopAnimationsModule],
      providers: [
        { provide: CrawlService, useValue: crawlService },
        { provide: ChatService, useValue: chatService },
        { provide: RagApiService, useValue: jasmine.createSpyObj('RagApiService', ['query']) },
        { provide: MatDialog, useValue: jasmine.createSpyObj('MatDialog', ['open']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CrawlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('calls crawlService.loadSites on init', () => {
    expect(crawlService.loadSites).toHaveBeenCalled();
  });

  it('calls crawlService.startCrawl with trimmed URL', () => {
    component.urlInput = '  https://example.com  ';
    component.startCrawl();
    expect(crawlService.startCrawl).toHaveBeenCalledWith('https://example.com');
  });

  it('does not crawl when URL is blank', () => {
    component.urlInput = '   ';
    component.startCrawl();
    expect(crawlService.startCrawl).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to confirm it fails**

Run from `angular-ui/`: `npm test -- --watch=false --browsers=ChromeHeadless --include="**/crawl.component.spec.ts"`
Expected: error — `CrawlComponent` cannot be found.

- [ ] **Step 3: Create crawl.component.ts**

Create `angular-ui/src/app/features/crawl/crawl.component.ts`:
```typescript
import {
  Component, DestroyRef, ElementRef, Injector,
  OnInit, ViewChild, afterNextRender, inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RagApiService } from '../../core/rag-api.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { CitationCardComponent } from '../../shared/citation-card/citation-card.component';
import { ChatService } from '../query/chat.service';
import { CrawlService } from './crawl.service';

@Component({
  selector: 'app-crawl',
  standalone: true,
  providers: [CrawlService, ChatService],
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatDialogModule,
    CitationCardComponent
  ],
  templateUrl: './crawl.component.html',
  styleUrl: './crawl.component.scss'
})
export class CrawlComponent implements OnInit {
  protected crawlService = inject(CrawlService);
  protected chatService = inject(ChatService);
  private ragApi = inject(RagApiService);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);
  private injector = inject(Injector);

  @ViewChild('messageList') private messageList!: ElementRef<HTMLElement>;

  urlInput = '';
  questionInput = '';
  isQuerying = false;

  ngOnInit(): void {
    this.crawlService.loadSites();
  }

  startCrawl(): void {
    const url = this.urlInput.trim();
    if (!url) return;
    this.crawlService.startCrawl(url);
  }

  confirmDelete(rootUrl: string): void {
    this.dialog.open(ConfirmDialogComponent, { data: { filename: rootUrl } })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(confirmed => {
        if (confirmed) this.crawlService.deleteSite(rootUrl);
      });
  }

  sendQuestion(): void {
    const text = this.questionInput.trim();
    if (!text || this.isQuerying) return;
    this.questionInput = '';
    this.isQuerying = true;
    this.chatService.addUserMessage(text);
    this.ragApi.query(text).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.chatService.addAssistantMessage(response);
        this.isQuerying = false;
        this.scrollToBottom();
      },
      error: () => {
        this.chatService.addErrorMessage();
        this.isQuerying = false;
      }
    });
  }

  private scrollToBottom(): void {
    afterNextRender(() => {
      const el = this.messageList?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, { injector: this.injector });
  }
}
```

- [ ] **Step 4: Create crawl.component.html**

Create `angular-ui/src/app/features/crawl/crawl.component.html`:
```html
<div class="crawl-container">

  <div class="crawl-section">
    <h2>Crawl a Website</h2>
    <div class="url-bar">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="url-field">
        <input matInput
          [(ngModel)]="urlInput"
          placeholder="https://example.com"
          [attr.disabled]="crawlService.crawlState() === 'running' ? true : null"
          (keydown.enter)="startCrawl()" />
      </mat-form-field>
      <button mat-raised-button color="primary"
        [disabled]="!urlInput.trim() || crawlService.crawlState() === 'running'"
        (click)="startCrawl()">
        @if (crawlService.crawlState() === 'running') {
          <mat-spinner diameter="20" />
        } @else {
          Crawl
        }
      </button>
    </div>

    @switch (crawlService.crawlState()) {
      @case ('running') {
        <p class="status-running">
          Crawling... ({{ crawlService.pagesVisited() }} pages visited)
        </p>
      }
      @case ('done') {
        <p class="status-done">
          Done — {{ crawlService.pagesIngested() }} pages,
          {{ crawlService.totalChunks() }} chunks ingested
        </p>
      }
      @case ('failed') {
        <p class="status-failed">{{ crawlService.errorMessage() }}</p>
      }
    }
  </div>

  @if (crawlService.crawledSites().length > 0) {
    <div class="sites-section">
      <h3>Crawled Sites</h3>
      <mat-list>
        @for (site of crawlService.crawledSites(); track site.rootUrl) {
          <mat-list-item>
            <span matListItemTitle class="site-url">{{ site.rootUrl }}</span>
            <span matListItemLine>
              {{ site.pagesIngested }} pages · {{ site.totalChunks }} chunks
            </span>
            <button mat-icon-button matListItemMeta color="warn"
              aria-label="Delete site"
              (click)="confirmDelete(site.rootUrl)">
              <mat-icon>delete</mat-icon>
            </button>
          </mat-list-item>
        }
      </mat-list>
    </div>
  }

  <div class="message-list" #messageList>
    @if (chatService.messages().length === 0) {
      <p class="empty-state">Crawl a website then ask questions about it</p>
    }
    @for (msg of chatService.messages(); track $index) {
      @if (msg.role === 'user') {
        <div class="message-row user">
          <div class="bubble user-bubble">{{ msg.text }}</div>
        </div>
      } @else {
        <div class="message-row assistant">
          <div class="assistant-card">
            <p class="answer-text">{{ msg.text }}</p>
            @for (citation of (msg.citations ?? []); track citation.ref) {
              <app-citation-card [citation]="citation" />
            }
          </div>
        </div>
      }
    }
  </div>

  <div class="input-bar">
    <mat-form-field appearance="outline" subscriptSizing="dynamic" class="input-field">
      <input matInput
        [(ngModel)]="questionInput"
        placeholder="Ask a question…"
        [attr.disabled]="isQuerying ? true : null"
        (keydown.enter)="sendQuestion()" />
    </mat-form-field>
    <button mat-icon-button color="primary"
      [attr.aria-label]="isQuerying ? 'Sending…' : 'Send message'"
      [disabled]="isQuerying || !questionInput.trim()"
      (click)="sendQuestion()">
      @if (isQuerying) {
        <mat-spinner diameter="20" />
      } @else {
        <mat-icon>send</mat-icon>
      }
    </button>
  </div>

</div>
```

- [ ] **Step 5: Create crawl.component.scss**

Create `angular-ui/src/app/features/crawl/crawl.component.scss`:
```scss
.crawl-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.crawl-section {
  padding: 0 0 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.12);

  h2 { margin: 0 0 12px; font-size: 1.1rem; font-weight: 500; }
}

.url-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.url-field { flex: 1; }

.status-running { color: rgba(0, 0, 0, 0.6); margin: 8px 0 0; font-size: 0.875rem; }
.status-done    { color: #388e3c; margin: 8px 0 0; font-size: 0.875rem; }
.status-failed  { color: #d32f2f; margin: 8px 0 0; font-size: 0.875rem; }

.sites-section {
  padding: 12px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.12);

  h3 { margin: 0 0 4px; font-size: 0.875rem; font-weight: 500; color: rgba(0, 0, 0, 0.6); }
}

.site-url {
  font-size: 0.875rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  text-align: center;
  color: rgba(0, 0, 0, 0.4);
  margin-top: 80px;
}

.message-row {
  display: flex;
  &.user      { justify-content: flex-end; }
  &.assistant { justify-content: flex-start; }
}

.bubble { padding: 8px 14px; max-width: 70%; }

.user-bubble {
  background: #3f51b5;
  color: #fff;
  border-radius: 18px 18px 2px 18px;
}

.assistant-card {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 2px 12px 12px 12px;
  padding: 12px;
  max-width: 80%;
}

.answer-text { margin: 0 0 8px; }

.input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.12);
}

.input-field { flex: 1; }
```

- [ ] **Step 6: Run component tests**

Run from `angular-ui/`: `npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add angular-ui/src/app/features/crawl/
git commit -m "feat: add CrawlComponent with URL input, site list, and chat interface"
```

---

## Task 10: Wire route and navigation

**Files:**
- Modify: `angular-ui/src/app/app.routes.ts`
- Modify: `angular-ui/src/app/app.component.ts`

- [ ] **Step 1: Add the /crawl route**

In `app.routes.ts`, add after the ingest route:
```typescript
{
  path: 'crawl',
  loadComponent: () =>
    import('./features/crawl/crawl.component').then(m => m.CrawlComponent)
},
```

- [ ] **Step 2: Add Crawl nav item to the sidenav**

In `app.component.ts`, add a new nav item after the ingest link inside `mat-nav-list`:
```html
<a mat-list-item routerLink="/crawl" routerLinkActive="active">
  <mat-icon matListItemIcon>language</mat-icon>
  <span matListItemTitle>Crawl</span>
</a>
```

- [ ] **Step 3: Run full Angular test suite**

Run from `angular-ui/`: `npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all tests pass.

- [ ] **Step 4: Build to confirm no compilation errors**

Run from `angular-ui/`: `npm run build`
Expected: `Build at: ... - Hash: ... - Time: ...ms` with no errors.

- [ ] **Step 5: Run full backend test suite**

Run: `mvn test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add angular-ui/src/app/app.routes.ts angular-ui/src/app/app.component.ts
git commit -m "feat: wire /crawl route and add Crawl nav item"
```

---

## Final verification checklist

- [ ] Backend: `mvn test -q` — all tests pass
- [ ] Angular: `npm test -- --watch=false --browsers=ChromeHeadless` — all tests pass
- [ ] Angular: `npm run build` — no compilation errors
- [ ] Manual smoke test (requires running stack):
  - Start PGVector: `docker compose up -d`
  - Start backend: `mvn spring-boot:run`
  - Start UI: `npm start` (from `angular-ui/`)
  - Navigate to `/crawl`, enter a URL, click Crawl, wait for "Done" status
  - Verify site appears in Crawled Sites list
  - Ask a question, verify citation shows page title and URL
  - Delete a site, verify it disappears from the list
