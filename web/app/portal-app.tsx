"use client";

import { Component, useCallback, useEffect, useMemo, useRef, useState, type ErrorInfo, type FormEvent, type ReactNode } from "react";
import { createUserWithEmailAndPassword, GoogleAuthProvider, onAuthStateChanged, sendPasswordResetEmail, signInWithEmailAndPassword, signInWithPopup, signOut, type User as FirebaseUser } from "firebase/auth";
import { collection, doc, limit, onSnapshot, orderBy, query, where, type DocumentData, type Query, type Unsubscribe } from "firebase/firestore";
import { getMessaging, getToken, isSupported } from "firebase/messaging";
import type { ClaimRecord, ItemRecord, MediaAsset, PrivateProfile, UserRole } from "@cbu-find/contracts";
import { api, ClientError, messageRequestBody, readableError, uploadSigned } from "./api-client";
import { auth, db, firebaseApp, firebaseConfigured, firebasePublicConfig, webPushVapidKey, workerBaseUrl } from "./firebase";

type Profile = PrivateProfile;
type Item = ItemRecord & { contactInfo?: string };
type Claim = ClaimRecord;
type Conversation = { id: string; participantIds: string[]; participantNames: Record<string, string>; participantPhotoUrls: Record<string, string>; itemId: string; itemTitle: string; itemImageUrl: string; updatedAt: number; lastMessage: string; lastSenderId: string; lastReadAt: Record<string, number>; locked?: boolean; closed?: boolean };
type Message = { id: string; senderId: string; text: string; mediaUrl: string; mediaPublicId?: string; mediaResourceType?: string; mediaType: string; mediaName: string; mediaSizeBytes: number; createdAt: number; editedAt?: number; deleted?: boolean; deletedAt?: number };
type ModerationCase = { id: string; targetType: string; targetId: string; subjectUserId?: string; conversationId?: string; reason: string; details: string; status: string; context?: Message[]; createdAt: number; updatedAt: number };
type LiveState<T> = { status: "loading" | "content" | "empty" | "error"; data: T; offline?: boolean; message?: string };
type ThemeMode = "SYSTEM" | "LIGHT" | "DARK";

const emptyProfile: Omit<Profile, "id" | "email" | "status" | "createdAt" | "updatedAt"> = { name: "", studentId: "", programme: "", yearOfStudy: "", phone: "", photoUrl: "" };

export default function PortalApp({ initialPath = "/" }: { initialPath?: string }) {
  const [route, setRoute] = useState(initialPath);
  const [session, setSession] = useState<{ loading: boolean; user: FirebaseUser | null }>({ loading: Boolean(auth), user: null });
  const [profileState, setProfileState] = useState<LiveState<Profile | null>>({ status: "loading", data: null });
  const [role, setRole] = useState<UserRole>("USER");
  const [bootstrapError, setBootstrapError] = useState("");
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => {
    if (typeof window === "undefined") return "SYSTEM";
    const stored = localStorage.getItem("cbu-find-theme");
    return stored === "LIGHT" || stored === "DARK" ? stored : "SYSTEM";
  });
  const bootstrapped = useRef("");

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const apply = () => {
      const dark = themeMode === "DARK" || (themeMode === "SYSTEM" && media.matches);
      document.documentElement.dataset.theme = dark ? "dark" : "light";
      document.documentElement.style.colorScheme = dark ? "dark" : "light";
    };
    localStorage.setItem("cbu-find-theme", themeMode);
    apply();
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, [themeMode]);

  useEffect(() => {
    if (!auth) return;
    const timeout = window.setTimeout(() => {
      setSession((current) => current.loading ? { loading: false, user: auth.currentUser } : current);
    }, 5_000);
    const unsubscribe = onAuthStateChanged(auth, (user) => {
      window.clearTimeout(timeout);
      if (!user) bootstrapped.current = "";
      setProfileState({ status: user ? "loading" : "empty", data: null });
      setRole("USER");
      setSession({ loading: false, user });
    }, () => {
      window.clearTimeout(timeout);
      setSession({ loading: false, user: auth.currentUser });
    });
    return () => { window.clearTimeout(timeout); unsubscribe(); };
  }, []);
  useEffect(() => {
    if (!session.user || !db) return;
    const profileUnsubscribe = onSnapshot(doc(db, "users", session.user.uid), { includeMetadataChanges: true }, (snapshot) => {
      if (!snapshot.exists()) {
        setProfileState({ status: "empty", data: null, offline: snapshot.metadata.fromCache });
        if (bootstrapped.current !== session.user!.uid) {
          bootstrapped.current = session.user!.uid;
          void bootstrap(session.user!);
        }
      } else setProfileState({ status: "content", data: { id: snapshot.id, ...snapshot.data() } as Profile, offline: snapshot.metadata.fromCache });
    }, (error) => setProfileState({ status: "error", data: null, message: readableError(error) }));
    const roleUnsubscribe = onSnapshot(doc(db, "roles", session.user.uid), (snapshot) => setRole((snapshot.data()?.role as UserRole | undefined) ?? "USER"), () => setRole("USER"));
    return () => { profileUnsubscribe(); roleUnsubscribe(); };
  }, [session.user]);
  useEffect(() => {
    const update = () => setRoute(window.location.pathname);
    window.addEventListener("popstate", update);
    return () => window.removeEventListener("popstate", update);
  }, []);

  const navigate = useCallback((path: string) => { window.history.pushState({}, "", path); setRoute(path); window.scrollTo({ top: 0 }); }, []);
  async function bootstrap(user: FirebaseUser) {
    try {
      setBootstrapError("");
      await api("/v1/profile/bootstrap", { method: "POST", body: JSON.stringify({ ...emptyProfile, name: user.displayName || user.email?.split("@")[0] || "CBU Find member", photoUrl: user.photoURL || "" }) });
    } catch (error) { setBootstrapError(readableError(error)); }
  }

  if (!firebaseConfigured || !workerBaseUrl) return <ConfigurationScreen />;
  if (session.loading) return <LoadingScreen label="Checking your session…" />;
  if (!session.user) return <AuthPanel />;
  if (profileState.status === "loading") return <LoadingScreen label="Loading your private profile…" />;
  if (!profileState.data) return <StatePage title="Your profile needs attention" message={bootstrapError || profileState.message || "CBU Find could not finish setting up your profile."} action="Retry profile setup" onAction={() => bootstrap(session.user!)} />;
  return <RenderBoundary key={route}><AppFrame user={session.user} profile={profileState.data} profileOffline={profileState.offline} role={role} route={route} navigate={navigate} themeMode={themeMode} onThemeModeChange={setThemeMode} /></RenderBoundary>;
}

function AppFrame({ user, profile, profileOffline, role, route, navigate, themeMode, onThemeModeChange }: { user: FirebaseUser; profile: Profile; profileOffline?: boolean; role: UserRole; route: string; navigate: (path: string) => void; themeMode: ThemeMode; onThemeModeChange: (mode: ThemeMode) => void }) {
  const page = route === "/" ? "/feed" : route;
  const links = [
    ["/feed", "⌂", "Reports"], ["/reports/new", "+", "Create report"], ["/claims", "✓", "Claims"], ["/messages", "✉", "Messages"], ["/profile", "○", "Profile"], ["/settings", "⚙", "Settings"],
    ...(role === "MODERATOR" || role === "ADMIN" ? [["/moderation", "◇", "Moderation"]] : []),
  ];
  const content = page === "/feed" ? <FeedScreen navigate={navigate} />
    : page === "/reports/new" ? <CreateReportScreen navigate={navigate} />
    : page.startsWith("/reports/") ? <ReportDetailsScreen id={decodeURIComponent(page.slice("/reports/".length))} user={user} navigate={navigate} />
    : page === "/claims" ? <ClaimsScreen user={user} navigate={navigate} />
    : page === "/messages" || page.startsWith("/messages/") ? <MessagesScreen user={user} conversationId={page.startsWith("/messages/") ? decodeURIComponent(page.slice("/messages/".length)) : ""} navigate={navigate} />
    : page === "/profile" ? <ProfileScreen profile={profile} />
    : page === "/settings" ? <SettingsScreen user={user} themeMode={themeMode} onThemeModeChange={onThemeModeChange} />
    : page === "/moderation" && role !== "USER" ? <ModerationScreen role={role} />
    : <StatePage title="Page not found" message="The link may be old or incomplete." action="Browse reports" onAction={() => navigate("/feed")} />;
  return <div className="portal-shell">
    <aside className="sidebar">
      <a href="/feed" className="brand" onClick={(event) => { event.preventDefault(); navigate("/feed"); }}><img src="/cbu_find_logo.png" alt="" /><span><strong>CBU Find</strong><small>Campus lost &amp; found</small></span></a>
      <nav>{links.map(([href, icon, label]) => <a key={href} href={href} className={page === href || (href === "/messages" && page.startsWith("/messages/")) ? "active" : ""} onClick={(event) => { event.preventDefault(); navigate(href); }}><i>{icon}</i>{label}</a>)}</nav>
      <div className="sidebar-profile"><Avatar profile={profile} /><span><strong>{profile.name}</strong><small>{role.toLowerCase()}</small></span><button onClick={() => signOut(auth!)} aria-label="Sign out">↪</button></div>
    </aside>
    <main className="portal-main">{profileOffline && <OfflineBanner />}{content}</main>
    <nav className="mobile-nav">{links.slice(0, 5).map(([href, icon, label]) => <a key={href} href={href} className={page === href ? "active" : ""} onClick={(event) => { event.preventDefault(); navigate(href); }}><i>{icon}</i><small>{label}</small></a>)}</nav>
  </div>;
}

function AuthPanel() {
  const [mode, setMode] = useState<"signin" | "signup" | "reset">("signin");
  const [name, setName] = useState(""), [email, setEmail] = useState(""), [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false), [message, setMessage] = useState(""), [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    try {
      if (mode === "reset") { await sendPasswordResetEmail(auth!, email.trim()); setMessage("Password reset email sent. Check your inbox and spam folder."); }
      else if (mode === "signup") { const credential = await createUserWithEmailAndPassword(auth!, email.trim(), password); await api("/v1/profile/bootstrap", { method: "POST", body: JSON.stringify({ ...emptyProfile, name: name.trim() }) }); await credential.user.reload(); }
      else await signInWithEmailAndPassword(auth!, email.trim(), password);
    } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); }
  }
  async function google() { setBusy(true); setError(""); try { await signInWithPopup(auth!, new GoogleAuthProvider()); } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); } }
  return <main className="auth-page"><section className="auth-story"><div className="brand light"><img src="/cbu_find_logo.png" alt="" /><strong>CBU Find</strong></div><div><span className="eyebrow">COPPERBELT UNIVERSITY</span><h1>Lost on campus.<br />Found by community.</h1><p>Report items, verify a claim, and arrange a safer handover from web or Android.</p></div><small>Phone-number sign-in has been retired. Use email/password or Google.</small></section><section className="auth-card"><span className="eyebrow">{mode === "reset" ? "ACCOUNT RECOVERY" : "WELCOME"}</span><h2>{mode === "signin" ? "Sign in" : mode === "signup" ? "Create account" : "Reset password"}</h2><p>{mode === "reset" ? "We’ll send a secure reset link to your email." : "Use the same account on every device."}</p><form onSubmit={submit}>{mode === "signup" && <Field label="Full name"><input required minLength={2} value={name} onChange={(event) => setName(event.target.value)} /></Field>}<Field label="Email"><input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} /></Field>{mode !== "reset" && <Field label="Password"><input required minLength={6} type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>}{error && <ErrorBanner message={error} />}{message && <div className="success-banner">{message}</div>}<button className="primary" disabled={busy}>{busy ? "Please wait…" : mode === "signin" ? "Sign in" : mode === "signup" ? "Create account" : "Send reset link"}</button></form>{mode !== "reset" && <button className="google" onClick={google} disabled={busy}>G&nbsp;&nbsp; Continue with Google</button>}<div className="auth-switch"><button onClick={() => setMode(mode === "signin" ? "signup" : "signin")}>{mode === "signin" ? "Create an account" : "Back to sign in"}</button>{mode === "signin" && <button onClick={() => setMode("reset")}>Forgot password?</button>}</div></section></main>;
}

function FeedScreen({ navigate }: { navigate: (path: string) => void }) {
  const [state, setState] = useState<LiveState<Item[]>>({ status: "loading", data: [] });
  const [type, setType] = useState<"ALL" | "LOST" | "FOUND">("ALL"), [search, setSearch] = useState(""), [category, setCategory] = useState("ALL");
  useEffect(() => listen(query(collection(db!, "items"), where("status", "in", ["ACTIVE", "MATCHED", "RESOLVED"]), orderBy("date", "desc"), limit(100)), setState), []);
  const filtered = useMemo(() => state.data.filter((item) => (type === "ALL" || item.type === type) && (category === "ALL" || item.category === category) && [item.title, item.description, item.location, item.category].some((value) => value?.toLowerCase().includes(search.toLowerCase())) && item.status !== "REMOVED"), [state.data, type, category, search]);
  const categories = ["ALL", ...new Set(state.data.map((item) => item.category).filter(Boolean))];
  return <Page title="Campus reports" eyebrow="GOOD EVENING" action={<button className="primary compact" onClick={() => navigate("/reports/new")}>Create report</button>}><div className="report-tools"><input aria-label="Search reports" placeholder="Search items, places or categories" value={search} onChange={(event) => setSearch(event.target.value)} /><div className="type-switch" aria-label="Report type">{(["ALL", "LOST", "FOUND"] as const).map((value) => <button key={value} className={type === value ? "active" : ""} onClick={() => setType(value)}>{value === "ALL" ? "ALL" : value}</button>)}</div><select aria-label="Category" value={category} onChange={(event) => setCategory(event.target.value)}>{categories.map((value) => <option key={value} value={value}>{value === "ALL" ? "All categories" : value}</option>)}</select></div>{state.status === "loading" ? <SkeletonRows /> : state.status === "error" ? <StateNotice kind="error" title="Reports could not load" message={state.message!} action="Retry" onAction={() => location.reload()} /> : filtered.length === 0 ? <StateNotice kind="empty" title="No matching reports" message={search ? "Try a broader search or clear a filter." : "Be the first to publish a report."} action="Create report" onAction={() => navigate("/reports/new")} /> : <><div className="report-section-title"><strong>Recent reports</strong><span>{filtered.length} report{filtered.length === 1 ? "" : "s"}{state.offline ? " · cached while offline" : ""}</span></div><div className="report-list">{filtered.map((item) => <ReportRow key={item.id} item={item} onClick={() => navigate(`/reports/${encodeURIComponent(item.id)}`)} />)}</div></>}</Page>;
}

function CreateReportScreen({ navigate }: { navigate: (path: string) => void }) {
  const [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState(""), [media, setMedia] = useState<MediaAsset[]>([]);
  async function choose(files: FileList | null) {
    if (!files) return; setUploading(true); setError("");
    try {
      const uploaded: MediaAsset[] = [];
      for (const file of Array.from(files).slice(0, 6 - media.length)) uploaded.push(await uploadSigned(file, "reports"));
      setMedia((current) => [...current, ...uploaded]);
    } catch (caught) { setError(readableError(caught)); } finally { setUploading(false); }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(""); const form = new FormData(event.currentTarget);
    try { const result = await api<{ id: string }>("/v1/reports", { method: "POST", body: JSON.stringify({ type: form.get("type"), title: form.get("title"), description: form.get("description"), category: form.get("category"), location: form.get("location"), date: new Date(String(form.get("date"))).getTime(), contactInfo: form.get("contactInfo"), media }) }); navigate(`/reports/${result.id}`); } catch (caught) { setError(readableError(caught)); setBusy(false); }
  }
  return <Page title="Create a report" eyebrow="HELP THE CAMPUS"><form className="form-card" onSubmit={submit}><div className="field-grid"><Field label="Report type"><select name="type" required><option value="LOST">I lost an item</option><option value="FOUND">I found an item</option></select></Field><Field label="Date"><input name="date" type="date" required defaultValue={new Date().toISOString().slice(0, 10)} /></Field></div><Field label="Title"><input name="title" minLength={3} maxLength={120} required placeholder="Black scientific calculator" /></Field><Field label="Description"><textarea name="description" minLength={10} maxLength={4000} required rows={5} placeholder="Add distinguishing details without revealing information only the owner should know." /></Field><div className="field-grid"><Field label="Category"><select name="category" required>{["Student ID & Documents", "Phones & Electronics", "Keys", "Bags & Luggage", "Clothing", "Books & Stationery", "Bank Cards & Money", "Jewellery & Accessories", "Other"].map((value) => <option key={value}>{value}</option>)}</select></Field><Field label="Campus location"><input name="location" list="campus-locations" required placeholder="Library entrance" /><datalist id="campus-locations">{["Main Library", "School of Mines", "School of Business", "Student Centre", "Clinic", "Main Gate", "Hostels", "Sports Complex"].map((value) => <option key={value}>{value}</option>)}</datalist></Field></div><Field label="Private contact details (optional)"><input name="contactInfo" maxLength={300} placeholder="Phone or preferred handover method" /><small>Only you and an accepted claimant can access this.</small></Field><Field label="Photos (optional)"><input type="file" accept="image/*" multiple onChange={(event) => choose(event.target.files)} disabled={uploading || media.length >= 6} /><small>Signed upload, up to 20 MB each. {uploading ? "Uploading…" : `${media.length}/6 uploaded.`}</small></Field>{media.length > 0 && <div className="media-row">{media.map((asset) => <div key={asset.secureUrl}><img src={asset.secureUrl} alt="Report attachment" /><button type="button" onClick={() => setMedia((current) => current.filter((value) => value.secureUrl !== asset.secureUrl))}>×</button></div>)}</div>}{error && <ErrorBanner message={error} />}<div className="form-actions"><button type="button" className="secondary" onClick={() => navigate("/feed")}>Cancel</button><button className="primary" disabled={busy || uploading}>{busy ? "Publishing…" : "Publish report"}</button></div></form></Page>;
}

function ReportDetailsScreen({ id, user, navigate }: { id: string; user: FirebaseUser; navigate: (path: string) => void }) {
  const [state, setState] = useState<LiveState<Item | null>>({ status: "loading", data: null }), [error, setError] = useState(""), [busy, setBusy] = useState(false), [note, setNote] = useState(""), [contact, setContact] = useState("");
  useEffect(() => onSnapshot(doc(db!, "items", id), { includeMetadataChanges: true }, (snapshot) => setState(snapshot.exists() ? { status: "content", data: { id: snapshot.id, ...snapshot.data() } as Item, offline: snapshot.metadata.fromCache } : { status: "empty", data: null }), (caught) => setState({ status: "error", data: null, message: readableError(caught) })), [id]);
  if (state.status === "loading") return <Page title="Report"><SkeletonGrid /></Page>;
  if (!state.data) return <Page title="Report"><StateNotice kind={state.status === "error" ? "error" : "empty"} title={state.status === "error" ? "Report could not load" : "Report not found"} message={state.message || "It may have been removed or the link is incomplete."} action="Browse reports" onAction={() => navigate("/feed")} /></Page>;
  const item = state.data, own = item.userId === user.uid;
  async function claim() { setBusy(true); setError(""); try { await api("/v1/claims", { method: "POST", body: JSON.stringify({ itemId: id, kind: item.type === "LOST" ? "FOUND_IT" : "THIS_IS_MINE", note }) }); navigate("/claims"); } catch (caught) { setError(readableError(caught)); setBusy(false); } }
  async function status(value: "ACTIVE" | "RESOLVED") { const confirmed = window.confirm(value === "RESOLVED" ? "Confirm that the item has been returned or the issue is otherwise resolved? The accepted claimant will be notified." : "Reopen this report? The accepted claim will be closed and the report will accept new claims again."); if (!confirmed) return; setBusy(true); setError(""); try { await api(`/v1/reports/${encodeURIComponent(id)}/status`, { method: "POST", body: JSON.stringify({ status: value }) }); } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); } }
  async function loadContact() { try { const result = await api<{ contactInfo: string }>(`/v1/reports/${encodeURIComponent(id)}/contact`); setContact(result.contactInfo || "No private contact was supplied."); } catch (caught) { setError(readableError(caught)); } }
  return <Page title={item.title} eyebrow={`${item.type} · ${item.status}`}><article className="detail-card">{(item.media?.[0]?.secureUrl || item.imageUrls?.[0] || item.imageUri) && <img className="detail-image" src={item.media?.[0]?.secureUrl || item.imageUrls?.[0] || item.imageUri || ""} alt="" />}<div className="detail-copy"><div className="pill-row"><span className={`pill ${item.type.toLowerCase()}`}>{item.type}</span><span className="pill neutral">{item.status}</span></div><h2>{item.title}</h2><p>{item.description}</p><dl><div><dt>Category</dt><dd>{item.category}</dd></div><div><dt>Location</dt><dd>{item.location}</dd></div><div><dt>Date</dt><dd>{formatDate(item.date)}</dd></div></dl>{error && <ErrorBanner message={error} />}{contact && <div className="success-banner"><strong>Private contact:</strong> {contact}</div>}{own ? <div className="button-row"><button className="primary" disabled={busy} onClick={() => status(item.status === "RESOLVED" ? "ACTIVE" : "RESOLVED")}>{item.status === "RESOLVED" ? "Reopen report" : "✓ Item returned — mark resolved"}</button></div> : item.status === "ACTIVE" ? <div className="claim-box"><h3>{item.type === "LOST" ? "Did you find this?" : "Is this yours?"}</h3><textarea maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} placeholder="Add a detail that helps the owner assess your claim (optional)." /><button className="primary" disabled={busy} onClick={claim}>{busy ? "Submitting…" : "Submit claim"}</button></div> : item.status === "MATCHED" ? <button className="secondary" onClick={loadContact}>View contact if your claim was accepted</button> : null}</div></article></Page>;
}

function ClaimsScreen({ user, navigate }: { user: FirebaseUser; navigate: (path: string) => void }) {
  const [owned, setOwned] = useState<Claim[]>([]), [submitted, setSubmitted] = useState<Claim[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState(""), [notice, setNotice] = useState(""), [workingId, setWorkingId] = useState("");
  useEffect(() => {
    let ready = 0; const done = () => { ready += 1; if (ready >= 2) setLoading(false); };
    const fail = (caught: unknown) => { setError(readableError(caught)); setLoading(false); };
    const first = onSnapshot(query(collection(db!, "claims"), where("itemOwnerId", "==", user.uid), orderBy("updatedAt", "desc")), (snapshot) => { setOwned(snapshot.docs.map(toRecord<Claim>)); done(); }, fail);
    const second = onSnapshot(query(collection(db!, "claims"), where("claimantId", "==", user.uid), orderBy("updatedAt", "desc")), (snapshot) => { setSubmitted(snapshot.docs.map(toRecord<Claim>)); done(); }, fail);
    return () => { first(); second(); };
  }, [user.uid]);
  async function action(id: string, value: "ACCEPT" | "REJECT" | "CANCEL") { setWorkingId(id); setError(""); setNotice(""); try { const result = await api<{ status: Claim["status"]; conversationId?: string }>(`/v1/claims/${encodeURIComponent(id)}/action`, { method: "POST", body: JSON.stringify({ action: value }) }); const update = (claim: Claim) => claim.id === id ? { ...claim, status: result.status, conversationId: result.conversationId ?? claim.conversationId } : claim; setOwned((current) => current.map(update)); setSubmitted((current) => current.map(update)); setNotice(result.status === "REJECTED" ? "Claim rejected. The claimant has been notified." : result.status === "CANCELLED" ? "Claim cancelled." : "Claim accepted. Your private conversation is ready."); if (result.conversationId) navigate(`/messages/${encodeURIComponent(result.conversationId)}`); } catch (caught) { setError(readableError(caught)); } finally { setWorkingId(""); } }
  const rows = [...owned.map((claim) => ({ claim, incoming: true })), ...submitted.map((claim) => ({ claim, incoming: false }))].sort((a, b) => b.claim.updatedAt - a.claim.updatedAt);
  return <Page title="Claims" eyebrow="VERIFY BEFORE CHAT">{error && <ErrorBanner message={error} />}{notice && <div className="success-banner">{notice}</div>}{loading ? <SkeletonRows /> : rows.length === 0 ? <StateNotice kind="empty" title="No claims yet" message="Claims you submit and responses to your reports appear here." action="Browse reports" onAction={() => navigate("/feed")} /> : <div className="list-card">{rows.map(({ claim, incoming }) => <div className="claim-row" key={`${incoming}-${claim.id}`}><span className={`status-dot ${claim.status.toLowerCase()}`} /><div><strong>{incoming ? "Claim on your report" : "Your submitted claim"}</strong><small>{claim.kind.replaceAll("_", " ")} · {formatDate(claim.updatedAt)}</small><p>{claim.note || "No additional note."}</p></div><span className="pill neutral">{claim.status}</span>{claim.status === "PENDING" && <div className="row-actions">{incoming ? <><button disabled={workingId === claim.id} className="primary tiny" onClick={() => action(claim.id, "ACCEPT")}>Accept</button><button disabled={workingId === claim.id} className="secondary tiny" onClick={() => action(claim.id, "REJECT")}>{workingId === claim.id ? "Updating…" : "Reject"}</button></> : <button disabled={workingId === claim.id} className="secondary tiny" onClick={() => action(claim.id, "CANCEL")}>Cancel</button>}</div>}{claim.status === "ACCEPTED" && claim.conversationId && <button className="primary tiny" onClick={() => navigate(`/messages/${encodeURIComponent(claim.conversationId!)}`)}>Open chat</button>}</div>)}</div>}</Page>;
}

function MessagesScreen({ user, conversationId, navigate }: { user: FirebaseUser; conversationId: string; navigate: (path: string) => void }) {
  const [state, setState] = useState<LiveState<Conversation[]>>({ status: "loading", data: [] });
  useEffect(() => listen(query(collection(db!, "conversations"), where("participantIds", "array-contains", user.uid), orderBy("updatedAt", "desc")), setState, normalizeConversation), [user.uid]);
  const selected = state.data.find((entry) => entry.id === conversationId);
  return <Page title="Messages" eyebrow="PRIVATE HANDOVER CHAT"><div className={`messages-layout ${selected ? "chat-open" : ""}`}><section className="conversation-panel">{state.status === "loading" ? <SkeletonRows /> : state.status === "error" ? <StateNotice kind="error" title="Inbox could not load" message={state.message!} action="Retry" onAction={() => location.reload()} /> : state.data.length === 0 ? <StateNotice kind="empty" title="No conversations yet" message="Messaging opens only after a report owner accepts a claim." action="View claims" onAction={() => navigate("/claims")} /> : state.data.map((conversation) => <button key={conversation.id} className={`conversation-row ${conversation.id === conversationId ? "selected" : ""}`} onClick={() => navigate(`/messages/${encodeURIComponent(conversation.id)}`)}><Avatar name={otherName(conversation, user.uid)} /><span><strong>{otherName(conversation, user.uid)}</strong><small>{conversation.itemTitle}</small><p>{conversation.lastMessage || "Claim accepted — conversation ready"}</p></span>{isUnread(conversation, user.uid) && <i />}</button>)}</section><section className="chat-panel">{selected ? <Chat conversation={selected} user={user} onBack={() => navigate("/messages")} /> : conversationId && state.status === "content" ? <StateNotice kind="error" title="Conversation unavailable" message="It may be locked, removed, or belong to another account." action="Back to inbox" onAction={() => navigate("/messages")} /> : <StateNotice kind="empty" title="Select a conversation" message="Accepted claims create a private place to arrange the return." action="View claims" onAction={() => navigate("/claims")} />}</section></div></Page>;
}

function Chat({ conversation, user, onBack }: { conversation: Conversation; user: FirebaseUser; onBack: () => void }) {
  const [state, setState] = useState<LiveState<Message[]>>({ status: "loading", data: [] }), [report, setReport] = useState<Item | null>(null), [draft, setDraft] = useState(""), [asset, setAsset] = useState<(MediaAsset & { type: "IMAGE" | "VIDEO" | "FILE"; name: string }) | null>(null), [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState(""), [editingId, setEditingId] = useState("");
  const end = useRef<HTMLDivElement>(null);
  const readOnly = conversation.locked || conversation.closed;
  useEffect(() => listen(query(collection(db!, "conversations", conversation.id, "messages"), orderBy("createdAt", "asc")), setState, normalizeMessage), [conversation.id]);
  useEffect(() => onSnapshot(doc(db!, "items", conversation.itemId), (snapshot) => setReport(snapshot.exists() ? { id: snapshot.id, ...snapshot.data() } as Item : null), (caught) => setError(readableError(caught))), [conversation.itemId]);
  useEffect(() => { void api(`/v1/conversations/${encodeURIComponent(conversation.id)}/read`, { method: "POST", body: "{}" }).catch(() => undefined); }, [conversation.id, state.data.length]);
  useEffect(() => { const target = end.current; if (target && typeof target.scrollIntoView === "function") target.scrollIntoView({ behavior: "smooth" }); }, [state.data.length]);
  async function choose(file?: File) { if (!file) return; setUploading(true); setError(""); try { const uploaded = await uploadSigned(file, "messages"); setAsset({ ...uploaded, type: file.type.startsWith("image/") ? "IMAGE" : file.type.startsWith("video/") ? "VIDEO" : "FILE", name: file.name }); } catch (caught) { setError(readableError(caught)); } finally { setUploading(false); } }
  async function send(event: FormEvent) {
    event.preventDefault();
    if ((!draft.trim() && !asset) || busy) return;
    setBusy(true); setError("");
    try {
      const root = `/v1/conversations/${encodeURIComponent(conversation.id)}/messages`;
      if (editingId) await api(`${root}/${encodeURIComponent(editingId)}`, { method: "PATCH", body: JSON.stringify({ text: draft }) });
      else await api(root, { method: "POST", body: messageRequestBody(draft, asset) });
      setDraft(""); setAsset(null); setEditingId("");
    } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); }
  }
  function editMessage(message: Message) { setEditingId(message.id); setDraft(message.text); setAsset(null); setError(""); setNotice(""); }
  async function deleteMessage(message: Message) {
    if (!confirm("Delete this message for everyone? Its text and attachment will be removed permanently.")) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await api(`/v1/conversations/${encodeURIComponent(conversation.id)}/messages/${encodeURIComponent(message.id)}`, { method: "DELETE" });
      if (editingId === message.id) { setEditingId(""); setDraft(""); }
      setNotice("Message deleted for everyone.");
    } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); }
  }
  async function blockOther() {
    const targetUid = conversation.participantIds.find((value) => value !== user.uid);
    if (!targetUid || !confirm("Block this account? New claims and messages will be disabled in both directions. Existing history stays visible.")) return;
    setError(""); setNotice("");
    try { await api("/v1/blocks", { method: "POST", body: JSON.stringify({ targetUid }) }); setNotice("Account blocked. Existing conversation history remains visible."); }
    catch (caught) { setError(readableError(caught)); }
  }
  async function resolveReport() {
    if (!report || report.userId !== user.uid || report.status !== "MATCHED") return;
    if (!window.confirm("Confirm that the item has been returned and this issue is resolved? The accepted claimant will be notified.")) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await api(`/v1/reports/${encodeURIComponent(report.id)}/status`, { method: "POST", body: JSON.stringify({ status: "RESOLVED" }) });
      setReport((current) => current ? { ...current, status: "RESOLVED", resolvedAt: Date.now(), updatedAt: Date.now() } : current);
      setNotice("Report marked resolved. Thanks for closing the loop.");
    } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); }
  }
  async function reportMessage(messageId: string) {
    const details = prompt("Tell moderators what happened. They receive this message and at most two messages before and after it.", "");
    if (details === null) return;
    setError(""); setNotice("");
    try { await api("/v1/abuse-reports", { method: "POST", body: JSON.stringify({ targetType: "MESSAGE", targetId: messageId, conversationId: conversation.id, reason: "OTHER", details }) }); setNotice("Report submitted with the limited moderation context window."); }
    catch (caught) { setError(readableError(caught)); }
  }
  const canResolve = report?.userId === user.uid && report.status === "MATCHED" && !readOnly;
  return <div className="chat"><header><button className="back" onClick={onBack}>←</button><Avatar name={otherName(conversation, user.uid)} /><span><strong>{otherName(conversation, user.uid)}</strong><small>{conversation.itemTitle}{report?.status === "RESOLVED" ? " · resolved" : readOnly ? conversation.closed ? " · match reopened" : " · locked" : ""}</small></span><button className="chat-action" onClick={blockOther}>Block</button></header><div className="message-scroll">{state.status === "loading" ? <LoadingInline /> : state.status === "error" ? <StateNotice kind="error" title="Messages could not load" message={state.message!} action="Retry" onAction={() => location.reload()} /> : state.data.length === 0 ? <StateNotice kind="empty" title="Conversation ready" message="Say hello and arrange a public campus handover." /> : state.data.map((message) => <MessageBubble key={message.id} message={message} own={message.senderId === user.uid} onReport={message.senderId !== user.uid && !message.deleted ? () => reportMessage(message.id) : undefined} onEdit={message.senderId === user.uid && !message.deleted && Boolean(message.text) && !readOnly ? () => editMessage(message) : undefined} onDelete={message.senderId === user.uid && !message.deleted ? () => deleteMessage(message) : undefined} />)}<div ref={end} /></div><form className="composer" onSubmit={send}>{canResolve && <div className="resolve-prompt"><span><strong>Handover complete?</strong><small>Close this report once the item has been returned.</small></span><button type="button" className="primary tiny" disabled={busy} onClick={resolveReport}>✓ Mark resolved</button></div>}{report?.status === "RESOLVED" && <div className="resolved-note">✓ This report is resolved</div>}{error && <ErrorBanner message={error} />}{notice && <div className="success-banner">{notice}</div>}{editingId && <div className="editing-banner"><span><strong>Editing message</strong><small>Save your changes or cancel.</small></span><button type="button" onClick={() => { setEditingId(""); setDraft(""); }}>Cancel</button></div>}{asset && <div className="attachment-preview"><span>{asset.type === "IMAGE" ? "▣" : "▤"}</span><div><strong>{asset.name}</strong><small>{formatBytes(asset.bytes ?? 0)}</small></div><button type="button" onClick={() => setAsset(null)}>×</button></div>}<div><label className="attach">＋<input hidden disabled={readOnly || Boolean(editingId)} type="file" onChange={(event) => choose(event.target.files?.[0])} /></label><textarea rows={1} maxLength={4000} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={editingId ? "Edit your message…" : conversation.closed ? "Match reopened — history only" : conversation.locked ? "Conversation locked" : "Write a message…"} disabled={readOnly} /><button className="send" aria-label={editingId ? "Save edited message" : "Send message"} disabled={readOnly || busy || uploading || (!draft.trim() && !asset)}>{uploading ? "…" : editingId ? "✓" : "↑"}</button></div></form></div>;
}

function ProfileScreen({ profile }: { profile: Profile }) {
  const [form, setForm] = useState(profile), [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState(""), [saved, setSaved] = useState(false);
  async function choosePhoto(file?: File) { if (!file) return; setUploading(true); setError(""); try { const photoAsset = await uploadSigned(file, "profiles"); setForm((current) => ({ ...current, photoUrl: photoAsset.secureUrl, photoAsset })); } catch (caught) { setError(readableError(caught)); } finally { setUploading(false); } }
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(""); setSaved(false); try { await api("/v1/profile", { method: "PATCH", body: JSON.stringify(form) }); setSaved(true); } catch (caught) { setError(readableError(caught)); } finally { setBusy(false); } }
  return <Page title="Your profile" eyebrow="PRIVATE ACCOUNT"><form className="form-card" onSubmit={submit}><div className="profile-intro"><Avatar profile={form} /><div><strong>{form.name}</strong><small>Email, student ID, and phone are never shown in public profiles.</small></div></div><Field label="Full name"><input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required minLength={2} /></Field><div className="field-grid"><Field label="Student ID"><input value={form.studentId} onChange={(event) => setForm({ ...form, studentId: event.target.value })} /></Field><Field label="Contact number (optional)"><input type="tel" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></Field></div><div className="field-grid"><Field label="Programme"><input value={form.programme} onChange={(event) => setForm({ ...form, programme: event.target.value })} /></Field><Field label="Year of study"><input value={form.yearOfStudy} onChange={(event) => setForm({ ...form, yearOfStudy: event.target.value })} /></Field></div><Field label="Profile photo"><input type="file" accept="image/*" onChange={(event) => choosePhoto(event.target.files?.[0])} disabled={uploading || busy} /><small>Signed private upload. {uploading ? "Uploading…" : "Choose a new image only when you want to replace it."}</small></Field>{error && <ErrorBanner message={error} />}{saved && <div className="success-banner">Profile saved across web and Android.</div>}<button className="primary" disabled={busy || uploading}>{busy ? "Saving…" : "Save profile"}</button></form></Page>;
}

function SettingsScreen({ user, themeMode, onThemeModeChange }: { user: FirebaseUser; themeMode: ThemeMode; onThemeModeChange: (mode: ThemeMode) => void }) {
  const [preferences, setPreferences] = useState({ claims: true, claimDecisions: true, messages: true, reportUpdates: true, moderation: true, showMessagePreview: false }), [message, setMessage] = useState(""), [error, setError] = useState(""), [deleting, setDeleting] = useState(false), [blockedAccounts, setBlockedAccounts] = useState<string[]>([]), [loadingSettings, setLoadingSettings] = useState(true);
  useEffect(() => {
    let active = true;
    void Promise.allSettled([api<{ targetUids: string[] }>("/v1/blocks"), api<typeof preferences>("/v1/notification-preferences")]).then(([blocks, saved]) => {
      if (!active) return;
      if (blocks.status === "fulfilled") setBlockedAccounts(blocks.value.targetUids); else setError(readableError(blocks.reason));
      if (saved.status === "fulfilled") setPreferences(saved.value); else setError(readableError(saved.reason));
      setLoadingSettings(false);
    });
    return () => { active = false; };
  }, []);
  async function save() { try { await api("/v1/notification-preferences", { method: "PUT", body: JSON.stringify(preferences) }); setMessage("Notification preferences saved."); } catch (caught) { setError(readableError(caught)); } }
  async function enablePush() { setError(""); try { if (!firebaseApp || !await isSupported() || !webPushVapidKey || !("serviceWorker" in navigator)) throw new ClientError("CONFIGURATION_REQUIRED", "Web push is not configured for this deployment.", false, "WEB-PUSH"); const permission = Notification.permission === "granted" ? "granted" : await Notification.requestPermission(); if (permission !== "granted") throw new ClientError("PERMISSION_DENIED", "Notification permission was not granted. Allow it in browser settings, then retry.", false, "WEB-PUSH-PERMISSION"); const swUrl = `/firebase-messaging-sw.js?${new URLSearchParams(firebasePublicConfig).toString()}`; const registration = await navigator.serviceWorker.register(swUrl); const token = await getToken(getMessaging(firebaseApp), { vapidKey: webPushVapidKey, serviceWorkerRegistration: registration }); if (!token) throw new ClientError("DEPENDENCY_UNAVAILABLE", "This browser could not register for push. Check your connection and retry.", true, "WEB-PUSH-TOKEN"); await api("/v1/devices", { method: "PUT", body: JSON.stringify({ token, platform: "WEB" }) }); setMessage("Push notifications enabled on this browser."); } catch (caught) { setError(readableError(caught)); } }
  async function removeAccount() { if (!confirm("Delete your CBU Find account? Active reports and private data will be removed. Shared history will be anonymized.")) return; setDeleting(true); setError(""); try { const result = await api<{ referenceId: string }>("/v1/account-deletion", { method: "POST", body: "{}" }); setMessage(`Deletion queued. Reference: ${result.referenceId}`); setTimeout(() => void signOut(auth!), 1500); } catch (caught) { setError(readableError(caught)); setDeleting(false); } }
  async function unblock(targetUid: string) { setError(""); try { await api(`/v1/blocks/${encodeURIComponent(targetUid)}`, { method: "DELETE" }); setBlockedAccounts((current) => current.filter((uid) => uid !== targetUid)); setMessage("Account unblocked."); } catch (caught) { setError(readableError(caught)); } }
  return <Page title="Settings" eyebrow="APPEARANCE, NOTIFICATIONS & PRIVACY"><div className="form-card settings"><h3>Appearance</h3><p className="muted">Choose how CBU Find looks on this device.</p><div className="theme-options"><button className={themeMode === "LIGHT" ? "selected" : ""} onClick={() => onThemeModeChange("LIGHT")}><span className="theme-preview light-preview"><i /><i /></span><strong>Light</strong></button><button className={themeMode === "DARK" ? "selected" : ""} onClick={() => onThemeModeChange("DARK")}><span className="theme-preview dark-preview"><i /><i /></span><strong>Dark</strong></button></div><label className="toggle"><span><strong>Follow device</strong><small>Switch automatically with your system appearance.</small></span><input type="checkbox" checked={themeMode === "SYSTEM"} onChange={(event) => onThemeModeChange(event.target.checked ? "SYSTEM" : "LIGHT")} /></label><hr /><h3>Push notifications</h3>{loadingSettings ? <LoadingInline /> : Object.entries(preferences).map(([key, value]) => <label className="toggle" key={key}><span><strong>{labelForPreference(key)}</strong><small>{key === "showMessagePreview" ? "Off by default for privacy." : "Receive this update on registered devices."}</small></span><input type="checkbox" checked={value} onChange={(event) => setPreferences({ ...preferences, [key]: event.target.checked })} /></label>)}<div className="button-row"><button className="secondary" onClick={enablePush} disabled={loadingSettings}>Enable browser push</button><button className="primary" onClick={save} disabled={loadingSettings}>Save preferences</button></div>{message && <div className="success-banner">{message}</div>}{error && <ErrorBanner message={error} />}<hr /><h3>Blocked accounts</h3>{blockedAccounts.length === 0 ? <p className="muted">You have not blocked any accounts.</p> : <div className="blocked-list">{blockedAccounts.map((uid) => <div key={uid}><code>{uid}</code><button className="secondary tiny" onClick={() => unblock(uid)}>Unblock</button></div>)}</div>}<hr /><h3>Account</h3><p className="muted">Signed in as {user.email}. Deletion removes credentials and private data, removes active reports and known media, and anonymizes shared history.</p><button className="danger" disabled={deleting} onClick={removeAccount}>{deleting ? "Queueing deletion…" : "Delete account"}</button></div></Page>;
}

function ModerationScreen({ role }: { role: UserRole }) {
  const [cases, setCases] = useState<ModerationCase[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState(""), [targetUid, setTargetUid] = useState(""), [targetRole, setTargetRole] = useState<UserRole>("MODERATOR");
  const load = useCallback(async () => { setLoading(true); setError(""); try { const result = await api<{ cases: ModerationCase[] }>("/v1/admin/cases"); setCases(result.cases); } catch (caught) { setError(readableError(caught)); } finally { setLoading(false); } }, []);
  useEffect(() => {
    let active = true;
    void api<{ cases: ModerationCase[] }>("/v1/admin/cases")
      .then((result) => { if (active) setCases(result.cases); })
      .catch((caught) => { if (active) setError(readableError(caught)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  async function act(record: ModerationCase, action: string) { const accountAction = action === "SUSPEND_USER" || action === "REACTIVATE_USER"; const targetId = prompt("Confirm the target document/user ID for this action:", accountAction ? record.subjectUserId || "" : action.includes("CONVERSATION") ? record.conversationId || "" : record.targetId); if (!targetId) return; try { await api(`/v1/admin/cases/${record.id}/action`, { method: "POST", body: JSON.stringify({ action, targetId }) }); await load(); } catch (caught) { setError(readableError(caught)); } }
  async function assignRole() { try { await api(`/v1/admin/roles/${encodeURIComponent(targetUid)}`, { method: "PUT", body: JSON.stringify({ role: targetRole }) }); setTargetUid(""); } catch (caught) { setError(readableError(caught)); } }
  return <Page title="Moderation" eyebrow={`${role} TOOLS`}>{error && <ErrorBanner message={error} />}{role === "ADMIN" && <div className="admin-role-box"><h3>Role management</h3><input placeholder="Firebase user UID" value={targetUid} onChange={(event) => setTargetUid(event.target.value)} /><select value={targetRole} onChange={(event) => setTargetRole(event.target.value as UserRole)}><option>USER</option><option>MODERATOR</option><option>ADMIN</option></select><button className="primary compact" disabled={!targetUid} onClick={assignRole}>Update role</button><small>Last-admin protection is enforced by the Worker.</small></div>}{loading ? <SkeletonRows /> : cases.length === 0 ? <StateNotice kind="empty" title="No moderation cases" message="New abuse reports appear here with only the allowed target context." /> : <div className="case-list">{cases.map((record) => <article key={record.id}><header><span className="pill neutral">{record.status}</span><strong>{record.reason}</strong><time>{formatDate(record.updatedAt)}</time></header><p>{record.details || "No additional details."}</p>{record.context && <div className="context-window"><small>Reported message with up to two adjacent messages on each side</small>{record.context.map((message) => <div key={message.id}><strong>{message.senderId}</strong><span>{message.text || `[${message.mediaType || "attachment"}]`}</span></div>)}</div>}<div className="row-actions"><button onClick={() => act(record, "DISMISS")}>Dismiss</button>{record.targetType === "REPORT" && <><button onClick={() => act(record, "REMOVE_REPORT")}>Remove report</button><button onClick={() => act(record, "RESTORE_REPORT")}>Restore</button></>}{record.targetType === "MESSAGE" && <><button onClick={() => act(record, "LOCK_CONVERSATION")}>Lock chat</button><button onClick={() => act(record, "UNLOCK_CONVERSATION")}>Unlock</button></>}<button onClick={() => act(record, "SUSPEND_USER")}>Suspend user</button><button onClick={() => act(record, "REACTIVATE_USER")}>Reactivate</button></div></article>)}</div>}</Page>;
}

function ReportRow({ item, onClick }: { item: Item; onClick: () => void }) { const image = item.media?.[0]?.secureUrl || item.imageUrls?.[0] || item.imageUri; return <button className="report-row" onClick={onClick}>{image ? <img src={image} alt="" /> : <img className="report-placeholder" src="/cbu_find_logo.png" alt="" />}<span><small className={item.type.toLowerCase()}>{item.type}</small><strong>{item.title}</strong><span>{item.location} · {formatDate(item.date)}</span></span>{item.status !== "ACTIVE" && <span className="pill neutral">{item.status}</span>}</button>; }
function MessageBubble({ message, own, onReport, onEdit, onDelete }: { message: Message; own: boolean; onReport?: () => void; onEdit?: () => void; onDelete?: () => void }) { return <div className={`message-line ${own ? "own" : ""} ${message.deleted ? "deleted" : ""}`}><div>{message.deleted ? <p className="deleted-copy">Message deleted</p> : <>{message.mediaUrl && (message.mediaType === "IMAGE" ? <a href={message.mediaUrl} target="_blank" rel="noreferrer"><img src={message.mediaUrl} alt={message.mediaName || "Shared attachment"} /></a> : <a className="file" href={message.mediaUrl} target="_blank" rel="noreferrer">▤ {message.mediaName || "Attachment"}</a>)}{message.text && <p>{message.text}</p>}</>}<span className="message-meta"><time>{formatTime(message.createdAt)}{message.editedAt && !message.deleted ? " · edited" : ""}</time>{onEdit && <button type="button" onClick={onEdit}>Edit</button>}{onDelete && <button type="button" onClick={onDelete}>Delete</button>}{onReport && <button type="button" onClick={onReport} aria-label="Report message">Report</button>}</span></div></div>; }
function Page({ title, eyebrow, action, children }: { title: string; eyebrow?: string; action?: ReactNode; children: ReactNode }) { return <div className="page"><header className="page-header"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h1>{title}</h1></div>{action}</header>{children}</div>; }
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="field"><span>{label}</span>{children}</label>; }
function ErrorBanner({ message }: { message: string }) { return <div className="error-banner" role="alert"><strong>Action failed</strong><span>{message}</span></div>; }
function OfflineBanner() { return <div className="offline-banner">Offline: showing cached data. Changes will be available when the connection returns.</div>; }
function LoadingScreen({ label }: { label: string }) { return <main className="state-page"><span className="spinner" /><p>{label}</p></main>; }
function LoadingInline() { return <div className="loading-inline"><span className="spinner" />Loading…</div>; }
function StatePage({ title, message, action, onAction }: { title: string; message: string; action: string; onAction: () => void }) { return <main className="state-page"><div className="state-card"><img src="/cbu_find_logo.png" alt="" /><h1>{title}</h1><p>{message}</p><button className="primary" onClick={onAction}>{action}</button></div></main>; }
export function StateNotice({ kind, title, message, action, onAction }: { kind: "empty" | "error"; title: string; message: string; action?: string; onAction?: () => void }) { return <div className={`state-notice ${kind}`}><i>{kind === "error" ? "!" : "◇"}</i><h3>{title}</h3><p>{message}</p>{action && <button className="secondary" onClick={onAction}>{action}</button>}</div>; }
function ConfigurationScreen() { return <main className="state-page"><div className="state-card"><img src="/cbu_find_logo.png" alt="" /><span className="eyebrow">DEPLOYMENT SETUP</span><h1>Connect CBU Find</h1><p>Add the Firebase public values, Worker API URL, App Check site key, and VAPID key from <code>.env.example</code>. Secrets belong only in Wrangler.</p></div></main>; }
function SkeletonGrid() { return <div className="card-grid">{[1, 2, 3, 4, 5, 6].map((value) => <div className="skeleton-card" key={value}><i /><span /><span /></div>)}</div>; }
function SkeletonRows() { return <div className="skeleton-rows">{[1, 2, 3].map((value) => <div key={value}><i /><span /></div>)}</div>; }
function Avatar({ profile, name }: { profile?: { name: string; photoUrl?: string }; name?: string }) { const value = profile?.name || name || "Campus member"; return profile?.photoUrl ? <img className="avatar" src={profile.photoUrl} alt="" /> : <span className="avatar fallback">{value.trim().slice(0, 1).toUpperCase()}</span>; }

class RenderBoundary extends Component<{ children: ReactNode }, { code: string; referenceId: string }> {
  state = { code: "", referenceId: "" };
  static getDerivedStateFromError(error: unknown) { return { code: renderFailureCode(error), referenceId: renderReferenceId() }; }
  componentDidCatch(_error: unknown, info: ErrorInfo) {
    console.error(JSON.stringify({ event: "ui.render_failed", code: this.state.code, referenceId: this.state.referenceId, components: info.componentStack?.split("\n").map((value) => value.trim()).filter(Boolean).slice(0, 6) }));
  }
  render() {
    if (!this.state.code) return this.props.children;
    return <StatePage title="This view could not open" message={`CBU Find stopped an unexpected display error. Return to Messages and retry. Reference: ${this.state.code}-${this.state.referenceId}`} action="Back to messages" onAction={() => { window.history.replaceState({}, "", "/messages"); window.location.reload(); }} />;
  }
}

function listen<T>(source: Query<DocumentData>, setState: (state: LiveState<T[]>) => void, map: (entry: { id: string; data(): DocumentData }) => T = toRecord<T>): Unsubscribe {
  setState({ status: "loading", data: [] });
  try {
    return onSnapshot(source, { includeMetadataChanges: true }, (snapshot) => {
      try {
        const data = snapshot.docs.map(map);
        setState({ status: data.length ? "content" : "empty", data, offline: snapshot.metadata.fromCache });
      } catch {
        setState({ status: "error", data: [], message: "This view received an unsupported legacy record. Retry, then contact an administrator if it continues. Reference: WEB-FIRESTORE-DATA" });
      }
    }, (error) => setState({ status: "error", data: [], message: readableError(error) }));
  } catch (error) {
    setState({ status: "error", data: [], message: readableError(error) });
    return () => undefined;
  }
}
function toRecord<T>(entry: { id: string; data(): DocumentData }): T { return { id: entry.id, ...entry.data() } as T; }
function normalizeConversation(entry: { id: string; data(): DocumentData }): Conversation {
  const data = entry.data();
  return {
    id: entry.id,
    participantIds: stringList(data.participantIds),
    participantNames: stringMap(data.participantNames),
    participantPhotoUrls: stringMap(data.participantPhotoUrls),
    itemId: stringValue(data.itemId),
    itemTitle: stringValue(data.itemTitle, "Campus item"),
    itemImageUrl: stringValue(data.itemImageUrl),
    updatedAt: milliseconds(data.updatedAt),
    lastMessage: stringValue(data.lastMessage),
    lastSenderId: stringValue(data.lastSenderId),
    lastReadAt: numberMap(data.lastReadAt),
    locked: data.locked === true,
    closed: data.closed === true,
  };
}
function normalizeMessage(entry: { id: string; data(): DocumentData }): Message {
  const data = entry.data();
  return { id: entry.id, senderId: stringValue(data.senderId), text: stringValue(data.text), mediaUrl: stringValue(data.mediaUrl), mediaPublicId: stringValue(data.mediaPublicId) || undefined, mediaResourceType: stringValue(data.mediaResourceType) || undefined, mediaType: stringValue(data.mediaType), mediaName: stringValue(data.mediaName), mediaSizeBytes: numberValue(data.mediaSizeBytes), createdAt: milliseconds(data.createdAt), editedAt: milliseconds(data.editedAt) || undefined, deleted: data.deleted === true, deletedAt: milliseconds(data.deletedAt) || undefined };
}
function stringValue(value: unknown, fallback = "") { return typeof value === "string" ? value : fallback; }
function stringList(value: unknown) { return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === "string") : []; }
function stringMap(value: unknown) { if (!value || typeof value !== "object" || Array.isArray(value)) return {}; return Object.fromEntries(Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === "string")); }
function numberMap(value: unknown) { if (!value || typeof value !== "object" || Array.isArray(value)) return {}; return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, milliseconds(entry)])); }
function numberValue(value: unknown) { return typeof value === "number" && Number.isFinite(value) ? value : 0; }
function milliseconds(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value instanceof Date) return value.getTime();
  if (value && typeof value === "object") {
    const timestamp = value as { toMillis?: () => number; seconds?: number; nanoseconds?: number; _seconds?: number; _nanoseconds?: number };
    if (typeof timestamp.toMillis === "function") { try { return timestamp.toMillis(); } catch { return 0; } }
    const seconds = numberValue(timestamp.seconds ?? timestamp._seconds);
    const nanos = numberValue(timestamp.nanoseconds ?? timestamp._nanoseconds);
    if (seconds) return seconds * 1000 + Math.floor(nanos / 1_000_000);
  }
  return 0;
}
function otherName(conversation: Conversation, uid: string) { const other = conversation.participantIds.find((value) => value !== uid) || ""; return stringValue(conversation.participantNames?.[other], "Campus member"); }
function isUnread(conversation: Conversation, uid: string) { return Boolean(conversation.lastSenderId && conversation.lastSenderId !== uid && milliseconds(conversation.updatedAt) > milliseconds(conversation.lastReadAt?.[uid])); }
function formatDate(value: unknown) { const timestamp = milliseconds(value); return timestamp ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(timestamp) : "Date unavailable"; }
function formatTime(value: unknown) { const timestamp = milliseconds(value); return timestamp ? new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(timestamp) : ""; }
function formatBytes(value: number) { return value >= 1024 * 1024 ? `${(value / 1024 / 1024).toFixed(1)} MB` : value >= 1024 ? `${(value / 1024).toFixed(1)} KB` : `${value} B`; }
function labelForPreference(key: string) { return ({ claims: "New claims", claimDecisions: "Claim decisions", messages: "Messages", reportUpdates: "Report updates", moderation: "Moderation actions", showMessagePreview: "Show message preview" } as Record<string, string>)[key] || key; }
function renderFailureCode(error: unknown) { const message = error instanceof Error ? error.message.toLowerCase() : ""; if (message.includes("scrollintoview")) return "WEB-RENDER-SCROLL"; if (message.includes("invalid time")) return "WEB-RENDER-DATE"; if (message.includes("not a function") || message.includes("objects are not valid")) return "WEB-RENDER-DATA"; return "WEB-RENDER"; }
function renderReferenceId() { try { return crypto.randomUUID().slice(0, 8).toUpperCase(); } catch { return Math.random().toString(36).slice(2, 10).toUpperCase(); } }
