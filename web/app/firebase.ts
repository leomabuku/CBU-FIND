import { getApp, getApps, initializeApp } from "firebase/app";
import { initializeAppCheck, ReCaptchaEnterpriseProvider, type AppCheck } from "firebase/app-check";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

export const firebasePublicConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? "",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? "",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? "",
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? "",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? "",
};

export const appCheckSiteKey = process.env.NEXT_PUBLIC_FIREBASE_APP_CHECK_SITE_KEY ?? "";
export const firebaseConfigured = Boolean(firebasePublicConfig.apiKey && firebasePublicConfig.projectId && firebasePublicConfig.appId && appCheckSiteKey);
const app = firebaseConfigured ? (getApps().length ? getApp() : initializeApp(firebasePublicConfig)) : null;

export const auth = app ? getAuth(app) : null;
export const db = app ? getFirestore(app) : null;
export const firebaseApp = app;
// Browser requests stay on the web app's origin. The web Worker forwards this
// path to the trusted API through a Cloudflare service binding, which avoids
// browser extensions and restrictive networks blocking a second workers.dev
// hostname as a cross-origin request.
export const workerBaseUrl = "/api/cbu";
export const webPushVapidKey = process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY ?? "";

let appCheckInstance: AppCheck | null = null;
export function appCheck(): AppCheck | null {
  if (!app || !appCheckSiteKey || typeof window === "undefined") return null;
  appCheckInstance ??= initializeAppCheck(app, { provider: new ReCaptchaEnterpriseProvider(appCheckSiteKey), isTokenAutoRefreshEnabled: true });
  return appCheckInstance;
}

// Firestore listeners can start before the first Worker mutation, so install
// App Check eagerly in the browser instead of waiting for api() to request it.
if (typeof window !== "undefined" && firebaseConfigured) appCheck();
