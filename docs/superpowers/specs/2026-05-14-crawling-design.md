# Web Crawling Feature — Design Spec
Date: 2026-05-14

## Overview

Add website crawling to the RAG pipeline. A user enters a URL; the system crawls
that page and all same-domain links found on it (depth 1, max 50 pages), ingests
the text into PGVector, and the user can immediately query the crawled content from
the same page. Crawling runs asynchronously in the background; the UI polls for
status and shows inline progress.

---

## Scope

- Backend: new `CrawlController`, `WebCrawlerService`, `CrawlJobStore`
- Angular: new `/crawl` route with URL input + inline status + chat interface
- No changes to existing `RagController`, `DocumentLoaderService`, or the
  chunk → embed → upsert pipeline

---

## Backend

### Dependency

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

### New files

```
src/main/java/com/test/rag/
├── controller/
│   └── CrawlController.java
├── exception/
│   └── CrawlException.java
├── model/
│   ├── CrawlStartResponse.java     (record)
│   └── CrawlStatusResponse.java    (record)
└── service/crawl/
    ├── WebCrawlerService.java      (interface)
    ├── JsoupWebCrawlerService.java (@Service)
    └── CrawlJobStore.java          (@Component)
```

### REST endpoints — `CrawlController`

| Method | Path | Request | Response | Notes |
|--------|------|---------|----------|-------|
| `POST` | `/api/rag/crawl` | `?url=https://example.com` | `202 CrawlStartResponse { jobId }` | Validates URL, starts async job |
| `GET`  | `/api/rag/crawl/{jobId}/status` | path var | `CrawlStatusResponse` | Returns current job state |

`CrawlController` injects `WebCrawlerService` and `CrawlJobStore` only.
`GlobalExceptionHandler` handles `CrawlException` → HTTP 400/500 as appropriate.

### Models

```java
record CrawlStartResponse(String jobId) {}

record CrawlStatusResponse(
    String jobId,
    String status,        // "RUNNING" | "DONE" | "FAILED"
    int pagesVisited,
    int pagesIngested,
    int totalChunks,
    String errorMessage   // null unless FAILED
) {}
```

### `CrawlJobStore`

In-memory `ConcurrentHashMap<String, CrawlStatusResponse>`.  
`jobId` = `UUID.randomUUID().toString()`.  
Jobs are never evicted during the app lifetime (acceptable for this scope).

### `JsoupWebCrawlerService`

Implements `WebCrawlerService`. The `crawl(String url, String jobId)` method carries
`@Async` — must be called via Spring proxy (from `CrawlController`, not self-invoked).

**Algorithm:**
1. Validate URL is reachable (Jsoup connect with `connectTimeoutMs`).
2. Fetch root page → extract `<title>` and `body` text → build `ParsedDocument`.
3. Extract all `<a href>` links, filter to same domain, deduplicate, cap at `maxPages - 1`.
4. For each URL (root + linked pages):
   a. Fetch page via Jsoup.
   b. Build `ParsedDocument`:
      - `content` = Jsoup `body().text()`
      - `sourceId` = `SHA-256(url)`
      - metadata: `filename` = page title (fallback: url), `source_id`, `page-url`,
        `content-type = text/html`, `upload-timestamp`
   c. Run `ChunkingService.chunk()` → `EmbeddingService.embed()` → `VectorStoreService.upsert()`.
   d. Update job in `CrawlJobStore` (`pagesVisited++`, `pagesIngested++`, `totalChunks += n`).
   e. On per-page error: log warning, increment `pagesVisited`, skip (do not abort job).
5. Set job status `DONE` on completion, `FAILED` if root URL unreachable.

**Invariants:**
- Never calls OpenAI API directly — uses existing `EmbeddingService` and `VectorStoreService`.
- `sourceId = SHA-256(url)` ensures re-crawling the same URL is idempotent.
- Throws `CrawlException` (unchecked) only for unrecoverable failures (bad URL, root unreachable).

### `RagProperties` additions

```properties
rag.crawl.max-pages=50
rag.crawl.connect-timeout-ms=10000
```

### `RagApplication` change

Add `@EnableAsync` alongside existing `@EnableRetry`.

---

## Angular UI

### New files

```
angular-ui/src/app/
├── features/crawl/
│   ├── crawl.component.ts       (standalone, providers: [ChatService, CrawlService])
│   ├── crawl.component.html
│   ├── crawl.component.scss
│   └── crawl.service.ts         (not providedIn root — injected by component)
```

### Updated files

| File | Change |
|------|--------|
| `core/models.ts` | Add `CrawlStartResponse`, `CrawlStatusResponse` interfaces |
| `core/rag-api.service.ts` | Add `startCrawl(url)` and `getCrawlStatus(jobId)` methods |
| `app.routes.ts` | Add `/crawl` lazy route |
| `app.component.ts` | Add "Crawl" nav item (`language` icon) |

### `CrawlService`

Not `providedIn: 'root'` — instantiated per component via `providers: [CrawlService]`
in the component decorator.

Signals exposed:
```typescript
crawlState: Signal<'idle' | 'running' | 'done' | 'failed'>
pagesVisited: Signal<number>
pagesIngested: Signal<number>
totalChunks: Signal<number>
errorMessage: Signal<string | null>
```

`startCrawl(url: string)`:
1. Calls `RagApiService.startCrawl(url)` → receives `jobId`.
2. Sets `crawlState` to `'running'`.
3. Starts polling: `interval(3000).pipe(switchMap(() => ragApi.getCrawlStatus(jobId)))`.
4. On each response: updates signals.
5. Stops polling (unsubscribe) when status is `'DONE'` or `'FAILED'`.

### Component layout

```
┌─────────────────────────────────────────┐
│  Crawl a Website                        │
│  ┌───────────────────────────┐ [Crawl]  │
│  │ https://example.com       │          │
│  └───────────────────────────┘          │
│  ◌ Crawling... (9 pages visited)        │  state = running
│  ✓ Done — 12 pages, 87 chunks ingested  │  state = done
│  ✕ Failed — could not reach URL         │  state = failed
├─────────────────────────────────────────┤
│  [chat messages — always visible]       │
│                                         │
├─────────────────────────────────────────┤
│  Ask a question…              [send]    │
└─────────────────────────────────────────┘
```

**`providers: [ChatService, CrawlService]`** on the `CrawlComponent` decorator creates
component-scoped instances of both services, keeping crawl page conversation history
isolated from the `/query` page and ensuring `CrawlService` state resets on navigation.

The chat section is always visible — users can query previously crawled or ingested
content before starting a new crawl.

The query call reuses the existing `POST /api/rag/query` endpoint unchanged.

---

## PGVector Metadata Keys (additions)

| Key | Written by | Used in |
|-----|-----------|---------|
| `page-url` | `JsoupWebCrawlerService` | Citations display |

`filename` for crawled pages holds the page `<title>` (or URL as fallback),
consistent with how `DocumentSummary` already uses `filename` for display.

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Root URL unreachable | Job status → `FAILED`, `errorMessage` set |
| Individual linked page fails | Log warning, skip page, job continues |
| Invalid URL format | `CrawlController` returns 400 before starting job |
| Unknown `jobId` on status poll | `CrawlController` returns 404 |

---

## What is NOT in scope

- Crawl depth > 1
- robots.txt compliance
- Authentication-protected pages
- Async job persistence (in-memory only; jobs lost on restart)
- Crawl history / re-crawl UI
- Per-source filtering of query results
