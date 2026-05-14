import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RagResponse, DocumentSummary, CrawlStartResponse, CrawlStatusResponse, CrawlSiteSummary } from './models';

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

  startCrawl(url: string): Observable<CrawlStartResponse> {
    return this.http.post<CrawlStartResponse>(`${this.base}/api/rag/crawl`, null, {
      params: { url }
    });
  }

  getCrawlStatus(jobId: string): Observable<CrawlStatusResponse> {
    return this.http.get<CrawlStatusResponse>(`${this.base}/api/rag/crawl/${jobId}/status`);
  }

  listCrawledSites(): Observable<CrawlSiteSummary[]> {
    return this.http.get<CrawlSiteSummary[]>(`${this.base}/api/rag/crawl/sites`);
  }

  deleteCrawledSite(rootUrl: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/rag/crawl/sites`, {
      params: { rootUrl }
    });
  }
}
