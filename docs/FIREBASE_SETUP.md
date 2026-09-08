# Firebase Spark setup

CBU Find uses Firebase Authentication, Firestore, FCM, App Check, and Crashlytics on Spark. It does not use Firebase Functions or Firebase Storage.

Enable Email/Password and Google providers. Phone/SMS authentication is retired. Before disabling the Phone provider in an existing project, run the ignored audit and help every phone-only user link an email or Google credential:

```powershell
npm run audit:phone-auth -- --project <firebase-project-id>
```

The audit never prints phone numbers and writes its UID-only result into ignored `backups/firestore/`.

Test and deploy Firestore rules/indexes only after the Worker and compatible clients are staged. Client writes to protected collections are denied; the Worker uses a least-privilege service account through IAM.

Register Android App Check with Play Integrity (debug provider only in debug builds), register web with reCAPTCHA v3, and add both App IDs plus the project number to `worker/wrangler.jsonc`. Enforce App Check only after real staging requests pass.

Generate a web-push VAPID key and expose only its public value. The Worker sends FCM HTTP v1 messages and removes invalid tokens. Crashlytics and Worker logs must never contain message bodies, tokens, phone numbers, student IDs, or attachments.
