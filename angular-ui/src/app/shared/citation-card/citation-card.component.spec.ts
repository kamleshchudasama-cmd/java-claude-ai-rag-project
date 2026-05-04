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
