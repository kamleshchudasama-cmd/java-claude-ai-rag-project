package com.test.rag.service.transcription;

import com.test.rag.exception.DocumentParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiVideoTranscriptionServiceTest {

    @Mock
    private OpenAiAudioTranscriptionModel transcriptionModel;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AudioTranscriptionResponse mockResponse;

    private OpenAiVideoTranscriptionService service;

    @BeforeEach
    void setUp() {
        service = new OpenAiVideoTranscriptionService(transcriptionModel);
    }

    @Test
    void transcribe_returnsTranscriptFromWhisper() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn("Hello world transcript");

        String result = service.transcribe(file);

        assertThat(result).isEqualTo("Hello world transcript");
    }

    @Test
    void transcribe_throwsDocumentParseException_whenTranscriptIsBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "silent.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn("   ");

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("No transcript extracted from");
    }

    @Test
    void transcribe_throwsDocumentParseException_whenTranscriptIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "null.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn(null);

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("No transcript extracted from");
    }

    @Test
    void transcribe_whenWhisperThrowsRuntimeException_propagates() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fail.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenThrow(new RuntimeException("Whisper API error"));

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Whisper API error");
    }

    @Test
    void transcribe_exceptionMessageContainsFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "myvideo.mp4", "video/mp4", "dummy".getBytes());
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult().getOutput()).thenReturn("");

        assertThatThrownBy(() -> service.transcribe(file))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("myvideo.mp4");
    }
}
