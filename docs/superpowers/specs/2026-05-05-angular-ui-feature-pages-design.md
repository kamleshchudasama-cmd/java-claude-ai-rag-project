# Angular UI Feature Pages Design

**Date:** 2026-05-05  
**Scope:** Three missing feature components for the Java RAG Angular UI  
**Approach:** Self-contained standalone components — separate `.html`, `.ts`, and `.scss` files per component

---

## Context

The Angular UI shell is already in place:
- `AppComponent` — sidenav with Query / Ingest / Documents nav items
- `app.routes.ts` — lazy routes wired to the three missing components
- `RagApiService` — HTTP calls to the Spring Boot backend
- `ChatService` — signal-based message history
- `DocumentsService` — signal-based document list with load/delete
- `CitationCardComponent` — `mat-expansion-panel` per citation
- `ConfirmDialogComponent` — delete confirmation dialog

All three feature components follow the same conventions as the existing code: standalone, constructor injection, Angular Material throughout. Templates go in `.html` files, styles in `.scss` files, class logic in `.ts` files.

---

## 1. QueryComponent

**Path:** `src/app/features/query/query.component.ts`

### Layout

Full-height flex column (`height: 100%`) split into two zones:

1. **Message list** (`flex: 1`, `overflow-y: auto`) — scrolls; auto-scrolls to bottom after each new message via `ViewChild` + `scrollIntoView`.
2. **Input bar** (fixed at bottom) — `mat-form-field` (full width) with a text `input`, Send `mat-icon-button` on the right.

### Message rendering

Iterates over `chatService.messages()` signal:

| Role | Appearance |
|---|---|
| `user` | Right-aligned pill — indigo background, white text |
| `assistant` | Left-aligned white card — answer text, then one `<app-citation-card>` per citation |

Empty state (no messages): centered muted text — *"Ask a question to get started"*.

### Interactions

- **Send:** button click or Enter key. Single-line `<input>` — no multiline support.
- **Loading state (`isLoading = true`):** Send button disabled and shows `mat-spinner` (diameter 20); input field disabled. No message placeholder in the list.
- **On success:** `ChatService.addAssistantMessage(response)` → scroll to bottom → `isLoading = false`.
- **On error:** `ChatService.addErrorMessage()` → `isLoading = false`.

### Local state

Plain component fields — no signals needed:

```ts
isLoading = false;
inputText = '';
```

### Dependencies

`ChatService`, `RagApiService`, `CitationCardComponent`, Angular Material: `MatFormFieldModule`, `MatInputModule`, `MatButtonModule`, `MatIconModule`, `MatProgressSpinnerModule`.

---

## 2. IngestComponent

**Path:** `src/app/features/ingest/ingest.component.ts`

### Layout

Centered card (`max-width: 560px`, `margin: 48px auto`) containing:

1. **Drop zone** — large dashed-border area, file type icon, hint text (`PDF · DOCX · HTML · up to 50 MB`), "Browse" link triggers hidden `<input type="file" accept=".pdf,.docx,.html">`.
2. **File preview card** — shown after file selection: type icon, bold filename, human-readable size, type badge. Two buttons: **Upload** (primary, raised) and **Clear** (basic).
3. **Status message area** — below buttons; inline success (green) or error (red) text.

### State machine

Local string field `state: 'idle' | 'fileSelected' | 'uploading' | 'success' | 'error'` drives template visibility via `@if`.

| State | Visible |
|---|---|
| `idle` | Drop zone only |
| `fileSelected` | Drop zone (dimmed, `opacity: 0.4`) + preview card + Upload / Clear |
| `uploading` | Same as `fileSelected`; Upload button disabled + spinner; drop zone `pointer-events: none` |
| `success` | Reset to `idle` + green inline message (auto-cleared after 3 s via `setTimeout`) |
| `error` | Stay in `fileSelected` + red message below buttons |

### Drag-and-drop

Native events on the drop zone div — no library:
- `(dragover)` — `$event.preventDefault()`, add `.drag-over` class
- `(dragleave)` — remove `.drag-over` class
- `(drop)` — `$event.preventDefault()`, extract `DataTransfer.files[0]`, run validation

### Validation (before setting `fileSelected`)

- Allowed MIME types: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/html`
- Max size: 50 MB (`50 * 1024 * 1024` bytes)
- On failure: set `state = 'error'` with a descriptive message; no file preview shown.

### File type icon map

```ts
private iconFor(type: string): string {
  if (type.includes('pdf')) return 'picture_as_pdf';
  if (type.includes('word')) return 'description';
  if (type.includes('html')) return 'code';
  return 'insert_drive_file';
}
```

### Dependencies

`RagApiService`, Angular Material: `MatCardModule`, `MatButtonModule`, `MatIconModule`, `MatProgressSpinnerModule`.

---

## 3. DocumentsComponent

**Path:** `src/app/features/documents/documents.component.ts`

### Layout

Two zones:

1. **Header row** — "Documents" heading (`h2`) left; document count badge right (`{{ documents().length }} document(s)`).
2. **Card list** — one card per document, full width, `8px` gap between cards.

### Card structure (per document)

```
[ icon ]  filename (bold)                          [ Delete ]
          PDF · 42 chunks · 8,320 tokens · 2.4 MB · May 1 2025
```

- **Icon:** same `iconFor()` logic as IngestComponent, keyed on `contentType`.
- **Subtitle:** formatted inline — no custom pipe. File size formatted via helper method `formatBytes(bytes: number): string` on the component class.
- **Delete button:** `mat-stroked-button` with `color="warn"`.

### Delete flow

1. Click Delete → `MatDialog.open(ConfirmDialogComponent, { data: { filename } })`.
2. `afterClosed()` returns `true` → `documentsService.delete(sourceId, () => this.deleteError = 'Failed to delete. Try again.')`.
3. On success: `DocumentsService.delete()` calls `load()` internally — list refreshes via signal.
4. `deleteError` string shown as a red alert strip at the top of the list; cleared on next successful action.

### States

| Condition | Display |
|---|---|
| `documents().length === 0` and no error | Centered muted text: *"No documents ingested yet. Use the Ingest page to add files."* |
| `documentsService.error()` is set | Red alert strip below the header |
| `deleteError` is set | Red alert strip below the header |

`ngOnInit` calls `documentsService.load()`. No explicit loading spinner — list starts empty and fills when the signal updates.

### Dependencies

`DocumentsService`, `MatDialog`, `ConfirmDialogComponent`, Angular Material: `MatButtonModule`, `MatIconModule`, `MatCardModule`, `MatDialogModule`.

---

## Shared Decisions

| Decision | Choice |
|---|---|
| Component style | Standalone, separate .html / .ts / .scss files |
| Injection | Constructor injection only |
| State | Plain component fields for ephemeral UI state; signals via services for shared state |
| Loading indicator (query) | Send button disabled + spinner; no message placeholder |
| Upload UX | Select → preview → confirm (two-step) |
| Documents layout | Card list (not table, not accordion) |
| Error handling | Inline messages, not snackbars (except implicit via `ChatService.addErrorMessage`) |
| Empty states | Explicit centered text on all three pages |

---

## Out of Scope

- Pagination or search on the Documents page
- Multi-file upload on the Ingest page
- Message persistence across browser sessions (ChatService is in-memory)
- Streaming responses
