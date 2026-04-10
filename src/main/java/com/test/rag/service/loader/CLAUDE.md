# loader — DocumentLoaderService
**Spec:** `docs/specs/document-loader-service.md`

## Responsibility
Parse raw files (PDF, DOCX, HTML, TXT) into plain text + metadata using Apache Tika.
Returns a `ParsedDocument`. Does **not** chunk, embed, or store anything.

## Inputs / Outputs
    In      Path or MultipartFile                    file to parse
    Out     ParsedDocument                           { String content, Map<String,String> metadata }

## Rules
- Use `AutoDetectParser` — never hard-code a format-specific parser.
- Use `BodyContentHandler` to strip headers/footers and boilerplate.
- Extract metadata: `filename`, `content-type`, `author`, `created-date`.
- On parse failure throw `DocumentParseException` (unchecked) — never swallow.
- **Do not** call `ChunkingService` or any other service from here.
- **Do not** inject Spring AI beans — this is pure Tika I/O.

## Dependencies
- Apache Tika `tika-parsers-standard-package` (no Spring beans needed)
- `DocumentParseException` from `com.test.rag.exception`
- `ParsedDocument` from `com.test.rag.model`
