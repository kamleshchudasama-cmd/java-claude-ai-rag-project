import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { QueryComponent } from './query.component';
import { ChatService } from './chat.service';
import { RagApiService } from '../../core/rag-api.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError, Subject } from 'rxjs';
import { RagResponse } from '../../core/models';

const mockResponse: RagResponse = {
  answer: 'RAG is Retrieval-Augmented Generation.',
  citations: [{ ref: 1, filename: 'doc.pdf', chunkIndex: 0, score: 0.92, chunkText: 'excerpt' }],
  totalTokens: 42
};

describe('QueryComponent', () => {
  let fixture: ComponentFixture<QueryComponent>;
  let component: QueryComponent;
  let chatService: ChatService;
  let ragApiSpy: jasmine.SpyObj<RagApiService>;

  beforeEach(async () => {
    ragApiSpy = jasmine.createSpyObj('RagApiService', ['query']);
    ragApiSpy.query.and.returnValue(of(mockResponse));

    await TestBed.configureTestingModule({
      imports: [QueryComponent, NoopAnimationsModule],
      providers: [
        ChatService,
        { provide: RagApiService, useValue: ragApiSpy }
      ]
    }).compileComponents();

    chatService = TestBed.inject(ChatService);
    fixture = TestBed.createComponent(QueryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows empty-state text when message list is empty', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.empty-state');
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('Ask a question to get started');
  });

  it('Send button is disabled when input is empty', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-icon-button]');
    expect(btn.disabled).toBeTrue();
  });

  it('clears inputText after send', fakeAsync(() => {
    component.inputText = 'test question';
    component.send();
    tick();
    expect(component.inputText).toBe('');
  }));

  it('renders user bubble with the sent text', fakeAsync(() => {
    component.inputText = 'What is RAG?';
    component.send();
    tick();
    fixture.detectChanges();
    const bubble: HTMLElement = fixture.nativeElement.querySelector('.user-bubble');
    expect(bubble).toBeTruthy();
    expect(bubble.textContent?.trim()).toBe('What is RAG?');
  }));

  it('renders assistant card with a citation-card after successful response', fakeAsync(() => {
    component.inputText = 'What is RAG?';
    component.send();
    tick();
    fixture.detectChanges();
    const card: HTMLElement = fixture.nativeElement.querySelector('.assistant-card');
    expect(card).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-citation-card')).toBeTruthy();
  }));

  it('calls ragApi.query with the trimmed input text', fakeAsync(() => {
    component.inputText = '  What is RAG?  ';
    component.send();
    tick();
    expect(ragApiSpy.query).toHaveBeenCalledWith('What is RAG?');
  }));

  it('sets isLoading true while request is in flight', () => {
    const subject = new Subject<RagResponse>();
    ragApiSpy.query.and.returnValue(subject.asObservable());
    component.inputText = 'test';
    component.send();
    expect(component.isLoading).toBeTrue();
    subject.complete();
  });

  it('calls addErrorMessage and clears isLoading on API error', fakeAsync(() => {
    ragApiSpy.query.and.returnValue(throwError(() => new Error('Network error')));
    const errorSpy = spyOn(chatService, 'addErrorMessage').and.callThrough();
    component.inputText = 'test';
    component.send();
    tick();
    expect(errorSpy).toHaveBeenCalled();
    expect(component.isLoading).toBeFalse();
  }));

  it('send() does nothing when inputText is whitespace-only', fakeAsync(() => {
    component.inputText = '   ';
    component.send();
    tick();
    expect(ragApiSpy.query).not.toHaveBeenCalled();
    expect(chatService.messages().length).toBe(0);
  }));

  it('Send button is disabled while isLoading is true', () => {
    const subject = new Subject<RagResponse>();
    ragApiSpy.query.and.returnValue(subject.asObservable());
    component.inputText = 'test';
    component.send();
    fixture.detectChanges();
    const sendBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-icon-button]');
    expect(sendBtn.disabled).toBeTrue();
    subject.complete();
  });

  it('empty-state element is removed after the first message is sent', fakeAsync(() => {
    component.inputText = 'Hello';
    component.send();
    tick();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty-state')).toBeNull();
  }));

  it('multiple consecutive sends accumulate all messages in the list', fakeAsync(() => {
    component.inputText = 'Question 1';
    component.send();
    tick();
    component.inputText = 'Question 2';
    component.send();
    tick();
    // 2 user messages + 2 assistant responses = 4 total
    expect(chatService.messages().length).toBe(4);
    expect(chatService.messages()[2]).toEqual({ role: 'user', text: 'Question 2' });
  }));
});
