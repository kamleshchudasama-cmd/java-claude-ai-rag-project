# context — ContextBuilderService
**Spec:** `docs/specs/context-builder-service.md`

## Responsibility
Assemble retrieved chunks and the user query into a grounded prompt string
ready for `GenerationService`. Pure string assembly — no AI API calls.

## Inputs / Outputs
    In      String userQuery                         raw user question
            List<ScoredChunk>                        retrieval results from RetrievalService
    Out     BuiltContext                             { systemPrompt, userMessage, List<Citation> }

## Rules
- Load the prompt template from `resources/prompts/rag-system.st` using Spring AI `StringTemplate`.
- Inject chunks as numbered references `[1]`, `[2]`, … `[N]` in the context block.
- Build a `Citation` for each chunk: `{ ref, filename, chunkIndex, score }`.
- Truncate the assembled context so it stays within `ragProperties.getMaxContextTokens()` (default 4096).
  Truncation must preserve the most relevant (highest-scoring) chunks.
- **Do not** call any AI API — this is pure string work.
- **Do not** call `GenerationService` — the controller orchestrates that handoff.

## Dependencies
- `RagProperties` from `com.test.rag.config`
- `ScoredChunk`, `BuiltContext`, `Citation` from `com.test.rag.model`
- Prompt template: `src/main/resources/prompts/rag-system.st`
