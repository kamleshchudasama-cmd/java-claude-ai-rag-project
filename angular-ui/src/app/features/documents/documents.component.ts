import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { DocumentsService } from './documents.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss'
})
export class DocumentsComponent implements OnInit {
  protected documentsService = inject(DocumentsService);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);

  deleteError = '';

  ngOnInit(): void {
    this.documentsService.load();
  }

  confirmDelete(sourceId: string, filename: string): void {
    this.deleteError = '';
    this.dialog.open(ConfirmDialogComponent, { data: { filename } })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(confirmed => {
        if (confirmed) {
          this.documentsService.delete(sourceId, () => {
            this.deleteError = 'Failed to delete. Try again.';
          });
        }
      });
  }

  iconFor(contentType: string): string {
    if (contentType?.includes('pdf')) return 'picture_as_pdf';
    if (contentType?.includes('word')) return 'description';
    if (contentType?.includes('html')) return 'code';
    return 'insert_drive_file';
  }

  typeBadge(contentType: string): string {
    if (contentType?.includes('pdf')) return 'PDF';
    if (contentType?.includes('word')) return 'DOCX';
    if (contentType?.includes('html')) return 'HTML';
    return 'File';
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  }
}
