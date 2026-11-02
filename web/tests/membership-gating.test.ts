import { describe, expect, it } from "vitest";
import { canActFor } from "../app/portal-app";

describe("membership gating", () => {
  it("blocks visitors and unverified accounts", () => {
    expect(canActFor(null, "USER")).toBe(false);
    expect(canActFor({ status: "UNVERIFIED" }, "USER")).toBe(false);
    expect(canActFor({ status: "PENDING" }, "USER")).toBe(false);
    expect(canActFor({ status: "REJECTED" }, "USER")).toBe(false);
  });

  it("allows verified students", () => {
    expect(canActFor({ status: "VERIFIED" }, "USER")).toBe(true);
  });

  it("lets staff operate without student status, matching the API", () => {
    expect(canActFor({ status: "UNVERIFIED" }, "MODERATOR")).toBe(true);
    expect(canActFor(null, "ADMIN")).toBe(true);
  });
});
