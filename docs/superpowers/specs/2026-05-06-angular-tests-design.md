# Angular Test Enhancement Design
**Date:** 2026-05-06
**Scope:** All Angular components and services in `angular-ui/src/app/`
**Framework:** Jasmine + Karma
**Approach:** Enhance existing specs (Option B); high coverage (behaviour + branch + boundary)

---

## Goals

- Create the one missing spec file (`confirm-dialog.component.spec.ts`)
- Enhance all existing spec files with branch coverage, edge cases, and boundary values
- Leave existing passing tests untouched — new cases are appended only
- All tests must pass under `ng test`

---

## File Inventory

| Spec File | Current Tests | New Tests | Total |
|---|---|---|---|
| `confirm-dialog.component.spec.ts` | 0 (new file) | 4 | 4 |
| `app.component.spec.ts` | 1 | 4 | 5 |
| `rag-api.service.spec.ts` | 4 | 3 | 7 |
| `citation-card.component.spec.ts` | 2 | 3 | 5 |
| `documents.service.spec.ts` | 4 | 3 | 7 |
| `documents.component.spec.ts` | 8 | 4 | 12 |
| `ingest.component.spec.ts` | 9 | 4 | 13 |
| `chat.service.spec.ts` | 4 | 2 | 6 |
| `query.component.spec.ts` | 8 | 4 | 12 |
| **Total** | **40** | **31** | **71** |

---

## Domain Groups (parallel execution)

### Group 1 — Core / Shared

#### `confirm-dialog.component.spec.ts` (NEW — 4 tests)
1. creates the component
2. renders the filename from injected `MAT_DIALOG_DATA`
3. Cancel button closes dialog without returning `true`
4. Delete button closes dialog with `true`

**Setup:** Provide `MAT_DIALOG_DATA` with `{ filename: 'test.pdf' }` and a `MatDialogRef` spy.

#### `app.component.spec.ts` (+4 tests)
5. renders the "RAG System" sidenav title
6. renders Query, Ingest, and Documents nav links
7. nav links carry the correct `routerLink` values (`/query`, `/ingest`, `/documents`)
8. template contains a `<router-outlet>`

**Setup:** Keep existing `RouterTestingModule` + `NoopAnimationsModule`.

#### `rag-api.service.spec.ts` (+3 tests)
9. `ingest` places the file under the `'file'` key in `FormData`
10. `query` maps HTTP response body to a `RagResponse` and emits it
11. HTTP errors (e.g., 500) propagate to subscriber as observable errors

#### `citation-card.component.spec.ts` (+3 tests)
12. displays the similarity score in the panel description area
13. displays the chunk index
14. renders correctly when `ref` is greater than 1 (e.g., `[3]`)

---

### Group 2 — Documents Feature

#### `documents.service.spec.ts` (+3 tests)
15. `load()` clears a previous error signal before issuing a new request
16. `delete()` clears the error signal on a successful delete + reload
17. `delete()` sets the error signal on API failure (distinct from the `onError` callback path)

#### `documents.component.spec.ts` (+4 tests)
18. error strip is absent when the error signal is `null`
19. each doc card displays chunk count and total tokens
20. each doc card displays a human-readable file size (e.g., "2.38 MB")
21. each doc card displays a formatted `uploadedAt` date string

---

### Group 3 — Ingest Feature

#### `ingest.component.spec.ts` (+4 tests)
22. accepts HTML file type (`text/html`) — shows preview card, does not reject
23. `clear()` resets `errorMessage` to empty string
24. upload button is disabled while state is `'uploading'`
25. file `<input>` `change` event calls `setFile` with the selected file

---

### Group 4 — Query Feature

#### `chat.service.spec.ts` (+2 tests)
26. `messages()` returns an empty array on service instantiation
27. `addAssistantMessage` with an empty `citations` array stores `citations: []` correctly

#### `query.component.spec.ts` (+4 tests)
28. `send()` does nothing (no API call, no message added) when `inputText` is whitespace-only
29. Send button is disabled while `isLoading` is `true`
30. empty-state element is removed after the first message is added
31. multiple consecutive sends accumulate all messages in the list

---

## Conventions

- **No mocks of internals** — only mock service/API boundaries
- **Signals** — use `.set()` then `fixture.detectChanges()` to trigger re-render
- **Async** — use `fakeAsync` + `tick()` for setTimeout and observable completions
- **DOM queries** — use CSS class selectors matching the existing spec pattern
- **Error propagation** — use `throwError(() => new Error(...))` from `rxjs`
- **MatDialogRef spy** — `jasmine.createSpyObj('MatDialogRef', ['close'])`

---

## Success Criteria

- `ng test --watch=false` exits with 0 failures
- All 71 tests pass
- No existing tests are modified or removed
