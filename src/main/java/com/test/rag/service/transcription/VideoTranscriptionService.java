package com.test.rag.service.transcription;

import org.springframework.web.multipart.MultipartFile;

public interface VideoTranscriptionService {
    String transcribe(MultipartFile file);
}
