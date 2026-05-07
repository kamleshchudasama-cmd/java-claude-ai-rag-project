package com.test.rag.service.generation;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.RagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiGenerationServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatResponse chatResponse;

    private RagProperties props;
    private GenerationService service;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        service = new OpenAiGenerationService(chatClient, props);
    }

    private void givenChatClientReturns(ChatResponse response) {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse())
            .thenReturn(response);
    }

    @Test
    void generate_returnsAnswerTextFromChatResponse() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("This is the AI answer.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("What is AI?", List.of()));

        assertThat(result.answer()).isEqualTo("This is the AI answer.");
    }

    @Test
    void generate_answerWithCitationMarkers_returnedVerbatim() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Machine learning [1] is a subset of AI [2].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 1)
        )));

        assertThat(result.answer()).isEqualTo("Machine learning [1] is a subset of AI [2].");
    }

    @Test
    void generate_noCitationMarkersInAnswer_returnsEmptyCitations() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Answer with no citation markers.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query",
                List.of(citation(1, "doc.pdf", 0))));

        assertThat(result.citations()).isEmpty();
    }

    @Test
    void generate_allCitationMarkersInAnswer_returnsAllCitations() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("First point [1] and second point [2].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 1)
        )));

        assertThat(result.citations()).hasSize(2);
    }

    @Test
    void generate_partialCitationMarkersInAnswer_returnsOnlyReferencedCitations() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Only [1] is mentioned here.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 1),
                citation(3, "c.pdf", 2)
        )));

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).ref()).isEqualTo(1);
        assertThat(result.citations().get(0).filename()).isEqualTo("a.pdf");
    }

    @Test
    void generate_sameRefUsedMultipleTimesInAnswer_citationIncludedOnce() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Claim A [1]. Also claim B [1].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query",
                List.of(citation(1, "source.pdf", 0))));

        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void generate_multipleRefsForOneClaim_allReferencedCitationsIncluded() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("Neural networks [1][2] power deep learning.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "a.pdf", 0),
                citation(2, "b.pdf", 0)
        )));

        assertThat(result.citations()).hasSize(2);
    }

    @Test
    void generate_unreferencedCitationsNotIncludedInResponse() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("The answer is supported by [2].");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of(
                citation(1, "unused-a.pdf", 0),
                citation(2, "used.pdf",     1),
                citation(3, "unused-b.pdf", 2)
        )));

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filename()).isEqualTo("used.pdf");
    }

    @Test
    void generate_usageMetadataIsNull_totalTokensIsZero() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("An answer.");
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);

        RagResponse result = service.generate(builtContext("query", List.of()));

        assertThat(result.totalTokens()).isZero();
    }

    @Test
    void generate_usageMetadataPresent_totalTokensFromApiResponse() {
        givenChatClientReturns(chatResponse);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("An answer.");
        when(chatResponse.getMetadata().getUsage().getTotalTokens()).thenReturn(250);

        RagResponse result = service.generate(builtContext("query", List.of()));

        assertThat(result.totalTokens()).isEqualTo(250);
    }

    @Test
    void generate_chatClientThrowsRuntimeException_propagatesToCaller() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .chatResponse())
            .thenThrow(new RuntimeException("Chat API unavailable"));

        assertThatThrownBy(() -> service.generate(builtContext("query", List.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Chat API unavailable");
    }

    // --- helpers ---

    private BuiltContext builtContext(String userMessage, List<Citation> citations) {
        return new BuiltContext("You are a grounded assistant.", userMessage, citations);
    }

    private Citation citation(int ref, String filename, int chunkIndex) {
        return new Citation(ref, filename, chunkIndex, BigDecimal.valueOf(0.90), "Sample text for ref " + ref);
    }
}