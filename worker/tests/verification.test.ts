import { describe, expect, it } from "vitest";
import { canAutoApprove, type MatchResult } from "../src/verification/matcher";
import { detectImageType, membershipOf, normalizeStudentNumber, summaryOf } from "../src/verification";
import { parseStudentId } from "../src/verification/parse";

const SAMPLE_ID = `COPPERBELT UNIVERSITY
STUDENT IDENTITY CARD
NAME: CHANDA MULENGA
STUDENT NUMBER: 2021123456
PROGRAMME: BSc COMPUTER SCIENCE
ACADEMIC YEAR: 2025/2026`;

describe("student ID parsing", () => {
  it("extracts the fields printed on a typical CBU card", () => {
    const parsed = parseStudentId(SAMPLE_ID);
    expect(parsed.fields.studentNumber).toBe("2021123456");
    expect(parsed.fields.fullName).toBe("Chanda Mulenga");
    expect(parsed.fields.programme).toContain("COMPUTER SCIENCE");
    expect(parsed.fields.academicYear).toBe("2025/2026");
    expect(parsed.confidence.studentNumber).toBeGreaterThan(0.5);
  });

  it("leaves fields blank instead of inventing values", () => {
    const parsed = parseStudentId("COPPERBELT UNIVERSITY\nKEEP THIS CARD SAFE");
    expect(parsed.fields.studentNumber).toBe("");
    expect(parsed.confidence.studentNumber).toBeUndefined();
  });

  it("reads a value printed on the line after its label", () => {
    const parsed = parseStudentId("STUDENT NUMBER\n2019001234\nNAME:\nMUTALE BWALYA");
    expect(parsed.fields.studentNumber).toBe("2019001234");
    expect(parsed.fields.fullName).toBe("Mutale Bwalya");
  });
});

describe("document validation", () => {
  const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0, 0, 0, 0, 0]);
  const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]);
  const webp = new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]);

  it("identifies supported images from their magic bytes", () => {
    expect(detectImageType(jpeg)).toBe("image/jpeg");
    expect(detectImageType(png)).toBe("image/png");
    expect(detectImageType(webp)).toBe("image/webp");
  });

  it("rejects anything that is not a JPEG, PNG or WebP", () => {
    expect(detectImageType(new Uint8Array([0x25, 0x50, 0x44, 0x46, 0, 0, 0, 0, 0, 0, 0, 0]))).toBeNull();
    expect(detectImageType(new Uint8Array(4))).toBeNull();
  });
});

describe("record matching policy", () => {
  const strong: MatchResult = { outcome: "MATCHED", confidence: 0.97, provider: "cbu-record-api" };

  it("never auto-approves when no record source is configured", () => {
    expect(canAutoApprove({ outcome: "MATCHED", confidence: 1, provider: "not-configured" }, 1)).toBe(false);
    expect(canAutoApprove({ outcome: "UNAVAILABLE", confidence: 0, provider: "not-configured" }, 1)).toBe(false);
  });

  it("never auto-approves from OCR alone or a weak match", () => {
    expect(canAutoApprove(strong, 0.4)).toBe(false);
    expect(canAutoApprove({ ...strong, confidence: 0.6 }, 0.95)).toBe(false);
    expect(canAutoApprove({ ...strong, outcome: "AMBIGUOUS" }, 0.95)).toBe(false);
    expect(canAutoApprove({ ...strong, outcome: "NO_MATCH" }, 0.95)).toBe(false);
  });

  it("auto-approves only a strong record match with a confidently read student number", () => {
    expect(canAutoApprove(strong, 0.9)).toBe(true);
  });
});

describe("membership projection", () => {
  it("defaults to unverified for accounts that never submitted", () => {
    expect(membershipOf(null, null)).toEqual({ status: "UNVERIFIED", institutionId: "cbu", updatedAt: 0, request: null });
  });

  it("never exposes a document path or hash to a client", () => {
    const summary = summaryOf({ id: "r1", status: "APPROVED", uid: "u1", documentPath: "verification/u1/x.jpg", documentHash: "abc", submittedAt: 5, updatedAt: 6 });
    expect(summary).not.toHaveProperty("documentPath");
    expect(summary).not.toHaveProperty("documentHash");
    expect(JSON.stringify(summary)).not.toContain("verification/u1");
  });

  it("normalises student numbers before they are used as directory keys", () => {
    expect(normalizeStudentNumber(" 2021/123-456 ")).toBe("2021123456");
    expect(normalizeStudentNumber("cbu/21/001")).toBe("CBU21001");
  });
});
