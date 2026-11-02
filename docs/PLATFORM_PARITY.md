# Platform parity — Android and web

CBU Find is one product with two clients. Both run against the same Firebase project (`cbu-lost-and-found`), the same Firestore data, and the same trusted API Worker. A student who reports an item on their phone and later opens the web app must find the same report, the same claim, and the same conversation.

This document records what "the same job" means concretely, how the two clients are kept aligned, and which differences are intentional.

## 1. Capability matrix

Verified against source on both sides (2026-11-02).

| Capability | Android | Web | Notes |
| --- | --- | --- | --- |
| Email/password sign-up and sign-in | `AuthScreen.kt` | `AuthPanel` | Same Firebase project, same accounts |
| Google sign-in | `AuthScreen.kt` | `AuthPanel` | Web needs the hostname in Firebase authorized domains |
| Password reset | `AuthViewModel.sendPasswordReset` | `AuthPanel` | |
| Browse public reports | `HomeDashboardScreen.kt` | `FeedScreen` | Real-time Firestore listeners |
| Search, LOST/FOUND filter, category filter | `HomeDashboardScreen.kt` | `FeedScreen` | Same 9 categories |
| Create report with photos | `CreateListingScreen.kt` | `CreateReportScreen` | **6 photos**, signed upload, compressed before sending |
| Report details | `ItemDetailsScreen.kt` | `ReportDetailsScreen` | Shows every attached photo |
| Claim an item | `ItemDetailsScreen.kt` | `ReportDetailsScreen` | `FOUND_IT` / `THIS_IS_MINE` |
| Reveal private contact | accepted claim only | accepted claim only | Via `GET /v1/reports/:id/contact` |
| Mark resolved / reopen | `ItemDetailsViewModel` | `ReportDetailsScreen`, `Chat` | Owner only; closes the accepted claim |
| Claims accept / reject / cancel | `ClaimsScreen.kt` | `ClaimsScreen` | Acceptance creates the conversation |
| Inbox with unread state | `InboxScreen.kt` | `MessagesScreen` | |
| Chat with attachments | `ChatScreen.kt` | `Chat` | Image/video/file, 20 MB ceiling |
| Edit and delete own message | `ChatScreen.kt` | `Chat` | `PATCH`/`DELETE /v1/conversations/:id/messages/:messageId` |
| Block / report a message | `ChatScreen.kt` | `Chat` | Moderators see ±2 adjacent messages only |
| Mark resolved from the chat | `ChatScreen.kt` | `Chat` | |
| Edit profile and photo | `ProfileScreen.kt` | `ProfileScreen` | |
| Theme: system / light / dark | `SettingsScreen.kt` | `SettingsScreen` | Stored per device |
| Notification preferences | `SettingsScreen.kt` | `SettingsScreen` | Same 6 toggles |
| Register device for push | FCM (native) | FCM web push | Same `PUT /v1/devices`, `platform` differs |
| Blocked accounts list + unblock | `SettingsScreen.kt` | `SettingsScreen` | |
| Delete account | `SettingsScreen.kt` | `SettingsScreen` | Queued job, same reference id |
| Moderation case queue | `ModerationScreen.kt` | `ModerationScreen` | |
| Moderation actions | `ModerationScreen.kt` | `ModerationScreen` | Remove/restore report, lock/unlock chat, suspend/reactivate user |
| Role management (ADMIN) | `ModerationScreen.kt` | `ModerationScreen` | Last-admin protection enforced by the Worker |

## 2. Intentional differences

These are platform realities, not gaps. Neither client blocks the other.

- **Push transport.** Android uses native FCM; the browser uses FCM web push through `public/firebase-messaging-sw.js`. Both register tokens with the same endpoint.
- **Location entry.** Android offers a GPS-assisted picker (`LocationPicker.kt`) with reverse geocoding when permission is granted. The web app offers free text with campus suggestions and never requests location. Both store a plain `location` string, so a report created on either side renders identically on the other.
- **Navigation shell.** Android uses a bottom navigation bar; web uses a sidebar on desktop and a bottom bar on narrow screens.
- **Sign-in UI.** Google sign-in on web uses a popup; on Android it uses the platform account picker.

## 3. Shared constants (single source of truth)

`packages/contracts/src/index.ts` defines the values both clients must agree on:

| Constant | Value | Used by |
| --- | --- | --- |
| `REPORT_CATEGORIES` | 9 campus categories | Web report form, Android `ItemCategories` mirror, web filter list |
| `MAX_REPORT_MEDIA` | 6 | Web upload cap, Worker `reportSchema`, Android `MAX_REPORT_PHOTOS` |
| `MAX_MEDIA_BYTES` | 20 MB | Web `uploadSigned`, Worker `mediaAssetSchema`, Android `CHAT_MEDIA_MAX_BYTES` |
| `IMAGE_MAX_DIMENSION` | 1600 | Web `compressImage`, Android `ImageCompressor` |
| `IMAGE_JPEG_QUALITY` | 82 | Web `compressImage`, Android `ImageCompressor` |

The web client and the Worker import these directly. Kotlin cannot import TypeScript, so the Android values are mirrored in source and **verified by tests**.

## 4. How drift is prevented

`web/tests/platform-parity.test.ts` reads the Android sources and fails if:

- `ItemCategories.all` differs from `REPORT_CATEGORIES`
- `MAX_REPORT_PHOTOS` differs from `MAX_REPORT_MEDIA`
- `CHAT_MEDIA_MAX_BYTES` differs from `MAX_MEDIA_BYTES`
- `MAX_DIMENSION` or `JPEG_QUALITY` in `ImageCompressor.kt` differ from the contracts

It runs as part of `npm test`. Verified by temporarily reverting Android to 3 photos — the test failed — then restoring 6.

When changing a shared value: update `packages/contracts`, the Android mirror, and run `npm test`.

## 5. Fixes applied in this pass

| Issue | Before | After |
| --- | --- | --- |
| Photos per report | Android 3, web 6, API 6 | 6 everywhere, from one constant |
| Report photo processing | Android resized to 1600 px / q82; web uploaded raw files up to 20 MB | Both resize to 1600 px / q82 before upload |
| Category definitions | Duplicated literals in Kotlin and TS with no check | Contract constant + parity test |
| Drift detection | None | `platform-parity.test.ts` |

**Not verified here:** the Android change was made without compiling. This environment has no JDK or Android SDK, so `./gradlew :app:assembleDebug` could not run. The edit is limited to one constant, three usages, and one string; it must still be confirmed on a machine with JDK 17 and SDK 36 before release.

## 6. Adding a feature to parity

1. Add the data shape or constant to `packages/contracts`.
2. Add the Worker endpoint/validation.
3. Implement it in `web/app/portal-app.tsx` (or a new route under `web/app/`).
4. Implement it in the matching Android screen and view model.
5. Extend `platform-parity.test.ts` if a new shared constant was introduced.
6. Update the matrix in this document.

A feature that ships on only one client is a defect against this goal, unless it is listed in §2 as an intentional platform difference.
