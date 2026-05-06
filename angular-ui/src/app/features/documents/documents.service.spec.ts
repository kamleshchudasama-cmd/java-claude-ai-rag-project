import { TestBed } from '@angular/core/testing';
import { DocumentsService } from './documents.service';
import { RagApiService } from '../../core/rag-api.service';
import { of, throwError } from 'rxjs';
import { DocumentSummary } from '../../core/models';

const mockDoc: DocumentSummary = {
  filename: 'test.pdf', sourceId: 'abc123', contentType: 'application/pdf',
  author: '', createdDate: '', uploadedAt: '2026-05-04',
  fileSizeBytes: 1024, chunkCount: 5, totalTokens: 200
};

describe('DocumentsService', () => {
  let service: DocumentsService;
  let apiSpy: jasmine.SpyObj<RagApiService>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj('RagApiService', ['listDocuments', 'deleteDocument']);
    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    TestBed.configureTestingModule({
      providers: [DocumentsService, { provide: RagApiService, useValue: apiSpy }]
    });
    service = TestBed.inject(DocumentsService);
  });

  it('load populates the documents signal', () => {
    service.load();
    expect(service.documents()).toEqual([mockDoc]);
    expect(service.error()).toBeNull();
  });

  it('load sets error signal on API failure', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');
  });

  it('delete calls deleteDocument then refreshes the list', () => {
    apiSpy.deleteDocument.and.returnValue(of(undefined));
    service.load();
    service.delete('abc123', () => {});
    expect(apiSpy.deleteDocument).toHaveBeenCalledWith('abc123');
    expect(apiSpy.listDocuments).toHaveBeenCalledTimes(2);
  });

  it('delete calls onError callback on API failure', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    const onError = jasmine.createSpy('onError');
    service.delete('abc123', onError);
    expect(onError).toHaveBeenCalled();
  });

  it('load() clears a previous error when the subsequent request succeeds', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');

    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    service.load();
    expect(service.error()).toBeNull();
  });

  it('delete() on success re-loads documents and clears a pre-existing error', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');

    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    apiSpy.deleteDocument.and.returnValue(of(undefined));
    service.delete('abc123', () => {});
    expect(service.error()).toBeNull();
    expect(service.documents()).toEqual([mockDoc]);
  });

  it('delete() does not call load() when the API returns an error', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    service.delete('abc123', () => {});
    expect(apiSpy.listDocuments).not.toHaveBeenCalled();
  });
});
