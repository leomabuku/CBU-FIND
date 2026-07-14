# Optional Blaze activation checklist

The current build uses Cloudinary for image uploads, so Firebase Blaze is no longer required for profile/report photos. Keep this checklist only if you later decide to return to Firebase Storage or need paid Firebase services.

Use this checklist after attaching a billing account to Firebase project `cbu-lost-and-found`.

## Before upgrading

- [ ] Revoke the previously downloaded Firebase Admin SDK service-account key in Google Cloud Console.
- [ ] Confirm the Android package is `com.campus.lostandfound`.
- [ ] Confirm `app/google-services.json` is the current client configuration.
- [ ] Confirm both debug SHA fingerprints remain registered in Firebase.
- [ ] Decide on a Cloud Storage location close to Zambia; bucket location cannot be changed later.

## Activate Blaze safely

- [ ] Upgrade the Firebase project to Blaze and attach the intended billing account.
- [ ] Create a fixed monthly Google Cloud budget equivalent to K100.
- [ ] Add early alerts (1%, 10%, 25%, 50%, 80%, and 100%).
- [ ] Remember that budget alerts notify; they do not stop usage or guarantee a K100 maximum.

## Create and secure Storage

- [ ] Open **Firebase Console → Storage → Get started**.
- [ ] Create the default `cbu-lost-and-found.firebasestorage.app` bucket.
- [ ] From the project root, deploy the prepared rules:

  ```powershell
  & "$env:APPDATA\npm\firebase.cmd" deploy --only storage --project cbu-lost-and-found
  ```

  Or run the guarded helper, which verifies local prerequisites and asks for explicit confirmation:

  ```powershell
  .\scripts\activate-blaze-storage.ps1
  ```

- [ ] Confirm the Firebase Storage Rules page shows the same rules as `storage.rules`.
- [ ] Upload one profile photo and one three-photo report from the app.
- [ ] Confirm the files appear under `users/{uid}/profile/` and `users/{uid}/reports/`.
- [ ] Confirm each uploaded file is JPEG, less than 5 MB, and has an `uploadedBy` metadata value.

## Phone authentication

- [ ] Open **Authentication → Settings → SMS region policy**.
- [ ] Allow Zambia before testing real `+260` phone numbers.
- [ ] Configure fictional test numbers first to avoid charges and throttling.
- [ ] Confirm SHA-1 and SHA-256 fingerprints are registered for app verification.

## Final verification

- [ ] Register, sign in, sign out, and sign in again with Email/Password.
- [ ] Repeat with Google and Phone.
- [ ] Edit student ID, programme, year, contact information, and profile photo.
- [ ] Create lost and found reports with and without images.
- [ ] Confirm reports exist in Firestore and images exist in Storage.
- [ ] Mark a report returned and verify its resolved status.
- [ ] Test search, category, lost/found, and returned-item filters.
- [ ] Run `:app:assembleDebug` and `:app:lintDebug` before the final demonstration.

## Do not enable yet

Do not enforce Firebase App Check until the Play Integrity or debug provider is configured and verified. Enabling enforcement prematurely can block every development build.
