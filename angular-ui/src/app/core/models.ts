export interface Citation {
  ref: number;
  filename: string;
  chunkIndex: number;
  score: number;
  chunkText: string;
}

export interface RagResponse {
  answer: string;
  citations: Citation[];
  totalTokens: number;
}

export interface DocumentSummary {
  filename: string;
  sourceId: string;
  contentType: string;
  author: string;
  createdDate: string;
  uploadedAt: string;
  fileSizeBytes: number;
  chunkCount: number;
  totalTokens: number;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  citations?: Citation[];
  totalTokens?: number;
}
