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

export interface CrawlStartResponse {
  jobId: string;
}

export interface CrawlStatusResponse {
  jobId: string;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  pagesVisited: number;
  pagesIngested: number;
  totalChunks: number;
  errorMessage: string | null;
}

export interface CrawlSiteSummary {
  rootUrl: string;
  pagesIngested: number;
  totalChunks: number;
  lastCrawledAt: string;
}
