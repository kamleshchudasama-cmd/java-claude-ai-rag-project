import { TestBed } from '@angular/core/testing';
import { CrawlService } from './crawl.service';
import { RagApiService } from '../../core/rag-api.service';
import { of, throwError } from 'rxjs';
import { CrawlStatusResponse, CrawlSiteSummary } from '../../core/models';

describe('CrawlService', () => {
  let service: CrawlService;
  let ragApi: jasmine.SpyObj<RagApiService>;

  const doneStatus: CrawlStatusResponse = {
    jobId: 'job1', status: 'DONE',
    pagesVisited: 5, pagesIngested: 5, totalChunks: 40, errorMessage: null
  };

  const sites: CrawlSiteSummary[] = [
    { rootUrl: 'https://example.com', pagesIngested: 5, totalChunks: 40, lastCrawledAt: '2026-05-14T10:00:00Z' }
  ];

  beforeEach(() => {
    ragApi = jasmine.createSpyObj('RagApiService', [
      'startCrawl', 'getCrawlStatus', 'listCrawledSites', 'deleteCrawledSite'
    ]);
    TestBed.configureTestingModule({
      providers: [
        CrawlService,
        { provide: RagApiService, useValue: ragApi }
      ]
    });
    service = TestBed.inject(CrawlService);
  });

  it('starts in idle state', () => {
    expect(service.crawlState()).toBe('idle');
    expect(service.pagesVisited()).toBe(0);
    expect(service.crawledSites()).toEqual([]);
  });

  it('sets crawlState to running after startCrawl succeeds', () => {
    ragApi.startCrawl.and.returnValue(of({ jobId: 'job1' }));
    ragApi.getCrawlStatus.and.returnValue(of(doneStatus));
    ragApi.listCrawledSites.and.returnValue(of(sites));

    service.startCrawl('https://example.com');

    expect(ragApi.startCrawl).toHaveBeenCalledWith('https://example.com');
  });

  it('sets crawlState to failed when startCrawl errors', () => {
    ragApi.startCrawl.and.returnValue(throwError(() => new Error('Network error')));

    service.startCrawl('https://example.com');

    expect(service.crawlState()).toBe('failed');
    expect(service.errorMessage()).toBe('Failed to start crawl');
  });

  it('loadSites updates crawledSites signal', () => {
    ragApi.listCrawledSites.and.returnValue(of(sites));

    service.loadSites();

    expect(service.crawledSites()).toEqual(sites);
  });

  it('deleteSite calls api and reloads sites', () => {
    ragApi.deleteCrawledSite.and.returnValue(of(undefined));
    ragApi.listCrawledSites.and.returnValue(of([]));

    service.deleteSite('https://example.com');

    expect(ragApi.deleteCrawledSite).toHaveBeenCalledWith('https://example.com');
    expect(ragApi.listCrawledSites).toHaveBeenCalled();
  });
});
