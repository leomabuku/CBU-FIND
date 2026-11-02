import type { FieldConfidence, VerificationFields } from "@cbu-find/contracts";

export interface ParsedStudentId {
  fields: VerificationFields;
  confidence: FieldConfidence;
}

const LABELS: Record<keyof VerificationFields, RegExp> = {
  // \b keeps "STUDENT IDENTITY CARD" from matching as a "student ID" label.
  studentNumber: /(?:student\s*(?:number|no\.|id|reg(?:istration)?\s*(?:number|no\.)?)\b|reg\.?\s*no\.?\b|id\s*no\.?\b)/i,
  fullName: /(?:full\s*name|name\s*of\s*student|student\s*name|^name)\b/i,
  programme: /(?:programme|program|course|degree|faculty|school)\b/i,
  academicYear: /(?:academic\s*year|year\s*of\s*study|valid|session)\b/i,
};

const YEAR_PATTERN = /\b(20\d{2})\s*[\/\-–]\s*(20\d{2})\b/;
const STUDENT_NUMBER_PATTERN = /\b([A-Z]{0,4}[\/\-]?\d{5,10}[A-Z]?)\b/i;

/**
 * Best-effort field extraction from OCR text. Every field carries a confidence
 * score and anything uncertain is simply left blank for the student to correct;
 * a moderator always makes the final decision.
 */
export function parseStudentId(text: string): ParsedStudentId {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const fields: VerificationFields = { studentNumber: "", fullName: "", programme: "", academicYear: "" };
  const confidence: FieldConfidence = {};

  for (const [field, pattern] of Object.entries(LABELS) as Array<[keyof VerificationFields, RegExp]>) {
    const value = valueAfterLabel(lines, pattern);
    if (!value) continue;
    // A student number must actually contain digits; OCR often matches the
    // words "STUDENT ID" on the card header instead of the number itself.
    if (field === "studentNumber" && !/\d{4,}/.test(value.replace(/\s+/g, ""))) continue;
    fields[field] = field === "fullName" ? smartName(value) : value;
    confidence[field] = 0.75;
  }

  if (!fields.studentNumber) {
    const candidate = lines.map((line) => STUDENT_NUMBER_PATTERN.exec(line)?.[1]).find((match) => match && /\d{4,}/.test(match));
    if (candidate) { fields.studentNumber = candidate.toUpperCase(); confidence.studentNumber = 0.5; }
  }
  if (!fields.academicYear) {
    const candidate = YEAR_PATTERN.exec(text)?.[0];
    if (candidate) { fields.academicYear = candidate.replace(/\s+/g, ""); confidence.academicYear = 0.55; }
  }
  if (!fields.fullName) {
    // Longest multi-word line written in capitals is usually the holder's name.
    const candidate = lines
      .filter((line) => /^[A-Z][A-Z\s.'-]{5,}$/.test(line) && line.split(/\s+/).length >= 2)
      .sort((left, right) => right.length - left.length)[0];
    if (candidate) { fields.fullName = smartName(candidate); confidence.fullName = 0.45; }
  }

  fields.studentNumber = fields.studentNumber.replace(/\s+/g, "").toUpperCase();
  return { fields, confidence };
}

/** Reads "Label: value", or the following line when the label stands alone. */
function valueAfterLabel(lines: string[], pattern: RegExp): string {
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (!pattern.test(line)) continue;
    const inline = line.replace(pattern, "").replace(/^[\s:.\-–]+/, "").trim();
    if (inline.length >= 2) return inline;
    // Label on its own line: the value is usually the next readable line.
    const next = lines[index + 1]?.trim();
    if (next && next.length >= 2 && /^[A-Za-z]/.test(next)) return next;
  }
  return "";
}

/** "CHANDA MULENGA" becomes "Chanda Mulenga"; mixed-case names are left alone. */
function smartName(value: string): string {
  const trimmed = value.replace(/\s+/g, " ").trim();
  if (!/^[A-Z\s.'-]+$/.test(trimmed)) return trimmed;
  return titleCase(trimmed);
}

function titleCase(value: string): string {
  return value.toLowerCase().replace(/\b[a-z]/g, (letter) => letter.toUpperCase()).trim();
}
