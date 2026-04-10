# Spec: ChunkingService

## Responsibility
Split a `ParsedDocument` into overlapping `DocumentChunk` objects using a
recursive character-based strategy. Pure text processing — no API calls.

---

## Interface
```java
public interface ChunkingService {
    List<DocumentChunk> chunk(ParsedDocument document);
}
```

## Input / Output
```java
// Input: ParsedDocument (from DocumentLoaderService)

// Output:
public record DocumentChunk(
    String chunkId,                  // SHA-256(sourceId + chunkIndex)
    String content,                  // chunk text
    int chunkIndex,                  // position in original document
    int tokenCount,                  // estimated: ceil(charCount / 4.0)
    Map<String, String> metadata     // inherited from ParsedDocument + chunk_index
)
```

---

## Behaviour
- Split order: paragraph → sentence → word boundary
- Overlap: last N tokens of chunk K become first N tokens of chunk K+1
- Discard chunks under `rag.chunking.min-chunk-size` tokens
- `chunkId = SHA-256(sourceId + chunkIndex)` — deterministic across re-ingestion
- Inherit all metadata from `ParsedDocument`, add `chunk_index` key
- Token estimate: `Math.ceil(charCount / 4.0)`
- Log: source filename, total chunks, min/max/avg chunk size

## Defaults (always from RagProperties)
| Parameter | Default | Property Key |
|---|---|---|
| Chunk size | 512 tokens | `rag.chunking.chunk-size` |
| Overlap | 50 tokens | `rag.chunking.overlap` |
| Min chunk size | 100 tokens | `rag.chunking.min-chunk-size` |

---

## Constraints
- No external API calls
- No DB writes
- All params from `RagProperties` — no `@Value` in this class
- Must be deterministic: same input → same chunk IDs always

---

## Error Handling
| Scenario | Behaviour |
|---|---|
| Null or blank content | Throw `ChunkingException("Cannot chunk empty document")` |
| Content under min size | Return single chunk with full content |

---

## Test Cases
- 2000-word doc → assert overlap exists between consecutive chunks
- 50-word doc → assert single chunk returned
- Same input twice → assert identical `chunkId` values (determinism)
- All chunks inherit source metadata
- No chunk below `min-chunk-size` tokens