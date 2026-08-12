package com.adi.naukri.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link NaukriAutomator} against the live mock-naukri jar.
 *
 * <p>Starts {@code ../mock-naukri/target/mock-naukri.jar} on a random free port in
 * {@code @BeforeAll}, drives four scenarios through a real Chromium browser, then
 * tears down in {@code @AfterAll}.</p>
 *
 * <p>Requires {@code mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI
 * -Dexec.args="install chromium"} to have been run once beforehand.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Tag("integration")
class NaukriAutomatorAgainstMockIT {

    static Process mock;
    static int port;
    static String baseUrl;

    @BeforeAll
    static void startMock() throws Exception {
        port = TestPorts.free();
        baseUrl = "http://127.0.0.1:" + port;

        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar",
                "../mock-naukri/target/mock-naukri.jar",
                "--server.port=" + port
        );
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        mock = pb.start();

        TestPorts.waitUntilOpen(port, Duration.ofSeconds(30));
    }

    @AfterAll
    static void stopMock() {
        if (mock != null) {
            mock.destroy();
        }
    }

    @BeforeEach
    void resetMockState() throws Exception {
        URL resetUrl = new URL(baseUrl + "/_mock/reset");
        HttpURLConnection conn = (HttpURLConnection) resetUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.getResponseCode();
        conn.disconnect();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: build a default AutomatorConfig
    // ─────────────────────────────────────────────────────────────────────────

    private AutomatorConfig cfg(boolean manualLogin) throws Exception {
        Path downloadsDir = Path.of(System.getProperty("java.io.tmpdir"), "na-it-" + port);
        return new AutomatorConfig(
                baseUrl,
                downloadsDir,
                30_000L,
                15_000L,
                manualLogin,
                Duration.ofMinutes(1)
        );
    }

    private StepListener noopListener() {
        return new StepListener() {
            @Override public void onStepStarted(AutomationStep step) {}
            @Override public void onStep(StepResult r) {}
            @Override public void onManualLoginAwait(String email) {}
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: happy path — all steps succeed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void full_happy_flow_completes_all_steps() throws Exception {
        try (PlaywrightSession session = new PlaywrightSession()) {
            List<StepResult> results = new NaukriAutomator().run(
                    "ok@x.com", "Test User", "password",
                    AutomationRunMode.HEADLESS,
                    cfg(false), session,
                    (email, timeout, dash) -> true,
                    noopListener()
            );

            // All 6 steps must succeed: LOGIN, HEADLINE_APPEND, HEADLINE_STRIP,
            // DOWNLOAD_RESUME, UPLOAD_RESUME, LOGOUT
            assertEquals(6, results.size(),
                    "Expected 6 step results but got: " + results);
            for (StepResult r : results) {
                assertTrue(r.ok(), "Step " + r.step() + " failed: " + r.error());
            }
        }

        // Assert mock recorded side-effects
        JsonNode state = fetchMockState();
        assertEquals(2, state.get("headlineSaveCount").asInt(),
                "Expected headlineSaveCount==2 (append + strip)");
        String uploaded = state.get("uploadedResumeName").asText(null);
        assertNotNull(uploaded, "Expected uploadedResumeName to be set");
        assertTrue(!uploaded.isBlank(), "uploadedResumeName must not be blank");
        assertTrue(state.get("loggedOut").asBoolean(), "Expected mock to record logout");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: bad credentials — AUTH_FAILED after LOGIN step
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void bad_credentials_short_circuits_with_auth_failed() throws Exception {
        try (PlaywrightSession session = new PlaywrightSession()) {
            List<StepResult> results = new NaukriAutomator().run(
                    "bad@x.com", "Test user", "wrongpassword",
                    AutomationRunMode.HEADLESS,
                    cfg(false), session,
                    (email, timeout, dash) -> true,
                    noopListener()
            );

            assertEquals(1, results.size(),
                    "Expected exactly 1 result (LOGIN failure)");
            StepResult loginResult = results.get(0);
            assertEquals(AutomationStep.LOGIN, loginResult.step());
            assertTrue(!loginResult.ok(), "Login should have failed");
            assertNotNull(loginResult.error());
            assertTrue(loginResult.error().contains("AUTH_FAILED"),
                    "Error should contain AUTH_FAILED, got: " + loginResult.error());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: OTP redirect — REQUIRES_MANUAL after LOGIN step
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void otp_page_short_circuits_with_requires_manual() throws Exception {
        try (PlaywrightSession session = new PlaywrightSession()) {
            List<StepResult> results = new NaukriAutomator().run(
                    "otp@x.com", "Test User",  "anypassword",
                    AutomationRunMode.HEADLESS,
                    cfg(false), session,
                    (email, timeout, dash) -> true,
                    noopListener()
            );

            assertEquals(1, results.size(),
                    "Expected exactly 1 result (LOGIN failure)");
            StepResult loginResult = results.get(0);
            assertEquals(AutomationStep.LOGIN, loginResult.step());
            assertTrue(!loginResult.ok(), "Login should have failed");
            assertNotNull(loginResult.error());
            assertTrue(loginResult.error().contains("REQUIRES_MANUAL"),
                    "Error should contain REQUIRES_MANUAL, got: " + loginResult.error());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: manual login gate — gate navigates browser via OTP form, run succeeds
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void manual_login_gate_resumes_when_dashboard_reached() throws Exception {
        try (PlaywrightSession session = new PlaywrightSession()) {
            // Gate implementation: uses the session's last opened page to navigate
            // to /otp, fills and submits the OTP form so the mock sets the session
            // cookie, then the dashboard check passes.
            ManualLoginGate testGate = (email, timeout, dashboardReached) -> {
                try {
                    // Browser is currently on /nlogin/login. Navigate to /otp to simulate
                    // the OTP redirect and then submit the OTP form.
                    Page page = session.lastPage();
                    page.navigate(baseUrl + "/otp");
                    page.fill(NaukriSelectors.OTP_INPUT, "123456");
                    page.click("button[type='submit']");
                    // Mock redirects to /mnjuser/homepage after OTP submit
                    page.waitForLoadState();
                    return dashboardReached.get();
                } catch (Exception ex) {
                    return false;
                }
            };

            List<StepResult> results = new NaukriAutomator().run(
                    "otp@x.com", "Test User", "anypassword",
                    AutomationRunMode.HEADLESS,
                    cfg(true), session,
                    testGate,
                    new StepListener() {
                        @Override public void onStepStarted(AutomationStep step) {}
                        @Override public void onStep(StepResult r) {}
                        @Override public void onManualLoginAwait(String e) {
                            // Gate will be called right after this; gate lambda emits AwaitManualLogin.
                        }
                    }
            );

            // Full happy path should succeed
            assertEquals(6, results.size(),
                    "Expected 6 step results (manual login succeeded) but got: " + results);
            for (StepResult r : results) {
                assertTrue(r.ok(), "Step " + r.step() + " failed: " + r.error());
            }
        }

        // Assert side effects
        JsonNode state = fetchMockState();
        assertEquals(2, state.get("headlineSaveCount").asInt());
        String uploaded = state.get("uploadedResumeName").asText(null);
        assertNotNull(uploaded);
        assertTrue(!uploaded.isBlank());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode fetchMockState() throws Exception {
        URL stateUrl = new URL(baseUrl + "/_mock/state");
        return ObjectMapperHolder.MAPPER.readTree(stateUrl.openStream());
    }
}
