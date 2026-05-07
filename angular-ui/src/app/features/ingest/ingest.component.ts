import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RagApiService } from '../../core/rag-api.service';

export type UploadState = 'idle' | 'fileSelected' | 'uploading';

const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/html',
  'text/markdown',
  'text/x-markdown'
];
const MAX_BYTES = 50 * 1024 * 1024;

@Component({
  selector: 'app-ingest',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './ingest.component.html',
  styleUrl: './ingest.component.scss'
})
export class IngestComponent {
  private ragApi = inject(RagApiService);
  private destroyRef = inject(DestroyRef);

  state: UploadState = 'idle';
  isDragOver = false;
  selectedFile: File | null = null;
  errorMessage = '';
  successMessage = '';

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    const related = event.relatedTarget as Node | null;
    if (related && (event.currentTarget as HTMLElement).contains(related)) return;
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    const file = event.dataTransfer?.files[0];
    if (file) this.setFile(file);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.setFile(file);
    input.value = '';
  }

  upload(): void {
    if (!this.selectedFile || this.state !== 'fileSelected') return;
    this.state = 'uploading';
    this.errorMessage = '';
    this.ragApi.ingest(this.selectedFile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: () => {
        this.state = 'idle';
        this.selectedFile = null;
        this.successMessage = 'Uploaded successfully';
        setTimeout(() => { this.successMessage = ''; }, 3000);
      },
      error: () => {
        this.state = 'fileSelected';
        this.errorMessage = 'Upload failed. Please try again.';
      }
    });
  }

  clear(): void {
    this.state = 'idle';
    this.selectedFile = null;
    this.errorMessage = '';
    this.successMessage = '';
  }

  private setFile(file: File): void {
    if (!ALLOWED_TYPES.includes(file.type)) {
      this.errorMessage = `Unsupported file type: "${file.type || 'unknown'}". Use PDF, DOCX, HTML, or MD.`;
      return;
    }
    if (file.size > MAX_BYTES) {
      this.errorMessage = 'File exceeds the 50 MB limit.';
      return;
    }
    this.selectedFile = file;
    this.state = 'fileSelected';
    this.errorMessage = '';
    this.successMessage = '';
  }

  iconFor(type: string): string {
    if (type.includes('pdf')) return 'picture_as_pdf';
    if (type.includes('word')) return 'description';
    if (type.includes('html')) return 'code';
    if (type.includes('markdown')) return 'article';
    return 'insert_drive_file';
  }

  typeBadge(type: string): string {
    if (type.includes('pdf')) return 'PDF';
    if (type.includes('word')) return 'DOCX';
    if (type.includes('html')) return 'HTML';
    if (type.includes('markdown')) return 'MD';
    return 'File';
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
