import { Injectable, signal } from '@angular/core';
import { ChatMessage, RagResponse } from '../../core/models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  readonly messages = signal<ChatMessage[]>([]);

  addUserMessage(text: string): void {
    this.messages.update(msgs => [...msgs, { role: 'user', text }]);
  }

  addAssistantMessage(response: RagResponse): void {
    this.messages.update(msgs => [...msgs, {
      role: 'assistant',
      text: response.answer,
      citations: response.citations,
      totalTokens: response.totalTokens
    }]);
  }

  addErrorMessage(): void {
    this.messages.update(msgs => [...msgs, {
      role: 'assistant',
      text: 'Something went wrong. Please try again.'
    }]);
  }

  reset(): void {
    this.messages.set([]);
  }
}
