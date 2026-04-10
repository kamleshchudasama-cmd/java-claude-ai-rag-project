# Spec — GenerationService

**Package:** `com.test.rag.service.generation`

## Responsibility
Call Gemini-2.5-flash via Spring AI `ChatClient` with the built context,
return a `RagResponse` with answer + citations.

## Inputs / Outputs
    In      BuiltContext
                systemPrompt    String
                userMessage     String
                citations       List<Citation>
    Out     RagResponse
                answer          String
                citations       List<Citation>
                totalTokens     int

## Behaviour
- Use `ChatClient` bean (Spring AI) — never raw Google SDK
- temperature: 0.2 (from `RagProperties.temperature`)
- maxOutputTokens: 2048 (from `RagProperties.maxOutputTokens`)
- Parse model reply to extract inline citation markers `[1]`…`[N]`
- Map each marker back to the corresponding `Citation` from `BuiltContext`
- Every response must populate `citations` — never return an empty list if chunks were used
- Log: total prompt tokens, completion tokens, latency (ms)

## Do Not
- Call the Gemini API directly — always go through Spring AI `ChatClient`
- Hardcode temperature or token limits — read from `RagProperties`
