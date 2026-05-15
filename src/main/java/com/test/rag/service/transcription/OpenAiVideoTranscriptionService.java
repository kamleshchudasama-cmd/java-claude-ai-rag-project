package com.test.rag.service.transcription;

import com.test.rag.exception.DocumentParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@Service
public class OpenAiVideoTranscriptionService implements VideoTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVideoTranscriptionService.class);

    private final OpenAiAudioTranscriptionModel transcriptionModel;

    public OpenAiVideoTranscriptionService(OpenAiAudioTranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @Override
    public String transcribe(MultipartFile file) {
        String filename = Objects.nonNull(file.getOriginalFilename())
                ? file.getOriginalFilename() : "video";
        long startMs = System.currentTimeMillis();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to read video file: " + filename, e);
        }

        Resource audioResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        AudioTranscriptionResponse response = transcriptionModel.call(
                new AudioTranscriptionPrompt(audioResource));

        String transcript = response.getResult().getOutput();
        if (Objects.isNull(transcript) || transcript.isBlank()) {
            throw new DocumentParseException("No transcript extracted from: " + filename);
        }

        log.info("Transcribed video='{}' chars={} latencyMs={}",
                filename, transcript.length(), System.currentTimeMillis() - startMs);
        return transcript;
    }
}
