import { Component, DestroyRef, ElementRef, Injector, ViewChild, afterNextRender, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ChatService } from './chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { CitationCardComponent } from '../../shared/citation-card/citation-card.component';

@Component({
  selector: 'app-query',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CitationCardComponent
  ],
  templateUrl: './query.component.html',
  styleUrl: './query.component.scss'
})
export class QueryComponent {
  protected chatService = inject(ChatService);
  private ragApi = inject(RagApiService);
  private destroyRef = inject(DestroyRef);
  private injector = inject(Injector);

  @ViewChild('messageList') private messageList!: ElementRef<HTMLElement>;

  isLoading = false;
  inputText = '';

  send(): void {
    const text = this.inputText.trim();
    if (!text || this.isLoading) return;
    this.inputText = '';
    this.isLoading = true;
    this.chatService.addUserMessage(text);
    this.ragApi.query(text).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.chatService.addAssistantMessage(response);
        this.isLoading = false;
        this.scrollToBottom();
      },
      error: () => {
        this.chatService.addErrorMessage();
        this.isLoading = false;
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
