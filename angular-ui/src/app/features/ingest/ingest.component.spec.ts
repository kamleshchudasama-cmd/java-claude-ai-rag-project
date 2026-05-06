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

  it('clear() also resets successMessage', () => {
    (component as any).setFile(pdfFile);
    component.successMessage = 'Uploaded successfully';
    component.clear();
    expect(component.successMessage).toBe('');
  });

  it('accepts HTML file type and transitions to fileSelected state', () => {
    const htmlFile = new File(['<html></html>'], 'page.html', { type: 'text/html' });
    (component as any).setFile(htmlFile);
    fixture.detectChanges();
    expect(component.state).toBe('fileSelected');
    expect(fixture.nativeElement.querySelector('.preview-card')).toBeTruthy();
  });

  it('clear() resets errorMessage to an empty string', () => {
    (component as any).setFile(new File([''], 'bad.exe', { type: 'application/x-msdownload' }));
    expect(component.errorMessage).toContain('Unsupported file type');
    component.clear();
    expect(component.errorMessage).toBe('');
  });

  it('upload button is disabled while state is uploading', () => {
    const subject = new Subject<void>();
    ragApiSpy.ingest.and.returnValue(subject.asObservable());
    (component as any).setFile(pdfFile);
    component.upload();
    fixture.detectChanges();
    const uploadBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[color="primary"]');
    expect(uploadBtn.disabled).toBeTrue();
    subject.complete();
  });

  it('onFileSelected calls setFile with the chosen file', () => {
    spyOn(component as any, 'setFile');
    const file = new File([''], 'page.html', { type: 'text/html' });
    const mockEvent = { target: { files: [file], value: '' } } as unknown as Event;
    component.onFileSelected(mockEvent);
    expect((component as any).setFile).toHaveBeenCalledWith(file);
  });
});
