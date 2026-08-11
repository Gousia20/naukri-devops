/**
 * TDD – RED tests written first, then ExcelDropzone implemented to make them GREEN.
 *
 * Created by: Adikarthik Gupta C B
 */
import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import type { ParsedEmailRow } from "../api/types";
import ExcelDropzone from "./ExcelDropzone";

const BASE = "*/api";

const CANNED_ROWS: ParsedEmailRow[] = [
  { email: "alice@example.com", rowIndex: 1 },
  { email: "invalid-email",     rowIndex: 2 },
];

const server = setupServer(
  http.post(`${BASE}/parse-excel`, () =>
    HttpResponse.json(CANNED_ROWS, { status: 200 })
  )
);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("ExcelDropzone", () => {
  it("calls onParsed with rows returned by /api/parse-excel after file is selected", async () => {
    const onParsed = vi.fn();
    render(<ExcelDropzone onParsed={onParsed} />);

    // Simulate a file-input change with a tiny Blob (msw handles the actual POST)
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(fileInput).toBeTruthy();

    const blob = new Blob(["dummy"], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
    const file = new File([blob], "emails.xlsx", { type: blob.type });

    await userEvent.upload(fileInput, file);

    await waitFor(() => expect(onParsed).toHaveBeenCalledWith(CANNED_ROWS));
  });

  it("renders a preview table with valid rows and an invalid-rows panel", async () => {
    const onParsed = vi.fn();
    render(<ExcelDropzone onParsed={onParsed} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const blob = new Blob(["dummy"], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
    const file = new File([blob], "emails.xlsx", { type: blob.type });

    await userEvent.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText("alice@example.com")).toBeInTheDocument();
      expect(screen.getByText("invalid-email")).toBeInTheDocument();
    });
  });

  it("shows an error message when the API returns 500", async () => {
    server.use(
      http.post(`${BASE}/parse-excel`, () =>
        new HttpResponse(null, { status: 500 })
      )
    );
    const onParsed = vi.fn();
    render(<ExcelDropzone onParsed={onParsed} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const blob = new Blob(["dummy"], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
    const file = new File([blob], "emails.xlsx", { type: blob.type });

    await userEvent.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(onParsed).not.toHaveBeenCalled();
  });
});
