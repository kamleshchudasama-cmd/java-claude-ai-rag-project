package com.test.rag.controller;

import com.test.rag.model.CrawlSiteSummary;
import com.test.rag.model.CrawlStatusResponse;
import com.test.rag.service.crawl.CrawlJobStore;
import com.test.rag.service.crawl.WebCrawlerService;
import com.test.rag.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CrawlController.class)
class CrawlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private WebCrawlerService webCrawlerService;
    @MockBean private CrawlJobStore crawlJobStore;
    @MockBean private VectorStoreService vectorStoreService;

    @Test
    void startCrawl_returns_202_with_jobId() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "https://example.com"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());

        verify(webCrawlerService).crawl(eq("https://example.com"), anyString());
    }

    @Test
    void startCrawl_returns_400_for_invalid_url() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "not-a-url"))
                .andExpect(status().isBadRequest());

        verify(webCrawlerService, never()).crawl(any(), any());
    }

    @Test
    void startCrawl_returns_400_for_non_http_scheme() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "ftp://example.com"))
                .andExpect(status().isBadRequest());

        verify(webCrawlerService, never()).crawl(any(), any());
    }

    @Test
    void startCrawl_returns_400_for_blank_url() throws Exception {
        mockMvc.perform(post("/api/rag/crawl").param("url", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatus_returns_200_with_job_data() throws Exception {
        CrawlStatusResponse status = new CrawlStatusResponse(
                "job1", "RUNNING", 3, 2, 15, null);
        when(crawlJobStore.get("job1")).thenReturn(Optional.of(status));

        mockMvc.perform(get("/api/rag/crawl/job1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job1"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.pagesVisited").value(3));
    }

    @Test
    void getStatus_returns_404_for_unknown_jobId() throws Exception {
        when(crawlJobStore.get("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rag/crawl/unknown/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSites_returns_200_with_site_list() throws Exception {
        when(vectorStoreService.listCrawledSites()).thenReturn(List.of(
                new CrawlSiteSummary("https://example.com", 5, 40, "2026-05-14T10:00:00Z")));

        mockMvc.perform(get("/api/rag/crawl/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rootUrl").value("https://example.com"))
                .andExpect(jsonPath("$[0].pagesIngested").value(5));
    }

    @Test
    void deleteSite_returns_204_when_deleted() throws Exception {
        when(vectorStoreService.deleteByCrawlRoot("https://example.com")).thenReturn(true);

        mockMvc.perform(delete("/api/rag/crawl/sites").param("rootUrl", "https://example.com"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSite_returns_404_when_not_found() throws Exception {
        when(vectorStoreService.deleteByCrawlRoot("https://unknown.com")).thenReturn(false);

        mockMvc.perform(delete("/api/rag/crawl/sites").param("rootUrl", "https://unknown.com"))
                .andExpect(status().isNotFound());
    }
}
