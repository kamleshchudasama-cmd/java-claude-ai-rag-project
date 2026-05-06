import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RagApiService } from './rag-api.service';
import { RagResponse } from './models';
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
});
