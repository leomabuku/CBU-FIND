"use client";

import { useEffect, useState } from "react";
import { GoogleAuthProvider, onAuthStateChanged, sendPasswordResetEmail, signInWithEmailAndPassword, signInWithPopup, signOut } from "firebase/auth";
import { api, readableError } from "../api-client";
import { auth, firebaseConfigured } from "../firebase";

/**
 * Public deletion page. Required so a former user can delete their account after
 * uninstalling the app, without downloading anything. It only signs the person
 * in and queues the same deletion job the apps use.
 */
export function DeleteAccountClient() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [signedIn, setSignedIn] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reference, setReference] = useState("");
  const [resetSent, setResetSent] = useState(false);

  useEffect(() => {
    if (!auth) return;
    return onAuthStateChanged(auth, (user) => setSignedIn(Boolean(user)));
  }, []);

  async function signIn(event: React.FormEvent) {
    event.preventDefault();
    if (!auth) return;
    setBusy(true); setError("");
    try { await signInWithEmailAndPassword(auth, email.trim(), password); }
    catch (caught) { setError(readableError(caught)); }
    finally { setBusy(false); }
  }

  async function google() {
    if (!auth) return;
    setBusy(true); setError("");
    try { await signInWithPopup(auth, new GoogleAuthProvider()); }
    catch (caught) { setError(readableError(caught)); }
    finally { setBusy(false); }
  }

  async function reset() {
    if (!auth) return;
    setError("");
    try { await sendPasswordResetEmail(auth, email.trim()); setResetSent(true); }
    catch (caught) { setError(readableError(caught)); }
  }

  async function requestDeletion() {
    if (!window.confirm("Delete your CBU Find account permanently? Active reports and private data are removed and shared history is anonymized.")) return;
    setBusy(true); setError("");
    try {
      const result = await api<{ referenceId: string }>("/v1/account-deletion", { method: "POST", body: "{}" });
      setReference(result.referenceId);
      await signOut(auth!).catch(() => undefined);
    } catch (caught) { setError(readableError(caught)); }
    finally { setBusy(false); }
  }

  return <main className="state-page">
    <div className="state-card">
      <img src="/cbu_find_logo.png" alt="" />
      <span className="eyebrow">ACCOUNT DELETION</span>
      <h1>Delete your CBU Find account</h1>
      {!firebaseConfigured ? <p>This deployment is not configured yet. Contact the CBU Find administrator.</p> : reference ? <>
        <p>Your deletion request has been queued.</p>
        <p><strong>Reference: {reference}</strong></p>
        <p>Cleanup removes your credentials, private data, identity documents and active reports. Shared history is anonymized. You can close this page.</p>
      </> : signedIn ? <>
        <p>Deleting removes your Firebase sign-in, private profile, student ID documents, notification settings and active reports. Resolved reports and conversations are anonymized rather than removed so other members keep their history.</p>
        <p>Deletion is queued as a background job and usually completes within minutes. If it fails it retries automatically.</p>
        {error && <div className="error-banner" role="alert"><strong>Action failed</strong><span>{error}</span></div>}
        <button className="danger" disabled={busy} onClick={() => void requestDeletion()}>{busy ? "Queueing deletion…" : "Delete my account"}</button>
      </> : <>
        <p>Sign in with the account you want deleted. You do not need the mobile app.</p>
        <form onSubmit={signIn}>
          <label className="field"><span>Email</span><input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <label className="field"><span>Password</span><input type="password" required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          {error && <div className="error-banner" role="alert"><strong>Action failed</strong><span>{error}</span></div>}
          {resetSent && <div className="success-banner">Password reset email sent. Check your inbox, then sign in here.</div>}
          <button className="primary" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
        </form>
        <button className="google" disabled={busy} onClick={() => void google()}>G&nbsp;&nbsp; Continue with Google</button>
        <button className="secondary" disabled={!email.trim()} onClick={() => void reset()}>Forgot password?</button>
      </>}
    </div>
  </main>;
}
