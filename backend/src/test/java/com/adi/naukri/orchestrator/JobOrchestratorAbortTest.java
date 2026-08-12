package com.adi.naukri.orchestrator;

import com.adi.naukri.api.AccountInput;
import com.adi.naukri.automation.*;
import com.adi.naukri.report.ReportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that JobOrchestrator.stop() immediately calls abort()
 * on the running automator.
 */
class JobOrchestratorAbortTest {

    @TempDir
    Path tempDir;

    private JobOrchestrator orchestrator;

    @AfterEach
    void tearDown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
            orchestrator = null;
        }
    }

    @Test
    void stop_calls_abort_and_job_status_becomes_RUN_STOPPED_quickly()
            throws Exception {

        AtomicBoolean abortCalled = new AtomicBoolean(false);

        CountDownLatch runnerStarted = new CountDownLatch(1);
        CountDownLatch abortLatch = new CountDownLatch(1);

        Automator slowFake = new Automator() {

            private volatile Thread runnerThread;

            @Override
            public List<StepResult> run(
                    String email,
                    String name,
                    String password,
                    AutomationRunMode mode,
                    AutomatorConfig cfg,
                    PlaywrightSession session,
                    ManualLoginGate gate,
                    StepListener listener) {

                runnerThread = Thread.currentThread();
                runnerStarted.countDown();

                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return List.of(
                        StepResult.failure(
                                AutomationStep.LOGIN,
                                "STOPPED_BY_ABORT",
                                0L
                        )
                );
            }

            @Override
            public void abort() {
                abortCalled.set(true);
                abortLatch.countDown();

                Thread t = runnerThread;
                if (t != null) {
                    t.interrupt();
                }
            }
        };

        JobEventBus bus = new JobEventBus();
        ReportWriter writer = new ReportWriter();
        RetryPolicy policy = new RetryPolicy();

        orchestrator = new JobOrchestrator(
                slowFake,
                policy,
                writer,
                bus,
                () -> null,
                new RunRegistry()
        );

        List<JobEvent> events = new CopyOnWriteArrayList<>();

        CountDownLatch stopped = new CountDownLatch(1);

        bus.subscribe(null, event -> {
            events.add(event);

            if (event instanceof JobEvent.RunStopped) {
                stopped.countDown();
            }
        });

        AccountInput account =
                new AccountInput("Test User", "slow@test.com");

        JobRequest request = new JobRequest(
                List.of(account),
                "pw",
                false,
                false,
                tempDir.toString(),
                null,
                null,
                0L
        );

        JobHandle handle = orchestrator.start(request);

        assertTrue(
                runnerStarted.await(5, TimeUnit.SECONDS),
                "Fake automator never signalled it started"
        );

        long stopCalledAt = System.currentTimeMillis();

        orchestrator.stop(handle.jobId());

        assertTrue(
                abortLatch.await(3, TimeUnit.SECONDS),
                "abort() was never called after stop()"
        );

        assertTrue(
                abortCalled.get(),
                "abortCalled flag should be true"
        );

        assertTrue(
                stopped.await(10, TimeUnit.SECONDS),
                "RUN_STOPPED event not received within 10 seconds"
        );

        long elapsed =
                System.currentTimeMillis() - stopCalledAt;

        assertTrue(
                elapsed < 15_000,
                "stop() took too long (" + elapsed + " ms); expected < 15 s"
        );

        assertTrue(
                events.stream()
                        .anyMatch(e -> e instanceof JobEvent.RunStopped),
                "Expected RunStopped event"
        );

        assertFalse(
                events.stream()
                        .anyMatch(e -> e instanceof JobEvent.RunCompleted),
                "Did not expect RunCompleted when run was stopped"
        );

        handle.future().get(5, TimeUnit.SECONDS);
    }

    @Test
    void abort_is_idempotent_when_no_run_in_progress() {

        NaukriAutomator automator = new NaukriAutomator();

        assertDoesNotThrow(
                automator::abort,
                "abort() must not throw when idle"
        );

        assertDoesNotThrow(
                automator::abort,
                "abort() must not throw on second call"
        );
    }
}