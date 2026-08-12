package com.adi.naukri.api;

import com.adi.naukri.orchestrator.JobHandle;
import com.adi.naukri.orchestrator.JobOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    JobOrchestrator orch;

    @Test
    void start_returns_jobId_and_wsUrl() throws Exception {
        when(orch.start(any()))
                .thenReturn(new JobHandle(
                        "job-123",
                        CompletableFuture.completedFuture(null)
                ));

        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accounts": [
                                {
                                  "name": "Test User",
                                  "email": "a@x.com"
                                }
                              ],
                              "password": "p",
                              "headless": false,
                              "manualLogin": false,
                              "outputFolder": "C:\\\\tmp\\\\r"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.wsUrl").value("/ws/jobs/job-123"));
    }

    @Test
    void stop_returns_204_and_calls_orchestrator() throws Exception {
        mvc.perform(post("/api/jobs/job-123/stop"))
                .andExpect(status().isNoContent());

        verify(orch).stop("job-123");
    }

    @Test
    void continue_returns_204_and_calls_orchestrator() throws Exception {
        mvc.perform(post("/api/jobs/job-456/continue"))
                .andExpect(status().isNoContent());

        verify(orch).continueNow("job-456");
    }

    @Test
    void skip_returns_204_and_calls_orchestrator() throws Exception {
        mvc.perform(post("/api/jobs/job-789/skip"))
                .andExpect(status().isNoContent());

        verify(orch).skip("job-789");
    }

    @Test
    void start_rejects_empty_accounts_with_400() throws Exception {
        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accounts": [],
                              "password": "p",
                              "headless": false,
                              "manualLogin": false,
                              "outputFolder": "C:\\\\tmp"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_rejects_blank_password_with_400() throws Exception {
        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accounts": [
                                {
                                  "name": "Test User",
                                  "email": "a@x.com"
                                }
                              ],
                              "password": "   ",
                              "headless": false,
                              "manualLogin": false,
                              "outputFolder": "C:\\\\tmp"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_returns_409_when_job_already_running() throws Exception {
        when(orch.start(any()))
                .thenThrow(new IllegalStateException(
                        "A job run is already in progress"
                ));

        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accounts": [
                                {
                                  "name": "Test User",
                                  "email": "a@x.com"
                                }
                              ],
                              "password": "pass",
                              "headless": false,
                              "manualLogin": false,
                              "outputFolder": "C:\\\\tmp\\\\r"
                            }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("job-already-running"));
    }
}