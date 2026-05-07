import { TestBed } from '@angular/core/testing';
import { ChatService } from './chat.service';
import { RagResponse } from '../../core/models';

describe('ChatService', () => {
  let service: ChatService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatService);
  });

  afterEach(() => service.reset());

  it('addUserMessage appends a user message', () => {
    service.addUserMessage('Hello');
    expect(service.messages()).toEqual([{ role: 'user', text: 'Hello' }]);
  });

  it('addAssistantMessage appends the answer with citations and tokens', () => {
    const response: RagResponse = {
      answer: 'RAG stands for Retrieval-Augmented Generation.',
      citations: [{ ref: 1, filename: 'doc.pdf', chunkIndex: 0, score: 0.92, chunkText: 'excerpt' }],
      totalTokens: 50
    };
    service.addAssistantMessage(response);
    const msgs = service.messages();
    expect(msgs.length).toBe(1);
    expect(msgs[0].role).toBe('assistant');
    expect(msgs[0].text).toBe(response.answer);
    expect(msgs[0].citations).toEqual(response.citations);
    expect(msgs[0].totalTokens).toBe(50);
  });

  it('history accumulates across multiple calls', () => {
    service.addUserMessage('Q1');
    service.addAssistantMessage({ answer: 'A1', citations: [], totalTokens: 10 });
    service.addUserMessage('Q2');
    expect(service.messages().length).toBe(3);
    expect(service.messages()[2]).toEqual({ role: 'user', text: 'Q2' });
  });

  it('addErrorMessage appends a fixed error assistant message', () => {
    service.addErrorMessage();
    expect(service.messages()[0]).toEqual({
      role: 'assistant',
      text: 'Something went wrong. Please try again.'
    });
  });

  it('messages() starts as an empty array', () => {
    expect(service.messages()).toEqual([]);
  });

  it('addAssistantMessage with empty citations stores citations as an empty array', () => {
    const response: RagResponse = { answer: 'Hello', citations: [], totalTokens: 5 };
    service.addAssistantMessage(response);
    expect(service.messages()[0].citations).toEqual([]);
    expect(service.messages()[0].totalTokens).toBe(5);
  });
});
