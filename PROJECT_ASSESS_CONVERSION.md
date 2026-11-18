# Project A.S.S.E.S.S. source conversion

Derived from the supplied MCQ Quick Check v1.2.5 project. Existing educational features remain preexisting work.

- New Android application ID: `com.jpagdi.projectassess`; internal Kotlin namespace remains `com.jpagdi.mcqquickcheck` for now.
- App launcher label: Project A.S.S.E.S.S.; version 1.0.0.
- Removed license API, subscription UI, entitlement checks, scan quotas, item caps, and commercial backend source files.
- Classes remain an optional Settings toggle; exports are not paywalled.
- Update checks re-enabled against `sirjpagdi/Project-A.S.S.E.S.S.-Release` (read-only GitHub API call; see README for the release convention). Test in airplane mode and with a newer test release before distribution.
- Original welcome animation assets remain pending new artwork. Other branding and help/changelog text may still reference the original application.
- No APK compiled or device-tested. Run Android Gradle build, lint, and regression testing before distribution.
- Use a new private release signing key; do not ship the included historical debug keystore as a release credential.
- Preserve required third-party licenses and original development provenance.

## Fixes after the first conversion pass

- Restored the update flow that had been stubbed out (launch check, home prompt, About button) and removed a call to `maybeShowRestorePrompt()`, which no longer existed (compile error).
- File sharing used the old package name for the FileProvider authority in four places; now `<applicationId>.fileprovider`, matching the manifest (would have crashed every export, share, update install and diagnostic email).
- `allowBackup` is now false so learner records are not copied to Android cloud backup; the in-app Backup Data remains.
- Removed paid-plan, server and Device-ID text from What's New and the changelog; added a Project A.S.S.E.S.S. v1.0.0 entry.
- Diagnostic report address changed to `sirjpagdi+project.assess@gmail.com`.
- Added a GitHub Actions workflow so the APK can be compiled without a local Android setup.
- A static scan found no unresolved resources, manifest classes or layout views. This is not a compile; a real Gradle build is still required.

