import { Injectable, inject, signal } from '@angular/core';
import { RagApiService } from '../../core/rag-api.service';
import { DocumentSummary } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private api = inject(RagApiService);
  readonly documents = signal<DocumentSummary[]>([]);
  readonly error = signal<string | null>(null);

  load(): void {
    this.api.listDocuments().subscribe({
      next: docs => {
        this.documents.set(docs);
        this.error.set(null);
      },
      error: () => this.error.set('Failed to load documents.')
    });
  }

  delete(sourceId: string, onError: () => void): void {
    this.api.deleteDocument(sourceId).subscribe({
      next: () => this.load(),
      error: onError
    });
  }
}
