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
  deleteError = signal<string>('');
  isLoading = signal<boolean>(false);
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
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) } as any);

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

  it('shows empty-state text when documents list is empty and not loading', () => {
    fakeService.isLoading.set(false);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement.querySelector('.empty-state');
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('No documents ingested yet');
  });

  it('shows loading spinner and hides empty-state while isLoading is true', () => {
    fakeService.isLoading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.loading-state')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.empty-state')).toBeNull();
  });

  it('hides loading spinner once isLoading becomes false', () => {
    fakeService.isLoading.set(true);
    fixture.detectChanges();
    fakeService.isLoading.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.loading-state')).toBeNull();
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
    expect(fakeService.delete).toHaveBeenCalledWith('abc123');
  });

  it('does not call delete when dialog is cancelled', () => {
    dialogSpy.open.and.returnValue({ afterClosed: () => of(false) } as any);
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.doc-card button');
    btn.click();
    expect(fakeService.delete).not.toHaveBeenCalled();
  });

  it('error strip is absent when both error signals are null/empty', () => {
    fakeService.error.set(null);
    fakeService.deleteError.set('');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.alert-strip')).toBeNull();
  });

  it('shows delete error strip when deleteError signal is set', () => {
    fakeService.deleteError.set('Failed to delete. Try again.');
    fixture.detectChanges();
    const strips: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.alert-strip');
    const texts = Array.from(strips).map(s => s.textContent ?? '');
    expect(texts.some(t => t.includes('Failed to delete'))).toBeTrue();
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

  it('iconFor returns videocam for video/mp4', () => {
    expect(component.iconFor('video/mp4')).toBe('videocam');
  });

  it('iconFor returns videocam for video/quicktime', () => {
    expect(component.iconFor('video/quicktime')).toBe('videocam');
  });

  it('typeBadge returns VIDEO for video/mp4', () => {
    expect(component.typeBadge('video/mp4')).toBe('VIDEO');
  });

  it('doc card renders VIDEO badge for a video document', () => {
    const videoDoc: DocumentSummary = {
      ...mockDoc,
      filename: 'lecture.mp4',
      contentType: 'video/mp4'
    };
    fakeService.documents.set([videoDoc]);
    fixture.detectChanges();
    const meta: HTMLElement = fixture.nativeElement.querySelector('.doc-meta');
    expect(meta.textContent).toContain('VIDEO');
  });
});
