import {
  Component, DestroyRef, ElementRef, Injector,
  OnInit, ViewChild, afterNextRender, inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RagApiService } from '../../core/rag-api.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { CitationCardComponent } from '../../shared/citation-card/citation-card.component';
import { ChatService } from '../query/chat.service';
import { CrawlService } from './crawl.service';

@Component({
  selector: 'app-crawl',
  standalone: true,
  providers: [CrawlService, ChatService],
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatDialogModule,
    CitationCardComponent
  ],
  templateUrl: './crawl.component.html',
  styleUrl: './crawl.component.scss'
})
export class CrawlComponent implements OnInit {
  protected crawlService = inject(CrawlService);
  protected chatService = inject(ChatService);
  private ragApi = inject(RagApiService);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);
  private injector = inject(Injector);

  @ViewChild('messageList') private messageList!: ElementRef<HTMLElement>;

  urlInput = '';
  questionInput = '';
  isQuerying = false;

  ngOnInit(): void {
    this.crawlService.loadSites();
  }

  startCrawl(): void {
    const url = this.urlInput.trim();
    if (!url) return;
    this.crawlService.startCrawl(url);
  }

  confirmDelete(rootUrl: string): void {
    this.dialog.open(ConfirmDialogComponent, { data: { filename: rootUrl } })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(confirmed => {
        if (confirmed) this.crawlService.deleteSite(rootUrl);
      });
  }

  sendQuestion(): void {
    const text = this.questionInput.trim();
    if (!text || this.isQuerying) return;
    this.questionInput = '';
    this.isQuerying = true;
    this.chatService.addUserMessage(text);
    this.ragApi.query(text).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.chatService.addAssistantMessage(response);
        this.isQuerying = false;
        this.scrollToBottom();
      },
      error: () => {
        this.chatService.addErrorMessage();
        this.isQuerying = false;
      }
    });
  }

  private scrollToBottom(): void {
    afterNextRender(() => {
      const el = this.messageList?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, { injector: this.injector });
  }
}
