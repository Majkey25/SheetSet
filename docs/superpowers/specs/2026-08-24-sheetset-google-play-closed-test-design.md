# SheetSet Google Play Closed Test Design

## Goal

Publish SheetSet as a free Google Play closed-test app after the current emulator build receives user acceptance.

## Release identity

- App: `SheetSet`
- Package: `cz.teply.sheetset`
- Version: `0.5.0-alpha.1`
- Version code: `6`
- Minimum Android: 10 / API 29
- Target Android: API 36
- Category: Music & Audio
- Default listing language: English (United States)

The Play artifact is an Android App Bundle signed with a SheetSet-specific upload key. Google Play App Signing manages the distribution signing key. Signing passwords and keystores remain outside Git.

## App details

The existing About destination becomes App details. It contains the version, Android requirement, offline privacy summary, Privacy policy, license, repository, releases, and the same yellow Buy Me a Coffee support button used by ScanIt. The support link is optional and grants no feature, entitlement, badge, priority, or digital content.

## Privacy and website

The existing GitHub Pages workflow deploys `site/`. Add `site/privacy.html` and link it from the site navigation, footer, Play Console, and App details.

The policy names SheetSet and Majkey25, provides `majkeylab@gmail.com` as the privacy contact, and states:

- PDFs, annotations, setlists, labels, settings, and backups stay on-device unless the user explicitly exports or shares them.
- SheetSet has no accounts, ads, analytics, app-owned Internet permission, tracking SDK, cloud backend, or billing SDK.
- Android system pickers and share targets process user-selected files under their own terms.
- App-private data remains until deleted, restored over, or removed by uninstall; exported files remain at the user-selected destination.
- GitHub Pages and external links use their providers' policies.

## Store listing and policy declarations

Prepare an English and Czech listing plus a publication worksheet. The listing describes only shipped behavior. It does not claim PDF text editing, OCR, cloud sync, built-in scanning, or automatic scrolling.

Play declarations:

- App access: no account or special access.
- Ads: no.
- Data collection and sharing: no developer collection or sharing.
- Target audience: 18 and over; not designed for children.
- Account deletion: not applicable.
- News, government, health, financial, gambling, social, and user-generated-content declarations: no.

## Assets

Use the existing monochrome launcher identity and real emulator captures containing only synthetic QA music. Prepare a 512 × 512 PNG icon, a 1024 × 500 PNG feature graphic, and accurate phone/tablet screenshots. Do not include copyrighted scores, rankings, prices, Play badges, device frames, or unfinished features.

## Release gate

Before Play upload:

1. Run unit tests, lint, debug/release builds, signed bundle build, and API 29/API 35 emulator tests.
2. Verify the AAB package, version, signing certificate, manifest permissions, and SHA-256.
3. Merge through a protected PR and verify GitHub Pages returns the policy over HTTPS.
4. Wait for explicit user acceptance of the visible emulator build.
5. Upload and roll out only to Closed testing, then verify the tester opt-in URL and release status.
