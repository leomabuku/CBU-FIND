"use client";

import { useEffect, useState } from "react";
import { getToken } from "firebase/app-check";
import type { VerificationRequestSummary } from "@cbu-find/contracts";
import { api, readableError } from "./api-client";
import { appCheck, auth } from "./firebase";

type QueueItem = VerificationRequestSummary & { uid: string; documentBytes: number };

export function ModerationVerifications() {
  const [items, setItems] = useState<QueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [openId, setOpenId] = useState("");
  const [documentUrl, setDocumentUrl] = useState("");
  const [reason, setReason] = useState("");
  const [workingId, setWorkingId] = useState("");

  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let active = true;
    api<{ requests: QueueItem[] }>("/v1/admin/verifications")
      .then((result) => { if (active) setItems(result.requests); })
      .catch((caught) => { if (active) setError(readableError(caught)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [refreshKey]);
  useEffect(() => () => { if (documentUrl) URL.revokeObjectURL(documentUrl); }, [documentUrl]);

  async function openDocument(request: QueueItem) {
    setError(""); setNotice("");
    if (openId === request.id) { setOpenId(""); setDocumentUrl(""); return; }
    try {
      // The document endpoint requires the ID token and App Check header, so it
      // cannot be used as a bare <img src>. Fetch it, then render a local blob.
      const check = appCheck();
      if (!check || !auth?.currentUser) throw new Error("Sign in again to view documents.");
      const [idToken, appCheckToken] = await Promise.all([auth.currentUser.getIdToken(), getToken(check)]);
      const response = await fetch(`/api/cbu/v1/admin/verifications/${encodeURIComponent(request.id)}/document`, {
        headers: { authorization: `Bearer ${idToken}`, "x-firebase-appcheck": appCheckToken.token },
      });
      if (!response.ok) throw new Error("The document could not be opened.");
      setDocumentUrl(URL.createObjectURL(await response.blob()));
      setOpenId(request.id);
      setReason("");
    } catch (caught) { setError(readableError(caught)); }
  }

  async function decide(request: QueueItem, decision: "APPROVE" | "REJECT" | "NEEDS_RESUBMISSION") {
    if (decision !== "APPROVE" && !reason.trim()) { setError("Give the student a reason before rejecting or requesting a new document."); return; }
    setWorkingId(request.id); setError(""); setNotice("");
    try {
      await api(`/v1/admin/verifications/${encodeURIComponent(request.id)}/decision`, { method: "POST", body: JSON.stringify({ decision, reason: reason.trim() }) });
      setNotice(decision === "APPROVE" ? "Membership approved." : decision === "REJECT" ? "Verification rejected and the student notified." : "Resubmission requested.");
      setOpenId(""); setDocumentUrl(""); setReason("");
      setRefreshKey((value) => value + 1);
    } catch (caught) { setError(readableError(caught)); }
    finally { setWorkingId(""); }
  }

  if (loading) return <div className="loading-inline"><span className="spinner" />Loading verification queue…</div>;

  return <div className="form-card">
    <h3>Student ID verification queue</h3>
    <p className="muted">Compare the private document with the confirmed details. Approving grants verified membership; it cannot be undone here.</p>
    {notice && <div className="success-banner">{notice}</div>}
    {error && <div className="error-banner" role="alert"><strong>Action failed</strong><span>{error}</span></div>}
    {items.length === 0 ? <p className="muted">No requests are awaiting review.</p> : <div className="case-list">
      {items.map((request) => <article key={request.id}>
        <header>
          <span className="pill neutral">{request.status.replaceAll("_", " ")}</span>
          <strong>{request.fields.fullName || "Unnamed submission"}</strong>
          <time>{new Date(request.submittedAt).toLocaleString()}</time>
        </header>
        <dl>
          <div><dt>Student number</dt><dd>{request.fields.studentNumber || "—"} <span className="muted">({Math.round((request.confidence.studentNumber ?? 0) * 100)}% read)</span></dd></div>
          <div><dt>Programme</dt><dd>{request.fields.programme || "—"}</dd></div>
          <div><dt>Academic year</dt><dd>{request.fields.academicYear || "—"}</dd></div>
          <div><dt>Record match</dt><dd>{request.matchOutcome} · {Math.round(request.matchConfidence * 100)}%</dd></div>
          <div><dt>OCR</dt><dd>{request.ocrProvider} · {Math.round(request.ocrConfidence * 100)}%</dd></div>
          <div><dt>Account</dt><dd><code>{request.uid}</code></dd></div>
        </dl>
        <div className="button-row">
          <button className="secondary small" onClick={() => void openDocument(request)}>{openId === request.id ? "Close document" : "View ID document"}</button>
        </div>
        {openId === request.id && documentUrl && <img className="detail-image" src={documentUrl} alt="Student ID document under review" />}
        <label className="field"><span>Reason (required unless approving)</span>
          <input value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="e.g. Student number does not match the card" />
        </label>
        <div className="row-actions">
          <button className="primary small" disabled={workingId === request.id} onClick={() => void decide(request, "APPROVE")}>Approve</button>
          <button className="secondary small" disabled={workingId === request.id} onClick={() => void decide(request, "NEEDS_RESUBMISSION")}>Request new document</button>
          <button className="secondary small" disabled={workingId === request.id} onClick={() => void decide(request, "REJECT")}>Reject</button>
        </div>
      </article>)}
    </div>}
  </div>;
}
