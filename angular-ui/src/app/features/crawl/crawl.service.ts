import { Injectable, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, interval } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { RagApiService } from '../../core/rag-api.service';
import { CrawlSiteSummary, RagResponse } from '../../core/models';

@Injectable()
export class CrawlService {
  private ragApi = inject(RagApiService);
  private destroyRef = inject(DestroyRef);

  readonly crawlState = signal<'idle' | 'running' | 'done' | 'failed'>('idle');
  readonly pagesVisited = signal(0);
  readonly pagesIngested = signal(0);
  readonly totalChunks = signal(0);
  readonly errorMessage = signal<string | null>(null);
  readonly crawledSites = signal<CrawlSiteSummary[]>([]);

  startCrawl(url: string): void {
    this.ragApi.startCrawl(url).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ jobId }) => {
        this.crawlState.set('running');
        this.startPolling(jobId);
      },
      error: () => {
        this.crawlState.set('failed');
        this.errorMessage.set('Failed to start crawl');
      }
    });
  }

  private startPolling(jobId: string): void {
    interval(3000).pipe(
      switchMap(() => this.ragApi.getCrawlStatus(jobId)),
      takeWhile(status => status.status === 'RUNNING', true),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: status => {
        this.pagesVisited.set(status.pagesVisited);
        this.pagesIngested.set(status.pagesIngested);
        this.totalChunks.set(status.totalChunks);
        if (status.status === 'DONE') {
          this.crawlState.set('done');
          this.loadSites();
        } else if (status.status === 'FAILED') {
          this.crawlState.set('failed');
          this.errorMessage.set(status.errorMessage ?? 'Crawl failed');
        }
      }
    });
  }

  loadSites(): void {
    this.ragApi.listCrawledSites().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: sites => this.crawledSites.set(sites),
      error: () => { /* silent — sites list simply won't refresh */ }
    });
  }

  deleteSite(rootUrl: string): void {
    this.ragApi.deleteCrawledSite(rootUrl).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.loadSites(),
      error: () => { /* silent — delete failed, list stays as-is */ }
    });
  }

  query(text: string): Observable<RagResponse> {
    return this.ragApi.query(text);
  }
}
