import { Injectable, inject, signal } from '@angular/core';
import { RagApiService } from '../../core/rag-api.service';
import { DocumentSummary } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private api = inject(RagApiService);
  readonly documents = signal<DocumentSummary[]>([]);
  readonly error = signal<string | null>(null);
  readonly deleteError = signal<string>('');
  readonly isLoading = signal<boolean>(false);

  load(): void {
    this.isLoading.set(true);
    this.api.listDocuments().subscribe({
      next: docs => {
        this.documents.set(docs);
        this.error.set(null);
        this.deleteError.set('');
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Failed to load documents.');
        this.isLoading.set(false);
      }
    });
  }

  delete(sourceId: string): void {
    this.deleteError.set('');
    this.documents.update(docs => docs.filter(d => d.sourceId !== sourceId));
    this.api.deleteDocument(sourceId).subscribe({
      next: () => this.load(),
      error: () => {
        this.load();
        this.deleteError.set('Failed to delete. Try again.');
      }
    });
  }
}
