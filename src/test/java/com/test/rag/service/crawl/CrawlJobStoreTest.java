package com.test.rag.service.crawl;

import com.test.rag.model.CrawlStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CrawlJobStoreTest {

    private CrawlJobStore store;

    @BeforeEach
    void setUp() {
        store = new CrawlJobStore();
    }

    @Test
    void create_stores_running_state_with_zero_counts() {
        store.create("job1", "RUNNING");

        CrawlStatusResponse result = store.get("job1").orElseThrow();
        assertEquals("job1", result.jobId());
        assertEquals("RUNNING", result.status());
        assertEquals(0, result.pagesVisited());
        assertEquals(0, result.pagesIngested());
        assertEquals(0, result.totalChunks());
        assertNull(result.errorMessage());
    }

    @Test
    void update_replaces_entire_job_state() {
        store.create("job1", "RUNNING");
        store.update("job1", "DONE", 10, 9, 72, null);

        CrawlStatusResponse result = store.get("job1").orElseThrow();
        assertEquals("DONE", result.status());
        assertEquals(10, result.pagesVisited());
        assertEquals(9, result.pagesIngested());
        assertEquals(72, result.totalChunks());
        assertNull(result.errorMessage());
    }

    @Test
    void update_stores_error_message_on_failure() {
        store.create("job1", "RUNNING");
        store.update("job1", "FAILED", 0, 0, 0, "Cannot reach URL");

        CrawlStatusResponse result = store.get("job1").orElseThrow();
        assertEquals("FAILED", result.status());
        assertEquals("Cannot reach URL", result.errorMessage());
    }

    @Test
    void get_returns_empty_for_unknown_job_id() {
        Optional<CrawlStatusResponse> result = store.get("unknown");
        assertTrue(result.isEmpty());
    }

    @Test
    void multiple_jobs_are_stored_independently() {
        store.create("job1", "RUNNING");
        store.create("job2", "RUNNING");
        store.update("job1", "DONE", 5, 5, 40, null);

        assertEquals("DONE", store.get("job1").orElseThrow().status());
        assertEquals("RUNNING", store.get("job2").orElseThrow().status());
    }
}
