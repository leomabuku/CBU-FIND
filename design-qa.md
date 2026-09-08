# CBU Find design QA

- Source visual truth (dark): `C:\Users\leokm\.codex\generated_images\019fea5b-87c3-79c0-bc80-b1110607e43d\exec-adae262c-46c1-482a-b576-e5f0c9b67903.png`
- Source visual truth (light): `C:\Users\leokm\.codex\generated_images\019fea5b-87c3-79c0-bc80-b1110607e43d\exec-445d3a9b-2e34-4a91-9fdf-4a9cd75fb656.png`
- Source pixels: 853 x 1844 for each reference.
- Web implementation: `https://cbu-find-web-staging.leokmabuku.workers.dev`
- Android implementation evidence: `C:\ANTIGRAVITY\Campus lost and found\docs\ux-audit\cbu-ui.png`
- Browser viewport: 948 x 600 CSS pixels at device scale 1.
- Android capture: 1080 x 2400 device pixels.
- State: authenticated web report feed in dark mode; authenticated web Settings in dark and light modes; Android signed-out dark mode.

## Full-view comparison evidence

The authenticated web feed was captured in the browser and compared with both selected references. It uses the selected direction's near-black/ivory surfaces, orange primary action, compact report rows, prominent search, segmented report-type control, category filter, and persistent navigation. The light and dark Settings states were both captured after using the actual appearance controls. The source is a narrow mobile composition while the web evidence is a desktop composition, so the comparison is structural rather than pixel-for-pixel.

The Android APK was installed successfully on the configured emulator and its signed-out dark state was captured. The emulator does not hold an authenticated account, so the Android Home and Settings states could not be captured without an account sign-in.

## Focused-region comparison evidence

- Feed controls: search, report type, category filter, and Create report were legible and usable.
- Report list: thumbnails, LOST/FOUND labels, titles, location/date metadata, and state badges aligned consistently.
- Appearance: Light, Dark, and Follow device changed the real document theme and persisted locally.
- Messaging: inbox and an existing conversation rendered on the deployed web app without raw data or a false empty state.

## Comparison history

1. P2 — the first desktop capture showed horizontal overflow because the three-column report toolbar exceeded the available content width beside the sidebar.
   - Fix: changed the toolbar to stack below 1100 px and made the segmented control distribute its buttons.
   - Post-fix evidence: the recaptured 948 x 600 feed had no horizontal scrollbar; all controls and report rows fit the viewport.

## Required fidelity surfaces

- Fonts and typography: hierarchy, compact metadata, weight, wrapping, and truncation are consistent; system/Geist fallbacks differ slightly from the generated reference but remain an acceptable platform adaptation.
- Spacing and layout rhythm: compact grouped rows and control spacing match the selected density; the web sidebar is an intentional desktop adaptation.
- Colors and visual tokens: dark graphite/black, warm ivory, orange action, green FOUND state, and muted borders map to the references.
- Image quality and asset fidelity: the real CBU Find logo and report images are used; missing report images use the real logo rather than a generated placeholder.
- Copy and content: campus-specific report, claim, settings, and messaging language is retained.

## Remaining blocker

- P2 — the authenticated Android Home and Settings screens still need a same-state visual capture on the user's signed-in physical phone. Automated build, lint, model compatibility tests, and emulator installation pass, but the emulator is signed out.

## Implementation checklist

- Install the new debug APK on the signed-in phone.
- Open Messages and the previously crashing conversation.
- Verify Home in light and dark modes.
- Reject one disposable test claim and confirm the immediate status update.

final result: blocked
