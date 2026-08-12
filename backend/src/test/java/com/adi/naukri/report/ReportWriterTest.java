package com.adi.naukri.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportWriterTest {

    @TempDir
    Path runDir;

    ReportWriter w = new ReportWriter();

    @Test
    void writes_csv_json_and_logs() throws Exception {

        var res = List.of(
            new AccountResult(
                "a@x.com",
                AccountStatus.OK,
                null,
                "cv.pdf",
                "cv_2026-07-14.pdf",
                Instant.parse("2026-07-14T10:00:00Z"),
                Instant.parse("2026-07-14T10:00:30Z"),
                0,
                List.of(
                    new StepTiming("login", 1500),
                    new StepTiming("logout", 500)
                )
            ),

            new AccountResult(
                "b@x.com",
                AccountStatus.AUTH_FAILED,
                "wrong password",
                null,
                null,
                Instant.parse("2026-07-14T10:00:30Z"),
                Instant.parse("2026-07-14T10:00:45Z"),
                1,
                List.of()
            )
        );

        w.write(runDir, res);

        Path csv = runDir.resolve("report.csv");
        Path json = runDir.resolve("report.json");
        Path log1 = runDir.resolve("logs/a@x.com.log");

        assertTrue(Files.exists(csv));
        assertTrue(Files.exists(json));
        assertTrue(Files.exists(log1));

        String csvText = Files.readString(csv);

        assertTrue(csvText.startsWith(
            "email,status,error,resumeOldName,resumeNewName,startedAt,endedAt,retries"
        ));

        assertTrue(csvText.contains(
            "a@x.com,OK,,cv.pdf,cv_2026-07-14.pdf,"
        ));

        assertTrue(csvText.contains(
            "b@x.com,AUTH_FAILED,wrong password,,,"
        ));

        var parsed = new ObjectMapper()
            .readTree(Files.readString(json));

        var accounts = parsed.get("accounts");

        assertNotNull(accounts);
        assertEquals(2, accounts.size());

        assertEquals(
            "OK",
            accounts.get(0).get("status").asText()
        );

        assertEquals(
            1500,
            accounts.get(0)
                .get("steps")
                .get(0)
                .get("durationMs")
                .asInt()
        );
    }
}