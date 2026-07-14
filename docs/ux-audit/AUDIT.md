# CBU Find core-flow audit

## Scope

Combined UX and accessibility review of login, report browsing, report publishing, and profile editing from the supplied Android screenshots.

## Flow steps

1. **Login — needs work.** The hierarchy is clear, but raw Firebase errors reduce trust and only email/Google were visible despite Phone being enabled. The revised flow uses friendly errors and exposes Email, Google, and Phone.
2. **Browse reports — generally healthy.** Search, lost/found tabs, categories, and resolved state are discoverable. The prior screen did not distinguish cached reports from server data. The revised screen includes synchronization status and image previews.
3. **Create report — blocked.** “Publishing…” had no timeout or recovery because Firestore accepted a local cache write while the server remained unreachable. The revised flow uses server-required transactions, bounded waiting, upload progress, and actionable errors.
4. **Profile update — blocked.** The modal disabled actions indefinitely during an unreachable Firestore write. The revised flow fails within a bounded period, preserves entered data, reports the backend problem, and supports a profile photo.
5. **Empty and filtered states — healthy with caveats.** Empty-state copy is clear, but an empty cache could be mistaken for a genuinely empty online feed. Synchronization status now resolves that ambiguity.

## Screenshot evidence

- `01-home-with-report.png`: locally visible report without online certainty.
- `02-report-publishing-stuck.png`: indefinite publishing state.
- `03-profile-saving-stuck.png`: indefinite profile save.
- `04-home-empty-state.png`: useful empty state, ambiguous data source.
- `05-login-error.png`: authentication error is technically accurate but not recoverable for ordinary users.

## Accessibility risks visible from screenshots

- Some secondary text and disabled controls have low contrast in dark mode.
- Horizontal category chips require scrolling and may hide available filters.
- Loading state changes require TalkBack verification; screenshots cannot prove announcement behavior.
- Touch-target sizing appears adequate, but needs device testing with font scaling and TalkBack.

## Verification limits

Screenshots cannot verify TalkBack semantics, focus order, dynamic text scaling, actual network recovery, or Firebase rule enforcement. These require device and backend testing.
