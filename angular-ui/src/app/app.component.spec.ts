import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent, NoopAnimationsModule, RouterTestingModule],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders the "RAG System" sidenav title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const title: HTMLElement = fixture.nativeElement.querySelector('.sidenav-title');
    expect(title.textContent?.trim()).toBe('RAG System');
  });

  it('renders Query, Ingest, and Documents nav links', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const titleSpans: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('span[matlistitemtitle]')
    );
    const texts = titleSpans.map(s => s.textContent?.trim());
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
    expect(links.length).toBe(3);
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
});
