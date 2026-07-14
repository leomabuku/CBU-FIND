# Firebase setup for CBU Find

This guide configures the Firebase project used by the Android application: `cbu-lost-and-found`.

## 1. Keep only client configuration in the Android project

The Android app needs this file:

```text
app/google-services.json
```

It must match package name `com.campus.lostandfound`.

Never put a Firebase Admin SDK service-account JSON key inside this repository or APK. Delete any `firebase-adminsdk-*.json` file from the project and revoke that key in Google Cloud Console under **IAM & Admin → Service Accounts → Keys**.

## 2. Register Android signing fingerprints

Open **Firebase Console → Project settings → Your apps → Android app** and add the debug fingerprints:

```text
SHA-1
11:02:73:C0:E9:D5:96:D2:3D:7E:6F:67:86:47:10:64:19:C1:84:47

SHA-256
E3:35:95:82:E6:AD:57:5E:D5:1D:9A:51:3E:D5:93:AA:7D:3B:97:CA:A5:BD:27:90:16:BE:25:53:DB:E4:45:92
```

Download a fresh `google-services.json` whenever these credentials or Google sign-in settings change. Replace the file in `app/`, rebuild, and reinstall the APK.

## 3. Configure Authentication

Open **Build → Authentication → Sign-in method**.

Enable:

- Email/Password
- Google, with a project support email
- Phone

Google authentication requires the Android SHA fingerprints and the web OAuth client generated into `google-services.json`.

Phone authentication notes:

- Use E.164 numbers such as `+260970000000`.
- New projects can have a small daily SMS quota. Configure fictional test phone numbers under the Phone provider for development; test numbers do not send SMS or consume quota.
- Keep SHA-256 registered for Play Integrity verification.
- Obtain user consent because phone numbers are sent to Google for abuse prevention.
- New Firebase projects allow no SMS regions by default. Open **Authentication → Settings → SMS region policy**, choose an allow policy, and enable **Zambia** before testing a `+260` number.

Official references: [Google sign-in](https://firebase.google.com/docs/auth/android/google-signin), [phone authentication](https://firebase.google.com/docs/auth/android/phone-auth).

## 4. Create the Cloud Firestore database — currently required

Authentication and Firestore are separate products. Enabling sign-in does not create a database.

Open **Build → Firestore Database**, then:

1. Click **Create database**.
2. Choose **Native mode**.
3. Select a location near the expected users. This location cannot be changed later.
4. Choose **Production mode**; the repository contains appropriate authenticated-user rules.
5. Wait until the database status is ready.

The app uses the default database ID `(default)`. Do not create a differently named database unless the Android code is changed to target it.

Official reference: [Create a Cloud Firestore database](https://firebase.google.com/docs/firestore/quickstart).

## 5. Install the Firebase CLI and deploy rules/indexes

Install Node.js, then run:

```powershell
npm install -g firebase-tools
firebase login
firebase use cbu-lost-and-found
firebase deploy --only firestore
```

This deploys:

- `firestore.rules`
- `firestore.indexes.json`

The indexes support reports ordered by date and filtered by report type or user. Index creation can take several minutes. The Firebase Console **Firestore → Indexes** page should eventually show each index as enabled.

Official reference: [Firebase CLI](https://firebase.google.com/docs/cli).

## 6. Understand offline and online report states

Firestore offline persistence is enabled by default on Android. Cached reports can appear without being confirmed by the backend. CBU Find now displays whether its feed is from Firebase or the local cache and uses online transactions for publishing and profile changes.

After publishing a report, verify it under:

```text
Firestore Database → Data → items → {reportId}
```

User profiles appear under:

```text
Firestore Database → Data → users → {firebaseUid}
```

Official reference: [Firestore offline behavior](https://firebase.google.com/docs/firestore/manage-data/enable-offline).

## 7. Configure image storage

The app now uses Cloudinary for profile and report images because Firebase billing/card validation can block Firebase Storage setup.

Firebase Storage is no longer required for the current build. Keep Firebase focused on Authentication and Cloud Firestore.

To enable images:

1. Create a Cloudinary account.
2. Create an unsigned image upload preset.
3. Add `CLOUDINARY_CLOUD_NAME` and `CLOUDINARY_UPLOAD_PRESET` to `local.properties`.
4. Rebuild the app.

See [Cloudinary image storage setup](CLOUDINARY_SETUP.md) for the exact steps.

Do not put image bytes directly into Firestore documents. Firestore should store only the Cloudinary HTTPS image URLs returned after upload.

## 8. Recommended production hardening

Before a public campus launch:

- Enable Firebase App Check with Play Integrity.
- Add a release signing certificate and register its SHA-1/SHA-256 fingerprints.
- Replace the debug APK with a signed release build.
- Set Google Cloud billing budget alerts if Blaze is enabled.
- Add account deletion and privacy-policy flows.
- Test Firestore rules with the Firebase Emulator Suite.
- Add moderation, reporting and blocking before enabling direct user messaging.

## Troubleshooting

### `CONFIGURATION_NOT_FOUND`

The selected authentication provider is disabled or the installed APK has an outdated Firebase configuration. Enable the provider, refresh `google-services.json`, rebuild and reinstall.

### `client is offline`

Create the default Firestore database, deploy rules and confirm the phone can reach Google services. Authentication succeeding does not prove Firestore exists.

### `PERMISSION_DENIED`

Deploy `firestore.rules`, then confirm the signed-in user's UID matches the document owner. For image upload errors, check `docs/CLOUDINARY_SETUP.md` and confirm the Cloudinary preset is unsigned.

### A report appears but publishing never completes

Older builds displayed Firestore's optimistic local cache write. The current build uses server-required transactions and times out with a useful error instead.

### Google account picker opens, then sign-in fails

Confirm Google is enabled, both SHA fingerprints are registered, the refreshed configuration contains Android and web OAuth clients, and the newest APK is installed.
