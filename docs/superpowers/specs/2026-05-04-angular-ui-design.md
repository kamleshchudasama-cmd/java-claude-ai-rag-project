# Angular UI Design — Java RAG System

**Date:** 2026-05-04
**Status:** Approved

## Overview

A standalone Angular 18 frontend for the Java RAG system. Lives at `angular-ui/` in the repo root. Provides document ingestion, chat-style querying, and document management via the existing Spring Boot REST API.

---

## Stack

| Concern | Choice |
|---|---|
| Framework | Angular 18 (standalone components) |
| UI Library | Angular Material |
| State | Angular Signals (`signal()`, `computed()`) |
| HTTP | Angular `HttpClient` |
| Dev server port | 4200 |
| Backend | `http://localhost:8080` (env-configurable) |

---

## Folder Structure

```
angular-ui/
├── angular.json
├── package.json
├── tsconfig.json
└── src/
    ├── environments/
    │   ├── environment.ts           # apiBaseUrl: 'http://localhost:8080'
    │   └── environment.prod.ts
    ├── main.ts                      # bootstrapApplication
    └── app/
        ├── app.config.ts            # provideRouter (lazy), provideHttpClient, provideAnimations
        ├── app.component.ts         # shell: MatSidenav + <router-outlet>
        ├── core/
        │   └── rag-api.service.ts   # all HttpClient calls
        ├── features/
        │   ├── ingest/
        │   │   └── ingest.component.ts
        │   ├── query/
        │   │   ├── query.component.ts
        │   │   └── chat.service.ts
        │   └── documents/
        │       ├── documents.component.ts
        │       └── documents.service.ts
        └── shared/
            └── citation-card/
                └── citation-card.component.ts
```

---

## Routing

| Path | Component | Notes |
|---|---|---|
| `/` | — | Redirects to `/query` |
| `/query` | `QueryComponent` | Lazy-loaded |
| `/ingest` | `IngestComponent` | Lazy-loaded |
| `/documents` | `DocumentsComponent` | Lazy-loaded |

---

## Backend Change Required

No CORS configuration exists in the Spring Boot app today. A new `WebConfig.java` class implementing `WebMvcConfigurer` must be added to `com.test.rag.config`, allowing all HTTP methods and headers from `http://localhost:4200`.

---

## Components & Features

### Shell (`AppComponent`)
- `MatSidenav` always-open on desktop, hamburger-toggled on mobile
- `MatNavList` with three items: Query (`chat`), Ingest (`upload_file`), Documents (`folder`)
- `<router-outlet>` fills the main content area

### `/query` — Chat Interface
- Chat history scrolls in the main area: user messages right-aligned, assistant answers left-aligned (Material cards)
- `MatFormField` + `MatInput` pinned to the bottom; submit on Enter or send button
- `MatProgressSpinner` replaces the send button while a query is in flight
- Input disabled while loading
- Each assistant card shows inline `CitationCardComponent` entries below the answer text
- Error response rendered as an assistant message: "Something went wrong. Please try again."

### `/ingest` — Document Upload
- `MatCard` with a styled `<div>` drop zone using native `dragover`/`drop` events + a hidden `<input type="file">` for click-to-browse
- Accepted types hint: PDF, DOCX, HTML · max 50 MB
- Selected file name + size shown before upload
- `MatProgressBar` during upload
- Success: green `MatSnackBar` toast
- Error: red `MatSnackBar` with server error message or generic fallback
- Upload button re-enabled after error so user can retry

### `/documents` — Document Library
- `MatTable` with columns: Filename, Type, Size, Chunks, Tokens, Uploaded At, Delete
- Delete button (`MatIconButton` with trash icon) opens `MatDialog` confirmation before calling DELETE
- `DocumentsService` refreshes the signal after every successful delete
- Empty state: "No documents ingested yet" with a link to `/ingest`
- Inline `MatError` banner shown if list fetch fails

### `CitationCardComponent` (shared, standalone)
- `MatExpansionPanel` per citation
- Header: `[N] filename · score`
- Body: chunk text excerpt

---

## Data Flow

### `RagApiService`
Single class with `HttpClient`. Base URL from `environment.ts`. No other class imports `HttpClient`.

```
ingest(file: File): Observable<void>
  POST /api/rag/ingest  (multipart/form-data, field: "file")

query(question: string): Observable<RagResponse>
  POST /api/rag/query?q=<question>

listDocuments(): Observable<DocumentSummary[]>
  GET /api/rag/documents

deleteDocument(sourceId: string): Observable<void>
  DELETE /api/rag/documents/{sourceId}
```

### `ChatService`
- `signal<ChatMessage[]>` — in-memory only, resets on refresh
- `ChatMessage`: `{ role: 'user' | 'assistant', text: string, citations?: Citation[], totalTokens?: number }`
- Methods: `addUserMessage(text)`, `addAssistantMessage(response: RagResponse)`
- No HTTP — components call `RagApiService.query()` and pass the result here

### `DocumentsService`
- `signal<DocumentSummary[]>`
- Calls `RagApiService.listDocuments()` on init and after every successful delete
- Components read the signal directly

---

## Error Handling

| Context | Behaviour |
|---|---|
| Query failure | Error `ChatMessage` pushed to history; input re-enabled |
| Upload failure | `MatSnackBar` (red); progress bar hidden; button re-enabled |
| Document list failure | Inline `MatError` banner inside table card |
| Delete failure | `MatSnackBar` (red) |
| Loading guard | Each feature has `loading = signal<boolean>(false)`; inputs disabled while true |

All HTTP errors caught at component level with RxJS `catchError`. Non-2xx responses never have their body parsed.

---

## TypeScript Interfaces (frontend models)

```typescript
interface RagResponse {
  answer: string;
  citations: Citation[];
  totalTokens: number;
}

interface Citation {
  ref: number;
  filename: string;
  chunkIndex: number;
  score: number;
  chunkText: string;
}

interface DocumentSummary {
  filename: string;
  sourceId: string;
  contentType: string;
  author: string;
  createdDate: string;
  uploadedAt: string;
  fileSizeBytes: number;
  chunkCount: number;
  totalTokens: number;
}

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  citations?: Citation[];
  totalTokens?: number;
}
```

---

## Testing

**`RagApiService`** — `HttpClientTestingModule`. One test per endpoint: correct HTTP method, URL, and response mapping.

**`ChatService`** — pure signal logic. Tests: `addUserMessage` appends correctly, `addAssistantMessage` attaches citations, history accumulates across calls.

**`DocumentsService`** — mocked `RagApiService`. Tests: signal populated on init, list refreshes after delete.

**Components** — shallow `TestBed` with `RagApiService` mocked via `jasmine.createSpyObj`. Per component: renders without error, submit triggers correct service call, loading disables input, error shows correct feedback.

No e2e tests for v1 — requires live backend with real OpenAI key and PGVector.
