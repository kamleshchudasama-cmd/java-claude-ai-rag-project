# Angular UI Feature Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three missing standalone Angular 18 feature components — `QueryComponent`, `IngestComponent`, and `DocumentsComponent` — that complete the RAG system's Angular UI.

**Architecture:** Each component is self-contained (Approach A): inline template, inline styles, constructor injection, all state as plain component fields. Services (`ChatService`, `DocumentsService`, `RagApiService`) are already implemented and injected; components only wire them to the DOM. No sub-components are created beyond what already exists (`CitationCardComponent`, `ConfirmDialogComponent`).

**Tech Stack:** Angular 18, Angular Material 18, Jasmine + Karma, `@angular/forms` (FormsModule for ngModel), RxJS `of`/`throwError`/`Subject` in tests.

---

## File Structure

| Action | Path | Responsibility |
|---|---|---|
| CREATE | `angular-ui/src/app/features/query/query.component.ts` | Chat UI — message list, input bar, send logic |
| CREATE | `angular-ui/src/app/features/query/query.component.spec.ts` | QueryComponent tests |
| CREATE | `angular-ui/src/app/features/ingest/ingest.component.ts` | File upload — drop zone, preview card, state machine |
| CREATE | `angular-ui/src/app/features/ingest/ingest.component.spec.ts` | IngestComponent tests |
| CREATE | `angular-ui/src/app/features/documents/documents.component.ts` | Document card list with delete flow |
| CREATE | `angular-ui/src/app/features/documents/documents.component.spec.ts` | DocumentsComponent tests |

No existing files need modification — routes are already wired in `app.routes.ts`.

---

## Task 1: QueryComponent

**Files:**
- Create: `angular-ui/src/app/features/query/query.component.ts`
- Create: `angular-ui/src/app/features/query/query.component.spec.ts`

- [ ] **Step 1: Write the failing spec**

Create `angular-ui/src/app/features/query/query.component.spec.ts`:

```typescript
import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { QueryComponent } from './query.component';
import { ChatService } from './chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError, Subject } from 'rxjs';
import { RagResponse } from '../../core/models';

const mockResponse: RagResponse = {
  answer: 'RAG is Retrieval-Augmented Generation.',
  citations: [{ ref: 1, filename: 'doc.pdf', chunkIndex: 0, score: 0.92, chunkText: 'excerpt' }],
  totalTokens: 42
};

describe('QueryComponent', () => {
  let fixture: ComponentFixture<QueryComponent>;
  let component: QueryComponent;
  let chatService: ChatService;
  let ragApiSpy: jasmine.SpyObj<RagApiService>;

  beforeEach(async () => {
    ragApiSpy = jasmine.createSpyObj('RagApiService', ['query']);
    ragApiSpy.query.and.returnValue(of(mockResponse));

    await TestBed.configureTestingModule({
      imports: [QueryComponent, NoopAnimationsModule],
      providers: [
        ChatService,
        { provide: RagApiService, useValue: ragApiSpy }
      ]
    }).compileComponents();

    chatService = TestBed.inject(ChatService);
    fixture = TestBed.createComponent(QueryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows empty-state text when message list is empty', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.empty-state');
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('Ask a question to get started');
  });

  it('Send button is disabled when input is empty', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-icon-button]');
    expect(btn.disabled).toBeTrue();
  });

  it('clears inputText after send', fakeAsync(() => {
    component.inputText = 'test question';
    component.send();
    tick();
    expect(component.inputText).toBe('');
  }));

  it('renders user bubble with the sent text', fakeAsync(() => {
    component.inputText = 'What is RAG?';
    component.send();
    tick();
    fixture.detectChanges();
    const bubble: HTMLElement = fixture.nativeElement.querySelector('.user-bubble');
    expect(bubble).toBeTruthy();
    expect(bubble.textContent?.trim()).toBe('What is RAG?');
  }));

  it('renders assistant card with a citation-card after successful response', fakeAsync(() => {
    component.inputText = 'What is RAG?';
    component.send();
    tick();
    fixture.detectChanges();
    const card: HTMLElement = fixture.nativeElement.querySelector('.assistant-card');
    expect(card).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-citation-card')).toBeTruthy();
  }));

  it('calls ragApi.query with the trimmed input text', fakeAsync(() => {
    component.inputText = '  What is RAG?  ';
    component.send();
    tick();
    expect(ragApiSpy.query).toHaveBeenCalledWith('What is RAG?');
  }));

  it('sets isLoading true while request is in flight', () => {
    const subject = new Subject<RagResponse>();
    ragApiSpy.query.and.returnValue(subject.asObservable());
    component.inputText = 'test';
    component.send();
    expect(component.isLoading).toBeTrue();
    subject.complete();
  });

  it('calls addErrorMessage and clears isLoading on API error', fakeAsync(() => {
    ragApiSpy.query.and.returnValue(throwError(() => new Error('Network error')));
    const errorSpy = spyOn(chatService, 'addErrorMessage').and.callThrough();
    component.inputText = 'test';
    component.send();
    tick();
    expect(errorSpy).toHaveBeenCalled();
    expect(component.isLoading).toBeFalse();
  }));
});
```

- [ ] **Step 2: Run the spec to confirm it fails with a missing module error**

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -20
```

Expected: compilation error — `Cannot find module './query.component'`

- [ ] **Step 3: Implement QueryComponent**

Create `angular-ui/src/app/features/query/query.component.ts`:

```typescript
import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ChatService } from './chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { CitationCardComponent } from '../../shared/citation-card/citation-card.component';

@Component({
  selector: 'app-query',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CitationCardComponent
  ],
  template: `
    <div class="chat-container">
      <div class="message-list" #messageList>
        @if (chatService.messages().length === 0) {
          <p class="empty-state">Ask a question to get started</p>
        }
        @for (msg of chatService.messages(); track $index) {
          @if (msg.role === 'user') {
            <div class="message-row user">
              <div class="bubble user-bubble">{{ msg.text }}</div>
            </div>
          } @else {
            <div class="message-row assistant">
              <div class="assistant-card">
                <p class="answer-text">{{ msg.text }}</p>
                @for (citation of (msg.citations ?? []); track citation.ref) {
                  <app-citation-card [citation]="citation" />
                }
              </div>
            </div>
          }
        }
      </div>
      <div class="input-bar">
        <mat-form-field appearance="outline" class="input-field">
          <input matInput
            [(ngModel)]="inputText"
            placeholder="Ask a question…"
            [disabled]="isLoading"
            (keydown.enter)="send()" />
        </mat-form-field>
        <button mat-icon-button color="primary"
          [disabled]="isLoading || !inputText.trim()"
          (click)="send()">
          @if (isLoading) {
            <mat-spinner diameter="20" />
          } @else {
            <mat-icon>send</mat-icon>
          }
        </button>
      </div>
    </div>
  `,
  styles: [`
    .chat-container { display: flex; flex-direction: column; height: 100%; }
    .message-list { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
    .empty-state { text-align: center; color: rgba(0,0,0,0.4); margin-top: 80px; }
    .message-row { display: flex; }
    .message-row.user { justify-content: flex-end; }
    .message-row.assistant { justify-content: flex-start; }
    .bubble { padding: 8px 14px; max-width: 70%; }
    .user-bubble { background: #3f51b5; color: #fff; border-radius: 18px 18px 2px 18px; }
    .assistant-card { background: #fff; border: 1px solid rgba(0,0,0,0.12); border-radius: 2px 12px 12px 12px; padding: 12px; max-width: 80%; }
    .answer-text { margin: 0 0 8px; }
    .input-bar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; border-top: 1px solid rgba(0,0,0,0.12); }
    .input-field { flex: 1; }
  `]
})
export class QueryComponent {
  protected chatService = inject(ChatService);
  private ragApi = inject(RagApiService);

  @ViewChild('messageList') private messageList!: ElementRef<HTMLElement>;

  isLoading = false;
  inputText = '';

  send(): void {
    const text = this.inputText.trim();
    if (!text || this.isLoading) return;
    this.inputText = '';
    this.isLoading = true;
    this.chatService.addUserMessage(text);
    this.ragApi.query(text).subscribe({
      next: response => {
        this.chatService.addAssistantMessage(response);
        this.isLoading = false;
        setTimeout(() => this.scrollToBottom());
      },
      error: () => {
        this.chatService.addErrorMessage();
        this.isLoading = false;
      }
    });
  }

  private scrollToBottom(): void {
    const el = this.messageList?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }
}
```

- [ ] **Step 4: Run the spec and confirm all 8 tests pass**

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -30
```

Expected: `QueryComponent: 8 specs, 0 failures`

- [ ] **Step 5: Commit**

```bash
cd angular-ui && cd .. && git add angular-ui/src/app/features/query/query.component.ts angular-ui/src/app/features/query/query.component.spec.ts && git commit -m "feat(angular-ui): add QueryComponent with inline-citation chat layout"
```

---

## Task 2: IngestComponent

**Files:**
- Create: `angular-ui/src/app/features/ingest/ingest.component.ts`
- Create: `angular-ui/src/app/features/ingest/ingest.component.spec.ts`

- [ ] **Step 1: Write the failing spec**

Create `angular-ui/src/app/features/ingest/ingest.component.spec.ts`:

```typescript
import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { IngestComponent } from './ingest.component';
import { RagApiService } from '../../core/rag-api.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError, Subject } from 'rxjs';

describe('IngestComponent', () => {
  let fixture: ComponentFixture<IngestComponent>;
  let component: IngestComponent;
  let ragApiSpy: jasmine.SpyObj<RagApiService>;

  const pdfFile = new File(['content'], 'doc.pdf', { type: 'application/pdf' });
  const docxFile = new File(['content'], 'doc.docx', {
    type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  });

  beforeEach(async () => {
    ragApiSpy = jasmine.createSpyObj('RagApiService', ['ingest']);
    ragApiSpy.ingest.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [IngestComponent, NoopAnimationsModule],
      providers: [{ provide: RagApiService, useValue: ragApiSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(IngestComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows drop zone and no preview card in idle state', () => {
    expect(fixture.nativeElement.querySelector('.drop-zone')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.preview-card')).toBeNull();
  });

  it('rejects files with unsupported type and stays in idle', () => {
    const txtFile = new File(['content'], 'doc.txt', { type: 'text/plain' });
    (component as any).setFile(txtFile);
    expect(component.state).toBe('idle');
    expect(component.errorMessage).toContain('Unsupported file type');
  });

  it('rejects files exceeding 50 MB and stays in idle', () => {
    const bigFile = new File([new ArrayBuffer(51 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' });
    (component as any).setFile(bigFile);
    expect(component.state).toBe('idle');
    expect(component.errorMessage).toContain('50 MB');
  });

  it('shows preview card with filename after valid PDF is set', () => {
    (component as any).setFile(pdfFile);
    fixture.detectChanges();
    const preview: HTMLElement = fixture.nativeElement.querySelector('.preview-card');
    expect(preview).toBeTruthy();
    expect(preview.textContent).toContain('doc.pdf');
  });

  it('shows preview card after valid DOCX is set', () => {
    (component as any).setFile(docxFile);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.preview-card')).toBeTruthy();
  });

  it('sets state to uploading during upload', () => {
    const subject = new Subject<void>();
    ragApiSpy.ingest.and.returnValue(subject.asObservable());
    (component as any).setFile(pdfFile);
    component.upload();
    expect(component.state).toBe('uploading');
    subject.complete();
  });

  it('resets to idle and shows success message after upload succeeds', fakeAsync(() => {
    (component as any).setFile(pdfFile);
    component.upload();
    tick();
    expect(component.state).toBe('idle');
    expect(component.successMessage).toBe('Uploaded successfully');
    tick(3000);
    expect(component.successMessage).toBe('');
  }));

  it('stays in fileSelected and shows error message after upload fails', fakeAsync(() => {
    ragApiSpy.ingest.and.returnValue(throwError(() => new Error('Server error')));
    (component as any).setFile(pdfFile);
    component.upload();
    tick();
    expect(component.state).toBe('fileSelected');
    expect(component.errorMessage).toContain('Upload failed');
  }));

  it('clear() resets state to idle and clears selectedFile', () => {
    (component as any).setFile(pdfFile);
    component.clear();
    expect(component.state).toBe('idle');
    expect(component.selectedFile).toBeNull();
  });
});
```

- [ ] **Step 2: Run the spec to confirm it fails with a missing module error**

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -20
```

Expected: compilation error — `Cannot find module './ingest.component'`

- [ ] **Step 3: Implement IngestComponent**

Create `angular-ui/src/app/features/ingest/ingest.component.ts`:

```typescript
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RagApiService } from '../../core/rag-api.service';

type UploadState = 'idle' | 'fileSelected' | 'uploading';

const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/html'
];
const MAX_BYTES = 50 * 1024 * 1024;

@Component({
  selector: 'app-ingest',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="page-center">
      <div class="upload-card">
        <h2>Ingest Document</h2>

        <div class="drop-zone"
          [class.drag-over]="isDragOver"
          [class.dimmed]="state !== 'idle'"
          [class.no-pointer]="state === 'uploading'"
          (dragover)="onDragOver($event)"
          (dragleave)="onDragLeave()"
          (drop)="onDrop($event)"
          (click)="state === 'idle' && fileInput.click()">
          <mat-icon class="drop-icon">upload_file</mat-icon>
          <p class="drop-hint">
            Drop file here or
            <span class="browse-link" (click)="$event.stopPropagation(); fileInput.click()">browse</span>
          </p>
          <p class="drop-types">PDF · DOCX · HTML · up to 50 MB</p>
        </div>

        <input #fileInput type="file" accept=".pdf,.docx,.html" style="display:none"
          (change)="onFileSelected($event)" />

        @if (state === 'fileSelected' || state === 'uploading') {
          <div class="preview-card">
            <mat-icon class="file-icon">{{ iconFor(selectedFile!.type) }}</mat-icon>
            <div class="file-info">
              <div class="file-name">{{ selectedFile!.name }}</div>
              <div class="file-meta">{{ formatBytes(selectedFile!.size) }} · {{ typeBadge(selectedFile!.type) }}</div>
            </div>
          </div>
          <div class="actions">
            <button mat-raised-button color="primary"
              [disabled]="state === 'uploading'"
              (click)="upload()">
              @if (state === 'uploading') {
                <mat-spinner diameter="20" />
              } @else {
                Upload
              }
            </button>
            <button mat-button [disabled]="state === 'uploading'" (click)="clear()">Clear</button>
          </div>
        }

        @if (errorMessage) {
          <p class="status-error">{{ errorMessage }}</p>
        }
        @if (successMessage) {
          <p class="status-success">{{ successMessage }}</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .page-center { display: flex; justify-content: center; padding: 48px 16px; }
    .upload-card { width: 100%; max-width: 560px; }
    h2 { margin: 0 0 24px; }
    .drop-zone { border: 2px dashed rgba(0,0,0,0.25); border-radius: 8px; padding: 40px; text-align: center; cursor: pointer; transition: background 0.2s, border-color 0.2s; }
    .drop-zone:hover, .drop-zone.drag-over { background: rgba(63,81,181,0.05); border-color: #3f51b5; }
    .drop-zone.dimmed { opacity: 0.4; }
    .drop-zone.no-pointer { pointer-events: none; }
    .drop-icon { font-size: 40px; width: 40px; height: 40px; color: rgba(0,0,0,0.4); }
    .drop-hint { margin: 8px 0 4px; }
    .browse-link { color: #3f51b5; text-decoration: underline; cursor: pointer; }
    .drop-types { font-size: 0.8rem; color: rgba(0,0,0,0.4); margin: 0; }
    .preview-card { display: flex; align-items: center; gap: 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 6px; padding: 12px 16px; margin-top: 16px; }
    .file-icon { color: #3f51b5; }
    .file-name { font-weight: 500; }
    .file-meta { font-size: 0.85rem; color: rgba(0,0,0,0.54); }
    .actions { display: flex; gap: 8px; margin-top: 12px; }
    .status-error { color: #f44336; margin-top: 8px; }
    .status-success { color: #4caf50; margin-top: 8px; }
  `]
})
export class IngestComponent {
  private ragApi = inject(RagApiService);

  state: UploadState = 'idle';
  isDragOver = false;
  selectedFile: File | null = null;
  errorMessage = '';
  successMessage = '';

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(): void {
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    const file = event.dataTransfer?.files[0];
    if (file) this.setFile(file);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.setFile(file);
    input.value = '';
  }

  upload(): void {
    if (!this.selectedFile || this.state !== 'fileSelected') return;
    this.state = 'uploading';
    this.errorMessage = '';
    this.ragApi.ingest(this.selectedFile).subscribe({
      next: () => {
        this.state = 'idle';
        this.selectedFile = null;
        this.successMessage = 'Uploaded successfully';
        setTimeout(() => { this.successMessage = ''; }, 3000);
      },
      error: () => {
        this.state = 'fileSelected';
        this.errorMessage = 'Upload failed. Please try again.';
      }
    });
  }

  clear(): void {
    this.state = 'idle';
    this.selectedFile = null;
    this.errorMessage = '';
  }

  private setFile(file: File): void {
    if (!ALLOWED_TYPES.includes(file.type)) {
      this.errorMessage = `Unsupported file type: "${file.type || 'unknown'}". Use PDF, DOCX, or HTML.`;
      return;
    }
    if (file.size > MAX_BYTES) {
      this.errorMessage = 'File exceeds the 50 MB limit.';
      return;
    }
    this.selectedFile = file;
    this.state = 'fileSelected';
    this.errorMessage = '';
    this.successMessage = '';
  }

  iconFor(type: string): string {
    if (type.includes('pdf')) return 'picture_as_pdf';
    if (type.includes('word')) return 'description';
    if (type.includes('html')) return 'code';
    return 'insert_drive_file';
  }

  typeBadge(type: string): string {
    if (type.includes('pdf')) return 'PDF';
    if (type.includes('word')) return 'DOCX';
    if (type.includes('html')) return 'HTML';
    return 'File';
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
```

- [ ] **Step 4: Run the spec and confirm all 9 tests pass**

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -30
```

Expected: `IngestComponent: 9 specs, 0 failures` (and the QueryComponent suite still passes)

- [ ] **Step 5: Commit**

```bash
cd angular-ui && cd .. && git add angular-ui/src/app/features/ingest/ingest.component.ts angular-ui/src/app/features/ingest/ingest.component.spec.ts && git commit -m "feat(angular-ui): add IngestComponent with drop-zone and file preview"
```

---

## Task 3: DocumentsComponent

**Files:**
- Create: `angular-ui/src/app/features/documents/documents.component.ts`
- Create: `angular-ui/src/app/features/documents/documents.component.spec.ts`

- [ ] **Step 1: Write the failing spec**

Create `angular-ui/src/app/features/documents/documents.component.spec.ts`:

```typescript
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DocumentsComponent } from './documents.component';
import { DocumentsService } from './documents.service';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { DocumentSummary } from '../../core/models';
import { of } from 'rxjs';

const mockDoc: DocumentSummary = {
  filename: 'spring-ai.pdf',
  sourceId: 'abc123',
  contentType: 'application/pdf',
  author: '',
  createdDate: '',
  uploadedAt: '2025-05-01T00:00:00Z',
  fileSizeBytes: 2_500_000,
  chunkCount: 42,
  totalTokens: 8320
};

class FakeDocumentsService {
  documents = signal<DocumentSummary[]>([]);
  error = signal<string | null>(null);
  load = jasmine.createSpy('load');
  delete = jasmine.createSpy('delete');
}

describe('DocumentsComponent', () => {
  let fixture: ComponentFixture<DocumentsComponent>;
  let component: DocumentsComponent;
  let fakeService: FakeDocumentsService;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    fakeService = new FakeDocumentsService();
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) });

    await TestBed.configureTestingModule({
      imports: [DocumentsComponent, NoopAnimationsModule],
      providers: [
        { provide: DocumentsService, useValue: fakeService },
        { provide: MatDialog, useValue: dialogSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('calls load() on init', () => {
    expect(fakeService.load).toHaveBeenCalled();
  });

  it('shows empty-state text when documents list is empty', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.empty-state');
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('No documents ingested yet');
  });

  it('renders one card per document', () => {
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.doc-card');
    expect(cards.length).toBe(1);
    expect(cards[0].textContent).toContain('spring-ai.pdf');
  });

  it('shows the document count badge', () => {
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const badge: HTMLElement = fixture.nativeElement.querySelector('.count-badge');
    expect(badge.textContent).toContain('1');
  });

  it('shows error strip when error signal is set', () => {
    fakeService.error.set('Failed to load documents.');
    fixture.detectChanges();
    const strip: HTMLElement = fixture.nativeElement.querySelector('.alert-strip');
    expect(strip).toBeTruthy();
    expect(strip.textContent).toContain('Failed to load documents.');
  });

  it('opens confirm dialog when Delete is clicked', () => {
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.doc-card button');
    btn.click();
    expect(dialogSpy.open).toHaveBeenCalled();
  });

  it('calls delete with sourceId when dialog is confirmed', () => {
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.doc-card button');
    btn.click();
    expect(fakeService.delete).toHaveBeenCalledWith('abc123', jasmine.any(Function));
  });

  it('does not call delete when dialog is cancelled', () => {
    dialogSpy.open.and.returnValue({ afterClosed: () => of(false) });
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.doc-card button');
    btn.click();
    expect(fakeService.delete).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run the spec to confirm it fails with a missing module error**

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -20
```

Expected: compilation error — `Cannot find module './documents.component'`

- [ ] **Step 3: Implement DocumentsComponent**

Create `angular-ui/src/app/features/documents/documents.component.ts`:

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { DocumentsService } from './documents.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatDialogModule],
  template: `
    <div class="docs-page">
      <div class="docs-header">
        <h2>Documents</h2>
        <span class="count-badge">{{ documentsService.documents().length }} document(s)</span>
      </div>

      @if (documentsService.error()) {
        <div class="alert-strip">{{ documentsService.error() }}</div>
      }
      @if (deleteError) {
        <div class="alert-strip">{{ deleteError }}</div>
      }

      @if (documentsService.documents().length === 0 && !documentsService.error()) {
        <p class="empty-state">No documents ingested yet. Use the Ingest page to add files.</p>
      }

      <div class="doc-list">
        @for (doc of documentsService.documents(); track doc.sourceId) {
          <div class="doc-card">
            <mat-icon class="doc-icon">{{ iconFor(doc.contentType) }}</mat-icon>
            <div class="doc-info">
              <div class="doc-name">{{ doc.filename }}</div>
              <div class="doc-meta">
                {{ typeBadge(doc.contentType) }} · {{ doc.chunkCount }} chunks ·
                {{ doc.totalTokens }} tokens · {{ formatBytes(doc.fileSizeBytes) }} ·
                {{ formatDate(doc.uploadedAt) }}
              </div>
            </div>
            <button mat-stroked-button color="warn"
              (click)="confirmDelete(doc.sourceId, doc.filename)">
              Delete
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .docs-page { max-width: 800px; }
    .docs-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    h2 { margin: 0; }
    .count-badge { font-size: 0.85rem; color: rgba(0,0,0,0.54); background: rgba(0,0,0,0.06); padding: 2px 8px; border-radius: 12px; }
    .alert-strip { background: #ffebee; color: #c62828; border-radius: 4px; padding: 10px 14px; margin-bottom: 12px; }
    .empty-state { color: rgba(0,0,0,0.4); text-align: center; margin-top: 80px; }
    .doc-list { display: flex; flex-direction: column; gap: 8px; }
    .doc-card { display: flex; align-items: center; gap: 12px; border: 1px solid rgba(0,0,0,0.12); border-radius: 6px; padding: 12px 16px; background: #fff; }
    .doc-icon { color: #3f51b5; flex-shrink: 0; }
    .doc-info { flex: 1; }
    .doc-name { font-weight: 500; }
    .doc-meta { font-size: 0.85rem; color: rgba(0,0,0,0.54); }
  `]
})
export class DocumentsComponent implements OnInit {
  protected documentsService = inject(DocumentsService);
  private dialog = inject(MatDialog);

  deleteError = '';

  ngOnInit(): void {
    this.documentsService.load();
  }

  confirmDelete(sourceId: string, filename: string): void {
    this.deleteError = '';
    this.dialog.open(ConfirmDialogComponent, { data: { filename } })
      .afterClosed()
      .subscribe(confirmed => {
        if (confirmed) {
          this.documentsService.delete(sourceId, () => {
            this.deleteError = 'Failed to delete. Try again.';
          });
        }
      });
  }

  iconFor(contentType: string): string {
    if (contentType?.includes('pdf')) return 'picture_as_pdf';
    if (contentType?.includes('word')) return 'description';
    if (contentType?.includes('html')) return 'code';
    return 'insert_drive_file';
  }

  typeBadge(contentType: string): string {
    if (contentType?.includes('pdf')) return 'PDF';
    if (contentType?.includes('word')) return 'DOCX';
    if (contentType?.includes('html')) return 'HTML';
    return 'File';
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric', month: 'short', day: 'numeric'
    });
  }
}
```

- [ ] **Step 4: Run the spec and confirm all 8 tests pass**

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -30
```

Expected: `DocumentsComponent: 8 specs, 0 failures` (all prior suites still pass — 25 specs total, 0 failures)

- [ ] **Step 5: Commit**

```bash
cd angular-ui && cd .. && git add angular-ui/src/app/features/documents/documents.component.ts angular-ui/src/app/features/documents/documents.component.spec.ts && git commit -m "feat(angular-ui): add DocumentsComponent with card list and delete flow"
```

---

## Verification

After all three tasks:

```bash
cd angular-ui && ng test --watch=false --browsers=ChromeHeadless 2>&1 | grep -E "SUMMARY|specs|failures"
```

Expected output:
```
Executed 25 of 25 SUCCESS
```

Then smoke-test the running app:

```bash
cd angular-ui && ng serve
```

Open http://localhost:4200 — verify:
1. `/query` — empty state text visible, type a question, send, message and citations appear
2. `/ingest` — drop zone shown, select a PDF, preview card appears, Upload button active
3. `/documents` — empty state or card list if backend is running
