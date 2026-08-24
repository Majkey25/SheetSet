# SheetSet Google Play Closed Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare, publish, and verify SheetSet `0.5.0-alpha.1` for Google Play Closed testing after user acceptance.

**Architecture:** Keep the single Android app and static `site/` deployment. Add conditional local release signing through ignored `keystore.properties`, publish policy/store metadata from source, and use Play App Signing for distributed APKs.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle, Android App Bundle, GitHub Actions/Pages, Google Play Console.

---

### Task 1: App details and policy link

**Files:**
- Modify: `app/src/main/java/cz/teply/sheetset/ui/SettingsDrawer.kt`
- Modify: `app/src/main/res/values*/strings.xml`
- Test: `app/src/androidTest/java/cz/teply/sheetset/SettingsFlowTest.kt`

- [ ] Add a failing UI assertion for App details and Privacy policy while retaining the ScanIt-style support button assertion.
- [ ] Run the targeted API 35 test and confirm it fails because App details or Privacy policy is absent.
- [ ] Rename the visible About destination to App details and add `https://majkey25.github.io/SheetSet/privacy.html`.
- [ ] Run the targeted test and confirm it passes in English and Czech resources compile.

### Task 2: Public policy and Play worksheet

**Files:**
- Create: `site/privacy.html`
- Modify: `site/index.html`
- Modify: `site/styles.css`
- Create: `docs/play-store/PLAY_CONSOLE.md`

- [ ] Write a SheetSet-specific privacy policy with developer contact, local data handling, retention/deletion, sharing, GitHub Pages hosting, and external-link disclosures.
- [ ] Add Privacy links to site navigation and footer.
- [ ] Write exact English and Czech listing text, release notes, App content answers, Data safety answers, and asset requirements.
- [ ] Serve `site/` locally and verify `/`, `/privacy.html`, navigation, responsive layout, and no console/network errors beyond local assets.

### Task 3: Release identity and signing

**Files:**
- Modify: `.gitignore`
- Modify: `app/build.gradle.kts`
- Create locally only: `keystore.properties`
- Create outside Git: `C:/Users/mates/.android/sheetset/sheetset-upload.jks`

- [ ] Add `keystore.properties` to `.gitignore`.
- [ ] Load signing properties only when the local file exists; keep debug/CI builds unchanged.
- [ ] Set version code `6` and version name `0.5.0-alpha.1`.
- [ ] Generate a SheetSet-specific RSA 4096-bit upload key valid for 10,000 days without printing its password.
- [ ] Build `bundleRelease` and verify the AAB is signed with the new upload certificate.

### Task 4: Store assets

**Files:**
- Create: `docs/play-store/assets/icon.png`
- Create: `docs/play-store/assets/feature-graphic.png`
- Create: `docs/play-store/assets/en-US/phone/*.png`
- Create: `docs/play-store/assets/cs-CZ/phone/*.png`
- Create: `docs/play-store/assets/tablet/*.png`

- [ ] Capture synthetic QA screens from the final emulator build.
- [ ] Produce the Play icon and feature graphic from the existing SheetSet identity.
- [ ] Validate dimensions, PNG mode, alpha rules, file size, and absence of private/copyrighted content.

### Task 5: Android and repository verification

- [ ] Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest --no-daemon --console=plain`.
- [ ] Run all instrumentation tests on API 35 and changed flows on API 29.
- [ ] Inspect merged manifest permissions and runtime dependencies.
- [ ] Review `git diff --check`, secrets, generated files, and signing-file exclusion.

### Task 6: GitHub publication and Pages

- [ ] Commit with Conventional Commits, push the feature branch, and open a PR to `main`.
- [ ] Wait for Android CI and review the full PR diff.
- [ ] Merge only after required checks; restore branch protection immediately if an administrative bypass is needed.
- [ ] Verify the Pages workflow succeeds and both public URLs return HTTP 200 over HTTPS.

### Task 7: Google Play Closed testing

- [ ] Wait for explicit user acceptance of the visible emulator build.
- [ ] Create or select SheetSet in Play Console with package `cz.teply.sheetset`.
- [ ] Complete store listing, App content, Data safety, content rating, target audience, contact, and Privacy policy.
- [ ] Configure a closed tester list and feedback channel.
- [ ] Upload the exact verified signed AAB, add localized release notes, save, review, and roll out to Closed testing.
- [ ] Verify processing succeeds, the release is available to testers, and the opt-in URL opens.
