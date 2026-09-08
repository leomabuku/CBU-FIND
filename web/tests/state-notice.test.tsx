import "@testing-library/jest-dom/vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ClientError, messageRequestBody, readableError } from "../app/api-client";
import { StateNotice } from "../app/portal-app";

describe("StateNotice", () => {
  it("renders an actionable error without pretending the data is empty", () => {
    const retry = vi.fn();
    render(<StateNotice kind="error" title="Inbox could not load" message="Check your connection. Reference: TEST-1" action="Retry" onAction={retry} />);
    expect(screen.getByText("Inbox could not load")).toBeInTheDocument();
    expect(screen.getByText(/Reference: TEST-1/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(retry).toHaveBeenCalledOnce();
  });
});

describe("authentication errors", () => {
  it("explains Google domain and popup failures", () => {
    expect(readableError({ code: "auth/unauthorized-domain" })).toContain("WEB-AUTH-DOMAIN");
    expect(readableError({ code: "auth/popup-blocked" })).toContain("WEB-AUTH-POPUP-BLOCKED");
  });
});

describe("message requests", () => {
  it("omits a null attachment from text-only replies", () => {
    expect(JSON.parse(messageRequestBody("Hello", null))).toEqual({ text: "Hello" });
  });

  it("shows the invalid field and stable reference for validation failures", () => {
    const error = new ClientError("VALIDATION_FAILED", "Some information is invalid.", false, "TEST-VALIDATION", { attachment: "Add a valid attachment." });
    expect(readableError(error)).toContain("Attachment: Add a valid attachment.");
    expect(readableError(error)).toContain("Reference: TEST-VALIDATION");
  });
});
