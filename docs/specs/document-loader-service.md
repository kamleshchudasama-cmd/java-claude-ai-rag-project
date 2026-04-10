# Spec: DocumentLoaderService

## Responsibility
Accept a file input, parse raw content using Apache Tika, and return a
structured `ParsedDocument` containing extracted text and metadata.
Entry point of the entire ingestion pipeline.

---

## Interface
```java
public interface DocumentLoaderService {
    ParsedDocument load(Path filePath);
    ParsedDocument load(MultipartFile file);
}
```

## Input / Output
```java
// Input: Path or MultipartFile

// Output:
public record ParsedDocument(
    String content,                  // full extracted text
    Map<String, String> metadata,    // filename, content-type, author, created-date
    String sourceId                  // SHA-256(filename + fileSize)
)
```

---

## Behaviour
- Use Tika `AutoDetectParser` — handles PDF, DOCX, HTML, TXT automatically
- Use `BodyContentHandler` to extract body text only (strips headers/footers)
- Extract metadata keys: `filename`, `Content-Type`, `Author`, `Creation-Date`
- Generate `sourceId` as `SHA-256(filename + fileSize)` for deduplication
- Throw `DocumentParseException` (unchecked) with original cause on Tika failure
- Log: filename, detected content-type, extracted char count, parse latency

## Constraints
- Must NOT perform chunking
- Must NOT call any external API
- Must NOT write to DB
- `AutoDetectParser` must be a Spring singleton `@Bean`

---

## Error Handling
| Scenario | Behaviour |
|---|---|
| Unsupported file type | Throw `DocumentParseException("Unsupported type: " + detectedType)` |
| Corrupt/unreadable file | Throw `DocumentParseException` wrapping `TikaException` |
| Empty extracted content | Throw `DocumentParseException("No content extracted from: " + filename)` |

---

## Test Cases
- Valid PDF → content non-empty, metadata has filename
- Valid DOCX → content non-empty
- HTML file → boilerplate tags stripped
- Corrupt file → `DocumentParseException` thrown
- Empty file → `DocumentParseException` thrown
