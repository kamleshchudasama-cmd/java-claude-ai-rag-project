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
    dialogSpy.open.and.returnValue({ afterClosed: () => of(false) } as any);
    fakeService.documents.set([mockDoc]);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.doc-card button');
    btn.click();
    expect(fakeService.delete).not.toHaveBeenCalled();
  });
});
