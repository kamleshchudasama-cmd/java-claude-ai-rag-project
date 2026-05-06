# Angular Test Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 30 new Jasmine/Karma tests across 9 spec files — creating 1 new spec and enhancing 8 existing ones — to achieve high branch and boundary coverage across all Angular components and services.

**Architecture:** All new tests are appended after existing tests in each spec file; no existing test is modified or removed. Tasks 1–9 are file-isolated and can execute in parallel within each group. Task 10 runs last.

**Tech Stack:** Angular 17+ (standalone components), Jasmine, Karma, Angular Material, RxJS

---

## File Map

| Action | File |
|---|---|
| Create | `angular-ui/src/app/shared/confirm-dialog/confirm-dialog.component.spec.ts` |
| Modify | `angular-ui/src/app/app.component.spec.ts` |
| Modify | `angular-ui/src/app/core/rag-api.service.spec.ts` |
| Modify | `angular-ui/src/app/shared/citation-card/citation-card.component.spec.ts` |
| Modify | `angular-ui/src/app/features/documents/documents.service.spec.ts` |
| Modify | `angular-ui/src/app/features/documents/documents.component.spec.ts` |
| Modify | `angular-ui/src/app/features/ingest/ingest.component.spec.ts` |
| Modify | `angular-ui/src/app/features/query/chat.service.spec.ts` |
| Modify | `angular-ui/src/app/features/query/query.component.spec.ts` |

## Test Count

| File | Existing | New | Total |
|---|---|---|---|
| `confirm-dialog.component.spec.ts` | 0 | 4 | 4 |
| `app.component.spec.ts` | 1 | 4 | 5 |
| `rag-api.service.spec.ts` | 4 | 3 | 7 |
| `citation-card.component.spec.ts` | 2 | 2 | 4 |
| `documents.service.spec.ts` | 4 | 3 | 7 |
| `documents.component.spec.ts` | 8 | 4 | 12 |
| `ingest.component.spec.ts` | 9 | 4 | 13 |
| `chat.service.spec.ts` | 4 | 2 | 6 |
| `query.component.spec.ts` | 8 | 4 | 12 |
| **Total** | **40** | **30** | **70** |

> Note: `citation-card` gets 2 new tests instead of 3 — the template does not render `chunkIndex`, so that case is replaced with a ref > 1 scenario.

---

## Group 1 — Core / Shared (Tasks 1–4 can run in parallel)

---

### Task 1: Create confirm-dialog.component.spec.ts (4 new tests)

**Files:**
- Create: `angular-ui/src/app/shared/confirm-dialog/confirm-dialog.component.spec.ts`

The component uses `MAT_DIALOG_DATA` for the filename and `MatDialogRef` for closing. The `[mat-dialog-close]="true"` directive on the Delete button calls `dialogRef.close(true)` when clicked.

- [ ] **Step 1: Create the spec file**

Create `angular-ui/src/app/shared/confirm-dialog/confirm-dialog.component.spec.ts`:

```typescript
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let component: ConfirmDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { filename: 'test.pdf' } },
        { provide: MatDialogRef, useValue: dialogRefSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('renders the filename from injected dialog data', () => {
    const content: HTMLElement = fixture.nativeElement.querySelector('mat-dialog-content');
    expect(content.textContent).toContain('test.pdf');
  });

  it('Cancel button is present and has the mat-dialog-close attribute', () => {
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button')
    );
    const cancelBtn = buttons.find(b => b.textContent?.trim() === 'Cancel');
    expect(cancelBtn).toBeTruthy();
    expect(cancelBtn!.hasAttribute('mat-dialog-close')).toBeTrue();
  });

  it('Delete button closes the dialog with true when clicked', () => {
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button')
    );
    const deleteBtn = buttons.find(b => b.textContent?.trim() === 'Delete');
    expect(deleteBtn).toBeTruthy();
    deleteBtn!.click();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(true);
  });
});
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/confirm-dialog.component.spec.ts"
```

Expected: `Executed 4 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/shared/confirm-dialog/confirm-dialog.component.spec.ts
git commit -m "test(angular): add ConfirmDialogComponent spec"
```

---

### Task 2: Enhance app.component.spec.ts (+4 tests)

**Files:**
- Modify: `angular-ui/src/app/app.component.spec.ts`

The existing spec has one `it` block that creates a fixture inline. Follow the same pattern — each new test creates its own `AppComponent` fixture. The `RouterTestingModule` already provides `href` attributes on router links.

- [ ] **Step 1: Append 4 new `it` blocks inside the existing `describe` block, after the last existing `it`**

```typescript
  it('renders the "RAG System" sidenav title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const title: HTMLElement = fixture.nativeElement.querySelector('.sidenav-title');
    expect(title.textContent?.trim()).toBe('RAG System');
  });

  it('renders Query, Ingest, and Documents nav links', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const links: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('mat-nav-list a')
    );
    const texts = links.map(l => l.textContent?.trim());
    expect(texts).toContain('Query');
    expect(texts).toContain('Ingest');
    expect(texts).toContain('Documents');
  });

  it('nav links point to the correct routes', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const links: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('mat-nav-list a')
    );
    const hrefs = links.map(l => l.getAttribute('href'));
    expect(hrefs).toContain('/query');
    expect(hrefs).toContain('/ingest');
    expect(hrefs).toContain('/documents');
  });

  it('template contains a router-outlet element', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  });
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/app.component.spec.ts"
```

Expected: `Executed 5 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/app.component.spec.ts
git commit -m "test(angular): enhance AppComponent spec with nav link and routing tests"
```

---

### Task 3: Enhance rag-api.service.spec.ts (+3 tests)

**Files:**
- Modify: `angular-ui/src/app/core/rag-api.service.spec.ts`

The existing spec does not import `RagResponse`. Add the import, then append three tests covering FormData key name, response mapping, and HTTP error propagation.

- [ ] **Step 1: Add the missing import**

After the line `import { RagApiService } from './rag-api.service';`, add:

```typescript
import { RagResponse } from './models';
```

- [ ] **Step 2: Append 3 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('ingest appends the file under the "file" key in FormData', () => {
    const file = new File(['content'], 'report.pdf', { type: 'application/pdf' });
    service.ingest(file).subscribe();
    const req = http.expectOne(`${base}/api/rag/ingest`);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(null);
  });

  it('query emits the RagResponse returned by the server', () => {
    const mockResponse: RagResponse = { answer: 'RAG is...', citations: [], totalTokens: 10 };
    let result: RagResponse | undefined;
    service.query('test question').subscribe(r => result = r);
    const req = http.expectOne(r => r.url === `${base}/api/rag/query`);
    req.flush(mockResponse);
    expect(result).toEqual(mockResponse);
  });

  it('propagates HTTP 500 errors to the subscriber as an observable error', () => {
    let caughtError: any;
    service.listDocuments().subscribe({ error: e => caughtError = e });
    const req = http.expectOne(`${base}/api/rag/documents`);
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
    expect(caughtError).toBeTruthy();
    expect(caughtError.status).toBe(500);
  });
```

- [ ] **Step 3: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/rag-api.service.spec.ts"
```

Expected: `Executed 7 specs, 0 failures.`

- [ ] **Step 4: Commit**

```bash
git add angular-ui/src/app/core/rag-api.service.spec.ts
git commit -m "test(angular): enhance RagApiService spec with FormData key, response mapping, and error propagation"
```

---

### Task 4: Enhance citation-card.component.spec.ts (+2 tests)

**Files:**
- Modify: `angular-ui/src/app/shared/citation-card/citation-card.component.spec.ts`

The template formats score with Angular's `DecimalPipe` using `'1.2-2'`, so `0.924` renders as `0.92`. The template does **not** render `chunkIndex`, so that case is replaced with a ref > 1 scenario. Use `fixture.componentRef.setInput()` to update the input between tests.

- [ ] **Step 1: Append 2 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('displays the similarity score formatted to 2 decimal places', () => {
    // citation from beforeEach has score: 0.924 — DecimalPipe '1.2-2' renders it as "0.92"
    const header: HTMLElement = fixture.nativeElement.querySelector('mat-panel-title');
    expect(header.textContent).toContain('0.92');
  });

  it('renders correctly when ref is greater than 1', () => {
    const altCitation: Citation = {
      ref: 3, filename: 'notes.pdf', chunkIndex: 1, score: 0.85, chunkText: 'alt excerpt'
    };
    fixture.componentRef.setInput('citation', altCitation);
    fixture.detectChanges();
    const header: HTMLElement = fixture.nativeElement.querySelector('mat-panel-title');
    expect(header.textContent).toContain('[3]');
    expect(header.textContent).toContain('notes.pdf');
  });
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/citation-card.component.spec.ts"
```

Expected: `Executed 4 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/shared/citation-card/citation-card.component.spec.ts
git commit -m "test(angular): enhance CitationCardComponent spec with score formatting and ref > 1 tests"
```

---

## Group 2 — Documents Feature (Tasks 5–6 can run in parallel)

---

### Task 5: Enhance documents.service.spec.ts (+3 tests)

**Files:**
- Modify: `angular-ui/src/app/features/documents/documents.service.spec.ts`

Key behaviour to cover: `load()` clearing a previous error on success; `delete()` triggering a reload that clears error; `delete()` not calling `load()` on the error path.

- [ ] **Step 1: Append 3 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('load() clears a previous error when the subsequent request succeeds', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');

    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    service.load();
    expect(service.error()).toBeNull();
  });

  it('delete() on success re-loads documents and clears a pre-existing error', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');

    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    apiSpy.deleteDocument.and.returnValue(of(undefined));
    service.delete('abc123', () => {});
    expect(service.error()).toBeNull();
    expect(service.documents()).toEqual([mockDoc]);
  });

  it('delete() does not call load() when the API returns an error', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    service.delete('abc123', () => {});
    expect(apiSpy.listDocuments).not.toHaveBeenCalled();
  });
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/documents.service.spec.ts"
```

Expected: `Executed 7 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/features/documents/documents.service.spec.ts
git commit -m "test(angular): enhance DocumentsService spec with error clearing and reload behaviour"
```

---

### Task 6: Enhance documents.component.spec.ts (+4 tests)

**Files:**
- Modify: `angular-ui/src/app/features/documents/documents.component.spec.ts`

The `.doc-meta` line renders: `typeBadge · chunkCount chunks · totalTokens tokens · formatBytes · formatDate`.

`formatBytes(2_500_000)` → `"2.4 MB"`. For the date test, use `uploadedAt: '2025-06-15T12:00:00Z'` (noon UTC) so the rendered date is June 15 in every timezone.

- [ ] **Step 1: Append 4 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('error strip is absent when the error signal is null', () => {
    fakeService.error.set(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.alert-strip')).toBeNull();
  });

  it('doc card displays chunk count and total tokens in the meta line', () => {
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const meta: HTMLElement = fixture.nativeElement.querySelector('.doc-meta');
    expect(meta.textContent).toContain('42 chunks');
    expect(meta.textContent).toContain('8320 tokens');
  });

  it('doc card displays a human-readable file size', () => {
    // mockDoc.fileSizeBytes = 2_500_000 → formatBytes → "2.4 MB"
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const meta: HTMLElement = fixture.nativeElement.querySelector('.doc-meta');
    expect(meta.textContent).toContain('2.4 MB');
  });

  it('doc card displays a formatted uploadedAt date string', () => {
    // Use noon UTC to avoid date-shifting across timezones
    const safeDoc = { ...mockDoc, uploadedAt: '2025-06-15T12:00:00Z' };
    fakeService.documents.set([safeDoc]);
    fixture.detectChanges();
    const meta: HTMLElement = fixture.nativeElement.querySelector('.doc-meta');
    expect(meta.textContent).toContain('Jun');
    expect(meta.textContent).toContain('15');
  });
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/documents.component.spec.ts"
```

Expected: `Executed 12 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/features/documents/documents.component.spec.ts
git commit -m "test(angular): enhance DocumentsComponent spec with meta display and error strip tests"
```

---

## Group 3 — Ingest Feature

---

### Task 7: Enhance ingest.component.spec.ts (+4 tests)

**Files:**
- Modify: `angular-ui/src/app/features/ingest/ingest.component.spec.ts`

`text/html` is in `ALLOWED_TYPES`. The upload button uses `[disabled]="state === 'uploading'"`. `onFileSelected` reads `event.target.files[0]` — pass a mock event object to test the method directly. `Subject` is already imported in the existing spec.

- [ ] **Step 1: Append 4 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('accepts HTML file type and transitions to fileSelected state', () => {
    const htmlFile = new File(['<html></html>'], 'page.html', { type: 'text/html' });
    (component as any).setFile(htmlFile);
    fixture.detectChanges();
    expect(component.state).toBe('fileSelected');
    expect(fixture.nativeElement.querySelector('.preview-card')).toBeTruthy();
  });

  it('clear() resets errorMessage to an empty string', () => {
    (component as any).setFile(new File([''], 'bad.exe', { type: 'application/x-msdownload' }));
    expect(component.errorMessage).toContain('Unsupported file type');
    component.clear();
    expect(component.errorMessage).toBe('');
  });

  it('upload button is disabled while state is uploading', () => {
    const subject = new Subject<void>();
    ragApiSpy.ingest.and.returnValue(subject.asObservable());
    (component as any).setFile(pdfFile);
    component.upload();
    fixture.detectChanges();
    const uploadBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[color="primary"]');
    expect(uploadBtn.disabled).toBeTrue();
    subject.complete();
  });

  it('onFileSelected calls setFile with the chosen file', () => {
    spyOn(component as any, 'setFile');
    const file = new File([''], 'page.html', { type: 'text/html' });
    const mockEvent = { target: { files: [file], value: '' } } as unknown as Event;
    component.onFileSelected(mockEvent);
    expect((component as any).setFile).toHaveBeenCalledWith(file);
  });
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/ingest.component.spec.ts"
```

Expected: `Executed 13 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/features/ingest/ingest.component.spec.ts
git commit -m "test(angular): enhance IngestComponent spec with HTML type, clear, disabled button, and file input tests"
```

---

## Group 4 — Query Feature (Tasks 8–9 can run in parallel)

---

### Task 8: Enhance chat.service.spec.ts (+2 tests)

**Files:**
- Modify: `angular-ui/src/app/features/query/chat.service.spec.ts`

`RagResponse` is already imported in the existing spec.

- [ ] **Step 1: Append 2 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('messages() starts as an empty array', () => {
    expect(service.messages()).toEqual([]);
  });

  it('addAssistantMessage with empty citations stores citations as an empty array', () => {
    const response: RagResponse = { answer: 'Hello', citations: [], totalTokens: 5 };
    service.addAssistantMessage(response);
    expect(service.messages()[0].citations).toEqual([]);
    expect(service.messages()[0].totalTokens).toBe(5);
  });
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/chat.service.spec.ts"
```

Expected: `Executed 6 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/features/query/chat.service.spec.ts
git commit -m "test(angular): enhance ChatService spec with initial state and empty citations tests"
```

---

### Task 9: Enhance query.component.spec.ts (+4 tests)

**Files:**
- Modify: `angular-ui/src/app/features/query/query.component.spec.ts`

`send()` trims `inputText` and returns early if empty — whitespace-only strings become `""` after trim. `Subject` and `RagResponse` are already imported in the existing spec. The Send button selector is `button[mat-icon-button]`.

- [ ] **Step 1: Append 4 new `it` blocks inside the existing `describe`, after the last existing `it`**

```typescript
  it('send() does nothing when inputText is whitespace-only', fakeAsync(() => {
    component.inputText = '   ';
    component.send();
    tick();
    expect(ragApiSpy.query).not.toHaveBeenCalled();
    expect(chatService.messages().length).toBe(0);
  }));

  it('Send button is disabled while isLoading is true', () => {
    const subject = new Subject<RagResponse>();
    ragApiSpy.query.and.returnValue(subject.asObservable());
    component.inputText = 'test';
    component.send();
    fixture.detectChanges();
    const sendBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-icon-button]');
    expect(sendBtn.disabled).toBeTrue();
    subject.complete();
  });

  it('empty-state element is removed after the first message is sent', fakeAsync(() => {
    component.inputText = 'Hello';
    component.send();
    tick();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty-state')).toBeNull();
  }));

  it('multiple consecutive sends accumulate all messages in the list', fakeAsync(() => {
    component.inputText = 'Question 1';
    component.send();
    tick();
    component.inputText = 'Question 2';
    component.send();
    tick();
    // 2 user messages + 2 assistant responses = 4 total
    expect(chatService.messages().length).toBe(4);
    expect(chatService.messages()[2]).toEqual({ role: 'user', text: 'Question 2' });
  }));
```

- [ ] **Step 2: Run the spec in isolation**

```bash
cd angular-ui && npx ng test --watch=false --include="**/query.component.spec.ts"
```

Expected: `Executed 12 specs, 0 failures.`

- [ ] **Step 3: Commit**

```bash
git add angular-ui/src/app/features/query/query.component.spec.ts
git commit -m "test(angular): enhance QueryComponent spec with whitespace guard, loading disabled, empty state, and accumulation tests"
```

---

## Task 10: Final verification

**Files:** None modified.

- [ ] **Step 1: Run the complete test suite**

```bash
cd angular-ui && npx ng test --watch=false
```

Expected: `Executed 70 specs, 0 failures.`

- [ ] **Step 2: If any test fails, trace it**

Read the failure message carefully:
- `Expected null to be truthy` on a DOM query → the CSS selector doesn't match; inspect the rendered HTML with `console.log(fixture.nativeElement.innerHTML)` added temporarily.
- `Expected spy X to have been called` → the spy wasn't triggered; check the correct method is being spied on.
- `Error: 1 timer(s) still in the queue` → a `setTimeout` wasn't flushed; add `tick(3000)` or `discardPeriodicTasks()` at the end of the `fakeAsync` block.

- [ ] **Step 3: Commit any fixes**

```bash
git add <fixed-file>
git commit -m "test(angular): fix test failures found during full suite run"
```
