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

  it('isLoading is false after load() completes successfully', () => {
    service.load();
    expect(service.isLoading()).toBeFalse();
  });

  it('isLoading is false after load() fails', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.isLoading()).toBeFalse();
  });

  it('load sets error signal on API failure', () => {
    apiSpy.listDocuments.and.returnValue(throwError(() => new Error('500')));
    service.load();
    expect(service.error()).toBe('Failed to load documents.');
  });

  it('delete calls deleteDocument then refreshes the list', () => {
    apiSpy.deleteDocument.and.returnValue(of(undefined));
    service.load();
    service.delete('abc123');
    expect(apiSpy.deleteDocument).toHaveBeenCalledWith('abc123');
    expect(apiSpy.listDocuments).toHaveBeenCalledTimes(2);
  });

  it('delete sets deleteError signal on API failure', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    service.delete('abc123');
    expect(service.deleteError()).toBe('Failed to delete. Try again.');
  });

  it('delete clears deleteError before the API call', () => {
    apiSpy.deleteDocument.and.returnValue(of(undefined));
    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    service.delete('abc123');
    expect(service.deleteError()).toBe('');
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
    service.delete('abc123');
    expect(service.error()).toBeNull();
    expect(service.documents()).toEqual([mockDoc]);
  });

  it('delete() calls load() to revert the optimistic removal when the API returns an error', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    service.delete('abc123');
    expect(apiSpy.listDocuments).toHaveBeenCalled();
  });

  it('load() clears deleteError after a previously failed delete', () => {
    apiSpy.deleteDocument.and.returnValue(throwError(() => new Error('500')));
    service.delete('abc123');
    expect(service.deleteError()).toBe('Failed to delete. Try again.');

    apiSpy.listDocuments.and.returnValue(of([mockDoc]));
    service.load();
    expect(service.deleteError()).toBe('');
  });
});
