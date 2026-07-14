# CBU Find project change record

This document records the changes made during the assisted development of the Campus Lost and Found Android application. It describes application behavior, Firebase configuration, security work, fixes, and supporting files.

## Starting project state

The workspace already contained a native Android application using Kotlin, Jetpack Compose, Material 3, Firebase Authentication, Cloud Firestore, and Compose Navigation. It had basic email/password and Google authentication, lost/found tabs, simple report creation, report details, profile viewing, and returned-item status.

The initial implementation lacked robust Firebase setup, online-state certainty, image support, phone authentication, detailed profiles, production security rules, usable error handling, and polished loading/empty states.

## 2026-06-28 — core Firebase application build

### Architecture and data

- Expanded `User` with programme, year of study, phone number, provider/profile photo URL, and creation timestamp.
- Expanded `Item` with resolved timestamp and multiple image URLs while retaining legacy single-image compatibility.
- Added controlled lost-and-found categories for documents, electronics, keys, bags, clothing, books, money, jewellery, and other items.
- Preserved the repository/ViewModel/Compose separation already present in the project.

### Authentication

- Added validation and loading/error state handling to email registration and login.
- Retained Google authentication and corrected its OAuth configuration workflow.
- Added Firebase-auth readiness state so splash routing distinguishes authenticated and unauthenticated users.
- Hydrated new Google users from their provider name, email, phone, and photo.
- Added editable app-specific profile fields after third-party registration.

### Lost-and-found workflow

- Added validated lost/found report creation.
- Added categories, location, description, safe contact information, report ownership, and resolved/returned status.
- Added search across item title, description, category, and location.
- Added lost/found, category, and returned-state filters.
- Added confirmation before marking an item returned.
- Added clear loading, empty, unavailable, and resolved states.

### Interface

- Reworked the visual system to a professional cobalt, teal, and coral Material 3 palette.
- Rebuilt login, home, report creation, report details, and profile screens.
- Added light/dark theme support, larger touch targets, improved hierarchy, cards, status labels, and clearer calls to action.

### Firebase configuration

- Added `firebase.json`, `firestore.rules`, and `firestore.indexes.json`.
- Added compound indexes for type/date and user/date queries.
- Added ownership-based Firestore access rules.
- Added `README.md` setup/build instructions.

## 2026-06-28 — authentication configuration repair

- Diagnosed `CONFIGURATION_NOT_FOUND` as disabled/missing Authentication configuration rather than billing.
- Verified the Firebase project ID and Android package.
- Identified that the original configuration had no usable OAuth clients and a placeholder web client ID.
- Registered/used SHA-1 and SHA-256 signing fingerprints.
- Replaced `google-services.json` with refreshed Firebase client configuration.
- Removed the placeholder `default_web_client_id`, allowing the Google Services plugin to generate the real web OAuth client resource.
- Built and installed the corrected APK on the connected phone.

## 2026-06-28 — synchronization, images, and phone authentication

### Firestore certainty

- Diagnosed indefinite Publishing/Saving states as offline Firestore cache writes waiting for server acknowledgement.
- Replaced ambiguous writes with server-required, single-attempt Firestore transactions.
- Added bounded timeouts and readable backend errors.
- Added live synchronization state showing server, cache, pending writes, or backend error.
- Prevented cached listings from being presented as confirmed online writes.

### Images

- Added Firebase Storage SDK and Coil image rendering.
- Added selection of up to three report images.
- Added profile-photo selection and provider-photo display.
- Added report image previews on cards and detail screens.
- Added upload progress and a publish-without-photos recovery path.
- Added `storage.rules` restricting uploads to the authenticated user's profile/report folders, images only, under 5 MB.

### Phone sign-in

- Added Phone Authentication UI, SMS code request, code verification, automatic verification handling, and Firebase error states.
- Added Zambia `+260` guidance and SMS quota/test-number documentation.
- Added clear handling for SMS region-policy failures.

### Documentation and audit

- Added `docs/FIREBASE_SETUP.md` with Authentication, Firestore, Storage, rules, indexes, billing, security, and troubleshooting guidance.
- Added `docs/ux-audit/` containing supplied workflow evidence and `AUDIT.md`.

## 2026-06-30 — production bug fixes

### Sign-out crash

- Used the supplied Android log to identify a main-thread crash caused by a Firestore listener closing a Kotlin Flow with `PERMISSION_DENIED` after authentication was cleared.
- Changed listener errors into visible synchronization states instead of uncaught coroutine failures.
- Rebuilt, installed, and device-tested sign-out; the process remained alive and returned to login.

### Firestore permission failures

- Identified that `@DocumentId` values are populated from document paths but are not serialized as Firestore fields.
- Removed impossible rules requiring embedded `id` fields from users/items.
- Deployed corrected Firestore rules and indexes to project `cbu-lost-and-found`.
- Restored profile updates and report writes for authenticated owners.

### Storage and phone diagnostics

- Improved Storage errors to distinguish missing bucket, missing Blaze setup, and denied rules.
- Added recovery allowing selected images to be removed and a report published without images.
- Documented that new Firebase projects deny all SMS regions by default and Zambia must be allowed explicitly.

## 2026-07-06 — Blaze readiness and change tracking

### Upload hardening

- Added orientation-aware image processing using EXIF metadata.
- Added sampled decoding, maximum 1600-pixel dimension, JPEG quality 82, and a final 5 MB guard.
- Added explicit `image/jpeg` Storage metadata and uploader UID metadata.
- Reduced bandwidth, memory pressure, Storage usage, and inconsistent phone-photo behavior.

### Firebase readiness

- Added `.firebaserc` with the correct default Firebase project.
- Split Storage create/update rules from delete rules so owners can clean up their files safely.
- Added stricter profile field validation and length limits to Firestore rules.
- Added `docs/BLAZE_ACTIVATION_CHECKLIST.md` containing the exact billing, Storage, phone, deployment, and verification steps for the next day.
- Added `scripts/activate-blaze-storage.ps1`, a guarded deployment helper that checks required files and requires explicit confirmation before publishing Storage rules.
- Kept App Check enforcement intentionally disabled until a debug/Play Integrity provider is registered, preventing accidental lockout of development builds.

### Security housekeeping

- Removed the Firebase Admin SDK private key from the Android workspace without reading or using it.
- Extended `.gitignore` to exclude Admin SDK keys and JVM crash logs.
- The removed service-account key must still be revoked in Google Cloud Console because deleting the local copy does not invalidate it.

## 2026-07-13 — Cloudinary image storage migration

Firebase billing/card validation blocked Firebase Storage activation, so image hosting was moved to Cloudinary while keeping Firebase for authentication and Firestore.

### Image upload backend

- Replaced Firebase Storage uploads with Cloudinary unsigned image uploads.
- Kept the existing app-facing `uploadImage(uri, userId, folder)` repository API so profile and report screens did not need a data-flow rewrite.
- Kept the existing image compression path: selected photos are still orientation-aware JPEGs with a 5 MB maximum before upload.
- Stored Cloudinary `secure_url` values in the existing Firestore `photoUrl`, `imageUri`, and `imageUrls` fields.
- Removed the Firebase Storage Android dependency from the app module.

### Configuration

- Added Android `BuildConfig` fields for `CLOUDINARY_CLOUD_NAME` and `CLOUDINARY_UPLOAD_PRESET`.
- Loaded those values from Gradle properties or `local.properties`, allowing local setup without hardcoding account values in Kotlin source.
- Added clear runtime errors when Cloudinary is not configured, the cloud name is wrong, or the unsigned upload preset is rejected.

### Build tooling

- Upgraded Android Gradle Plugin from 8.1.1 to 8.7.3.
- Updated the Gradle wrapper metadata from Gradle 8.2 to Gradle 8.10.2.
- Verified the debug APK build with Android Studio's bundled JDK 21 using the locally cached Gradle 8.10.2 distribution.

### Documentation

- Added `docs/CLOUDINARY_SETUP.md` with exact Cloudinary setup steps.
- Updated the Firebase setup guide to state that Firebase Storage is no longer required for the current build.
- Marked the Blaze checklist as optional for future Firebase Storage or paid Firebase services.
- Updated the README to describe Firebase Auth/Firestore plus Cloudinary image storage.

## Current Firebase resources

- Project: `cbu-lost-and-found`
- Android package: `com.campus.lostandfound`
- Authentication: Email/Password, Google, Phone UI implemented
- Firestore: rules and indexes prepared and deployed
- Image storage: Cloudinary unsigned upload preset required; Firebase Storage no longer required for the current build
- App Check: documented but not enforced

## Important local files

- `app/google-services.json`: Firebase Android client configuration
- `firestore.rules`: Firestore authorization and validation
- `firestore.indexes.json`: required composite indexes
- `docs/CLOUDINARY_SETUP.md`: Cloudinary setup guide for image uploads
- `storage.rules`: optional legacy Firebase Storage authorization and upload validation
- `firebase.json`: Firebase CLI deployment mapping
- `.firebaserc`: default Firebase project selection
- `docs/FIREBASE_SETUP.md`: full Firebase setup guide
- `docs/BLAZE_ACTIVATION_CHECKLIST.md`: next-day activation checklist
- `docs/PROJECT_CHANGELOG.md`: this change record
- `docs/ux-audit/AUDIT.md`: screenshot-backed UX findings

## Known remaining work

- Create a Cloudinary unsigned upload preset and add local Cloudinary values.
- Enable Zambia in the SMS region policy and verify Phone Authentication using fictional numbers first.
- Revoke the previously downloaded Admin SDK key.
- Add release signing fingerprints before producing a Play Store/release APK.
- Configure and test App Check before enabling enforcement.
- Run the complete manual verification checklist on a physical phone.
