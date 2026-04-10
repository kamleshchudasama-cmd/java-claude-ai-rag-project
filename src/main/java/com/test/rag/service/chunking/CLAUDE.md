# chunking — ChunkingService
**Spec:** `docs/specs/chunking-service.md`

## Responsibility
Split a `ParsedDocument` into overlapping `DocumentChunk` objects ready for embedding.
Pure text processing — no external API calls of any kind.

## Inputs / Outputs
    In      ParsedDocument                           output of DocumentLoaderService
    Out     List<DocumentChunk>                      ordered, overlapping chunks

## Rules
- Splitting strategy: recursive — paragraph → sentence → word boundary.
- **All** size/overlap params must come from `RagProperties` — no magic numbers.
  - `ragProperties.getChunkSize()`    default 512 tokens
  - `ragProperties.getChunkOverlap()` default  50 tokens
  - `ragProperties.getMinChunkSize()` default 100 tokens — discard smaller chunks
- Assign a deterministic `chunkId` = `SHA-256(sourceFilename + chunkIndex)`.
- Preserve source metadata from `ParsedDocument` on every chunk.
- **Do not** call any external API, Spring AI bean, or other service.

## Dependencies
- `RagProperties` from `com.test.rag.config`
- `ParsedDocument`, `DocumentChunk` from `com.test.rag.model`
