# Spec — ContextBuilderService

**Package:** `com.test.rag.service.context`

## Responsibility
Assemble retrieved chunks and the user query into a grounded prompt string
ready for `GenerationService`. Pure string assembly — no AI API calls.

## Inputs / Outputs
    In      String userQuery
            List<ScoredChunk>
    Out     BuiltContext
                systemPrompt    String
                userMessage     String
                citations       List<Citation>

## Behaviour
- Load prompt template from `resources/prompts/rag-system.st` (Spring AI StringTemplate)
- Inject chunks as numbered `[1]`…`[N]` references in the context block
- Build `Citation` per chunk: `{ int ref, String filename, int chunkIndex, double score }`
- Truncate context to stay within `RagProperties.maxContextTokens` (default 4096)
  — truncation must keep the highest-scoring chunks

## Do Not
- Call any AI API — this is pure string assembly
- Call `GenerationService` — the controller orchestrates that handoff
