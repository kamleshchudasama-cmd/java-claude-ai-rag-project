# generation — GenerationService
**Spec:** `docs/specs/generation-service.md`

## Responsibility
Call gpt-4o-mini via Spring AI `ChatClient` using the grounded prompt from
`ContextBuilderService`, then parse the model reply into a `RagResponse` with
inline citations mapped back to source documents.

## Inputs / Outputs
    In      BuiltContext                             output of ContextBuilderService
    Out     RagResponse                              { String answer, List<Citation> citations, int totalTokens }

## Rules
- **Never** call the OpenAI API directly — always use the injected `ChatClient` bean.
- Read generation params exclusively from `RagProperties`:
  - `ragProperties.getTemperature()`     default 0.2
  - `ragProperties.getMaxOutputTokens()` default 2048
- Parse inline `[1]`…`[N]` citation markers from the model reply.
- Map each marker back to the corresponding `Citation` in `BuiltContext.citations()`.
- Every `RagResponse` **must** populate `citations` — never return an empty list if chunks were used.
- **Log:** total prompt tokens, completion tokens, latency (ms).

## Dependencies
- `ChatClient` bean from `com.test.rag.config.SpringAiConfig`
- `RagProperties` from `com.test.rag.config`
- `BuiltContext`, `RagResponse`, `Citation` from `com.test.rag.model`
