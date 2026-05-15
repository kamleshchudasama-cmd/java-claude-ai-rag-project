# Video Support Design

**Date:** 2026-05-15
**Status:** Approved

## Overview

Add video file ingestion to the RAG system. Users upload a video via the existing `POST /api/rag/ingest` endpoint. The audio is transcribed via OpenAI Whisper API and the transcript is stored in PGVector through the same chunking/embedding pipeline used for all other document types. Videos appear in the documents list by filename with no playback UI.

---

## Constraints

- Max video size: **5 MB** (enforced in `VideoDocumentLoaderService`)
- Whisper API hard limit is 25 MB — 5 MB is safely inside it
- Supported MIME types: `video/mp4`, `video/quicktime`, `video/x-msvideo`, `video/webm`, `video/mpeg`
- No new Maven dependencies required — `OpenAiAudioTranscriptionModel` is already on the classpath via `spring-ai-starter-model-openai`
- No PGVector schema changes — same metadata keys as existing documents
- No new REST endpoints — reuse `POST /api/rag/ingest`

---

## Backend Architecture

### New Classes

```
service/
├── loader/
│   ├── DocumentLoaderService.java               (unchanged — interface)
│   ├── TikaDocumentLoaderService.java           (unchanged)
│   ├── VideoDocumentLoaderService.java          (NEW)
│   └── DispatchingDocumentLoaderService.java    (NEW — @Primary)
└── transcription/
    ├── VideoTranscriptionService.java           (NEW — interface)
    └── OpenAiVideoTranscriptionService.java     (NEW — Whisper via Spring AI)
```

### Routing

`DispatchingDocumentLoaderService` is annotated `@Primary` and implements `DocumentLoaderService`. It inspects the file MIME type:
- `video/*` → delegates to `VideoDocumentLoaderService`
- anything else → delegates to `TikaDocumentLoaderService`

Both delegates are injected using `@Qualifier` to avoid Spring ambiguity.

If `MultipartFile.getContentType()` returns `null` or blank, `DispatchingDocumentLoaderService` falls through to `TikaDocumentLoaderService` (safe default).

The `RagController` is **unchanged** — it still injects a single `DocumentLoaderService`.

### VideoTranscriptionService

```java
// Interface
public interface VideoTranscriptionService {
    String transcribe(MultipartFile file);
}
```

`OpenAiVideoTranscriptionService` injects `OpenAiAudioTranscriptionModel` (Spring AI bean) and calls it with the `MultipartFile` bytes. Returns the plain-text transcript string.

### VideoDocumentLoaderService

Implements `DocumentLoaderService`. Steps:
1. Validate file size ≤ `rag.max-video-size-bytes` (5 MB) — throw `DocumentParseException` if exceeded
2. Call `VideoTranscriptionService.transcribe(file)` → transcript string
3. Validate transcript is non-empty — throw `DocumentParseException` if empty
4. Compute `sourceId` = SHA-256(`filename:fileSize`)
5. Build and return `ParsedDocument(transcript, metadata, sourceId)`

Metadata keys written (same conventions as `TikaDocumentLoaderService`):

| Key | Value |
|---|---|
| `filename` | original filename |
| `source_id` | SHA-256 hex |
| `content-type` | detected MIME type (e.g. `video/mp4`) |
| `upload-timestamp` | `Instant.now()` |
| `file-size-bytes` | file size in bytes |

---

## Data Flow

### Ingestion (video)

```
MultipartFile (video/*)
  → DispatchingDocumentLoaderService.load()        detects video/* MIME
  → VideoDocumentLoaderService.load()              validates size ≤ 5 MB
  → OpenAiVideoTranscriptionService.transcribe()   Whisper API → transcript string
  → ParsedDocument(transcript, metadata, sourceId)
  → RecursiveChunkingService.chunk()               unchanged
  → OpenAiEmbeddingService.embed()                 unchanged
  → PgVectorStoreService.upsert()                  unchanged
```

### Query

Unchanged. Transcribed video chunks are retrieved by cosine similarity identical to document chunks. Citations show the video filename and chunk index.

---

## Configuration

**`application.properties` additions:**
```properties
spring.ai.openai.audio.transcription.options.model=whisper-1
rag.max-video-size-bytes=5242880
```

**`RagProperties` addition:**
```java
private long maxVideoSizeBytes = 5_242_880L;
```

---

## Error Handling

All errors throw `DocumentParseException` (unchecked), caught by `GlobalExceptionHandler` → HTTP 500.

| Scenario | Message |
|---|---|
| File > 5 MB | `"Video exceeds 5 MB limit: <filename>"` |
| Whisper API failure | `"Failed to transcribe: <filename>"` wrapping cause |
| Empty transcript returned | `"No transcript extracted from: <filename>"` |
| Unsupported format rejected by Whisper | `"Failed to transcribe: <filename>"` wrapping API error |

---

## Frontend Changes

### `ingest.component.ts`

- Add to `ALLOWED_TYPES`: `'video/mp4'`, `'video/quicktime'`, `'video/x-msvideo'`, `'video/webm'`, `'video/mpeg'`
- Update `iconFor()`: return `'videocam'` for `video/` prefix
- Update `typeBadge()`: return `'VIDEO'` for `video/` prefix
- Update user-facing error message to include "MP4, MOV, AVI, WEBM"

### `documents.component.ts`

- Update `iconFor()`: return `'videocam'` for `contentType?.startsWith('video/')`
- Update `typeBadge()`: return `'VIDEO'` for `contentType?.startsWith('video/')`

No new components, routes, or HTML template changes required. Videos render in the existing doc card layout with filename, chunk count, token count, file size, and upload date.

---

## Testing

| Test class | What it covers |
|---|---|
| `OpenAiVideoTranscriptionServiceTest` | Mock `OpenAiAudioTranscriptionModel`; verify transcript returned; verify `DocumentParseException` on empty result and API failure |
| `VideoDocumentLoaderServiceTest` | Mock `VideoTranscriptionService`; verify `ParsedDocument` fields; verify size rejection |
| `DispatchingDocumentLoaderServiceTest` | Verify `video/*` routes to video loader; non-video routes to Tika loader |
| `ingest.component.spec.ts` | Assert video MIME types are accepted; assert non-video types still rejected |
| `documents.component.spec.ts` | Assert `iconFor` / `typeBadge` return correct values for video content type |
