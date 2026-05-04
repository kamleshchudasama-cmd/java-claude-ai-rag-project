# Angular RAG UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Angular 18 + Material UI inside `angular-ui/` that lets users ingest documents, query the RAG pipeline in a chat interface, and manage the document library.

**Architecture:** Standalone Angular 18 components with lazy-loaded routes, a single `RagApiService` for all HTTP calls, and two lightweight signal-based services (`ChatService`, `DocumentsService`) for client state. A `WebConfig.java` CORS bean must be added to the Spring Boot app before any browser requests will succeed.

**Tech Stack:** Angular 18, Angular Material 18, Angular Signals, RxJS, Karma + Jasmine, Spring Boot `WebMvcConfigurer` for CORS.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `src/main/java/com/test/rag/config/WebConfig.java` | Create | CORS allow `localhost:4200` |
| `src/test/java/com/test/rag/config/WebConfigTest.java` | Create | Verify CORS headers |
| `angular-ui/` | Scaffold | `ng new` project root |
| `angular-ui/src/environments/environment.ts` | Create | Production env (apiBaseUrl) |
| `angular-ui/src/environments/environment.development.ts` | Create | Dev env (apiBaseUrl) |
| `angular-ui/src/styles.scss` | Modify | Add `.snack-error` class |
| `angular-ui/src/index.html` | Modify | Add Material Icons font link |
| `angular-ui/src/app/core/models.ts` | Create | All shared TS interfaces |
| `angular-ui/src/app/core/rag-api.service.ts` | Create | All HttpClient calls |
| `angular-ui/src/app/core/rag-api.service.spec.ts` | Create | RagApiService tests |
| `angular-ui/src/app/features/query/chat.service.ts` | Create | Signal-based chat history |
| `angular-ui/src/app/features/query/chat.service.spec.ts` | Create | ChatService tests |
| `angular-ui/src/app/features/documents/documents.service.ts` | Create | Signal-based document list |
| `angular-ui/src/app/features/documents/documents.service.spec.ts` | Create | DocumentsService tests |
| `angular-ui/src/app/shared/citation-card/citation-card.component.ts` | Create | Citation expansion panel |
| `angular-ui/src/app/shared/citation-card/citation-card.component.spec.ts` | Create | CitationCard tests |
| `angular-ui/src/app/shared/confirm-dialog/confirm-dialog.component.ts` | Create | Delete confirmation dialog |
| `angular-ui/src/app/app.routes.ts` | Modify | Lazy-loaded routes |
| `angular-ui/src/app/app.config.ts` | Modify | App providers |
| `angular-ui/src/app/app.component.ts` | Modify | Sidenav shell |
| `angular-ui/src/app/features/ingest/ingest.component.ts` | Create | File upload UI |
| `angular-ui/src/app/features/ingest/ingest.component.spec.ts` | Create | Ingest tests |
| `angular-ui/src/app/features/query/query.component.ts` | Create | Chat UI |
| `angular-ui/src/app/features/query/query.component.spec.ts` | Create | Query tests |
| `angular-ui/src/app/features/documents/documents.component.ts` | Create | Document library |
| `angular-ui/src/app/features/documents/documents.component.spec.ts` | Create | Documents tests |

---

## Task 1: Backend CORS Configuration

**Files:**
- Create: `src/main/java/com/test/rag/config/WebConfig.java`
- Create: `src/test/java/com/test/rag/config/WebConfigTest.java`

- [ ] **Step 1: Write the failing CORS test**

```java
// src/test/java/com/test/rag/config/WebConfigTest.java
package com.test.rag.config;

import com.test.rag.service.chunking.ChunkingService;
import com.test.rag.service.context.ContextBuilderService;
import com.test.rag.service.embedding.EmbeddingService;
import com.test.rag.service.generation.GenerationService;
import com.test.rag.service.loader.DocumentLoaderService;
import com.test.rag.service.retrieval.RetrievalService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.test.rag.controller.RagController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagController.class)
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private DocumentLoaderService documentLoaderService;
    @MockBean private ChunkingService chunkingService;
    @MockBean private EmbeddingService embeddingService;
    @MockBean private VectorStoreService vectorStoreService;
    @MockBean private RetrievalService retrievalService;
    @MockBean private ContextBuilderService contextBuilderService;
    @MockBean private GenerationService generationService;

    @Test
    void corsAllowsAngularDevOrigin() throws Exception {
        mockMvc.perform(options("/api/rag/documents")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn test -Dtest=WebConfigTest
```

Expected: `FAILED` — `Access-Control-Allow-Origin` header is missing.

- [ ] **Step 3: Implement WebConfig**

```java
// src/main/java/com/test/rag/config/WebConfig.java
package com.test.rag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn test -Dtest=WebConfigTest
```

Expected: `BUILD SUCCESS` — 1 test, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/test/rag/config/WebConfig.java src/test/java/com/test/rag/config/WebConfigTest.java
git commit -m "feat: add CORS config allowing localhost:4200"
```

---

## Task 2: Scaffold Angular 18 App

**Files:**
- Scaffold: `angular-ui/`
- Modify: `angular-ui/src/index.html`
- Modify: `angular-ui/src/styles.scss`

- [ ] **Step 1: Check Node and Angular CLI versions**

```bash
node --version   # must be >= 18
ng version       # must be >= 18; if missing: npm install -g @angular/cli@18
```

- [ ] **Step 2: Scaffold the project**

Run from the repo root:

```bash
ng new angular-ui --routing --style=scss --skip-git --no-ssr
```

When prompted:
- Which stylesheet format: **SCSS** (already set by flag)

- [ ] **Step 3: Install Angular Material**

```bash
cd angular-ui
ng add @angular/material --skip-confirmation --theme=indigo-pink --typography=false
```

`ng add` updates `angular.json` (theme styles), `app.config.ts` (adds `provideAnimationsAsync()`), and `index.html` (Material Icons font).

Verify `src/index.html` now contains:
```html
<link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
```

If the font link is missing, add it manually inside `<head>`.

- [ ] **Step 4: Add snack-error global style**

Open `angular-ui/src/styles.scss` and append:

```scss
.snack-error {
  --mdc-snackbar-container-color: #c62828;
  --mdc-snackbar-supporting-text-color: #fff;
  .mat-mdc-button { color: #fff !important; }
}
```

- [ ] **Step 5: Verify dev server starts**

```bash
ng serve
```

Open `http://localhost:4200`. You should see the default Angular welcome page. Stop the server (`Ctrl+C`).

- [ ] **Step 6: Commit scaffold**

```bash
cd ..
git add angular-ui
git commit -m "chore: scaffold Angular 18 app with Material"
```

---

## Task 3: Models, Environment Files

**Files:**
- Create: `angular-ui/src/environments/environment.ts`
- Create: `angular-ui/src/environments/environment.development.ts`
- Create: `angular-ui/src/app/core/models.ts`

- [ ] **Step 1: Generate environments**

```bash
cd angular-ui
ng generate environments
```

This creates `src/environments/environment.ts` and `src/environments/environment.development.ts`, and updates `angular.json` with `fileReplacements`.

- [ ] **Step 2: Write environment.ts (production)**

```typescript
// angular-ui/src/environments/environment.ts
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080'
};
```

- [ ] **Step 3: Write environment.development.ts**

```typescript
// angular-ui/src/environments/environment.development.ts
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080'
};
```

- [ ] **Step 4: Create the models file**

Create directory `src/app/core/` and write:

```typescript
// angular-ui/src/app/core/models.ts
export interface Citation {
  ref: number;
  filename: string;
  chunkIndex: number;
  score: number;
  chunkText: string;
}

export interface RagResponse {
  answer: string;
  citations: Citation[];
  totalTokens: number;
}

export interface DocumentSummary {
  filename: string;
  sourceId: string;
  contentType: string;
  author: string;
  createdDate: string;
  uploadedAt: string;
  fileSizeBytes: number;
  chunkCount: number;
  totalTokens: number;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  citations?: Citation[];
  totalTokens?: number;
}
```

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/environments angular-ui/src/app/core/models.ts angular-ui/angular.json
git commit -m "feat(angular-ui): add environment files and shared models"
```

---

## Task 4: RagApiService (TDD)

**Files:**
- Create: `angular-ui/src/app/core/rag-api.service.spec.ts`
- Create: `angular-ui/src/app/core/rag-api.service.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// angular-ui/src/app/core/rag-api.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RagApiService } from './rag-api.service';
import { environment } from '../../environments/environment';

describe('RagApiService', () => {
  let service: RagApiService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RagApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RagApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ingest POSTs multipart/form-data to /api/rag/ingest', () => {
    const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' });
    service.ingest(file).subscribe();
    const req = http.expectOne(`${base}/api/rag/ingest`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush(null);
  });

  it('query POSTs to /api/rag/query with q param', () => {
    service.query('What is RAG?').subscribe();
    const req = http.expectOne(r =>
      r.url === `${base}/api/rag/query` && r.params.get('q') === 'What is RAG?'
    );
    expect(req.request.method).toBe('POST');
    req.flush({ answer: 'RAG stands for...', citations: [], totalTokens: 10 });
  });

  it('listDocuments GETs /api/rag/documents', () => {
    service.listDocuments().subscribe();
    const req = http.expectOne(`${base}/api/rag/documents`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('deleteDocument DELETEs /api/rag/documents/{sourceId}', () => {
    service.deleteDocument('abc123').subscribe();
    const req = http.expectOne(`${base}/api/rag/documents/abc123`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ng test --watch=false --include=src/app/core/rag-api.service.spec.ts
```

Expected: 4 failures — `RagApiService` not found.

- [ ] **Step 3: Implement RagApiService**

```typescript
// angular-ui/src/app/core/rag-api.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RagResponse, DocumentSummary } from './models';

@Injectable({ providedIn: 'root' })
export class RagApiService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  ingest(file: File): Observable<void> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<void>(`${this.base}/api/rag/ingest`, form);
  }

  query(question: string): Observable<RagResponse> {
    return this.http.post<RagResponse>(`${this.base}/api/rag/query`, null, {
      params: { q: question }
    });
  }

  listDocuments(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>(`${this.base}/api/rag/documents`);
  }

  deleteDocument(sourceId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/rag/documents/${sourceId}`);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
ng test --watch=false --include=src/app/core/rag-api.service.spec.ts
```

Expected: `4 specs, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/core/rag-api.service.ts angular-ui/src/app/core/rag-api.service.spec.ts
git commit -m "feat(angular-ui): add RagApiService with tests"
```

---

## Task 5: ChatService (TDD)

**Files:**
- Create: `angular-ui/src/app/features/query/chat.service.spec.ts`
- Create: `angular-ui/src/app/features/query/chat.service.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// angular-ui/src/app/features/query/chat.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { ChatService } from './chat.service';
import { RagResponse } from '../../core/models';

describe('ChatService', () => {
  let service: ChatService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatService);
  });

  it('addUserMessage appends a user message', () => {
    service.addUserMessage('Hello');
    expect(service.messages()).toEqual([{ role: 'user', text: 'Hello' }]);
  });

  it('addAssistantMessage appends the answer with citations and tokens', () => {
    const response: RagResponse = {
      answer: 'RAG stands for Retrieval-Augmented Generation.',
      citations: [{ ref: 1, filename: 'doc.pdf', chunkIndex: 0, score: 0.92, chunkText: 'excerpt' }],
      totalTokens: 50
    };
    service.addAssistantMessage(response);
    const msgs = service.messages();
    expect(msgs.length).toBe(1);
    expect(msgs[0].role).toBe('assistant');
    expect(msgs[0].text).toBe(response.answer);
    expect(msgs[0].citations).toEqual(response.citations);
    expect(msgs[0].totalTokens).toBe(50);
  });

  it('history accumulates across multiple calls', () => {
    service.addUserMessage('Q1');
    service.addAssistantMessage({ answer: 'A1', citations: [], totalTokens: 10 });
    service.addUserMessage('Q2');
    expect(service.messages().length).toBe(3);
    expect(service.messages()[2]).toEqual({ role: 'user', text: 'Q2' });
  });

  it('addErrorMessage appends a fixed error assistant message', () => {
    service.addErrorMessage();
    expect(service.messages()[0]).toEqual({
      role: 'assistant',
      text: 'Something went wrong. Please try again.'
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ng test --watch=false --include=src/app/features/query/chat.service.spec.ts
```

Expected: 4 failures — `ChatService` not found.

- [ ] **Step 3: Implement ChatService**

```typescript
// angular-ui/src/app/features/query/chat.service.ts
import { Injectable, signal } from '@angular/core';
import { ChatMessage, RagResponse } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  readonly messages = signal<ChatMessage[]>([]);

  addUserMessage(text: string): void {
    this.messages.update(msgs => [...msgs, { role: 'user', text }]);
  }

  addAssistantMessage(response: RagResponse): void {
    this.messages.update(msgs => [...msgs, {
      role: 'assistant',
      text: response.answer,
      citations: response.citations,
      totalTokens: response.totalTokens
    }]);
  }

  addErrorMessage(): void {
    this.messages.update(msgs => [...msgs, {
      role: 'assistant',
      text: 'Something went wrong. Please try again.'
    }]);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
ng test --watch=false --include=src/app/features/query/chat.service.spec.ts
```

Expected: `4 specs, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/query/chat.service.ts angular-ui/src/app/features/query/chat.service.spec.ts
git commit -m "feat(angular-ui): add ChatService with signal-based message history"
```

---

## Task 6: DocumentsService (TDD)

**Files:**
- Create: `angular-ui/src/app/features/documents/documents.service.spec.ts`
- Create: `angular-ui/src/app/features/documents/documents.service.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// angular-ui/src/app/features/documents/documents.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { DocumentsService } from './documents.service';
import { RagApiService } from '../../core/rag-api.service';
import { of, throwError } from 'rxjs';
import { DocumentSummary } from '../../core/models';

const mockDoc: DocumentSummary = {
  filename: 'test.pdf', sourceId: 'abc123', contentType: 'application/pdf',
  author: '', createdDate: '', uploadedAt: '2026-05-04',
  fileSizeBytes: 1024, chunkCount: 5, totalTokens: 200
};

describe('DocumentsService', () => {
  let service: DocumentsService;
  let apiSpy: jasmine.SpyObj<RagApiService>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj('RagApiService', ['listDocuments', 'deleteDocument']);
    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    TestBed.configureTestingModule({
      providers: [DocumentsService, { provide: RagApiService, useValue: apiSpy }]
    });
    service = TestBed.inject(DocumentsService);
  });

  it('load populates the documents signal', () => {
    service.load();
    expect(service.documents()).toEqual([mockDoc]);
    expect(service.error()).toBeNull();
  });

  it('load sets error signal on API failure', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');
  });

  it('delete calls deleteDocument then refreshes the list', () => {
    apiSpy.deleteDocument.and.returnValue(of(undefined));
    service.load();
    service.delete('abc123', () => {});
    expect(apiSpy.deleteDocument).toHaveBeenCalledWith('abc123');
    expect(apiSpy.listDocuments).toHaveBeenCalledTimes(2);
  });

  it('delete calls onError callback on API failure', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    const onError = jasmine.createSpy('onError');
    service.delete('abc123', onError);
    expect(onError).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ng test --watch=false --include=src/app/features/documents/documents.service.spec.ts
```

Expected: 4 failures — `DocumentsService` not found.

- [ ] **Step 3: Implement DocumentsService**

```typescript
// angular-ui/src/app/features/documents/documents.service.ts
import { Injectable, inject, signal } from '@angular/core';
import { RagApiService } from '../../core/rag-api.service';
import { DocumentSummary } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private api = inject(RagApiService);
  readonly documents = signal<DocumentSummary[]>([]);
  readonly error = signal<string | null>(null);

  load(): void {
    this.api.listDocuments().subscribe({
      next: docs => {
        this.documents.set(docs);
        this.error.set(null);
      },
      error: () => this.error.set('Failed to load documents.')
    });
  }

  delete(sourceId: string, onError: () => void): void {
    this.api.deleteDocument(sourceId).subscribe({
      next: () => this.load(),
      error: onError
    });
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
ng test --watch=false --include=src/app/features/documents/documents.service.spec.ts
```

Expected: `4 specs, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/documents/documents.service.ts angular-ui/src/app/features/documents/documents.service.spec.ts
git commit -m "feat(angular-ui): add DocumentsService with signal-based document list"
```

---

## Task 7: CitationCardComponent (TDD)

**Files:**
- Create: `angular-ui/src/app/shared/citation-card/citation-card.component.spec.ts`
- Create: `angular-ui/src/app/shared/citation-card/citation-card.component.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// angular-ui/src/app/shared/citation-card/citation-card.component.spec.ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { CitationCardComponent } from './citation-card.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Citation } from '../../core/models';

describe('CitationCardComponent', () => {
  let fixture: ComponentFixture<CitationCardComponent>;

  const citation: Citation = {
    ref: 1, filename: 'report.pdf', chunkIndex: 3, score: 0.924, chunkText: 'Some excerpt text.'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CitationCardComponent, NoopAnimationsModule]
    }).compileComponents();
    fixture = TestBed.createComponent(CitationCardComponent);
    fixture.componentRef.setInput('citation', citation);
    fixture.detectChanges();
  });

  it('renders ref and filename in the panel header', () => {
    const header: HTMLElement = fixture.nativeElement.querySelector('mat-panel-title');
    expect(header.textContent).toContain('[1]');
    expect(header.textContent).toContain('report.pdf');
  });

  it('renders the chunk text in the panel body', () => {
    const body: HTMLElement = fixture.nativeElement.querySelector('.chunk-text');
    expect(body.textContent).toContain('Some excerpt text.');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
ng test --watch=false --include=src/app/shared/citation-card/citation-card.component.spec.ts
```

Expected: 2 failures — `CitationCardComponent` not found.

- [ ] **Step 3: Implement CitationCardComponent**

```typescript
// angular-ui/src/app/shared/citation-card/citation-card.component.ts
import { Component, Input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatExpansionModule } from '@angular/material/expansion';
import { Citation } from '../../core/models';

@Component({
  selector: 'app-citation-card',
  standalone: true,
  imports: [MatExpansionModule, DecimalPipe],
  template: `
    <mat-expansion-panel>
      <mat-expansion-panel-header>
        <mat-panel-title>
          [{{ citation.ref }}] {{ citation.filename }} &middot; {{ citation.score | number:'1.2-2' }}
        </mat-panel-title>
      </mat-expansion-panel-header>
      <p class="chunk-text">{{ citation.chunkText }}</p>
    </mat-expansion-panel>
  `,
  styles: ['.chunk-text { font-size: 0.85rem; color: rgba(0,0,0,0.6); white-space: pre-wrap; }']
})
export class CitationCardComponent {
  @Input({ required: true }) citation!: Citation;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
ng test --watch=false --include=src/app/shared/citation-card/citation-card.component.spec.ts
```

Expected: `2 specs, 0 failures`.

- [ ] **Step 5: Create ConfirmDialogComponent (no separate test — it is a pure UI component)**

```typescript
// angular-ui/src/app/shared/confirm-dialog/confirm-dialog.component.ts
import { Component, Inject } from '@angular/core';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Confirm Delete</h2>
    <mat-dialog-content>Delete "{{ data.filename }}"? This cannot be undone.</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" [mat-dialog-close]="true">Delete</button>
    </mat-dialog-actions>
  `
})
export class ConfirmDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { filename: string },
    public dialogRef: MatDialogRef<ConfirmDialogComponent>
  ) {}
}
```

- [ ] **Step 6: Commit**

```bash
git add angular-ui/src/app/shared
git commit -m "feat(angular-ui): add CitationCardComponent and ConfirmDialogComponent"
```

---

## Task 8: App Shell — Routing and Sidenav

**Files:**
- Modify: `angular-ui/src/app/app.routes.ts`
- Modify: `angular-ui/src/app/app.config.ts`
- Modify: `angular-ui/src/app/app.component.ts`
- Delete: `angular-ui/src/app/app.component.html` (if it exists as a separate file)
- Delete: `angular-ui/src/app/app.component.scss` (if it exists as a separate file)

- [ ] **Step 1: Write app.routes.ts**

```typescript
// angular-ui/src/app/app.routes.ts
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'query', pathMatch: 'full' },
  {
    path: 'query',
    loadComponent: () =>
      import('./features/query/query.component').then(m => m.QueryComponent)
  },
  {
    path: 'ingest',
    loadComponent: () =>
      import('./features/ingest/ingest.component').then(m => m.IngestComponent)
  },
  {
    path: 'documents',
    loadComponent: () =>
      import('./features/documents/documents.component').then(m => m.DocumentsComponent)
  }
];
```

- [ ] **Step 2: Write app.config.ts**

```typescript
// angular-ui/src/app/app.config.ts
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(),
    provideAnimationsAsync()
  ]
};
```

- [ ] **Step 3: Write app.component.ts (inline template)**

Delete `app.component.html` and `app.component.scss` if they exist as separate files, then replace `app.component.ts` entirely:

```typescript
// angular-ui/src/app/app.component.ts
import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule, MatIconModule],
  template: `
    <mat-sidenav-container class="container">
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="sidenav-title">RAG System</div>
        <mat-nav-list>
          <a mat-list-item routerLink="/query" routerLinkActive="active">
            <mat-icon matListItemIcon>chat</mat-icon>
            <span matListItemTitle>Query</span>
          </a>
          <a mat-list-item routerLink="/ingest" routerLinkActive="active">
            <mat-icon matListItemIcon>upload_file</mat-icon>
            <span matListItemTitle>Ingest</span>
          </a>
          <a mat-list-item routerLink="/documents" routerLinkActive="active">
            <mat-icon matListItemIcon>folder</mat-icon>
            <span matListItemTitle>Documents</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="content">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .container { height: 100vh; }
    .sidenav { width: 220px; border-right: 1px solid rgba(0,0,0,0.12); }
    .sidenav-title { padding: 20px 16px 12px; font-size: 1rem; font-weight: 500;
                     border-bottom: 1px solid rgba(0,0,0,0.12); margin-bottom: 8px; }
    .content { padding: 24px; height: 100%; box-sizing: border-box; }
    .active { background: rgba(63,81,181,0.1); }
  `]
})
export class AppComponent {}
```

- [ ] **Step 4: Verify the shell compiles**

```bash
ng serve
```

Open `http://localhost:4200`. You should see the sidenav with three items. Clicking each nav item shows a lazy-load chunk error (components not created yet) — that is expected. Stop the server.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/app.routes.ts angular-ui/src/app/app.config.ts angular-ui/src/app/app.component.ts
git commit -m "feat(angular-ui): add app shell with sidenav and lazy routes"
```

---

## Task 9: IngestComponent (TDD)

**Files:**
- Create: `angular-ui/src/app/features/ingest/ingest.component.spec.ts`
- Create: `angular-ui/src/app/features/ingest/ingest.component.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// angular-ui/src/app/features/ingest/ingest.component.spec.ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { IngestComponent } from './ingest.component';
import { RagApiService } from '../../core/rag-api.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

describe('IngestComponent', () => {
  let fixture: ComponentFixture<IngestComponent>;
  let component: IngestComponent;
  let apiSpy: jasmine.SpyObj<RagApiService>;
  let snackSpy: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('RagApiService', ['ingest']);
    snackSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [IngestComponent, NoopAnimationsModule],
      providers: [
        { provide: RagApiService, useValue: apiSpy },
        { provide: MatSnackBar, useValue: snackSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(IngestComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders without error', () => {
    expect(fixture.nativeElement).toBeTruthy();
  });

  it('upload calls api.ingest with the selected file', () => {
    apiSpy.ingest.and.returnValue(of(undefined));
    component.selectedFile = new File(['x'], 'test.pdf', { type: 'application/pdf' });
    component.upload();
    expect(apiSpy.ingest).toHaveBeenCalledWith(component.selectedFile as File);
  });

  it('upload shows success snackbar and clears file on success', () => {
    apiSpy.ingest.and.returnValue(of(undefined));
    component.selectedFile = new File(['x'], 'test.pdf');
    component.upload();
    expect(snackSpy.open).toHaveBeenCalledWith('Document ingested successfully!', 'Close', jasmine.any(Object));
    expect(component.selectedFile).toBeNull();
  });

  it('upload shows error snackbar and re-enables button on failure', () => {
    apiSpy.ingest.and.returnValue(throwError(() => new Error('500')));
    component.selectedFile = new File(['x'], 'test.pdf');
    component.upload();
    expect(snackSpy.open).toHaveBeenCalledWith(jasmine.any(String), 'Dismiss', jasmine.any(Object));
    expect(component.loading()).toBeFalse();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ng test --watch=false --include=src/app/features/ingest/ingest.component.spec.ts
```

Expected: 4 failures — `IngestComponent` not found.

- [ ] **Step 3: Implement IngestComponent**

```typescript
// angular-ui/src/app/features/ingest/ingest.component.ts
import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RagApiService } from '../../core/rag-api.service';
import { catchError, EMPTY } from 'rxjs';

@Component({
  selector: 'app-ingest',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatProgressBarModule, MatIconModule],
  template: `
    <mat-card class="ingest-card">
      <mat-card-header>
        <mat-card-title>Ingest Document</mat-card-title>
        <mat-card-subtitle>PDF, DOCX, HTML &middot; max 50 MB</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <div class="drop-zone" [class.dragover]="isDragOver"
             (dragover)="onDragOver($event)" (dragleave)="isDragOver = false"
             (drop)="onDrop($event)" (click)="fileInput.click()">
          <mat-icon>cloud_upload</mat-icon>
          @if (selectedFile) {
            <p>{{ selectedFile.name }} ({{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB)</p>
          } @else {
            <p>Drag &amp; drop a file here, or click to browse</p>
          }
        </div>
        <input #fileInput type="file" accept=".pdf,.docx,.html" hidden
               (change)="onFileSelected($event)">
        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }
      </mat-card-content>
      <mat-card-actions>
        <button mat-raised-button color="primary"
                [disabled]="!selectedFile || loading()" (click)="upload()">
          Upload
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    .ingest-card { max-width: 560px; margin: 40px auto; }
    .drop-zone {
      border: 2px dashed rgba(0,0,0,0.3); border-radius: 8px; padding: 48px;
      text-align: center; cursor: pointer; transition: background 0.2s;
    }
    .drop-zone:hover, .drop-zone.dragover { background: rgba(0,0,0,0.04); }
    .drop-zone mat-icon { font-size: 48px; height: 48px; width: 48px; color: rgba(0,0,0,0.4); }
    mat-progress-bar { margin-top: 16px; }
  `]
})
export class IngestComponent {
  private api = inject(RagApiService);
  private snackBar = inject(MatSnackBar);

  selectedFile: File | null = null;
  loading = signal(false);
  isDragOver = false;

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    const file = event.dataTransfer?.files[0] ?? null;
    if (file) this.selectedFile = file;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  upload(): void {
    if (!this.selectedFile) return;
    this.loading.set(true);
    this.api.ingest(this.selectedFile).pipe(
      catchError(err => {
        const msg = err?.error?.message ?? 'Upload failed. Please try again.';
        this.snackBar.open(msg, 'Dismiss', { duration: 5000, panelClass: 'snack-error' });
        this.loading.set(false);
        return EMPTY;
      })
    ).subscribe(() => {
      this.snackBar.open('Document ingested successfully!', 'Close', { duration: 3000 });
      this.selectedFile = null;
      this.loading.set(false);
    });
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
ng test --watch=false --include=src/app/features/ingest/ingest.component.spec.ts
```

Expected: `4 specs, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/ingest
git commit -m "feat(angular-ui): add IngestComponent with drag-and-drop upload"
```

---

## Task 10: QueryComponent (TDD)

**Files:**
- Create: `angular-ui/src/app/features/query/query.component.spec.ts`
- Create: `angular-ui/src/app/features/query/query.component.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// angular-ui/src/app/features/query/query.component.spec.ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { QueryComponent } from './query.component';
import { RagApiService } from '../../core/rag-api.service';
import { ChatService } from './chat.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { RagResponse } from '../../core/models';

describe('QueryComponent', () => {
  let fixture: ComponentFixture<QueryComponent>;
  let component: QueryComponent;
  let apiSpy: jasmine.SpyObj<RagApiService>;
  let chatService: ChatService;

  const mockResponse: RagResponse = { answer: 'RAG answer', citations: [], totalTokens: 20 };

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('RagApiService', ['query']);

    await TestBed.configureTestingModule({
      imports: [QueryComponent, NoopAnimationsModule],
      providers: [
        { provide: RagApiService, useValue: apiSpy },
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(QueryComponent);
    component = fixture.componentInstance;
    chatService = TestBed.inject(ChatService);
    fixture.detectChanges();
  });

  it('renders without error', () => {
    expect(fixture.nativeElement).toBeTruthy();
  });

  it('submit adds user message and calls api.query', () => {
    apiSpy.query.and.returnValue(of(mockResponse));
    component.question = 'What is RAG?';
    component.submit();
    expect(apiSpy.query).toHaveBeenCalledWith('What is RAG?');
    expect(chatService.messages()[0]).toEqual({ role: 'user', text: 'What is RAG?' });
  });

  it('submit clears the input field after sending', () => {
    apiSpy.query.and.returnValue(of(mockResponse));
    component.question = 'What is RAG?';
    component.submit();
    expect(component.question).toBe('');
  });

  it('loading signal is true while query is in flight', () => {
    const subject = new Subject<RagResponse>();
    apiSpy.query.and.returnValue(subject.asObservable());
    component.question = 'Q';
    component.submit();
    expect(component.loading()).toBeTrue();
    subject.complete();
  });

  it('submit adds error message and re-enables input on API failure', () => {
    apiSpy.query.and.returnValue(throwError(() => new Error('500')));
    component.question = 'Q';
    component.submit();
    const msgs = chatService.messages();
    expect(msgs[msgs.length - 1].text).toBe('Something went wrong. Please try again.');
    expect(component.loading()).toBeFalse();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ng test --watch=false --include=src/app/features/query/query.component.spec.ts
```

Expected: 5 failures — `QueryComponent` not found.

- [ ] **Step 3: Implement QueryComponent**

```typescript
// angular-ui/src/app/features/query/query.component.ts
import { Component, inject, signal, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { ChatService } from './chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { CitationCardComponent } from '../../shared/citation-card/citation-card.component';
import { catchError, EMPTY } from 'rxjs';

@Component({
  selector: 'app-query',
  standalone: true,
  imports: [
    FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatCardModule, CitationCardComponent
  ],
  template: `
    <div class="chat-container">
      <div class="messages" #messagesEl>
        @for (msg of chat.messages(); track $index) {
          <div class="message" [class.user]="msg.role === 'user'"
               [class.assistant]="msg.role === 'assistant'">
            <mat-card>
              <mat-card-content>
                <p class="message-text">{{ msg.text }}</p>
                @if (msg.citations?.length) {
                  <div class="citations">
                    @for (c of msg.citations!; track c.ref) {
                      <app-citation-card [citation]="c" />
                    }
                  </div>
                }
              </mat-card-content>
            </mat-card>
          </div>
        }
        @if (chat.messages().length === 0) {
          <p class="empty-hint">Ask a question about your ingested documents.</p>
        }
      </div>

      <div class="input-row">
        <mat-form-field class="question-field" appearance="outline">
          <mat-label>Ask a question</mat-label>
          <input matInput [(ngModel)]="question" [disabled]="loading()"
                 (keydown.enter)="submit()"
                 placeholder="What does this document say about...">
        </mat-form-field>
        @if (loading()) {
          <mat-spinner diameter="36" />
        } @else {
          <button mat-icon-button color="primary"
                  (click)="submit()" [disabled]="!question.trim()">
            <mat-icon>send</mat-icon>
          </button>
        }
      </div>
    </div>
  `,
  styles: [`
    .chat-container { display: flex; flex-direction: column; height: calc(100vh - 96px); }
    .messages { flex: 1; overflow-y: auto; padding: 8px 0; display: flex;
                flex-direction: column; gap: 12px; }
    .message { max-width: 80%; }
    .message.user { align-self: flex-end; }
    .message.assistant { align-self: flex-start; }
    .message-text { margin: 0; white-space: pre-wrap; }
    .citations { margin-top: 12px; display: flex; flex-direction: column; gap: 4px; }
    .empty-hint { color: rgba(0,0,0,0.4); text-align: center; margin-top: 40px; }
    .input-row { display: flex; align-items: center; gap: 8px; padding-top: 8px;
                 border-top: 1px solid rgba(0,0,0,0.12); }
    .question-field { flex: 1; }
  `]
})
export class QueryComponent implements AfterViewChecked {
  @ViewChild('messagesEl') private messagesEl!: ElementRef<HTMLDivElement>;

  protected chat = inject(ChatService);
  private api = inject(RagApiService);

  question = '';
  loading = signal(false);

  ngAfterViewChecked(): void {
    const el = this.messagesEl?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  submit(): void {
    const q = this.question.trim();
    if (!q || this.loading()) return;
    this.chat.addUserMessage(q);
    this.question = '';
    this.loading.set(true);
    this.api.query(q).pipe(
      catchError(() => {
        this.chat.addErrorMessage();
        this.loading.set(false);
        return EMPTY;
      })
    ).subscribe(response => {
      this.chat.addAssistantMessage(response);
      this.loading.set(false);
    });
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
ng test --watch=false --include=src/app/features/query/query.component.spec.ts
```

Expected: `5 specs, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add angular-ui/src/app/features/query/query.component.ts angular-ui/src/app/features/query/query.component.spec.ts
git commit -m "feat(angular-ui): add QueryComponent with chat interface"
```

---

## Task 11: DocumentsComponent (TDD)

**Files:**
- Create: `angular-ui/src/app/features/documents/documents.component.spec.ts`
- Create: `angular-ui/src/app/features/documents/documents.component.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// angular-ui/src/app/features/documents/documents.component.spec.ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DocumentsComponent } from './documents.component';
import { DocumentsService } from './documents.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { DocumentSummary } from '../../core/models';
import { of } from 'rxjs';

const mockDoc: DocumentSummary = {
  filename: 'report.pdf', sourceId: 'abc', contentType: 'application/pdf',
  author: '', createdDate: '', uploadedAt: '2026-05-04',
  fileSizeBytes: 2048, chunkCount: 3, totalTokens: 100
};

describe('DocumentsComponent', () => {
  let fixture: ComponentFixture<DocumentsComponent>;
  let docsSpy: jasmine.SpyObj<DocumentsService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackSpy: jasmine.SpyObj<MatSnackBar>;

  function createService(docs: DocumentSummary[], err: string | null) {
    return jasmine.createSpyObj<DocumentsService>('DocumentsService', ['load', 'delete'], {
      documents: signal(docs),
      error: signal(err)
    });
  }

  beforeEach(async () => {
    docsSpy = createService([], null);
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    snackSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [DocumentsComponent, NoopAnimationsModule],
      providers: [
        { provide: DocumentsService, useValue: docsSpy },
        { provide: MatDialog, useValue: dialogSpy },
        { provide: MatSnackBar, useValue: snackSpy },
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentsComponent);
    fixture.detectChanges();
  });

  it('renders without error', () => {
    expect(fixture.nativeElement).toBeTruthy();
  });

  it('calls docsService.load on init', () => {
    expect(docsSpy.load).toHaveBeenCalled();
  });

  it('shows empty state when documents signal is empty', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('No documents ingested yet');
  });

  it('shows error banner when error signal has a value', async () => {
    docsSpy = createService([], 'Failed to load documents.');
    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [DocumentsComponent, NoopAnimationsModule],
      providers: [
        { provide: DocumentsService, useValue: docsSpy },
        { provide: MatDialog, useValue: dialogSpy },
        { provide: MatSnackBar, useValue: snackSpy },
        provideRouter([])
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(DocumentsComponent);
    fixture.detectChanges();
    const banner: HTMLElement = fixture.nativeElement.querySelector('.error-banner');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Failed to load documents.');
  });

  it('confirmDelete opens MatDialog and calls delete on confirm', () => {
    const dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    dialogRefSpy.afterClosed.and.returnValue(of(true));
    dialogSpy.open.and.returnValue(dialogRefSpy);

    fixture.componentInstance.confirmDelete(mockDoc);

    expect(dialogSpy.open).toHaveBeenCalled();
    expect(docsSpy.delete).toHaveBeenCalledWith('abc', jasmine.any(Function));
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ng test --watch=false --include=src/app/features/documents/documents.component.spec.ts
```

Expected: 5 failures — `DocumentsComponent` not found.

- [ ] **Step 3: Implement DocumentsComponent**

```typescript
// angular-ui/src/app/features/documents/documents.component.ts
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DocumentsService } from './documents.service';
import { DocumentSummary } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule],
  template: `
    @if (docsService.error()) {
      <p class="error-banner">{{ docsService.error() }}</p>
    }

    @if (docsService.documents().length === 0 && !docsService.error()) {
      <p class="empty-state">
        No documents ingested yet.
        <a routerLink="/ingest">Ingest one now.</a>
      </p>
    } @else {
      <mat-table [dataSource]="docsService.documents()" class="docs-table">
        <ng-container matColumnDef="filename">
          <mat-header-cell *matHeaderCellDef>Filename</mat-header-cell>
          <mat-cell *matCellDef="let doc">{{ doc.filename }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="contentType">
          <mat-header-cell *matHeaderCellDef>Type</mat-header-cell>
          <mat-cell *matCellDef="let doc">{{ doc.contentType }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="fileSizeBytes">
          <mat-header-cell *matHeaderCellDef>Size</mat-header-cell>
          <mat-cell *matCellDef="let doc">{{ (doc.fileSizeBytes / 1024).toFixed(1) }} KB</mat-cell>
        </ng-container>
        <ng-container matColumnDef="chunkCount">
          <mat-header-cell *matHeaderCellDef>Chunks</mat-header-cell>
          <mat-cell *matCellDef="let doc">{{ doc.chunkCount }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="totalTokens">
          <mat-header-cell *matHeaderCellDef>Tokens</mat-header-cell>
          <mat-cell *matCellDef="let doc">{{ doc.totalTokens }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="uploadedAt">
          <mat-header-cell *matHeaderCellDef>Uploaded</mat-header-cell>
          <mat-cell *matCellDef="let doc">{{ doc.uploadedAt }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="actions">
          <mat-header-cell *matHeaderCellDef></mat-header-cell>
          <mat-cell *matCellDef="let doc">
            <button mat-icon-button color="warn" (click)="confirmDelete(doc)" aria-label="Delete">
              <mat-icon>delete</mat-icon>
            </button>
          </mat-cell>
        </ng-container>

        <mat-header-row *matHeaderRowDef="displayedColumns" />
        <mat-row *matRowDef="let row; columns: displayedColumns;" />
      </mat-table>
    }
  `,
  styles: [`
    .error-banner { color: #c62828; margin-bottom: 16px; }
    .empty-state { color: rgba(0,0,0,0.54); }
    .docs-table { width: 100%; }
  `]
})
export class DocumentsComponent implements OnInit {
  protected docsService = inject(DocumentsService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  readonly displayedColumns = ['filename', 'contentType', 'fileSizeBytes', 'chunkCount', 'totalTokens', 'uploadedAt', 'actions'];

  ngOnInit(): void {
    this.docsService.load();
  }

  confirmDelete(doc: DocumentSummary): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { filename: doc.filename },
      width: '360px'
    });
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;
      this.docsService.delete(doc.sourceId, () => {
        this.snackBar.open('Delete failed. Please try again.', 'Dismiss', {
          duration: 5000,
          panelClass: 'snack-error'
        });
      });
    });
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
ng test --watch=false --include=src/app/features/documents/documents.component.spec.ts
```

Expected: `5 specs, 0 failures`.

- [ ] **Step 5: Run the full Angular test suite**

```bash
ng test --watch=false
```

Expected: All specs pass, 0 failures.

- [ ] **Step 6: Verify the full UI works end-to-end**

Start both servers:
```bash
# Terminal 1 — Spring Boot (from repo root)
mvn spring-boot:run

# Terminal 2 — Angular dev server (from angular-ui/)
ng serve
```

Open `http://localhost:4200`. Verify:
1. Sidenav shows Query, Ingest, Documents
2. `/ingest` — drag a PDF and click Upload, check for success toast
3. `/documents` — uploaded file appears in the table
4. `/query` — ask a question and see a chat response with citations

- [ ] **Step 7: Commit**

```bash
git add angular-ui/src/app/features/documents
git commit -m "feat(angular-ui): add DocumentsComponent with table and delete confirmation"
```
