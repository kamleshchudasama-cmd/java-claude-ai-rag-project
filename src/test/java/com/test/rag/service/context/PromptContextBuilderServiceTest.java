package com.test.rag.service.context;

import com.test.rag.config.RagProperties;
import com.test.rag.model.BuiltContext;
import com.test.rag.model.Citation;
import com.test.rag.model.DocumentChunk;
import com.test.rag.model.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptContextBuilderServiceTest {

    private RagProperties props;
    private ContextBuilderService service;

    @BeforeEach
    void setUp() {
        props = new RagProperties(); // default maxContextTokens = 4096
        service = new PromptContextBuilderService(props);
    }

    // -------------------------------------------------------------------------
    // Empty input
    // -------------------------------------------------------------------------

    @Test
    void build_emptyChunks_returnsEmptyCitations() {
        BuiltContext result = service.build("What is AI?", List.of());

        assertThat(result.citations()).isEmpty();
    }

    @Test
    void build_emptyChunks_systemPromptIsRenderedWithoutContextPlaceholder() {
        BuiltContext result = service.build("What is AI?", List.of());

        // Template must be rendered — raw {context} placeholder must not remain
        assertThat(result.systemPrompt()).doesNotContain("{context}");
    }

    @Test
    void build_emptyChunks_userQueryPreservedInBuiltContext() {
        BuiltContext result = service.build("Explain transformers", List.of());

        assertThat(result.userMessage()).isEqualTo("Explain transformers");
    }

    // -------------------------------------------------------------------------
    // Single chunk — citation field correctness
    // -------------------------------------------------------------------------

    @Test
    void build_singleChunk_returnsOneCitation() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "AI is transformative", 0, 0.91, "report.pdf")));

        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void build_singleChunk_citationRefIsOne() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "AI content", 0, 0.91, "doc.pdf")));

        assertThat(result.citations().get(0).ref()).isEqualTo(1);
    }

    @Test
    void build_singleChunk_citationHasCorrectFilename() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "Content", 2, 0.88, "technical-report.pdf")));

        assertThat(result.citations().get(0).filename()).isEqualTo("technical-report.pdf");
    }

    @Test
    void build_singleChunk_citationHasCorrectChunkIndex() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "Content", 5, 0.88, "doc.pdf")));

        assertThat(result.citations().get(0).chunkIndex()).isEqualTo(5);
    }

    @Test
    void build_singleChunk_citationHasCorrectSimilarityScore() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "Content", 0, 0.873, "doc.pdf")));

        assertThat(result.citations().get(0).score()).isEqualTo(BigDecimal.valueOf(0.873));
    }

    @Test
    void build_singleChunk_citationHasCorrectChunkText() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "Machine learning is powerful", 0, 0.90, "doc.pdf")));

        assertThat(result.citations().get(0).chunkText()).isEqualTo("Machine learning is powerful");
    }

    // -------------------------------------------------------------------------
    // System prompt content
    // -------------------------------------------------------------------------

    @Test
    void build_singleChunk_systemPromptContainsChunkContent() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "Deep learning uses neural networks", 0, 0.91, "doc.pdf")));

        assertThat(result.systemPrompt()).contains("Deep learning uses neural networks");
    }

    @Test
    void build_singleChunk_systemPromptFormatsChunkWithBracketedRefNumber() {
        BuiltContext result = service.build("query",
                List.of(scoredChunk("c1", "AI content here", 0, 0.91, "doc.pdf")));

        assertThat(result.systemPrompt()).contains("[1] AI content here");
    }

    @Test
    void build_multipleChunks_systemPromptContainsAllRefNumbers() {
        List<ScoredChunk> chunks = List.of(
                scoredChunk("c1", "First content",  0, 0.95, "a.pdf"),
                scoredChunk("c2", "Second content", 0, 0.85, "b.pdf")
        );

        BuiltContext result = service.build("query", chunks);

        assertThat(result.systemPrompt()).contains("[1]");
        assertThat(result.systemPrompt()).contains("[2]");
    }

    // -------------------------------------------------------------------------
    // Multiple chunks — sort order and citation numbering
    // -------------------------------------------------------------------------

    @Test
    void build_multipleChunks_citationRefsStartAtOneAndIncrement() {
        List<ScoredChunk> chunks = List.of(
                scoredChunk("c1", "First",  0, 0.95, "a.pdf"),
                scoredChunk("c2", "Second", 0, 0.85, "b.pdf"),
                scoredChunk("c3", "Third",  0, 0.78, "c.pdf")
        );

        BuiltContext result = service.build("query", chunks);

        List<Integer> refs = result.citations().stream().map(Citation::ref).toList();
        assertThat(refs).containsExactly(1, 2, 3);
    }

    @Test
    void build_chunksPassedInAnyOrder_sortedByScoreDescendingBeforeAssigningRefs() {
        // Low-score chunk is passed first but must end up as [3], not [1]
        ScoredChunk low    = scoredChunk("c-low",  "Low score chunk",    0, 0.78, "doc.pdf");
        ScoredChunk high   = scoredChunk("c-high", "High score chunk",   0, 0.95, "doc.pdf");
        ScoredChunk medium = scoredChunk("c-med",  "Medium score chunk", 0, 0.85, "doc.pdf");

        BuiltContext result = service.build("query", List.of(low, high, medium));

        // Ref [1] must be the highest-scoring chunk
        assertThat(result.citations().get(0).ref()).isEqualTo(1);
        assertThat(result.citations().get(0).score()).isEqualTo(BigDecimal.valueOf(0.95));
        assertThat(result.citations().get(0).chunkText()).isEqualTo("High score chunk");
    }

    @Test
    void build_multipleChunks_lowestScoringChunkIsLastCitation() {
        ScoredChunk high   = scoredChunk("c1", "High content",   0, 0.95, "a.pdf");
        ScoredChunk medium = scoredChunk("c2", "Medium content", 0, 0.85, "b.pdf");
        ScoredChunk low    = scoredChunk("c3", "Low content",    0, 0.78, "c.pdf");

        BuiltContext result = service.build("query", List.of(high, medium, low));

        Citation last = result.citations().get(result.citations().size() - 1);
        assertThat(last.score()).isEqualTo(BigDecimal.valueOf(0.78));
        assertThat(last.chunkText()).isEqualTo("Low content");
    }

    @Test
    void build_multipleChunks_allIncludedWhenUnderTokenBudget() {
        List<ScoredChunk> chunks = List.of(
                scoredChunk("c1", "Short A", 0, 0.95, "a.pdf"),
                scoredChunk("c2", "Short B", 0, 0.85, "b.pdf")
        );

        BuiltContext result = service.build("query", chunks);

        assertThat(result.citations()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // Token budget — low-scoring chunks dropped when budget exceeded
    // -------------------------------------------------------------------------

    @Test
    void build_chunksExceedTokenBudget_lowerScoringChunkDropped() {
        // maxContextTokens = 5:
        // "[1] ABC" = 7 chars → ceil(7/4.0) = 2 tokens — fits
        // "[2] <long>" = many tokens — exceeds budget → dropped
        props.setMaxContextTokens(5);
        ScoredChunk highScore = scoredChunk("c1", "ABC", 0, 0.95, "a.pdf");
        ScoredChunk lowScore  = scoredChunk("c2",
                "This is a much longer content string that will push the token budget over the limit",
                0, 0.80, "b.pdf");

        BuiltContext result = service.build("query", List.of(highScore, lowScore));

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).score()).isEqualTo(BigDecimal.valueOf(0.95));
    }

    @Test
    void build_budgetTruncation_highestScoringChunkAlwaysIncluded() {
        props.setMaxContextTokens(5);
        // High-score chunk must be [1] and must survive truncation
        ScoredChunk high = scoredChunk("c1", "XYZ", 0, 0.99, "keep.pdf");
        ScoredChunk low  = scoredChunk("c2",
                "This is a very long content string that definitely exceeds budget",
                0, 0.70, "drop.pdf");

        BuiltContext result = service.build("query", List.of(low, high));

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filename()).isEqualTo("keep.pdf");
    }

    // -------------------------------------------------------------------------
    // Metadata fallback
    // -------------------------------------------------------------------------

    @Test
    void build_filenameNotInChunkMetadata_usesUnknownFallback() {
        DocumentChunk chunk = new DocumentChunk("c1", "Some content", 0, 5, Map.of());
        ScoredChunk scored  = new ScoredChunk(chunk, BigDecimal.valueOf(0.90));

        BuiltContext result = service.build("query", List.of(scored));

        assertThat(result.citations().get(0).filename()).isEqualTo("unknown");
    }

    // -------------------------------------------------------------------------
    // User query — must be echoed into BuiltContext.userMessage unchanged
    // -------------------------------------------------------------------------

    @Test
    void build_userQueryPreservedInBuiltContextRegardlessOfChunks() {
        String query = "How does backpropagation work?";
        ScoredChunk chunk = scoredChunk("c1", "Backprop explanation", 0, 0.91, "ml.pdf");

        BuiltContext result = service.build(query, List.of(chunk));

        assertThat(result.userMessage()).isEqualTo(query);
    }

    @Test
    void build_userQueryPreservedWhenNoChunks() {
        String query = "What is entropy?";

        BuiltContext result = service.build(query, List.of());

        assertThat(result.userMessage()).isEqualTo(query);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ScoredChunk scoredChunk(String chunkId, String content, int chunkIndex,
                                     double score, String filename) {
        DocumentChunk chunk = new DocumentChunk(
                chunkId, content, chunkIndex,
                (int) Math.ceil(content.length() / 4.0),
                Map.of("filename", filename)
        );
        return new ScoredChunk(chunk, BigDecimal.valueOf(score));
    }
}
