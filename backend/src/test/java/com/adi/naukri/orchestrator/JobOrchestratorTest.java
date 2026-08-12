package com.adi.naukri.orchestrator;

import com.adi.naukri.api.AccountInput;
import com.adi.naukri.automation.*;
import com.adi.naukri.report.AccountStatus;
import com.adi.naukri.report.ReportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for JobOrchestrator.
 *
 * Uses a FAKE Automator — no real browser launched.
 */
class JobOrchestratorTest {

    @TempDir
    Path tempDir;

    private JobEventBus bus;
    private ReportWriter reportWriter;
    private RetryPolicy retryPolicy;
    private JobOrchestrator orchestrator;

    private static Automator fakeOk() {
        return (email, name, password, mode, cfg, session, gate, listener) -> {
            List<StepResult> steps = new ArrayList<>();

            for (AutomationStep s : AutomationStep.values()) {
                listener.onStepStarted(s);

                StepResult r = StepResult.success(s, 10L);

                listener.onStep(r);
                steps.add(r);
            }

            return steps;
        };
    }

    private static Automator fakeBlockingGate(
            CountDownLatch releaseSignal,
            CountDownLatch awaitingLatch) {

        return (email, name, password, mode, cfg, session, gate, listener) -> {

            awaitingLatch.countDown();

            gate.waitForResume(
                    email,
                    Duration.ofSeconds(30),
                    () -> releaseSignal.getCount() == 0
            );

            List<StepResult> steps = new ArrayList<>();

            listener.onStepStarted(AutomationStep.LOGIN);

            StepResult r =
                    StepResult.success(AutomationStep.LOGIN, 5L);

            listener.onStep(r);
            steps.add(r);

            return steps;
        };
    }

    private void makeOrchestrator(Automator automator) throws Exception {

        if (orchestrator != null) {
            orchestrator.shutdown();
        }

        bus = new JobEventBus();

        orchestrator = new JobOrchestrator(
                automator,
                retryPolicy,
                reportWriter,
                bus,
                () -> null,
                new RunRegistry()
        );
    }

    private static List<AccountInput> accounts(String... emails) {
        List<AccountInput> result = new ArrayList<>();

        for (String email : emails) {
            String name = email.substring(0, email.indexOf('@'));
            result.add(new AccountInput(name, email));
        }

        return result;
    }

    @BeforeEach
    void setUp() throws Exception {
        reportWriter = new ReportWriter();
        retryPolicy = new RetryPolicy();

        makeOrchestrator(fakeOk());
    }

    @AfterEach
    void tearDown() throws Exception {

        if (orchestrator != null) {
            orchestrator.shutdown();
            orchestrator = null;
        }
    }

    // -----------------------------------------------------------------------
    // TC-1
    // -----------------------------------------------------------------------

    @Test
    void threeAccountsAllOk_publishesFullEventSequenceAndWritesReport()
            throws Exception {

        List<AccountInput> accounts =
                accounts("a@a.com", "b@b.com", "c@c.com");

        JobRequest req = new JobRequest(
                accounts,
                "secret",
                false,
                false,
                tempDir.toString(),
                null,
                null,
                0L
        );

        List<JobEvent> events = new CopyOnWriteArrayList<>();

        CountDownLatch done = new CountDownLatch(1);

        bus.subscribe(null, e -> {
            events.add(e);

            if (e instanceof JobEvent.RunCompleted) {
                done.countDown();
            }
        });

        JobHandle handle = orchestrator.start(req);

        assertTrue(
                done.await(10, TimeUnit.SECONDS),
                "RunCompleted not received in time"
        );

        handle.future().get(2, TimeUnit.SECONDS);

        assertTrue(
                events.stream()
                        .anyMatch(e -> e instanceof JobEvent.RunStarted),
                "Expected RunStarted"
        );

        long accountStartedCount =
                events.stream()
                        .filter(e -> e instanceof JobEvent.AccountStarted)
                        .count();

        assertEquals(
                3,
                accountStartedCount,
                "Expected 3 AccountStarted events"
        );

        long accountOkCount =
                events.stream()
                        .filter(e ->
                                e instanceof JobEvent.AccountCompleted ac
                                        && ac.status() == AccountStatus.OK)
                        .count();

        assertEquals(
                3,
                accountOkCount,
                "Expected 3 AccountCompleted(OK)"
        );

        assertTrue(
                events.stream()
                        .anyMatch(e -> e instanceof JobEvent.RunCompleted),
                "Expected RunCompleted"
        );

        JobEvent.RunCompleted rc =
                events.stream()
                        .filter(e -> e instanceof JobEvent.RunCompleted)
                        .map(e -> (JobEvent.RunCompleted) e)
                        .findFirst()
                        .orElseThrow();

        assertEquals(3, rc.summary().ok());

        long stepStartedCount =
                events.stream()
                        .filter(e -> e instanceof JobEvent.StepStarted)
                        .count();

        assertTrue(
                stepStartedCount >= 3,
                "Expected at least one StepStarted per account, got "
                        + stepStartedCount
        );

        assertTrue(
                Files.walk(tempDir)
                        .anyMatch(p ->
                                p.getFileName()
                                        .toString()
                                        .equals("report.csv")),
                "report.csv not found under output folder"
        );
    }

    // -----------------------------------------------------------------------
    // TC-2
    // -----------------------------------------------------------------------

    @Test
    void stopMidRun_remainingAccountsSkippedAndRunStoppedEmitted()
            throws Exception {

        CountDownLatch firstAccountRunning =
                new CountDownLatch(1);

        CountDownLatch releaseFirst =
                new CountDownLatch(1);

        Automator blockingFake =
                (email, name, password, mode, cfg, session, gate, listener) -> {

                    List<StepResult> steps = new ArrayList<>();

                    listener.onStepStarted(AutomationStep.LOGIN);

                    StepResult r =
                            StepResult.success(
                                    AutomationStep.LOGIN,
                                    5L
                            );

                    listener.onStep(r);
                    steps.add(r);

                    if (email.equals("a@a.com")) {

                        firstAccountRunning.countDown();

                        try {
                            releaseFirst.await(
                                    5,
                                    TimeUnit.SECONDS
                            );
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    return steps;
                };

        makeOrchestrator(blockingFake);

        List<JobEvent> events =
                new CopyOnWriteArrayList<>();

        CountDownLatch stopped =
                new CountDownLatch(1);

        bus.subscribe(null, e -> {

            events.add(e);

            if (e instanceof JobEvent.RunStopped) {
                stopped.countDown();
            }
        });

        JobRequest req =
                new JobRequest(
                        accounts(
                                "a@a.com",
                                "b@b.com",
                                "c@c.com"
                        ),
                        "pwd",
                        false,
                        false,
                        tempDir.toString(),
                        null,
                        null,
                        0L
                );

        JobHandle handle =
                orchestrator.start(req);

        assertTrue(
                firstAccountRunning.await(
                        5,
                        TimeUnit.SECONDS
                ),
                "Worker never reached a@a.com"
        );

        orchestrator.stop(handle.jobId());

        releaseFirst.countDown();

        assertTrue(
                stopped.await(
                        5,
                        TimeUnit.SECONDS
                ),
                "RunStopped not emitted"
        );

        long skippedCount =
                events.stream()
                        .filter(e ->
                                e instanceof JobEvent.AccountCompleted ac
                                        && ac.status()
                                        == AccountStatus.SKIPPED)
                        .count();

        assertTrue(
                skippedCount >= 1,
                "Expected at least one SKIPPED, got "
                        + skippedCount
        );
    }

    // -----------------------------------------------------------------------
    // TC-3
    // -----------------------------------------------------------------------

    @Test
    void manualLoginGate_publishesAwaitManualLoginAndResumesOnContinueNow()
            throws Exception {

        CountDownLatch releaseSignal =
                new CountDownLatch(1);

        CountDownLatch awaitingLatch =
                new CountDownLatch(1);

        makeOrchestrator(
                fakeBlockingGate(
                        releaseSignal,
                        awaitingLatch
                )
        );

        List<JobEvent> events =
                new CopyOnWriteArrayList<>();

        CountDownLatch completed =
                new CountDownLatch(1);

        bus.subscribe(null, e -> {

            events.add(e);

            if (e instanceof JobEvent.RunCompleted
                    || e instanceof JobEvent.RunStopped) {

                completed.countDown();
            }
        });

        JobRequest req =
                new JobRequest(
                        accounts("gated@x.com"),
                        "pwd",
                        false,
                        true,
                        tempDir.toString(),
                        null,
                        null,
                        0L
                );

        JobHandle handle =
                orchestrator.start(req);

        assertTrue(
                awaitingLatch.await(
                        5,
                        TimeUnit.SECONDS
                ),
                "Automator did not reach the gate"
        );

        Thread.sleep(200);

        assertTrue(
                events.stream()
                        .anyMatch(e ->
                                e instanceof JobEvent.AwaitManualLogin),
                "Expected AwaitManualLogin event"
        );

        releaseSignal.countDown();

        orchestrator.continueNow(handle.jobId());

        assertTrue(
                completed.await(
                        10,
                        TimeUnit.SECONDS
                ),
                "Run did not complete after continueNow"
        );
    }

    // -----------------------------------------------------------------------
    // TC-4
    // -----------------------------------------------------------------------

    @Test
    void concurrentSecondStart_throwsIllegalStateException()
            throws Exception {

        CountDownLatch blocker =
                new CountDownLatch(1);

        Automator blockingFake =
                (email, name, password, mode, cfg, session, gate, listener) -> {

                    try {
                        blocker.await(
                                10,
                                TimeUnit.SECONDS
                        );
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }

                    return List.of(
                            StepResult.success(
                                    AutomationStep.LOGIN,
                                    5L
                            )
                    );
                };

        makeOrchestrator(blockingFake);

        JobRequest req =
                new JobRequest(
                        accounts("x@x.com"),
                        "pwd",
                        false,
                        false,
                        tempDir.toString(),
                        null,
                        null,
                        0L
                );

        orchestrator.start(req);

        assertThrows(
                IllegalStateException.class,
                () -> orchestrator.start(req),
                "Expected IllegalStateException for concurrent start"
        );

        blocker.countDown();
    }

    // -----------------------------------------------------------------------
    // TC-5
    // -----------------------------------------------------------------------

    @Test
    void singleEmailHappyPath_smoke()
            throws Exception {

        List<JobEvent> events =
                new CopyOnWriteArrayList<>();

        CountDownLatch done =
                new CountDownLatch(1);

        bus.subscribe(null, e -> {

            events.add(e);

            if (e instanceof JobEvent.RunCompleted) {
                done.countDown();
            }
        });

        JobRequest req =
                new JobRequest(
                        accounts("smoke@test.com"),
                        "pass",
                        false,
                        false,
                        tempDir.toString(),
                        null,
                        null,
                        0L
                );

        JobHandle handle =
                orchestrator.start(req);

        assertTrue(
                done.await(
                        10,
                        TimeUnit.SECONDS
                )
        );

        handle.future().get(
                2,
                TimeUnit.SECONDS
        );

        assertTrue(
                events.stream()
                        .anyMatch(e ->
                                e instanceof JobEvent.RunCompleted)
        );

        JobEvent.RunCompleted rc =
                events.stream()
                        .filter(e ->
                                e instanceof JobEvent.RunCompleted)
                        .map(e ->
                                (JobEvent.RunCompleted) e)
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                1,
                rc.summary().ok()
        );
    }
}