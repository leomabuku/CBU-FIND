"use client";

import { useRef, useState } from "react";
import type { MembershipRecord, VerificationFields } from "@cbu-find/contracts";
import { api, readableError } from "./api-client";

const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
const ACCEPTED = ["image/jpeg", "image/png", "image/webp"];

type Scan = {
  requestId: string;
  fields: VerificationFields;
  confidence: Partial<Record<keyof VerificationFields, number>>;
  ocrProvider: string;
  extractionAvailable: boolean;
};

type Confirm = { status: string; autoApproved: boolean; matchOutcome: string };

const EMPTY: VerificationFields = { studentNumber: "", fullName: "", programme: "", academicYear: "" };

const STATUS_COPY: Record<string, { title: string; body: string }> = {
  UNVERIFIED: { title: "Membership not verified", body: "Upload a photo of your CBU student ID to report, claim and message." },
  PENDING: { title: "Verification in progress", body: "A moderator is reviewing your document. You will be notified when it is decided." },
  VERIFIED: { title: "Verified CBU student", body: "You can report items, claim them and message other students." },
  REJECTED: { title: "Verification not approved", body: "Check the reason below, then submit a clearer document." },
};

export function VerificationPanel({ membership, onChanged }: { membership: MembershipRecord | null; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [scan, setScan] = useState<Scan | null>(null);
  const [fields, setFields] = useState<VerificationFields>(EMPTY);
  const inputRef = useRef<HTMLInputElement>(null);

  const status = membership?.status ?? "UNVERIFIED";
  const copy = STATUS_COPY[status] ?? STATUS_COPY.UNVERIFIED;
  const request = membership?.request ?? null;
  const canSubmit = status === "UNVERIFIED" || status === "REJECTED";

  async function choose(file?: File) {
    if (!file) return;
    setError(""); setNotice("");
    if (!ACCEPTED.includes(file.type)) { setError("Choose a JPEG, PNG or WebP photograph of your student ID."); return; }
    if (file.size > MAX_DOCUMENT_BYTES) { setError("That image is larger than 5 MB. Take a lower-resolution photo and try again."); return; }
    setBusy(true);
    try {
      const imageBase64 = await readBase64(file);
      const result = await api<Scan>("/v1/verification/scan", { method: "POST", body: JSON.stringify({ imageBase64, contentType: file.type }) });
      setScan(result);
      setFields(result.fields);
      setNotice(result.extractionAvailable
        ? "Check the details we read from your card, correct anything wrong, then submit for review."
        : "Automatic reading is unavailable, so type the details exactly as printed. A moderator will compare them with your document.");
    } catch (caught) { setError(readableError(caught)); }
    finally { setBusy(false); if (inputRef.current) inputRef.current.value = ""; }
  }

  async function confirm(event: React.FormEvent) {
    event.preventDefault();
    if (!scan) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await api<Confirm>("/v1/verification/confirm", { method: "POST", body: JSON.stringify({ requestId: scan.requestId, fields }) });
      setScan(null);
      setNotice(result.autoApproved
        ? "Your student number matched university records, so your membership is verified."
        : "Submitted for moderator review. You will be notified when it is decided.");
      onChanged();
    } catch (caught) { setError(readableError(caught)); }
    finally { setBusy(false); }
  }

  return <div className="page">
    <header className="page-header"><div><span className="eyebrow">STUDENT MEMBERSHIP</span><h1>Verify your student ID</h1></div></header>
    <div className="form-card">
      <div className={`state-notice ${status === "VERIFIED" ? "" : "empty"}`}>
        <h3>{copy.title}</h3>
        <p>{copy.body}</p>
        {request?.decisionReason && <p className="muted">Moderator note: {request.decisionReason}</p>}
        {request && <p className="muted">Submitted {new Date(request.submittedAt).toLocaleString()} · state {request.status.replaceAll("_", " ").toLowerCase()}</p>}
      </div>

      <p className="muted">
        Your ID photo is stored privately. It is never published, never shown in a public profile, and only a moderator
        can open it while reviewing your request.
      </p>

      {canSubmit && !scan && <>
        <input ref={inputRef} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={(event) => void choose(event.target.files?.[0])} />
        <small>JPEG, PNG or WebP · up to 5 MB · the printed student number must be readable.</small>
      </>}

      {busy && !scan && <div className="loading-inline"><span className="spinner" />Uploading and reading your card…</div>}

      {scan && <form onSubmit={confirm}>
        <h3>Confirm the details on your card</h3>
        {(["studentNumber", "fullName", "programme", "academicYear"] as const).map((key) => <label className="field" key={key}>
          <span>
            {{ studentNumber: "Student number", fullName: "Full name", programme: "Programme", academicYear: "Academic year" }[key]}
            {scan.confidence[key] !== undefined && <em className="muted"> · read confidence {Math.round((scan.confidence[key] ?? 0) * 100)}%</em>}
          </span>
          <input
            value={fields[key]}
            required={key === "studentNumber" || key === "fullName"}
            maxLength={key === "studentNumber" ? 30 : key === "fullName" ? 100 : 120}
            onChange={(event) => setFields({ ...fields, [key]: event.target.value })}
          />
        </label>)}
        {scan.ocrProvider !== "not-configured" && <small>Fields were read automatically; always check them against your card.</small>}
        <div className="button-row">
          <button type="button" className="secondary" disabled={busy} onClick={() => { setScan(null); setNotice(""); }}>Cancel</button>
          <button className="primary" disabled={busy || !fields.studentNumber.trim() || !fields.fullName.trim()}>{busy ? "Submitting…" : "Submit for verification"}</button>
        </div>
      </form>}

      {notice && <div className="success-banner">{notice}</div>}
      {error && <div className="error-banner" role="alert"><strong>Action failed</strong><span>{error}</span></div>}
    </div>
  </div>;
}

function readBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("That file could not be read. Try choosing it again."));
    reader.onload = () => resolve(String(reader.result ?? "").split(",", 2)[1] ?? "");
    reader.readAsDataURL(file);
  });
}
