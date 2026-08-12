/**
 * TDD – RED tests written first, then SetupScreen implemented to make them GREEN.
 *
 * Created by: Adikarthik Gupta C B
 */

import {
  describe,
  it,
  expect,
  vi,
  beforeAll,
  afterAll,
  afterEach,
} from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import SetupScreen from "./SetupScreen";
const BASE = "http://127.0.0.1:5000";

const server = setupServer(
  http.get(`${BASE}/api/health`, () =>
    HttpResponse.json({ status: "ok" }, { status: 200 })
  ),

  http.get(`${BASE}/template`, () =>
    new HttpResponse(
      new Uint8Array([0x50, 0x4b]).buffer as ArrayBuffer,
      {
        status: 200,
        headers: {
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        },
      }
    )
  ),

  http.post(`${BASE}/parse-excel`, () =>
    HttpResponse.json([], { status: 200 })
  )
);

beforeAll(() => {
  server.listen({ onUnhandledRequest: "warn" });
});

afterEach(() => {
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});

async function fillPassword(password: string) {
  const field = screen.getByTestId("password");
  await userEvent.type(field, password);
}

async function addEmailManually(email: string) {
  await userEvent.click(
    screen.getByRole("tab", { name: /enter manually/i })
  );

  const nameInput = screen.getByTestId("chip-name-input");
  const chipInput = screen.getByTestId("chip-input");

  await userEvent.type(nameInput, "A");
  await userEvent.type(chipInput, `${email}{Enter}`);
}

async function setOutputFolder(path: string) {
  const folderField = screen.getByTestId("output-folder");

  await userEvent.clear(folderField);
  await userEvent.type(folderField, path);
}

async function setResumeFolder(path: string) {
  const folderField = screen.getByTestId("resume-folder");

  await userEvent.clear(folderField);
  await userEvent.type(folderField, path);
}

describe("SetupScreen", () => {
  it("Start button is disabled when no emails are present", async () => {
    const onStart = vi.fn();

    render(<SetupScreen onStart={onStart} />);

    const startBtn = screen.getByTestId("start");

    expect(startBtn).toBeDisabled();
  });

  it("Start button is disabled when password is blank (even with emails)", async () => {
    const onStart = vi.fn();

    render(<SetupScreen onStart={onStart} />);

    await addEmailManually("a@x.com");
    await setOutputFolder("C:\\out");

    const startBtn = screen.getByTestId("start");

    expect(startBtn).toBeDisabled();
  });

  it("Start button is disabled when output folder is blank (emails + password filled)", async () => {
    const onStart = vi.fn();

    render(<SetupScreen onStart={onStart} />);

    await addEmailManually("a@x.com");
    await fillPassword("pass123");

    const startBtn = screen.getByTestId("start");

    expect(startBtn).toBeDisabled();
  });

  it("turning on manual-login forces headless=false AND disables the headless toggle", async () => {
    const onStart = vi.fn();

    render(<SetupScreen onStart={onStart} />);

    await addEmailManually("a@x.com");
    await fillPassword("pass123");
    await setOutputFolder("C:\\out");
    await setResumeFolder("C:\\resumes");

    const manualLoginToggle = screen.getByRole("checkbox", {
      name: /log in manually/i,
    });

    await userEvent.click(manualLoginToggle);

    const headlessToggle = screen.getByRole("checkbox", {
      name: /run browser visibly/i,
    });

    expect(headlessToggle).toBeDisabled();

    const startBtn = screen.getByTestId("start");

    expect(startBtn).not.toBeDisabled();

    await userEvent.click(startBtn);

    expect(onStart).toHaveBeenCalledWith(
      expect.objectContaining({
        headless: false,
        manualLogin: true,
      })
    );
  });

  it("Download Excel template click triggers GET /api/template", async () => {
    let templateFetched = false;

    server.use(
      http.get(`${BASE}/api/template`, () => {
        templateFetched = true;

        return new HttpResponse(
          new Uint8Array([0x50, 0x4b]).buffer as ArrayBuffer,
          {
            status: 200,
            headers: {
              "Content-Type":
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            },
          }
        );
      })
    );

    render(<SetupScreen onStart={vi.fn()} />);

    const link = screen.getByRole("button", {
      name: /download excel template/i,
    });

    const appendSpy = vi.spyOn(document.body, "appendChild");

    await userEvent.click(link);

    expect(appendSpy).toHaveBeenCalled();

    appendSpy.mockRestore();
  });

  it("clicking Start with valid inputs emits the correct payload", async () => {
    const onStart = vi.fn();

    render(<SetupScreen onStart={onStart} />);

    await addEmailManually("a@x.com");
    await fillPassword("MyPass1");
    await setOutputFolder("C:\\runs");
    await setResumeFolder("C:\\resumes");

    expect(screen.getByTestId("password")).toHaveValue("MyPass1");

    expect(screen.getByTestId("output-folder")).toHaveValue(
      "C:\\runs"
    );

    expect(screen.getByTestId("resume-folder")).toHaveValue(
      "C:\\resumes"
    );


    const startBtn = screen.getByTestId("start");

    expect(startBtn).not.toBeDisabled();

    await userEvent.click(startBtn);

    expect(onStart).toHaveBeenCalledOnce();

    expect(onStart).toHaveBeenCalledWith({
      accounts: [
        {
          email: "a@x.com",
          name: "A",
        },
      ],
      password: "MyPass1",
      headless: false,
      manualLogin: false,
      outputFolder: "C:\\runs",
      resumeFolderPath: "C:\\resumes",
    });
  });
});