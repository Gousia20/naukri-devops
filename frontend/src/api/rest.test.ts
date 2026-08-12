import { describe, it, expect, beforeAll, afterAll, afterEach } from "vitest";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { startJob, stopJob, continueJob, skipJob, parseExcel } from "./rest";
import type { StartJobResponse, ParsedEmailRow } from "./types";

// Point all REST calls at port 5000 (default dev fallback)
const BASE = "/api";

const server = setupServer(
  http.post(`${BASE}/jobs`, async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    const response: StartJobResponse = {
      jobId: "test-job-1",
      wsUrl: `ws://127.0.0.1:5000/ws/jobs/test-job-1`
    };
    // Validate required fields were sent
    if (!body.emails || !body.password) {
      return HttpResponse.json({ error: "missing fields" }, { status: 400 });
    }
    return HttpResponse.json(response, { status: 200 });
  }),

  http.post(`${BASE}/jobs/:id/stop`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${BASE}/jobs/:id/continue`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${BASE}/jobs/:id/skip`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${BASE}/parse-excel`, () => {
    const rows: ParsedEmailRow[] = [
      { email: "alice@example.com", name: "Alice", rowIndex: 1 },
      { email: "bob@example.com", name: "Bob", rowIndex: 2 }
    ];
    return HttpResponse.json(rows, { status: 200 });
  })
);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("REST client", () => {
  it("startJob sends correct payload and returns StartJobResponse", async () => {
    const result = await startJob({
      emails: ["alice@example.com"],
      password: "secret123",
      headless: true,
      manualLogin: false,
      outputFolder: "/tmp/out"
    });
    expect(result.jobId).toBe("test-job-1");
    expect(result.wsUrl).toContain("test-job-1");
  });

  it("startJob throws on missing required fields (server returns 400)", async () => {
    server.use(
      http.post(`${BASE}/jobs`, () =>
        HttpResponse.json({ error: "missing fields" }, { status: 400 })
      )
    );
    await expect(
      startJob({ emails: [], password: "", headless: false, manualLogin: false, outputFolder: "" })
    ).rejects.toThrow("startJob failed: 400");
  });

  it("stopJob calls POST /jobs/:id/stop", async () => {
    await expect(stopJob("test-job-1")).resolves.toBeUndefined();
  });

  it("continueJob calls POST /jobs/:id/continue", async () => {
    await expect(continueJob("test-job-1")).resolves.toBeUndefined();
  });

  it("skipJob calls POST /jobs/:id/skip", async () => {
    await expect(skipJob("test-job-1")).resolves.toBeUndefined();
  });

  it("parseExcel POSTs multipart and returns ParsedEmailRow[]", async () => {
    const file = new File(["dummy xlsx content"], "emails.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    });
    const rows = await parseExcel(file);
    expect(rows).toHaveLength(2);
    expect(rows[0].email).toBe("alice@example.com");
    expect(rows[1].email).toBe("bob@example.com");
  });
});
