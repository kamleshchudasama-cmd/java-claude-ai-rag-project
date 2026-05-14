import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CrawlComponent } from './crawl.component';
import { CrawlService } from './crawl.service';
import { ChatService } from '../query/chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('CrawlComponent', () => {
  let fixture: ComponentFixture<CrawlComponent>;
  let component: CrawlComponent;
  let crawlService: jasmine.SpyObj<CrawlService>;
  let chatService: jasmine.SpyObj<ChatService>;

  beforeEach(async () => {
    crawlService = jasmine.createSpyObj('CrawlService',
      ['startCrawl', 'loadSites', 'deleteSite'],
      {
        crawlState: signal('idle'),
        pagesVisited: signal(0),
        pagesIngested: signal(0),
        totalChunks: signal(0),
        errorMessage: signal(null),
        crawledSites: signal([])
      }
    );
    chatService = jasmine.createSpyObj('ChatService',
      ['addUserMessage', 'addAssistantMessage', 'addErrorMessage'],
      { messages: signal([]) }
    );

    await TestBed.configureTestingModule({
      imports: [CrawlComponent, NoopAnimationsModule],
      providers: [
        { provide: RagApiService, useValue: jasmine.createSpyObj('RagApiService', ['query']) },
        { provide: MatDialog, useValue: jasmine.createSpyObj('MatDialog', ['open']) }
      ]
    })
    .overrideComponent(CrawlComponent, {
      set: {
        providers: [
          { provide: CrawlService, useValue: crawlService },
          { provide: ChatService, useValue: chatService }
        ]
      }
    })
    .compileComponents();

    fixture = TestBed.createComponent(CrawlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('calls crawlService.loadSites on init', () => {
    expect(crawlService.loadSites).toHaveBeenCalled();
  });

  it('calls crawlService.startCrawl with trimmed URL', () => {
    component.urlInput = '  https://example.com  ';
    component.startCrawl();
    expect(crawlService.startCrawl).toHaveBeenCalledWith('https://example.com');
  });

  it('does not crawl when URL is blank', () => {
    component.urlInput = '   ';
    component.startCrawl();
    expect(crawlService.startCrawl).not.toHaveBeenCalled();
  });
});
