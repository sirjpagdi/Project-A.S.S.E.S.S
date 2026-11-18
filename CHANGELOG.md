# Changelog

All notable changes to MCQ Quick Check (formerly MCQ Scanner). Keep this
file in sync with `app/src/main/java/com/jpagdi/mcqquickcheck/Changelog.kt`,
which mirrors this list inside the app (tap the version number on the
home screen).

## v1.2.5 — 2026-11-10

- New: Learners. Open Classes, then Learners: everyone enrolled in your
  classes in one searchable list (by name or ID). Tap a learner to see every
  test saved under their ID, newest first, with score and percent, their
  average, best and lowest, a small trend line, and the saved sheet of each
  test one tap away. It only reads results the app already keeps, so
  nothing changes in how you scan, and it stays available if a plan ends.
- New: archive tests and classes. Press and hold a test on the main screen
  (Archive) or a class on the Classes screen to hide it without deleting
  anything: its scans, scores and learners are kept, still count in the
  Learners database, and come back from "Archived tests" / "Archived
  classes" with Restore (or Delete permanently from there). Archived
  classes are left out of the exam class picker, unless an exam already uses
  one, and exams already using an archived class keep working.
- New: test sets. In a test's template screen, "Test sets" lets you print 2, 3 or
  4 sets (X Y, X Y Z or W X Y Z). The sheet gets a "Set:" bubble row under the
  title, in the same bubbles as the answers. Set X (W with four sets) uses the
  test's own answer key; add a key for each other set with its "Set key" card.
  Students shade their set and the scanner grades the sheet with that set's
  key. If the set bubble is blank, double-marked or has no key yet, the app asks
  "Which set is this sheet?" instead of guessing. The set is kept with each saved
  scan, and set keys follow the test through rename, duplicate, delete and
  Backup / Restore. Points per item are shared by all sets.
- New: set answer keys can be scanned. Each "Set key" card offers Create Manually
  or Scan Answer Key Instead, same as the main key.
- New: competencies per set. In Competencies, a test with sets gets an "Item
  numbers for" line: pick a set and place the same competencies on that set's
  item numbers (it starts as a copy of the first set). Competency names are shared.
- New: item analysis by set. A test with sets gets a "Showing" line under the
  subtitle: pick one set on its own (using that set's competency item numbers),
  or all sets pooled (each sheet counts with its own set's item numbers).
- New: tablet landscape. Screens now rotate on tablets without losing what you
  were doing, and on wide screens the content stays in a centered column instead
  of stretching edge to edge. Scanning stays portrait-only. The side menu is
  capped at 360 dp wide.

## v1.2.4 — 2026-11-06

- Improved: the ID reader has a better "eye". Besides the coarse pixel grid
  it used before, it now describes each digit by the direction of its pen
  strokes (HOG), which tells apart shapes the grid blurred together (a 6
  against a G, a 4 against a 9). On learners the model had never seen it
  reads about 91% of digits right, against 88% for the old description
  (it fixed 39 digits the old one got wrong and broke 18). In a simulation
  of a 40-learner class, 95% of sheets link to the right learner on their
  own with no wrong links, and about 1 in 20 needs a manual check (about 1
  in 10 before). Older model files still load.
- Improved: the model is retrained on about 1,100 real handwritten digits
  from two classes' scanned sheets, up from 120. A model trained on one class
  alone already read a different class's sheets at 93% of digits, so the
  reader carries over between classes; the second class adds variety. A
  sample that had been labelled wrongly (a "b"-style 0 filed as a 6) is
  corrected. The digit images stay in tools/id_model/real_samples/ for
  future retraining. The model file is about 700 KB (it was 140 KB) because
  it now reads 324 numbers per digit.
- Fixed: sheets of learners who are NOT enrolled in the class could link to a
  look-alike enrolled learner (same school prefix, different last digits):
  in a test, 4 of 45 stray sheets did. The automatic link now needs the read
  to agree with the roster ID almost digit for digit (the old rule tolerated
  several clearly-different digits). Genuine sheets that link on their own go
  from 92% to 85% in the same test, so a few more ask for a one-tap
  confirmation; strays that link by mistake drop from 9% to 2%. A stray whose
  ID differs from an enrolled learner's by a single clearly-written digit can
  still slip through, and a wrong link can be removed from the class sheet.

## v1.2.3 — 2026-10-16


xt sheet. In simulation this cuts the manual-tap
  pile from roughly 35-55% to roughly 10-20% of scans, at a cost of about
  one wrongly linked sheet in 200-400. A sheet whose best match already has
  a score still asks before replacing it, and a wrong score can be removed
  from the class sheet.
- New: "RE-CHECK IDS" on the class sheet, above the unmatched scans. It
  re-reads every unmatched sheet from its saved image and links the ones
  that now match, in several passes (each newly linked learner helps settle
  the next sheet, so the last few resolve by elimination). It also runs
  quietly after you confirm a sheet by hand.
- New: ID reading now writes to the same diagnostics log as the bubble
  scanner, so Send Diagnostic Report carries it. Per scan it records the
  confidence of every box, why a box was blank, the matcher's gate values
  and its decision; for scans linked automatically or confirmed by you, it
  also lists which boxes the reader got wrong as single "true>read" digit
  pairs (e.g. 6>0). It never records a learner ID, a name, or a digit
  string, so a report can't identify anyone.
- New: manual matching right after the scan. When a sheet's ID can't be
  matched confidently, a "Whose sheet is this?" dialog opens at once: the
  handwritten ID boxes cut from the sheet sit at the top, and under them —
  lined up digit for digit — what the reader saw and each candidate's ID,
  with every digit that disagrees shown in red, so a one-digit difference
  between near-identical LRNs is easy to spot. Tap the right learner, or
  "Someone else…" to search the roster by name or ID, or "Decide later" to
  leave it under Needs ID check. The same dialog opens from the unmatched
  cards on the class sheet, and the candidate list is now up to 5 learners.
- New: a light-grey "write inside here" zone is now printed inside every ID
  box of newly generated sheets, so learners keep their digits off the box
  lines (a digit touching a line was the reader's hardest case). Sheets you
  have already printed are unchanged and still read.
- Hardened: the ID reader straightens handwriting slant before reading
  (on digits sheared up to ~25 degrees the model went from 96% to 99.7%
  right; upright digits unchanged) and is stricter about which ink belongs
  to a box, so a speck of printed line can no longer stretch a digit into
  something else. Every unmatched scan now keeps its ID read with it, so
  re-checking after you confirm a sheet is instant and no longer needs the
  saved image; the RE-CHECK IDS button still re-reads saved images with the
  current reader.
- New: the "Whose sheet is this?" dialog — the handwritten ID next to the
  suggested learners — now opens right after a scan whose ID couldn't be
  matched confidently, so you can assign the sheet to a learner on the scan
  page itself. Cancel leaves it under Needs ID check on the class sheet.
- Changed: suggested matches only offer learners who don't have a score on
  this exam yet. Learners who already have one are listed last under
  "Other learners", tagged "Already scored".
- Improved: the ID digit model is retrained on 120 real handwritten digits
  from ten learners' sheets. On three sheets it had never seen it read 78%
  of digits right, against 56% for the previous model. The labelled digit
  images are kept in tools/id_model/real_samples/ so every retraining
  includes them.
- Note: reading a digit and recognising it are separate steps. On the
  test sheet this brings 12 unreadable boxes to 10 of 12 correct; oddly
  written digits (a filled-in 6, a "b"-style 6) can still be misread by
  the generic model. The roster matcher absorbs a few wrong digits, and
  `tools/id_model/train_id_model.py --samples` can teach the model your
  learners' own handwriting (crops are now cut WITH their margin — see
  that script's header).

## v1.2.2 — 2026-10-06

- New: restore your subscription after a reinstall. If you uninstall and
  reinstall the app, it silently asks the license server whether a paid
  plan is linked to your phone; if one is, you get a "Restore Your
  Subscription?" prompt (once a day until you accept) and one tap brings
  the plan back — no payment reference, no transfer request. If nothing
  matches, nothing happens and the app carries on as normal.
- How the match works: a phone that has a paid plan and still carries its
  original random Device ID now quietly reports its ANDROID_ID-derived ID
  too, the first time it syncs online on this version. That link is what
  lets a later reinstall find the plan. So: update to v1.2.2 and open the
  app online once BEFORE any reinstall. Installing an update over the old
  app never changes your Device ID or plan — only uninstalling does.
- Server: new `device_links` table (`migrate_v1_2_2.sql`) and the
  `/device/link`, `/device/restore-check` and `/device/restore` endpoints;
  the Worker changes are in `worker-patches/v1.2.2-restore-subscription.md`.
  A phone that was reinstalled BEFORE it could link is still restored
  with the admin Device Merge Tool.

## v1.2.1 — 2026-10-06

- New: the app now checks for updates on the first open of each
  calendar day (device local time) instead of a rolling 24 hours, and
  tells you on the home screen when a newer version is ready: Update Now
  (opens About, which handles the download and install), Skip This
  Version, or Later. At most one prompt per day. A check that fails —
  offline, rate-limited — no longer uses up the day; the next launch
  retries after 30 minutes.
- Changed: a fresh install now derives its Device ID from the phone's
  ANDROID_ID (hashed — the raw value is never sent) instead of a random
  UUID, so uninstalling and reinstalling on the same phone brings the
  same Device ID, and the subscription, back by itself. Installs that
  already have an ID keep it unchanged. Limits: Android 8+ ties
  ANDROID_ID to the app's signing key, so a factory reset, or a build
  signed with a different key (e.g. a debug build), still produces a new
  ID — use the transfer flow or the admin Device Merge Tool for those.
- New (admin / server): Device Merge Tool in `/admin` and a
  `device_aliases` table. Moves a subscription and scan count from an old
  Device ID to a new one and leaves the old ID aliased to the new, for
  teachers whose ID changed. Existing databases need
  `migrate_v1_2_1.sql`; the Worker changes are in
  `worker-patches/v1.2.1-device-merge.md`.

## v1.2.0 — 2026-09-27

- New: Subscription plans — Quarter (₱99/3mo), Semester (₱149/6mo), and
  Full Year (₱249/12mo), alongside the existing free tier. Reachable from
  the sidebar's new Subscription row.
- New: Subscriptions screen shows your current plan with a clear "what
  you can/can't do" breakdown, scans remaining, and renewal date. Submit
  a payment reference after paying — no license key to type or email.
  Your plan activates automatically the next time the app is online,
  usually your next launch.
- New: Transfer a paid subscription to a new phone from the
  Subscriptions screen (enter your old device's Device ID) — reviewed
  manually, same as a payment.
- New: tap a plan on the Subscriptions screen to compare it with your
  current plan, feature by feature (Free vs Quarter, Quarter vs
  Semester, and so on) before continuing to payment. Plan tiers: Excel
  exports of Item Analysis and the scanned-sheets list on any paid plan;
  Classes, ID row, auto-linking and class score sheets on Semester and
  Full Year; class score export on Full Year only.
- Item Analysis Excel export is now enforced as a paid feature — Free
  accounts see an upgrade prompt instead of exporting (on-screen
  Item Analysis stays free). Class score export likewise asks for the
  Full Year plan; the class score sheet itself stays viewable.
- Changed: the Most/Least Learned Competencies tables in the Item
  Analysis preview and exported .xlsx now have one row per competency
  (not one per item). The Item No. cell lists that competency's items
  as ranges, e.g. "1-15, 18, 21-24", followed by the competency and its
  rank.
- Free plan: 100 scans per month, resetting automatically on the 1st —
  not a one-time lifetime cap. Also limited to answer keys/sheets of up
  to 60 items, and does not include Item Analysis Excel export or the
  scanned-sheets list export (both stay available on-screen).
- Scanning works fully offline as before: the free plan's scan count is
  checked locally and only reconciled with the server once per app
  launch (when there's a connection), never blocking a scan just because
  there's no signal.
- Backend: new `/license-api` Cloudflare Worker + D1 project (separate
  from the app repo) — see its own README.md for deployment and the
  admin dashboard used to approve payments/transfers.
- Launch discount: about half off all paid plans for a limited time
  (Quarter ₱50, Semester ₱75, Full Year ₱125). The Subscriptions screen
  shows the discount automatically whenever the live price is below the
  app's regular-price reference — percentages are rounded to the nearest
  whole number, prices with centavos (e.g. ₱49.99) display in full, and
  the banner names the plans that are actually discounted — and hides it
  automatically once pricing reverts. Ending the promo is a Plan
  Settings edit in `/admin`, not an app update. The banner and the
  current-plan card follow your Button Accent / Top Panel Accent
  colors.
- New (Semester and Full Year plans): Classes — enroll your class rosters
  (ID, name and gender) by typing learners in or importing a CSV/XLSX
  file, assign classes to an exam, and each scan's score is saved to the
  right learner automatically. Every class has its own score sheet per
  exam that you can sort by name, score, ID or gender; exporting it to
  Excel is a Full Year plan feature. Optional: switch it on under Settings > Learner identification;
  your existing classes stay viewable if your plan ends.
- New: ID row on answer sheets — with Learner identification on, newly
  generated sheets get one extra header row where each learner writes
  their ID, one digit per box (4 to 12 digits, set with a stepper in
  Settings, default 6; a DepEd LRN is 12). The Score box grows beside it
  and is left blank for writing the score. Sheets you already printed
  are unchanged. The scanner matches the handwriting against your
  enrolled learners and asks you to confirm anything it isn't sure of.
- Backup and Restore now include your classes, learners and per-exam
  scores.

## v1.1.1 — 2026-09-19

- New: competency grouping for Item Analysis. "Set Up Competencies"
  (Item Analysis screen) lets you name competencies and check off which
  items belong to each. Adds a Competency column to the preview and
  exported .xlsx, plus a whole-class Mastery Level and a Most-Learned-
  first rank per competency (separate from the existing per-item Least
  Learned rank, which is unchanged).
- New: exported Item Analysis .xlsx gains two more tables below the main
  one (2-row gap, columns B-D and F-H) — "Most Learned Competencies" and
  "Least Learned Competencies", one row per item, matching the standard
  DepEd paper format so they can be copied straight into it. Only appear
  when at least one competency has been set up for that test.
- The exported .xlsx's sort/filter dropdowns (AutoFilter) now only span
  the main table's own rows, so sorting or filtering it can never shift
  or hide the two competency summary tables below.
- Competency names wrap within their cell instead of overflowing or
  getting clipped, both in the main table and the two summary tables.
- New: Duplicate — long-press a test on the home screen, then Duplicate
  alongside Rename/Cancel. Copies the template, answer key, points, and
  any competencies you've set up, under an auto-generated "(Copy)" name;
  scanned sheets and subfolders are never copied.

## v1.1.0 — 2026-09-12

- Renamed the app's package ID from `com.jpagdi.mcqscanner` to
  `com.jpagdi.mcqquickcheck`. **BREAKING for existing installs:** Android
  treats a package ID change as an entirely different app, so this build
  cannot be installed over the old one. Use Backup Data to export
  everything BEFORE switching, install this build fresh (uninstalling the
  old one first), then Restore Data to bring your saved tests, keys, and
  settings back.
- Fixed: Backup Data, Export Scans, and other file exports could crash
  with an out-of-memory error on larger datasets — Download now streams
  the finished file to disk instead of holding the whole thing in memory
  first.
- Fixed: Restore Data could crash the same way on larger backup files —
  now reads and applies a backup in a streaming pass instead of parsing
  the whole file into memory up front.
- Restore Data now asks to clear existing saved tests/scans before
  importing (instead of merging onto them), to avoid the conflicts that
  could previously cause a failed or corrupted restore.
- New: bulk-move and bulk-delete for scanned sheets — long-press a row's
  send or trash icon on the Scanned Sheets screen to select several at
  once, with a select/unselect-all toggle for either action.
- New: optional Parent/Guardian signature line on printed answer sheets,
  just below the scannable area — off by default, toggle it on in
  Settings. Doesn't appear in the on-screen preview, only on the actual
  generated sheet.
- New: 1200px scanned-sheet storage resolution option, alongside the
  existing 1600/1800/2000px choices.
- New: Save List button on the Scanned Sheets screen — saves that
  folder's entire list, including rows off-screen, as one full-resolution
  image file.
- Exported scanned-sheet zip filenames now include the folder name, so
  separately exported folders are easier to tell apart in Downloads.
- Item Analysis's exported .xlsx now includes whole-class test-result
  columns after D Recommendation: Number of Correct Response, % of
  Correct Response, Mastery Level (Mastered/Nearly Mastered/Below
  Mastery), and Least Learned (Rank) — ranking every item from 1 (least
  mastered) to n (most mastered).

## v1.0.0 — 2026-08-08

- First stable release. Version numbering resets here — versionCode keeps
  incrementing normally underneath (was 81, now 82; Android requires this
  to only ever increase), but versionName resets to mark this as the real
  starting point for public/tester distribution. Everything below this
  line (v0.1 through v4.0.2) is pre-1.0.0 development history, kept
  as-is for reference — not renumbered or rewritten. In the app itself,
  this same split is reflected as `Changelog.entries` (fresh, starts
  here) vs `Changelog.legacyEntries` (frozen, everything below),
  surfaced via a "Show more" expand under a "v0.0.0 — Historical
  development" bridge entry on the in-app Changelog screen.
- Redesigned interface throughout — rounded cards, clearer icons, more
  consistent look across every screen.
- Added: haptic feedback when swiping left/right between scanned sheets
  in the fullscreen reviewer.
- Fixed: Backup Data was silently skipping two settings that have
  existed for several versions — storage resolution (added v3.6.1) and
  font size (added v3.9.9). Both live in their own standalone getter/
  setter pairs outside SettingsPrefs.Defaults, which is why they were
  missed when Backup/Restore was last touched — that block only ever
  got updated in step with Defaults' own fields. Both are now included
  in Backup and Restore, guarded the same way paperSize/
  welcomeAnimationEnabled already are so restoring an older backup that
  predates this fix safely leaves the current value untouched instead
  of resetting it.
- Security: release builds are now signed with a real, private release
  key (keystore.properties, gitignored) instead of the same
  debug.keystore used for local development — that file is committed
  to this public repo, so anyone with repo access has always had it,
  meaning anyone could previously have built and signed a modified APK
  that Android would accept as a legitimate update to this app. This
  changes the app's signature going forward: existing installs will
  need to be uninstalled once before this version installs over them —
  a one-time cost, not expected to recur, since every release from here
  on reuses this same real key. Also enabled minifyEnabled (R8 code
  shrinking/obfuscation) on release builds, which was off entirely
  before — meaningfully raises the bar against casual decompiling,
  though not a guarantee against a determined attacker. See
  keystore.properties.template for the release-signing setup this now
  requires.
- What's New on the About page also resets forward-only from here — see
  WhatsNew.kt.

---
## Pre-1.0.0 development history

## v4.0.2 — 2026-08-08

- Fixed: v4.0.1 failed to build — ItemAnalysisActivity.kt used Toast
  without importing it (missed because text-based checks don't catch
  missing imports; this needs an actual compile to catch). Added the
  missing import.

## v4.0.1 — 2026-08-08

- Fixed: changing the font-size setting only updated the Settings
  screen itself live — every other already-open screen only picked
  up the new size the next time it was actually opened. Every screen
  now also re-applies the current font size when resumed (e.g.
  backing out of Settings), so returning to any screen after a
  font-size change reflects it immediately.
- Fixed: Item Analysis and Test Detail's folder-swipe row, table, and
  action buttons visibly jumped position depending on whether the
  current folder/page had data yet — the summary/subtitle text areas
  above them fully collapsed or shrank when empty, since they were
  sized purely by their own (much shorter) placeholder text. Those
  areas now reserve enough height for their longest realistic
  content, so nothing shifts based on data being present or not.
- Fixed: Item Analysis's Export button used to disappear entirely
  (along with the whole table area) whenever there wasn't enough scan
  data yet. It's now always visible; tapping it with no data shows a
  prompt instead of running an export.

## v4.0.0 — 2026-08-07

- New: system-wide font-size preference (Settings > FONT SIZE) — Very
  Small / Small / Medium (default) / Large. Rescales every screen's
  text and every rounded-card's vertical padding instantly, with no
  reopen/restart needed, by capturing each view's original XML size
  once and recalculating from that base every time the setting
  changes (so switching sizes repeatedly never compounds). Deliberately
  scoped to this app's own UI only — never touches the printed or
  previewed answer sheets, which stay governed entirely by
  SheetTemplate's fixed geometry.
- Fixed: the answer sheet preview (Generate/Edit Template screen) had
  regressed to the system font instead of Montserrat. Root cause: the
  font only ever got loaded from MainActivity's startup code, and the
  preview's own render function never loaded it itself — so if the
  preview screen was reached without MainActivity having already run
  in that session, it silently fell back to the system font with no
  error. The preview now ensures its own font is loaded before
  drawing. Printed/downloaded PDFs were never affected by this.

## v3.9.6 — 2026-08-06

- New: the "Gr. & Section:" label on all answer sheets, all paper
  sizes, is now simply "Section:" — reads naturally for university
  users, not just K-12.
- Fixed: table label text ("Name:", "Date:", "Section:", "Score:") and
  a capital "Q" in the test title could render wrong on the downloaded
  PDF only — letters with no gap between them, or Q as a solid filled
  disc — even though the in-app preview always looked correct. Three
  earlier general-purpose attempts at this (disabling font features,
  removing double-bolding, per-character positioning, even rendering
  as a bitmap image instead of PDF text) never fixed it and were
  reverted — see git history. This is a narrower, hand-verified fix
  instead: the exact letter-pairs confirmed to collapse in each of the
  4 fixed labels get a small manual extra gap in the PDF only, and the
  title's Q is drawn as the correctly-rendering "O" shape with its
  tail added back on top.

## v3.9.5 — 2026-08-06

- Fixed: student details table was off-center horizontally on generated
  answer sheets. GRID_LEFT/GRID_RIGHT margins were 5%/3% (not equal) —
  the bubble grid always re-centers its own content within those bounds
  so this never showed up there, but the table spans GRID_LEFT..GRID_RIGHT
  directly with no re-centering step, so it visibly inherited the
  asymmetry. Margins are now equal (4%/4%).
- Fixed: table label text and the test title could render with
  overlapping/crowded letters on the actual downloaded PDF, even though
  the in-app preview looked correct. Root cause: PdfDocument's canvas
  doesn't reliably use the custom Montserrat font's real per-glyph
  widths when spacing a drawText() call — it can fall back to tighter
  default spacing while still drawing the correct glyphs. Fixed by
  measuring and positioning each character manually instead of relying
  on drawText()'s internal spacing for these two strings.

## v3.9.4 — 2026-08-06

- New: the student details box (Name/Date/Gr. & Section/Score) on printed
  and previewed answer sheets now has rounded corners instead of square
  ones.

## v3.9.3 — 2026-08-06

- Fixed: after successfully installing an update, the About screen kept
  showing "Update Available" for the version just installed, even after
  fully closing and reopening the app. The cached update from before the
  install was only cleared by a fresh network check (throttled to once
  a day) or an explicit dismiss — nothing checked it against what's
  actually installed now. Now checks the real installed version first
  and clears the stale cache immediately if it's already caught up.
- Removed push.sh from the repo root (no longer needed).

## v3.9.2 — 2026-08-06

- Fixed: a downloaded update APK stayed in the app's private storage
  indefinitely after being installed, with no cleanup. The app now
  clears out any leftover update APK on every launch — by that point
  it's either already been installed (dead weight) or the install was
  canceled (a fresh download next time is simpler than trying to resume
  a half-finished one). Skips any download still genuinely in progress,
  so this can't race an update that's mid-download or was just handed
  to the installer.

## v3.9.1 — 2026-08-06

- Fixed: after Download was tapped on a found update, the app was
  supposed to automatically prompt to install once the APK finished
  downloading — confirmed on a real device that this silently never
  happened if the app was backgrounded while the download was still
  running (e.g. switching over to check the system Downloads app). The
  completion listener was tied to the app's own process and didn't
  survive it being killed in the background; the system's download
  notification still appeared (that part is OS-level, independent of
  this app), but tapping it there just reopened this app instead of
  installing anything. Replaced with a manifest-declared receiver that
  survives process death, so the install prompt now reliably appears
  regardless of whether the app was in the foreground when the download
  finished.

## v3.9.0 — 2026-08-06

- **New: Update Checker.** The About screen's Check for Updates button
  now actually checks GitHub for newer releases (was a "Coming soon!"
  placeholder). The app also checks silently in the background on
  launch, at most once every 24 hours — never interrupts anything, and
  any update it finds is simply waiting on the About screen next time
  it's opened. A manual tap always checks fresh, regardless of that 24h
  window. If a newer version is found, Download fetches the APK and
  hands it to the system installer; "Skip This Version" stops the nag
  for that specific release without silencing genuinely newer ones
  later. Re-adds the INTERNET permission (removed in v3.1.4) — this
  check is read-only and sends no scan data, sheets, or personal
  information anywhere; the About screen's description was updated to
  say so plainly.
- Fixed: the "Delete Sheet" / "Save & Proceed" buttons shown after an
  unclear scan still used plain unstyled buttons, missed by the earlier
  app-wide rounded-card button redesign. Converted to match, and changed
  from side-by-side (with "Delete Sheet" forced onto two lines just to
  match its neighbor's height) to each being its own full-width row —
  removes that forced line break entirely. The delete icon now tracks
  the same contrast-matched color as its label text instead of staying
  a fixed color regardless of the chosen accent.

## v3.8.1 — 2026-08-05

- **Fixed Scanned Sheets' swipe left/right on the folder name being
  broken.** The label's own long-press-to-rename listener was claiming
  touch before the row's swipe gesture ever saw it, so swipe only worked
  if the drag started exactly on a chevron's edge rather than the label
  itself (the natural place a thumb lands). Long-press-to-rename still
  works exactly as before.
- **Item Analysis: relocated the folder nav row for thumb reach** — moved
  from directly under the title to below the analysis result, just above
  the Export button. The title stays fixed at the top; applied the same
  swipe fix as above defensively, even though this row wasn't yet broken
  the same way.
- **Fixed "main" showing lowercase** ("main" instead of "Main") in Item
  Analysis's folder label, subtitle, and export dialog.

## v3.8.0 — 2026-08-04

- **Auto-save scanned sheets is now ON by default.** Updated its
  description to say "Scanned Sheets" instead of the old "Load Test"
  screen name.
- **Default storage resolution changed to 1800px** (was 2000px).
- **Item Analysis: added per-subfolder navigation.** Tapping Item
  Analysis still opens the combined view for all scanned sheets (the
  screen's original/default behavior, unchanged). A new frozen row above
  the table — chevrons plus swipe left/right, same pattern as Scanned
  Sheets' folder row — cycles through "All" and each individual
  subfolder, recomputing the table each time. The subtitle showing the
  test/subfolder name is now centered.
- Fixed a stale error message that told the user to "re-open this test
  from Load Test" — that screen was replaced by the home screen's Saved
  Tests list; the message now points there instead.

## v3.7.7 — 2026-08-04

- **Reversed course on button text casing: every rounded-card button is
  now ALL CAPS**, for consistency (most already were). Covers Scanned
  Sheets, Item Analysis, Save Settings, Generate PDF, Capture Photo, Scan
  Next, Done, Test Summary, Grant Camera Permission, Use This as Answer
  Key, Export (Item Analysis), Back/Export Backup/Choose Backup File/Save
  Answer Key/Apply to All/Check for Updates/Send Diagnostic Report — the
  last group is fixed centrally in the shared centerIconAndText helper
  rather than per-button.
- **Renamed "Answer Sheet Template" to "Answer Sheets"** and **"Scan
  Filled Answer Sheets" to "Scan Answer Sheets"**, all caps, everywhere
  both appear (Test Detail screen, Scanned Sheets screen, and the
  matching Help/FAQ instructions).
- **Bolded Top Panel Accent and Button Accent** button text in Settings.

## v3.7.6 — 2026-08-04

- **Bolded every button's text**, for consistency with icon+text buttons
  that were already bold — fixed at the shared centerIconAndText helper,
  so this covers every plain-Button icon+text pairing at once (Back
  buttons, Export Backup, Choose Backup File, Save Answer Key, Apply to
  All, Check for Updates, Send Diagnostic Report).
- **Switched five buttons from maroon-group to gold-group accent, with
  contrast-adaptive icon/text colors:** Export Backup, Choose Backup
  File, Save Settings, Check for Updates, and the scan result preview's
  Scan Next button (Done stays maroon).
- **Fixed Save Settings silently rendering as all-caps** ("SAVE
  SETTINGS") despite its source text already being Title Case — a
  leftover `textAllCaps="true"` the earlier casing sweep didn't catch
  since it only checked `android:text` values.

## v3.7.5 — 2026-08-04

- **Scanned Sheets: long-press the folder name** (in the swipeable row
  with the chevron buttons) to rename the current folder. Renaming Main
  isn't allowed, and renaming onto an existing folder's name is blocked
  with a warning. The folder tabs' existing long-press-to-remove is
  unchanged.
- **About screen: added a Check for Updates button**, between Send
  Diagnostic Report and Back. Tapping it currently shows "Coming soon!" —
  no update check exists yet.
- **Fixed button text-case inconsistency:** Generate/Edit Answer Sheet
  Template's Generate PDF button was "GENERATE PDF" — now Title Case,
  keeping PDF itself all-caps. Audited Create/Edit Answer Key and every
  sidebar-linked screen (Backup Data, Restore Data, Settings, Help/FAQ,
  About) — all were already consistent.

## v3.7.4 — 2026-08-04

- **Create/Edit Answer Key: Save Answer Key button now uses the rounded-
  card style** (was a plain default button shape with just a color tint).
- **About screen: app title, version, tagline, "What's New" heading, and
  "Legal & Licenses" heading are now bold.**
- **Scanned Sheets: the folder/subfolder name in the swipeable nav row
  (with the chevron buttons) is now bold.**
- **Generate/Edit Template: the preview note text size now matches the
  "Create Answer Key" screen's title text size.**
- **Generate/Edit Template: the Preview button's text was one size
  smaller (14sp) than the screen's other action buttons — now matches
  Save This Template's 15sp.**

## v3.7.3 — 2026-08-04

- **About screen: Send Diagnostic Report and Back swapped positions.**
  Send Diagnostic Report (the button used regularly during testing) now
  sits on top; Back sits below it. Both buttons' icon+text are now
  centered as a unit.
- **Create/Edit Answer Key: Apply to All moved to its own row** below the
  Points per item stepper, which now has the full row width to reliably
  fit on one line. Apply to All is now a maroon-group rounded-card button
  (was an unstyled default button) with its check icon and label centered
  together. Save Answer Key's check icon and label are now centered
  together too.
- **Converted every remaining old-style button to the app's rounded-card,
  centered icon+text design:** the camera permission screen's Grant
  Camera Permission button (now maroon-group, with contrast-adaptive
  icon/text colors); the scan result preview's Scan Next and Done
  buttons; and the detected-key screen's Use This as Answer Key, Scan
  Next, and Done buttons. Use This as Answer Key also switched from the
  maroon group to the gold group per explicit request.

## v3.7.2 — 2026-08-03

- **Fixed: the +/- stepper icons in Generate/Edit Answer Sheet Template
  were off-center inside their circles.** Missing `android:padding="0dp"`
  and `android:textSize="18sp"`, which Settings' matching steppers
  already had — copied Settings' proven attributes over. Both screens'
  steppers now share identical styling and size (36dp) and continue
  following the Top Panel (maroon) accent, same as v3.7.1. A separate
  attempt at a fixed, accent-independent color for these buttons is
  parked pending a decision — not shipped in this build.
- **Fixed: several icons/text had become invisible against their own
  accent-colored background.** Root cause: a container (a maroon header
  bar, or a card tagged to follow the gold Button Accent) and an icon or
  text sitting on top of it were both tinted to the *same* accent group —
  so the icon/text matched its own background exactly and disappeared.
  Affected: Test Detail's header hamburger/export/delete icons and its
  Scan Filled Answer Sheets card; Scanned Sheets' header hamburger and
  add-folder icons and its Test Summary card; Item Analysis's header
  hamburger icon and its Export button. Fixed with a new pair of tags
  (`accent_maroon_icon_on` / `accent_gold_icon_on`, plus `_text_on`
  variants) that compute readable contrast against the accent color
  instead of matching it.
- **Fixed: Item Analysis Export button's icon** wasn't rendering — same
  gold-on-gold contrast bug as above.
- **Fixed:** Capture Photo (live Scan screen) and Scan Filled Answer
  Sheets (Scanned Sheets screen) icon/text were hardcoded black with no
  adaptivity. Both now go white on a dark/darker chosen accent, black on
  a light/lighter one.
- **Changed: Scan Filled Answer Sheets' icon** replaced with a dedicated
  bubble-sheet glyph (scan-corner frame around a grid of bubbles),
  matching the icon set's existing style. This is also the icon used on
  the live Scan screen and in Settings, since all four are the same
  underlying asset.
- **Changed:** Test Detail's Scanned Sheets row icon replaced with a new
  asset (an earlier candidate had a soft shadow/blur artifact baked into
  the PNG and was swapped out before shipping).
- **Changed:** Test Detail's Answer Sheet Template row icon replaced with
  a checkmark-over-bubble-grid glyph.
- **Changed:** Scanned Sheets screen's Test Summary card icon replaced
  with a checkmark-badge-over-bubble-sheet glyph, cropped tight to its
  bounding box so it doesn't render smaller than the icon set's other
  assets.
- **Changed: Generate/Edit Answer Sheet Template — "Preview" is now
  all-caps ("PREVIEW"), and "Use This Template & Save Test" is now
  "SAVE THIS TEMPLATE" (all-caps).**
- **Changed:** Generate/Edit Answer Sheet Template's Create/Edit Answer Key
  button now reads "CREATE ANSWER KEY" / "EDIT ANSWER KEY" (all caps) —
  also fixes a mismatch where the edit-mode label previously read "Edit
  Answer Sheet" despite the button being for the answer key.
- **Changed:** Settings' Save Settings button now has a check icon and
  all-caps text, matching Save This Template's style.
- **Changed:** removed the small status dot from folder/subfolder tabs
  on the Scanned Sheets screen.

## v3.7.1 — 2026-08-03

- **Reverted: Number of Items/Columns/Questions per column +/- stepper
  buttons.** An attempt to hardcode them permanently gold did not render
  correctly on a real device, so they're back to following the Top
  Panel (maroon) accent, same as they did in v3.7.
- **Changed: stepper button circles made smaller** — Generate/Edit
  Template's steppers 44dp to 36dp, Settings' steppers 40dp to 34dp.

## v3.7 — 2026-08-01

- **New: rounded-card list style.** The Home screen's Saved Tests rows
  and the Test Detail screen's Answer Sheet Template / Scanned Sheets /
  Item Analysis rows are now themed cards with icons and a chevron,
  instead of flat rows or solid colored buttons. Card fill/stroke color
  is resolved from two new theme attrs (`colorSurfaceCard` /
  `colorSurfaceCardStroke`, see `attrs.xml`/`themes.xml`) — it follows
  your Light/Dark/Black theme choice only and is deliberately NOT wired
  into the accent-color tag system, so it won't move when you change
  your accent color.
- **New:** custom icons for Answer Sheet Template, Scanned Sheets, Item
  Analysis, and the Scan Filled Answer Sheets button.
- **Changed:** the Scanned Sheets scan count moved out of the row's own
  label text into a small badge (`tvScannedSheetsCount`) next to it —
  that badge is the one part of the row that IS accent-colored.
- **Fixed: Test Detail's Scanned Sheets / Item Analysis icons were
  swapped** — the two PNG files' actual image content had been reversed
  relative to their filenames.
- **Changed: Test Detail's Scan Filled Answer Sheets** rebuilt as a
  rounded card with its icon inline (was a plain `Button` with a
  mismatched corner radius), following the Button Accent (gold) group.
- **New: Generate/Edit Answer Sheet Template screen redesigned.** Header
  (menu icon + title) and the bottom Use This Template & Save Test /
  Generate PDF buttons are now frozen outside the scrolling area — only
  the middle configuration section scrolls. "Number of Items" is now one
  grouped rounded-pill stepper instead of three separate widgets. Columns
  / Questions per column now sit inside a boxed "LAYOUT" card with its
  own icon + label. Added icons to Create/Edit Answer Key and both
  preview buttons. Accent groups: Create/Edit Answer Key and Generate PDF
  follow the Top Panel (maroon) accent; Blank Preview, Answer Key
  Preview, and Use This Template & Save Test follow the Button (gold)
  accent.
- **New: Scanned Sheets screen redesigned.** Each scan is now a rounded
  card; score and percentage are split into two labels, with the
  percentage in a fixed highlight gold that does NOT follow the accent
  color system. Move/delete icons unchanged, now in a themed circular
  badge. Folder tabs rebuilt as rounded pills with a status dot and a
  scan-count badge. Test Summary now follows the Top Panel (maroon)
  accent; Scan Filled Answer Sheets follows the Button (gold) accent.
- **New: sidebar redesigned.** Header logo/title/tagline are now
  horizontally centered; tagline changed to "SCAN • CHECK • REVIEW". Row
  list rebuilt as rounded cards with icons, grouped under new section
  headers — DATA (Backup/Restore Data), PREFERENCES (Theme, Settings,
  renamed from "Appearance"), SUPPORT (Help/FAQ, About).
- **Changed: Backup Data, Restore Data, Help/FAQ, and About** — the
  shared Back/secondary action buttons on these four screens now use the
  same rounded-card style as the rest of the app, full width instead of
  wrap-content.
- **New: Generate/Edit Answer Sheet Template — icons and pill toggle.**
  Added icons to Use This Template & Save Test (check) and Generate PDF
  (page-download). Blank Preview now uses a closed-eye icon (was the same
  open-eye icon as Answer Key Preview), so the two are distinguishable at
  a glance. A–D / A–E is now a pill toggle instead of plain radio buttons
  — same underlying RadioGroup/RadioButton and Kotlin logic, restyled.
  Number of Items / Columns / Questions per column +/- buttons are now
  fully circular (were a rounded-rect Button shape that wasn't quite
  round at that size).
- **New: Settings screen redesigned** to match the Generate/Edit Template
  screen's conventions — frozen header and frozen Save Settings/Back
  buttons, settings grouped into TEMPLATE DEFAULTS / SCAN BEHAVIOR /
  STORAGE / ACCENT COLORS cards with icons, the same pill toggle
  (default choices, storage resolution) and circular stepper buttons.
- **New: A–D/A–E and storage-resolution pill toggles now follow the
  Button (gold) accent properly.** Checked-state fill and text contrast
  are both computed from the actual chosen accent color at runtime,
  instead of a fixed static gold.
- **New: pointed "Back" buttons.** Every screen's actual Back button
  (Backup/Restore's secondary button, Help, About, Settings) now uses a
  custom shape whose left edge comes to a point, instead of the standard
  rounded-card corners — visually distinct from every other button at a
  glance. Also carries a back-arrow icon.
- **New: icons added across the board** — Item Analysis Export, every
  button on the live Scan screen (Capture Photo, Grant Camera
  Permission, Use This as Answer Key, Delete Sheet, Save & Proceed, Scan
  Next, Done), Backup's Export Backup, Restore's Choose Backup File,
  About's Send Diagnostic Report, and the Accent Color picker's Revert
  to Default / Apply to All / Save Answer Key buttons.
- **Changed: sidebar reverted to a plain list.** The rounded-card
  treatment added earlier in v3.7 didn't belong on the sidebar — rows
  are back to a plain full-width list with icons, no card background or
  margins. This only affects `drawer_content.xml`'s shared style, so it's
  consistent across every screen automatically.

## v3.6.11 — 2026-08-01

- **Reverted a mistake from v3.6.10's last fix.** That release collapsed
  the sidebar's "MCQ" / "Quick Check" title into a single auto-sized
  line to solve "Quick Check" clipping — but that also changed "MCQ"'s
  size and merged both lines into one, neither of which was asked for or
  broken. Back to two separate TextViews, "MCQ" and "Quick Check" each on
  their own line, sized to match the home screen header exactly (18sp /
  9sp) — matching what was already confirmed correct.
- **Fixed: sidebar widened to roughly half the screen width** (computed
  from the actual device's screen width in code, not a guessed fixed dp
  — two guessed dp values already turned out wrong this release), so
  "Quick Check" has real room instead of being squeezed against a narrow
  fixed-width drawer.
- **Fixed: "Developed by Sir_JPagdi" was clipping** in the sidebar
  footer — same root cause as "Quick Check," the drawer was just too
  narrow for that string at 11sp. No layout change needed there; the
  drawer widening above fixes it too.
- **Uncertain, flagging honestly:** the `requestLayout()` call that's
  meant to stop "MCQ" from visually overlapping "Quick Check" (a font-swap
  timing issue, not a width issue) is still the same approach from
  v3.6.10 — it wasn't possible to fully confirm on a real device whether
  it holds up in every case, only that the width-clipping it was tangled
  up with last time is now separately fixed by the wider drawer. If the
  overlap still shows up, it needs a different fix than another
  `requestLayout()` call.

## v3.6.10 — 2026-08-01

- **Fixed: the home screen header and sidebar app titles still weren't
  rendering in Press Start 2P** even after v3.6.9's line-split and an
  earlier attempt at removing `android:textStyle="bold"`. Root cause was
  neither of those — it's `AppCompatActivity`'s view inflater, which
  backports `android:fontFamily` theme resolution to every `TextView` by
  force-applying the app-wide Montserrat font *after* inflation, and on
  this app's minSdk that backport wins over a per-view
  `android:fontFamily` XML attribute rather than losing to it. Any
  `fontFamily` set in the layout XML for these three TextViews was being
  silently overwritten the moment the theme's own font got applied.
  Fixed by giving the three TextViews IDs and setting their `Typeface`
  directly in `MainActivity.onCreate()` in Kotlin, which runs after the
  inflater backport and can't be overridden by it — no XML `fontFamily`
  attribute involved at all for these three spots now.
- **Fixed: with the font now actually rendering, "QUICK CHECK" clipped
  against the sidebar's fixed 300dp width** — Press Start 2P's glyphs run
  much wider than Montserrat per character, and 11sp wasn't small enough.
  Downsized "QUICK CHECK"/"Quick Check" to 9sp in both the home screen
  header and the sidebar, and made "MCQ" 18sp in both spots too — sizes
  now match exactly between the two locations instead of being tuned
  independently. The home screen header, previously one TextView with a
  `\n` line break at a single size, is now two separate TextViews (like
  the sidebar already was) so each line can have its own size.
- **Fixed: the sidebar still rendered Montserrat instead of Press Start
  2P on every screen except the home screen.** v3.6.10's first fix only
  set the Typeface in `MainActivity.onCreate()`, but the sidebar is
  shared across 12 screens via `DrawerHelper.attach()` — moved the
  typeface wiring there instead, so it's set once for every screen with
  sidebar access rather than needing to be copy-pasted into each one.
- **Fixed: "MCQ" still overlapped "Quick Check" in the sidebar even
  after the previous fix**, and — after three rounds of hand-picked
  sizes each clipping in a new way — the sidebar title is now a single
  TextView, single line ("MCQ Quick Check"), instead of two stacked
  TextViews. The overlap's real cause: swapping a `Typeface` after the
  initial layout pass doesn't reliably force the *sibling view below* to
  reposition before the next draw, so the second line could still paint
  against the first line's stale measured height depending on timing —
  `requestLayout()` on the swapped view alone wasn't enough to guarantee
  that. Collapsing to one line removes that whole class of bug rather
  than patching it further. Sized with `autoSizeTextType` (shrink-to-fit)
  instead of another fixed sp guess, so it's guaranteed to fit the
  sidebar's width on any device rather than clipping again on some
  screen size this wasn't tested on. The sidebar's earlier width increase
  (300dp → 340dp) is no longer needed for this and has been reverted.

## v3.6.9 — 2026-08-01

- **Fixed: the sidebar's app title was clipping** — "MCQ Quick Check" in
  Press Start 2P at a single fixed size ran wider than the drawer's fixed
  300dp width and cut off mid-word ("Check" missing entirely). Split into
  two separate lines, sized independently — "MCQ" larger (20sp), "Quick
  Check" smaller (11sp) — mirroring the home screen panel's existing
  two-line MCQ / QUICK CHECK split, just with its own tuned sizes.

## v3.6.8 — 2026-08-01

- **Fixed: the app was bundling actual Century Gothic font files.**
  Century Gothic is a commercial Monotype typeface, not freely
  redistributable — caught during a pre-Play-Store-publish review.
  Replaced app-wide (theme font + generated PDF answer sheets, same two
  wiring points as before) with **Montserrat**, from Google Fonts under
  the SIL Open Font License, which explicitly permits bundling in and
  redistributing with an app. License text kept at
  `licenses/fonts/Montserrat-OFL.txt` for the record.
- **Changed: the home screen's top panel title and the sidebar's app
  title now use Press Start 2P** (also SIL OFL, `licenses/fonts/
  PressStart2P-OFL.txt`), an 8-bit/arcade-style display font — just those
  two spots, not the general app font, which stays Montserrat everywhere
  else.

## v3.6.7 — 2026-07-31

- **Fixed: A4/Folio quadrants had unequal top/bottom margins once actually
  cut apart** — a top-row quadrant had a big gap above its Name/Date table
  and almost none above the grid's last row; a bottom-row quadrant had the
  opposite. Root cause: all 4 quadrants shared ONE canvas transform
  centered on the *whole* physical page, which kept the page's own
  outermost top/bottom margins equal but let all of A4/Folio's extra
  height land as one lump above the top-row quadrants and one lump below
  the bottom-row quadrants, with nothing near the shared middle cut line.
  Each quadrant now gets its own transform, centered independently within
  its own quarter of the physical page — verified numerically that every
  quadrant gets equal margin on all 4 sides for Letter (unchanged, 0
  either way), A4 (18pt), and Folio (36pt). The dashed cut line is now
  drawn once, directly at the true physical half in both axes, instead of
  once per region inside the old shared transform.

## v3.6.6 — 2026-07-31

- **Changed: the trash icon on the Scanned Sheets fullscreen preview moved
  from top-left to bottom-right, just below the image** — top corners are
  the hardest spot to reach one-handed on a large phone, bottom corners
  are the easiest.

## v3.6.5 — 2026-07-31

- **Confirmed: the QUARTER quadrant margin fix from v3.6.4 also covers
  stress-test/sample sheets** — both real sheets and stress sheets go
  through the same SheetGenerator functions, so this was already true as
  of that release; no separate fix needed here.
- **Changed: each stress-test config now packs all 4 of its variations
  onto ONE physical page's 4 quadrants, instead of 4 separate pages each
  repeating a single pattern across all 4 quadrants.** Cuts the stress-
  test batch from ~45 pages down to ~18 — 3 of every page's 4 quadrants
  used to be identical copies of the same one variation. Removed
  `SheetGenerator.generateStressTest()`, now redundant with
  `generateMultiVariant()`'s new optional per-quadrant defects parameter.

## v3.6.4 — 2026-07-31

- **New: a trash icon on the Scanned Sheets fullscreen preview** lets a
  scan be deleted right from there, with the same confirmation as
  deleting from the list itself.
- **Fixed: QUARTER-layout quadrants now get an equal content margin on
  all 4 sides after cutting apart, including the page's own outer edges**
  — previously only the internal cut edges had zero margin reserved; the
  page's outer edges had whatever margin the template's own fractions
  happened to leave, which wasn't necessarily the same amount. The dashed
  cut line itself is unchanged — still exactly on the true half/half
  boundary — only the printed content backs off from it.
- Removed `ResultAnnotator.markManualResolution()` — orphaned since
  v3.6.3 removed its only caller (the old editable ambiguous-answer flow
  on a saved scan). No behavior change; internal cleanup only.

## v3.6.3 — 2026-07-31

- **Fixed: resolving an ambiguous question from the Scanned Sheets folder
  (an already-saved scan) is no longer possible.** Assigning an answer to
  an ambiguous bubble was only ever meant to be available on the live
  result screen right after capture, before saving — reviewing a saved
  scan later now just shows which questions were left unresolved (struck
  through), with no way to edit them.
- **Fixed: the actual root cause of the stress-test/sample sheet bubble
  gap mismatch — bubble SIZE, not paper size.** Real sheets set a new
  template's bubble size to `BubbleSizePrefs.FIXED_MM` (2.68mm); the
  batch generator's SheetTemplate construction never set this at all and
  silently fell back to the class default (2.2mm) instead — a smaller
  bubble, and since spacing is derived from bubble diameter, a tighter
  gap too. v3.6.2's paper-size fix was real but wasn't the actual source
  of this particular mismatch.

## v3.6.2 — 2026-07-31

- **Fixed: stress-test and sample answer sheets (the hidden About-screen
  batch generator) always printed at a fixed Letter-size page, ignoring
  whatever paper size is actually set for real sheet generation** (Letter/
  A4/Folio). On anything other than Letter this put the bubbles at
  different physical margins than a real generated sheet on the same
  paper stock, even though the underlying layout math was identical —
  the two PDF paths now apply the exact same paper-size scale/center
  transform.
- **Removed swipe-to-change-folder from the Scanned Sheets header bar and
  from the scan list itself** — three versions of trying to make that
  reliable (title bar only, then the whole list) kept hitting edge cases
  with how touch events land on a scrollable list. Replaced with a
  dedicated non-scrollable row above the Test Summary button: < and >
  buttons plus swipe-anywhere-on-that-row, neither of which compete with
  the list's own scrolling since the row isn't part of it.

## v3.6.1 — 2026-07-31

- **Fixed: tapping the Columns or Rows +/- steppers on the Generate Sheet
  screen left the "Up to X questions per column..." hint text frozen at
  whatever range was true before the tap**, even though the field and the
  live preview below it had already moved on to the new value — only the
  question-count stepper was refreshing that hint text; the other two
  steppers never were.
- **Fixed: swiping between folders on the Scanned Sheets screen only
  registered when the swipe started on top of an actual scan row** —
  swiping over the blank space below the last row (or anywhere in a
  short/empty folder) did nothing. Root cause was a standard Android
  ScrollView quirk: a touch that starts on empty space with no view under
  it bypasses the swipe-detection logic entirely for that gesture. The
  scan list now fills the full screen height even when it has few or no
  rows, so every point on screen has something under it and swipe works
  the same everywhere.
- **New: 3 more stress-test/sample sheet configurations** in the hidden
  About-screen batch generator (long-press the title) — 30 items/2
  columns, 60 items/3 columns, and 90 items/4 columns, alongside the
  existing tier-edge configs. Updated the batch generator's own popup
  message, which was still describing the old "every paper layout,
  10–120 items" behavior from before v3.4/v3.6.
- **New: scanned sheet storage resolution preference** (Settings) — choose
  2000px (default, same as before), 1800px, or 1600px for the long edge
  of a scanned sheet's saved photo. Lower uses less storage per scan;
  doesn't affect scans already saved.

## v3.6 — 2026-07-31

- **New: answer sheets now support up to 100 questions (was 90)**, by
  adding a 4th column tier. New tiers: 10-25 items = 1 column, 26-50 = 2
  columns, 51-75 = 3 columns, 76-100 = 4 columns (was 10-30/31-60/61-90
  across 1-3 columns). Max items per column dropped from 30 to 25 to make
  room, which also means bubbles are drawn slightly larger than before,
  since the same page height is now split across fewer rows.
- **Fixed: answer sheet columns were being stretched to fill the full
  width of the sheet** even after v3.5.1 fixed the bubble-to-bubble
  spacing within a column — on any sheet using fewer than the maximum
  column count, this left a large dead gap between the last bubble of one
  column and the number label of the next, instead of that column simply
  ending where its content ends. Columns are now sized to their real
  content width and the whole row of columns is centered on the page, the
  same "don't stretch, center the leftover space" treatment row spacing
  already got in v3.5.1.
- **Fixed (found during testing, not reported): the fix above could very
  slightly overlap the 4th column with the printed page edge** specifically
  at the largest bubble-size setting combined with a 100-question,
  4-column sheet — added the same automatic shrink-to-fit safety net that
  was already protecting against a sheet running too tall, now also
  covering sheets that would run too wide.
- **Fixed: swipe between folders on the Scanned Sheets screen now works
  anywhere over the scan list itself**, not just the title bar at the very
  top — the title bar was too small and too high up to reach comfortably
  with one hand. The title bar swipe still works too.
- **New: an export icon next to the delete icon on the Test Detail
  screen.** Packages a test's scanned sheets into a zip file, ready to
  Share or Download — if the test has folders beyond Main, choose to
  package everything or just one folder first.

## v3.5.1 — 2026-07-30

- **Fixed: the actual bug in "column gap" — spacing BETWEEN individual
  A/B/C/D bubbles was still stretching to fill the block's width**, even
  after v3.5's fix (which only fixed the gap between side-by-side blocks
  and the number-label width, not this). Bubble-to-bubble spacing is now
  a genuine fixed pitch (one bubble-width gap between bubbles), the same
  on every sheet regardless of column count — this is also the single
  formula BubbleGrader/ResultAnnotator read at scan time, so detection
  stays in lockstep with what's printed.
- **Fixed: row gap is now a true fixed constant** — one per-row pitch,
  tuned so a full 30-row column (the largest any current layout ever
  produces) fills the available grid height exactly, used identically
  on every sheet and every item count. Previously this was recomputed
  from the printed bubble size instead of the column's available space,
  which is what left the "2-4 line" gap of unused space at top/bottom on
  a 30-question sheet. A 10- or 20-question sheet uses this same fixed
  pitch too — it just naturally has fewer rows, so it doesn't reach the
  bottom of the grid, same as before.
- **Changed: About page's Test Sheets generator inherits both fixes above
  automatically** — no separate change needed, since it builds sheets
  through the same SheetTemplate/SheetGenerator code the fixes live in.
- **Fixed: swipe on the Scanned Sheets screen wasn't registering.** It was
  attached only to the title text, whose actual touchable area is just as
  wide as "Scanned Sheets" itself — too small and inconsistent a target.
  Moved to the whole header bar; the hamburger/add-folder/select-mode
  icons inside it are unaffected since Android hands a tap to those
  buttons first, before the header bar's own swipe listener ever sees it.

## v3.5 — 2026-07-30

- **Fixed: Delete Sheet button (unclear-scan review screen) now follows
  the gold accent group** instead of a hardcoded red that ignored both
  accent color choices — matches every other button on this screen.
  Also forced its label onto two lines ("Delete" / "Sheet") so it's the
  same height as Save & Proceed next to it, which was already wrapping
  to two lines at that width.
- **Fixed: answer sheet column gap and question-number label width are
  now fixed to one real bubble's width**, instead of a proportional
  fraction of the block's own width. Previously side-by-side columns had
  NO gap at all (bubbles from adjacent columns could butt right up
  against each other) and the "12." label shrank/grew depending on how
  many columns the sheet had, with no relation to the actual printed
  bubble size. Both now measure exactly one bubble-diameter on every
  layout.
- **Changed: Download paper size moved out of Settings** and into a
  popup shown right when you tap GENERATE PDF, before the Share/Download
  prompt — you now confirm Letter/A4/Folio at the moment you actually
  export instead of it being a background setting that's easy to forget
  was ever changed. Your last choice is still remembered as the default.
- **New: swipe left/right on the Scanned Sheets screen's title bar** to
  move between Main and each subfolder — same order as the folder tabs.
  Separate from the existing swipe-through-scans gesture on the
  fullscreen viewer, which is unchanged.
- **Changed: About page's Test Sheets generator** now produces 5
  milestone item counts (10/20/30/60/90 — one per column-tier boundary)
  instead of one sheet per 10-question step, and generates a stress-test
  batch (realistic ambiguous marks) for all 5 of those, not just 60.

## v3.4 — 2026-07-29

- **Fixed: "MCQ Quick Check isn't responding" crash (OutOfMemoryError)
  when exporting a backup.** Real device crash log, traced to Export
  Backup building the ENTIRE backup — every saved scan's photo included —
  as one giant text string in memory before writing it, which could
  exceed the app's memory limit on a test with a lot of saved scans (made
  more reachable after v3.3 removed the old 60-scan cap with no
  replacement). Backups are now written straight to the file as they're
  built, one scan at a time, instead of all at once — old scans get
  shrunk to a smaller size during export too, which also makes the
  backup file itself smaller.
- **New: an unclear scan is no longer a dead end.** Previously, a scan
  flagged "unclear" just showed a warning with no way to see or keep what
  was actually read — the whole sheet had to be retaken. Now shows the
  full graded preview (tentative score, color-coded image), lets you
  resolve any ambiguous bubbles by hand right there, then asks Delete
  Sheet or Save & Proceed — you decide if it's good enough to keep,
  instead of being forced into a retake.
- **New: scanning an answer key that comes back unclear now still shows
  the detected key** (with unclear questions marked "?") and lets you use
  it — fills in what was read correctly and tells you exactly which
  questions still need manual entry, instead of a dead end. Also fixed a
  crash where this would fail to fill in ANY answer if even one question
  was unclear.
- **Changed: scanned-sheets-per-test now has no cap at all** (was
  unlimited already as of v3.3, this just confirms it stays that way —
  see the backup-export fix above for the memory-safety work that makes
  this sustainable). Saved scans are now stored at a smaller resolution
  automatically to help keep storage growth in check; only affects the
  saved copy, not what you see right after scanning.
- **Removed: ½ cross and ½ length paper layouts.** ¼ page is now the only
  layout, with its supported question-count range extended from 10–60 up
  to 10–90 to cover what those two used to handle (10–30 questions → 1
  column, 31–60 → 2 columns, 61–90 → 3 columns). An existing test created
  with either removed layout can no longer be opened — you'll see a clear
  message and need to recreate it; this wasn't auto-converted, since any
  sheets already scanned for that test were captured against the OLD
  layout's shape, and silently reinterpreting them under the new one
  could have corrupted grading with no warning. Restoring an old backup
  that contains such a test now skips it (noted in the restore summary)
  rather than restoring it broken.
- **Removed: the "Faster scanning" toggle in Settings.** Capture is now
  always the faster mode — confirmed working correctly against real
  scanned/graded sheets, not just faster to capture, so there's no longer
  a quality tradeoff to opt into.
- **Removed: the bubble size slider on Generate Sheet.** Bubble size is
  now fixed at 2.68mm (the slider's old maximum) for every new sheet.
  Editing an existing test keeps that test's original bubble size exactly
  as it was — this doesn't retroactively change any sheet already
  created.

## v3.3 — 2026-07-29

- Removed: the per-test cap on saved scans (previously 60 per test,
  oldest-first eviction). It was silently dropping scans past the cap for
  any full class set, which defeated the whole point of a class-wide Test
  Summary / Item Analysis. There is deliberately no replacement cap — see
  the doc comment on `ScannedSheetStorage.kt` for the memory/performance
  tradeoff this accepts.
- New: standardized, realistic bubble size. A "Bubble size" slider
  (1.72–2.68mm radius) on the Generate Sheet screen replaces the old
  fixed sizing, which was drawing bubbles far larger than a real printed
  OMR bubble needs — worst on dense sheets like 30 questions on a
  quarter-page, which was the reported pain point. Row and column
  spacing now scale with the chosen bubble size too, so smaller bubbles
  also mean a comfortably tighter grid instead of just leaving extra
  blank space. The chosen size is baked into that test's own template
  permanently at creation time, so changing the slider later never
  affects previously-generated (and already-scanned) sheets.
- New: download paper size preference (Settings > Download paper size) —
  Letter, A4, or Folio (8.5"×13"). Printing-only: the sheet's internal
  layout is unchanged, it's uniformly scaled and centered onto whichever
  physical page size is chosen, so the corner guides stay square and
  scanning is unaffected.
- New: ambiguous questions (the ones shown with an orange strikethrough)
  can now be manually resolved by tapping the student's actual bubble
  (A–D or A–E) — available both on the scan result screen right after
  capture, and when reviewing a saved scan from the Scanned Sheets
  folder. Resolving updates the score and Item Analysis data immediately;
  leaving a question unresolved keeps it counted as ambiguous, same as
  before.
- Removed: the "Subject Teacher" line/toggle, on request. The answer
  sheet's header table is now always the shorter 2-row layout.
- Fixed (found while removing the above): a saved test's Subject Teacher
  setting was never actually being persisted through Backup Data or app
  restarts in the first place — every reload silently reset it to the
  default. Moot now that the feature itself is removed, but noting it in
  case anything else in this area seemed to "forget" settings before.

## v3.2.1 — 2026-07-28

- Fixed: Backup Data never included accent colors (Top Panel / Button
  accent) — a restore brought tests back but silently reset custom colors
  to default. Also now backs up folder names directly, so a folder
  created but never scanned into yet survives a restore too.
- Fixed: the "Add folder" dialog's input box (underline, blinking cursor,
  drag handle) was never wired into the accent color system — stuck on
  the base theme's default instead of your chosen accent, same bug class
  as several other dialogs fixed in earlier versions.
- Changed: tap-to-capture on the live camera preview is now a double tap
  instead of a single tap, to avoid accidental captures while just
  holding the phone up to frame the sheet. The dedicated Capture button
  and volume-key capture are unchanged.
- New: volume up/down now also advances to the next scan while reviewing
  a result, so a full capture-review-next cycle can be driven by volume
  keys alone.
- Changed: the "Faster scanning" toggle moved above Auto-save in
  Settings, since it's the fix for "scanning feels slow" and was easy to
  miss buried further down.
- Fixed: What's New (About screen) still listed several versions-old
  items instead of 3.0/3.1/3.2's actual new features.

## v3.2 — 2026-07-26 — FIRST STABLE RELEASE

- Renamed the app's package ID from `com.tnhs.mcqscanner` to
  `com.jpagdi.mcqscanner`, marking the app's independence from TNHS
  branding and preparing for future commercialization. Every source file,
  `AndroidManifest.xml`, the FileProvider authority, and both custom-view
  references in `activity_scan.xml` were updated to match — verified zero
  remaining references to the old package anywhere in the project.
  **BREAKING for existing installs:** Android treats a package ID change
  as an entirely different app — this build cannot be installed over the
  old one. Use Backup Data to export everything BEFORE switching, install
  this build fresh (uninstalling the old one first), then Restore Data
  to bring saved tests/keys/settings back.
- New optional Settings toggle: "Faster scanning" — switches ImageCapture
  from CAPTURE_MODE_MAXIMIZE_QUALITY to CAPTURE_MODE_MINIMIZE_LATENCY.
  Off by default (today's behavior unchanged unless explicitly opted in).
  Added after observing 3-5s capture times on a lower-end device — the
  actual captured photo (unlike the separate live-preview analyzer, which
  was already resolution-capped) had no resolution limit and was using
  the "prioritize quality" capture mode, a likely contributor on slower
  hardware. Deliberately an opt-in toggle rather than a blanket change:
  this hasn't been validated against real scan accuracy data yet, and
  MarkerDetector's proven-working behavior was tuned against
  MAXIMIZE_QUALITY output. Round-trips correctly through Backup/Restore.
- New: volume up/down now also captures on the scan screen, alongside
  the dedicated button and tap-anywhere-on-preview. Same gating and
  isCapturing guard as the other two triggers, so it can't race them.
- Export Backup now offers a Cancel/Share/Download choice instead of
  going straight to a Share-only intent — reused the same FileExportPrompt
  helper already shared by PDF/Item Analysis/diagnostics exports, instead
  of building a fourth copy of that flow.
- New: camera shutter sound on capture, with a Settings toggle (on by
  default). Bundled audio file (res/raw/camera_shutter.mp3), played via
  a short-lived MediaPlayer released on completion.
- Fixed: "Save Answer Key" button text was hardcoded black, on the
  assumption the accent would always be light/gold — unreadable against
  a dark chosen accent color. Now computes proper contrast via
  AccentColorPrefs.textColorFor(), the same luminance-based helper
  already used correctly elsewhere in the app.
- New: each diagnostic log entry now records tap-to-result latency and
  whether Faster Scanning was on, so future reports show real data on
  whether that toggle actually helps rather than guessing.

## v3.1.5 — 2026-07-26

- Fixed (for real this time): the blinking cursor in the "Rename test"
  dialog and the Send Diagnostic Report feedback field still showed the
  theme's maroon after v3.1.4's fix. Root cause found: both fields are
  wrapped in Material's TextInputLayout (the outlined-box style), which
  runs its own internal cursor-color sync tied to the box's stroke color
  — that sync overrides anything set directly on the child EditText, no
  matter how or when it's reapplied, which is why every previous attempt
  (mutate(), deferred post{}, focus-listener reapply) still lost. Now
  uses TextInputLayout's own setCursorColor() API instead of fighting its
  internal sync from outside. The Save-flow "Rename test" dialog (a plain
  EditText, not TextInputLayout) is unaffected by this specific bug and
  was not touched.

## v3.1.4 — 2026-07-26

- Replaced Send Diagnostic Report's backend entirely: it no longer uploads
  to a Google Apps Script / Drive pipeline (which never reliably worked
  across several deployment attempts — redirect/response-delivery
  failures that couldn't be fully resolved). It now opens your email app
  with a generated report (app/device info, scanner config, recent scan
  log, and your note) attached and pre-addressed, so you review and send
  it yourself. No server to host, no deployment to keep working. Removed
  the now-unused INTERNET permission along with it.
- Fixed: the blinking cursor in the "Rename test" dialog (and its
  save-flow sibling) still showed the theme's maroon after v3.1.3's fix.
  That fix's single deferred re-tint could still lose a timing race
  against the dialog's own auto-focus/keyboard show-up. Now re-applies
  the tint whenever the field gains focus, not just once after attach.

## v3.1.3 — 2026-07-26

- Fixed: Send Diagnostic Report was pointing at a stale/incorrect backend
  deployment URL. Updated to the confirmed-working deployment.
- Fixed: the blinking cursor in the "Rename test" dialog (and its
  save-flow sibling, which never had a cursor fix at all) still showed
  the theme's fixed maroon instead of the chosen accent color. The
  earlier cursor-tint code was missing a `.mutate()` call before tinting
  and ran before the field was attached/focused, so the tint didn't
  reliably stick — same underlying gap fixed in the Send Diagnostic
  Report feedback box's cursor.

## v3.1.2 — 2026-07-26

- Removed the menu button added to the scan (camera) screen in an
  earlier revision — this screen doesn't need one.
- Fixed: Send Diagnostic Report's feedback box (cursor, drag handle,
  box outline, and label text) wasn't reliably following the accent
  color when it changed. Now theme-adaptive instead — black in Light
  theme, white in Dark/Black — matching ordinary body text rather than
  the accent.

## v3.1.1 — 2026-07-26

- Fixed: Send Diagnostic Report could report success even when nothing
  reached Drive — it only checked the HTTP status code, and Apps Script
  always returns 200 even from its own error handler. Now checks the
  server's actual success/failure response.
- Fixed: the feedback field's label and placeholder text were
  overlapping on the Send Diagnostic Report dialog.
- Fixed: swiping the Test Summary to a folder with zero scanned sheets
  (including Main, when everything was actually filed under a different
  folder) made the whole summary section disappear and the buttons
  below jump up. Now shows a plain "scan at least one sheet" message
  instead, so the layout stays put.
- Fixed: text input fields (test title, question count, subject
  teacher, columns/rows on Generate/Edit Template; default question
  count and teacher name in Settings) were never wired into the accent
  color system at all — always showed the fixed default gold underline
  regardless of your chosen accent. Also fixed the same gap on the Send
  Diagnostic Report dialog's outlined box, missed when that dialog was
  first built.
- Fixed: those same text input fields' blinking cursor and drag handle
  still didn't follow the accent color even after the underline was
  fixed — the underline, cursor, and drag handle turned out to be three
  separate things that all needed tinting, not one.
- Removed "Main" as its own page when swiping the Test Summary — its
  stats are the same as "All Folders" once real folders are in use, so
  showing it separately was redundant.
- Send Diagnostic Report now sends device/app version alongside the
  report, matching the upgraded backend that stores reports in Drive
  and Sheets with analytics.

## v3.1 — 2026-07-25

- **Fixed: low-light scans failing to find the sheet at all.** Diagnosed
  from a real diagnostics log and sample photos — the corner-marker
  check that confirms a match is sitting on real paper (not a random
  dark patch in the scene) used a fixed brightness floor that genuinely
  dark, ordinary indoor-lit photos couldn't clear, even when the marker
  shape itself was found correctly. Lowered the floor so it still
  rejects non-paper clutter but no longer rejects real paper under
  normal (non-flash) lighting. Same class of bug as last version's
  scanner-accuracy fix, one stage earlier in the pipeline.
- **Diagnostics log is now capped at the most recent 100 scan attempts**
  and reads newest-first — it used to grow forever with no cleanup.
- **Fixed: Test Summary swipe wasn't working** on the Test Detail
  screen — a one-line gesture-detector fix.
- **Fixed: rename dialog's drag handle was still maroon** even after
  last version's cursor-color fix — the drag handle and cursor turned
  out to be two separate things to tint. (Android 9+; older versions
  keep the default handle color.)
- **Fixed: "Blank Preview" / "Answer Key Preview" buttons were different
  heights** on the Generate/Edit Template screen.
- **Hamburger icon fix, corrected:** last version's fix tinted every
  hamburger to your accent color — turns out that wasn't the ask. Every
  hamburger (including the home screen) now matches the screen's own
  title text color instead, which follows your theme but not your
  accent.
- **Scan screen now has a menu button** — every other screen already
  had one; this one never did.
- **Fixed: gold color on the Create/Edit Answer Key screen didn't follow
  your chosen accent** — both the selected-bubble fill and the "Save
  Answer Key" button were reading the theme's fixed gold instead.
- Added a "Saved Tests" heading above the test list on the home screen.
- Help/FAQ: fixed a reference to a "New Test" button that doesn't exist
  (it's the + button), bolded question lines for easier scanning, and
  added an entry explaining Send Diagnostic Report.
- Replaced Export Scan Diagnostics with Send Diagnostic Report — one tap
  sends an anonymous report (device/app info, recent scan activity, and
  an optional note from you) directly to us instead of sharing/
  downloading the log yourself. Never includes student names, answer
  sheets, photos, PDFs, or exam content. The "Clear Diagnostics Log"
  long-press option is unchanged.

## v3.0 — 2026-07-24

- **Scanner accuracy — real fix, diagnosed from an actual diagnostics
  log.** Photos with genuinely low absolute contrast (washed-out
  lighting, thin pencil, heavy compression) were being confidently and
  correctly read by Otsu thresholding as low-value — then thrown away
  and replaced with a fixed threshold that's too strict for that kind
  of photo, causing real marks to register as blank ("100% unclear").
  Fixed by trusting a high-confidence low reading instead of overriding
  it. This is the direct cause of pencil marks and dim-lit photos
  scoring far worse than ballpen/bright photos.
- **Fixed: cache buildup.** Every scan attempt — including rejected and
  unclear ones — was leaving a full-resolution photo behind with no
  cleanup, ever. Fixed at the source, plus an added cleanup sweep on
  app open for any other leftover scratch files.
- **Custom app font (Century Gothic)**, replacing the system font
  throughout the app and on generated PDF answer sheets.
- **Fixed: rename dialog's input box was always maroon**, regardless of
  your chosen accent color.
- **Fixed: hamburger menu icon** now follows your chosen accent color
  too.
- **Test Summary redesign:** removed the redundant test title and the
  maroon background panel — plain text that follows your theme now.
  Swipe left/right to page through All Folders and each individual
  folder's own summary.
- **Haptic feedback added to every delete button**, tap or long-press.
- **Footer opacity:** confirmed the header/footer inconsistency is a
  deliberate design difference (header sits on a more see-through
  panel, footer on a more solid one, by design) rather than a bug —
  not changed this round.

## v2.9 — 2026-07-24

- **Fixed: footer text still looked faded even after two prior attempts.**
  Root cause finally found — the Dark and Black themes never actually
  defined `android:textColorPrimary` at all, so every view using it
  (footer included) was quietly falling back to Android's translucent
  default instead of a solid color. Fixed once at the theme level, not
  the footer's own view attributes.
- **Fixed: "Use This as Answer Key" button had unreadable black text on
  a maroon button.** Two competing color rules were fighting each other —
  the accent system was already choosing the correct contrasting color,
  then older leftover code was overwriting it right after. Removed the
  leftover code.
- **Fixed: the folder-choice prompt before scanning had gone missing.**
  Scanning always saved into the Main folder with no way to choose,
  even for tests with multiple folders set up. Restored — tests with
  only Main skip the prompt and scan straight away; tests with
  additional folders now ask which one new scans should go into first.
- **Exit confirmation added.** Backing out of the home screen (button or
  gesture swipe) now asks before closing the app.
- **Download notifications.** Saving a file to your Downloads folder now
  shows a confirmation notification (Android 13+ may ask for
  notification permission once).
- **Bigger text on the home screen's saved-test list**, for readability.
- **Generate PDF, Export Item Analysis, Export Diagnostics, and Generate
  Test Sheets now all work the same way:** one button generates the
  file, then asks whether to Share or Download it, instead of separate
  buttons for each.
- **Fixed: downloading a generated file was saving it twice** — once to
  the visible Downloads folder, and a second, permanent copy hidden
  inside the app's own storage that never got cleaned up. Only the
  Downloads copy remains now.
- **Test Detail screen reworked:** the Test Summary button and popup are
  gone — the summary now shows directly on the screen itself, and Item
  Analysis is now a full-width button in the space that freed up.
- **New: Help / FAQ**, in the sidebar — walks through creating a test,
  scanning, folders, results, exporting, and appearance settings.
- **Haptic feedback added to popup prompts and confirmations** app-wide.
- **Compatibility review** for wider device support — permission
  requests now degrade safely by Android version instead of assuming
  the newest behavior everywhere; also fixed a pre-existing bug found
  during this review where file-sharing on Android versions below 10
  would have crashed for Item Analysis exports (a required file
  location was never registered).

## v2.8 — 2026-07-22

- **Fixed: welcome animation's status bar was still black, not matching
  the video.** A leftover fullscreen flag on that screen was silently
  overriding the status bar color — removed. Also fixed the footer
  (version/dev credit line) still looking faded: the credit line had no
  explicit text color at all, so fixing its opacity alone hadn't been
  enough.
- **Fixed: gold checkboxes on the Saved Tests and Scanned Sheets
  selection screens didn't follow the Button Accent color.** These are
  built fresh each time those lists redraw, so they needed their own fix
  on top of the general accent coverage from the last update.
- **Home screen: logo and title nudged up slightly** so they sit more
  comfortably within the header instead of crowding its bottom edge.
- **Fixed: an edited Answer Sheet Template wasn't actually saving.**
  Opening an existing test's template from Test Detail, changing it, and
  confirming looked like it worked but silently discarded the change —
  the screen that builds the edit never had anything listening for its
  result. Now genuinely persists, including handling a rename correctly
  instead of leaving a duplicate behind.
- **Fixed: theme changes from the sidebar didn't hold on other screens.**
  Switching Light/Dark/Black from a non-home screen updated that screen
  correctly, but any other screen already open underneath it kept
  showing the old theme until something else happened to refresh it.
  Same fix now also makes a saved accent color show up everywhere
  immediately, without needing an unrelated theme switch to force it.
- **Fixed: Dark theme's background was maroon-tinted, not neutral gray.**
  Now a genuine charcoal gray. (Black theme was already correct — checked
  separately, not just assumed clean.)
- **Splash screen now matches its own video's background** (status bar
  included) for a seamless fullscreen look, sampled directly from the
  video itself.
- **Tap the logo to replay the welcome animation** — works from the home
  screen and from the sidebar, on any screen.
- **Accent color picker**: fixed a preview bar corner that looked
  square instead of rounded (it was being clipped, not actually square),
  and centered the color swatch grid.
- **New app icon** (the launcher/home-screen icon — the logo shown inside
  the app is unchanged).
- Deleted a few more leftover files from old branding that were sitting
  unused in the project.
- **Fixed: the on-screen preview for an unclear/ambiguous scan now shows
  the same processed image the scanner actually judged.** It was
  previously falling back to an earlier-pipeline-stage image (before
  perspective correction and contrast processing) whenever a scan was
  flagged unclear, so what you saw on screen didn't quite match what the
  scanner was reacting to.
- **Scan reliability: adaptive threshold no longer swings on repeat scans.**
  Rescanning the exact same physical sheet could previously flip between
  ACCEPTED and REJECTED_UNCLEAR because the per-photo threshold reacted to
  small photo-to-photo noise. It now checks how trustworthy its own
  reading is on each photo and leans on a stable anchor value more when
  that reading looks shaky, instead of trusting every photo's number
  equally. Note: the exact cutoffs are a reasoned starting point, not yet
  confirmed against a real batch of diagnostic data — worth revisiting
  once you've pulled a fresh diagnostics export.
- **Home screen: header panel redesigned.** Narrower, pinned so it no
  longer scrolls with your test list, and the title is now two lines
  ("MCQ" / "QUICK CHECK").
- **Scanned Sheets: select multiple to delete at once.** Long-press any
  scan's trash icon to switch the whole list into select mode; a normal
  tap on a trash icon still deletes just that one scan.
- **Settings: two new accent color pickers** — Top Panel Accent and
  Button Accent — each with a swatch grid, a lighten/darken slider, and
  its own Revert to Default, applied across every screen (including the
  status bar).
- **Replaced: splash animation.** New launch video, same crop-to-fill
  playback (`WelcomeActivity`/`CropFillVideoView`) and timing logic as
  before — only the clip itself changed.
- **Replaced: app logo.** New pixel-art `</MCQ/>` mark, used everywhere
  the old logo appeared (home screen header, navigation drawer).
- **Replaced: launcher icon.** Previously the same transparent logo file
  doubled as the launcher icon; it now has its own dedicated icon asset
  (circular, with its own background) so it displays properly on the
  home screen app grid instead of transparent-on-whatever-wallpaper.
- **Removed: unused old school-facade image** (`school_facade.jpg`) —
  had zero references anywhere in code or layouts.

## v2.7 — 2026-07-20

- **Added: Item Analysis.** Difficulty (P) and discrimination (D) index
  per question, using Kelley's 27% high/low group split, with
  interpretation and recommendation text per the tlconestoga.ca rubric.
  Needs scans saved from this version onward — older scans didn't record
  per-question correctness and are silently excluded, not guessed at.
- **Added: Export Item Analysis as .xlsx.** Downloads directly to the
  device's Downloads folder on Android 10+; falls back to sharing the
  file on Android 9 and below (writing straight to Downloads on older
  Android needs a permission this app doesn't otherwise use, and adding
  one just for that felt like the wrong tradeoff).
- **Changed: the home screen is now the Saved Tests list itself.**
  Tapping a test opens a dedicated screen for it — Answer Sheet Template,
  Scanned Sheets, Test Summary, Item Analysis, Scan Filled Answer Sheets,
  and delete, all in one place. The old separate "Load Test" picker
  screen is gone, fully replaced.
- **Added: Select mode with bulk delete** on both the Saved Tests list
  and Scanned Sheets, each with a confirmation naming exactly what's
  about to be removed.
- **Added: sort scanned sheets** by date or score, ascending or
  descending — remembers your last choice.
- **Added: the app's menu is now reachable from every screen**, not just
  the home screen.
- **Changed: editing an existing test's template** now clearly reads as
  editing ("Edit Answer Sheet Template" / "Edit Answer Sheet") instead of
  reusing the same wording as creating a brand new one.
- **Changed: Test Summary's Mean and Median** now show as plain numbers;
  removed the "Item Analysis coming soon" note now that it's real.
- **Fixed: the Scanned Sheets screen's own title** still said "Saved
  Scans" even though the button opening it had already been renamed.
- **Changed: every screen now uses the same washed-out background** as
  the home screen, instead of a plain solid background.

## v2.6 — 2026-07-19

- **Added: scan preprocessing pipeline** (`ImagePreprocessor.kt`) — shadow
  and overhead-light-glare removal (illumination-field division), local
  contrast boost (CLAHE), and unsharp-mask sharpening, inserted right
  after dewarping and before grading. Grading, the score annotation, and
  the previewed/saved image all now use this same cleaned-up version
  instead of the raw dewarped photo. Deliberately NOT hard black/white
  thresholding — stays in continuous grayscale so BubbleGrader's own
  coverage-based ambiguity detection keeps working on real tonal data, not
  a flattened two-color image. See the file's own doc comment for the
  full reasoning.
- **Added: Test Summary.** New button on the saved-scans screen (Main and
  every subfolder) showing: count, highest/lowest score, mean, median,
  mode, standard deviation (population for Main, sample for a specific
  subfolder), and number of passers at 75% and 50%. Main's summary
  aggregates every subfolder's scans, not just scans saved directly under
  Main.

## v2.5 — 2026-07-18

- **Added: Legal & Licenses section.** The About screen now includes a
  copyright notice for the app itself, plus required attribution for the
  open-source libraries it depends on (OpenCV 4.9.0 and AndroidX, both
  Apache License 2.0). Also added a `LICENSE` file at the repo root.
- **Fixed: "Use This Template & Save Test" button wrapping.** Forced an
  explicit line break so the phrase always wraps as "Use This Template" /
  "& Save Test" instead of letting the system break it wherever it likes.
- **Added: number-of-questions +/- stepper**, next to the question-count
  field on both Generate Answer Sheet and Settings' default. Steps by 1
  (an item count, not a rows-per-column fit). On Generate Answer Sheet,
  the stepper is clamped through the same per-layout min/max as typed
  input; on Settings it uses a simple 1-90 range, matching how the
  existing columns/rows steppers there work (no per-layout awareness,
  since a "default" isn't tied to a layout yet).
- **Changed: sheet-size guidance wording at the 60-question boundary.**
  Going below 60 on a ½ length/½ cross sheet now says "Use 1/4 sheet for
  60 questions and below."; going above 60 on a ¼ page sheet now says
  "1/4 sheet is capped at 60 questions." Applies to both typed input and
  the new steppers above.
- **Fixed: Overwrite/Rename dialog button order.** The buttons were
  attached to the wrong slots, so OVERWRITE displayed on the left and
  RENAME on the right despite the labels suggesting otherwise. Now
  OVERWRITE is genuinely left, RENAME genuinely right.
- **Added: Backup Data now includes Settings defaults and app theme**
  (backupFormatVersion 3). Restore reads these back guarded by the same
  backward-compat pattern already used for older backup fields, so v1/v2
  backup files still restore cleanly without them.
- **Fixed: ½ cross saved-scan thumbnail crop.** Since v2.3 gave each paper
  layout its own true scan resolution, ½ cross's Name/Gr.&Section
  thumbnail was being cropped into an extreme sliver. It's now
  proportioned like ¼ page's crop instead of stretched edge-to-edge.
- **Fixed: subfolder tab text color on Dark/Black themes.** The unselected
  tab text was hardcoded to a dark color that assumed a light background.
- **Changed: "Scan Filled Sheets" button renamed to "Scan Filled Answer
  Sheets."**
- **Added: "Scan Filled Answer Sheets" button on the Saved Scans screen.**
  Scans directly into whichever folder (Main or a subfolder) is currently
  being viewed, without needing to go back to the home screen first.

## v2.4 — 2026-07-18

- **Changed: auto-save on template confirmation.** Confirming a template on
  the Generate Answer Sheet screen now saves it as a test automatically —
  the button reads "Use This Template and Save Test." The separate
  "Save Test" button on the home screen is gone.
- **Added: duplicate-title save conflict prompt.** Saving a test with a
  title that already exists now prompts Overwrite / Rename, with a second
  explicit confirmation before overwriting (which permanently deletes the
  existing test and its scans).
- **Fixed: score alignment.** The printed score is now right-aligned to the
  score cell instead of left-anchored after the "Score:" label — on
  HALF_LENGTH sheets a long score string could previously run off the
  visible photographed area. This also structurally eliminates the earlier
  v2.3 Subject/Teacher-toggle position bug, since the drawing position no
  longer depends on measuring the label's width at all.
- **Fixed: "Use This as Answer Key" button text** unreadable on Dark and
  Black themes.
- **Changed: item counts and column counts are now tied together, and more
  restricted overall**, based on real-device scan diagnostics — this
  applies to real test creation in `GenerateSheetActivity`, not just the
  prefilled test-sheet generator tool (the first-pass version of this fix
  only touched the generator tool; this supersedes that):
  - QUARTER: 10 items → 1 column, 11-20 items → 2 columns, 21-60 items →
    3 columns. Max item count unchanged (60).
  - HALF_LENGTH: 60-90 items, 3 columns only. Max item count lowered from
    120 to 90; the old 1-2 column choice is gone.
  - HALF_CROSS: 60-75 items → 5 columns, 76-90 items → 6 columns. Max item
    count lowered from 120 to 90; the old 1-4 column choice is gone.

  Column count is no longer a free choice at any layout — each item count
  now maps to exactly one valid column count. Investigated
  `SheetTemplate.kt`'s bubble-position math and the sheet generator's
  region-drawing code for a root cause first — found no column-count-
  dependent bug on inspection, so this restricts to the combinations
  confirmed working rather than shipping an unconfirmed geometry change.
- **Added: ambiguous-marks stress-test sheet.** The prefilled test-sheet
  generator (About > long-press title) now also produces one QUARTER,
  60-item, 3-column sheet with deliberately messy marks scattered through
  it — some questions left blank, some lightly/partially filled, some
  overflowing past the bubble outline, some with two bubbles marked on the
  same question — to test scanning against a realistic mix of clean and
  messy marks, not just uniformly clean or uniformly broken sheets.

## v2.3 — 2026-07-18

- **Changed: test-sheet generator overhaul.** New column/item-count matrix
  (testing tool only, long-press About screen title — not part of real
  test creation or scoring): QUARTER now also generates a 2-column
  variant (n=20-60); HALF_LENGTH/HALF_CROSS below 70 items dropped;
  HALF_LENGTH gains a 2-column variant (n=70-120); HALF_CROSS gains
  4-column and 5-column variants (n=70-120). Each generated sheet now
  also packs multiple distinct randomized fill-pattern variations onto
  one physical page (4 for QUARTER, 2 for HALF_LENGTH/HALF_CROSS) to
  better stress-test scanner consistency.
- **Fixed: half-page scan preview compression.** HALF_LENGTH and
  HALF_CROSS scans were being warped into a fixed, full-page-shaped
  output regardless of their actual (narrow-portrait or wide-landscape)
  tile shape, which both distorted and downscaled them well below the
  captured photo's real resolution — QUARTER happened to share the
  full page's aspect ratio, so it wasn't visibly affected. Scans now warp
  to their tile's correct aspect ratio at the source photo's own
  resolution.
- **Fixed: score print-position bug.** The printed score value shifted
  noticeably right whenever the Subject/Teacher line was toggled on, due
  to measuring the "Score:" label's width using a font size that
  (incorrectly) varied with that toggle. Now prints immediately after
  "Score:" consistently in both states.
- **Changed: new welcome-screen animation clip** (~2.25s, down from
  ~4.5s), with fade-out retimed to 2.10s-2.25s.
- **Added: scan diagnostics logging.** For tracking down scans that get
  flagged unclear/ambiguous even though they look fine (real-device
  feedback: half-page scans "barely" succeeding, one HALF_LENGTH scan that
  worked once and never since, some QUARTER/HALF_CROSS scans still flagged
  on captures that look clearly filled). Every scan attempt — successful,
  rejected as unclear, markers-not-found, or errored — is now logged with
  the internal per-question coverage numbers and adaptive ink threshold
  BubbleGrader used to make its decision. Share the log from
  About > Share Scan Diagnostics; long-press that button to clear the log
  and start a fresh testing session. No detection or threshold changed —
  this is instrumentation only, to gather real evidence before any tuning
  happens.

## v2.2 — 2026-07-17

- **Fixed: answer-key bubble shading.** The correct-answer bubble in the
  answer key preview and printed sheet was showing as a hollow ring
  (small dark center, unshaded gap before the outline) instead of a fully
  shaded bubble. Now reads as fully shaded.
- **Changed: welcome-animation toggle is now visible.** Moved from a
  hidden, invisible tap strip in the Changelog screen to a normal, labeled
  switch.
- **Changed: the live corner guide now turns green based on the phone's
  tilt sensor.** When the phone is held flat and level over the sheet, all
  4 guide squares light up together, instead of relying only on the
  per-frame camera-image check, which wasn't always catching a good
  alignment. This runs alongside the old check, not instead of it — the
  camera check still applies when the phone isn't flat.
- **Changed: sharper perspective correction on scanned sheets.** Switched
  the un-skewing step from Android's built-in image-warp to OpenCV's
  (already used elsewhere in the app), with higher-quality interpolation —
  closer to the crisp output of a phone's built-in "scan document" mode.
- **Added: downloadable prefilled test sheets.** Long-press the About
  screen title to generate and share a zip of ~30 prefilled PDFs — every
  paper layout × item count from 10 to 120 — for testing the scanner
  end to end. One-off testing tool, not a regular feature.
- **Changed: new welcome-screen animation clip**, with its fade-out
  retimed to match the new clip's ~4.5s length (previously tuned for the
  old clip's ~4.55s).
- **Fixed: stray divider line** under the text field in the Rename Test
  dialog (long-press a saved test).

## v2.1 — 2026-07-16

- **Fixed: "Scan Answer Key Instead" crashed on exit and couldn't be used
  at all.** Root cause: a safety check meant only for grading (which
  blocks scanning a test whose saved answer key doesn't match its sheet)
  was also firing during answer-key scanning itself — where "0 key items
  so far" is the normal starting point, not an error. That check now only
  applies when actually grading a filled sheet. Also hardened the camera
  cleanup code so any other early-exit path can't cause the same crash.
- **Fixed: on-sheet score still looked the same size after the last
  update.** Found the real cause: the size was being silently capped
  smaller than the multiplier intended. Re-derived it to size directly
  against the actual Score cell height instead.
- **New: Saved Tests now sort by most recent activity** — editing,
  saving, or scanning a test all count, not just when it was first saved.
- **New: long-press a saved test to rename it** — scans and folders
  attached to that test move with the rename automatically.
- **New: you can save a sheet template without an answer key set up
  yet** and come back to the answer key later.
- **New: item-count limits per paper size** — quarter-page sheets cap at
  60 items, half-page sheets (both layouts) cap at 120.
- **New: an optional welcome animation** on app launch.

## v2.0 — 2026-07-16

- **Fixed: scanned sheets from before this update looked like they'd
  vanished.** They hadn't — v1.9 changed how scans are stored, and the
  new code simply didn't know how to read the old format. Old scans are
  now automatically recognized and restored the next time you open the
  app. Nothing was ever deleted.
- **Fixed: swipe between saved scans didn't work.** The real cause: the
  swipe listener was attached to a view that could structurally never
  receive the touch (the zoomed image underneath always claimed it
  first) — not a sensitivity problem, it never had a chance to fire.
  Rebuilt so swipe reliably moves to the next/previous scan.
- **New: dedicated "Saved Tests" screen.** Load Test now opens a full
  screen (with room to grow) instead of a popup — same tests, same
  actions, more space.
- **New: folders are reachable even with zero scans in them**, so you
  can set up folders for a test before you've scanned anything into it.
- **New: move a saved scan to a different folder** from the saved-scans
  list.
- **New: "+" button to start a brand-new blank test** from the home
  screen, without disturbing whatever test is currently loaded.
- **New: items-per-column limits now scale to your column count**,
  rounded to sensible steps (multiples of 5/10) instead of a flat cap —
  and the app now suggests a good columns/rows split as you type.
- **New: answer key items can be set to 0 points**, with a confirmation
  prompt so it's never accidental — useful for voiding a bad question
  without deleting it. A separate +/- stepper for the bulk "Points per
  item" default still can't go below 1.
- **Changed: on-sheet score is bigger, bold, and green** for easier
  reading at a glance.
- **Changed: saved-scan thumbnails are properly sized** so the
  Name/Section crop is actually legible, not a tiny sliver.
- **Changed: About page now shows a "SCAN CHECK REVIEW" tagline.**
- Under-the-hood: saved scans and tests now check consistently before
  scanning starts, and several dialog/theme edge cases were re-verified.

## v1.9 — 2026-07-15

- **Fixed: pinch-zoom and double-tap zoom didn't work at all.** Root
  cause: the touch handler returned `false` for the very first touch of
  every gesture, which — per how Android's touch system works — meant the
  view never received any of the rest of that gesture (a second finger
  landing, or a second tap). This is why zoom looked completely broken
  rather than just glitchy. Fixed to always accept that first touch, then
  decide what to do with the rest of the gesture afterward.
- **Fixed: unclear%/ambiguous% could show over 100%.** A recent change
  meant a question could legitimately be counted in two overlapping
  categories at once; the math that combined them was still adding
  instead of counting the overlap once. Fixed to count correctly, which
  also removes a major cause of sheets needing several rescans before
  being accepted.
- **Fixed: the same physical sheet could score differently each time it
  was rescanned.** The ink-detection threshold was being recalculated
  fresh from each individual photo, which reacts to more than just real
  lighting differences — small, meaningless differences between two
  photos of the same sheet (focus, exposure, compression) could shift it
  enough to flip a few borderline bubbles. Blended it with a stable
  anchor point so small photo-to-photo noise matters less. **Reasoned
  fix, not yet confirmed by rescanning the same sheet multiple times —
  please test and report back.**
- **Fixed: a single, clearly and fully shaded bubble could get flagged
  ambiguous.** The "is there a second mark competing with this one"
  check only looked at how dark the second-place bubble was, not how far
  behind it was from a clearly dominant winner. A 95%-dark bubble next
  to a faint 40% smudge elsewhere in the row no longer counts as "two
  marks competing." **Reasoned fix, not yet confirmed.**
- **Fixed: a sheet could be scored against the wrong total** (e.g. "X /
  40" printed on a sheet whose title and grid both say "30 Items").
  Caused by a saved test where the answer key's length didn't match the
  sheet's actual question count — now checked before scanning starts,
  with a clear message telling you to rebuild that test's answer key,
  instead of silently grading against the wrong number.
- **HALF_LENGTH capture reliability:** found and fixed a race condition
  where the camera could start capturing before it had properly locked
  onto the same crop region the on-screen guide was showing, causing a
  captured photo that looked like a completely different, garbled slice
  of the sensor. This was most visible on HALF_LENGTH's narrow guide box.
  Now waits (with a safety cap) for that lock before allowing a capture.
  **Reasoned fix, not yet confirmed on a real device.**
- **Every unshaded/blank bubble is now flagged ambiguous** (orange
  strikethrough), not just faint-but-real marks — per explicit request,
  reversing an earlier version's narrower behavior. A sheet with several
  legitimately skipped questions still scans and grades normally; only
  the *visual* flag changed, not whether a scan gets accepted.
- **Sheet generator: the max items-per-column is now dynamic**
  (items ÷ columns for the chosen paper layout, up to 3 columns for
  Quarter/Half-Length or 6 for Half-Cross), replacing the old flat cap of
  30. Entering a question count now also suggests a near-square
  columns/rows combination automatically, instead of requiring both to be
  set by hand.
- **Saved scans: dedicated list screen**, replacing the old popup —
  shows a cropped Name/Gr.&Section thumbnail, score/percentage, and
  date/time for each saved scan. Tapping one opens it full-screen with
  swipe left/right to move to the next/previous scan in that folder.
  Each row has its own delete icon with a yes/no confirmation.
- **Score and percentage are now printed directly on the scanned sheet
  image itself**, right after the "Score:" label — not just as separate
  text above the image.
- **Folders for saved scans:** a default "Main" folder is always
  present, even when empty. Add more folders via the + icon on the list
  screen; long-press a folder tab to remove it (scans inside move back to
  Main, never deleted). If a test has folders beyond Main, you'll be
  asked which one to save into right before scanning starts; if it only
  has Main, scanning starts immediately with no extra prompt.
- Backup/Restore: scan backup format updated to carry folder and score
  data. Old backup files from before this version still restore
  correctly (detected automatically, no action needed).

## v1.8 — 2026-07-14

- **Fixed: Settings screen crash.** Two `TextView`s in the autosave section
  were missing required `layout_width`/`layout_height` attributes — Android
  requires both explicitly on every view. Confirmed via the actual crash
  log (`UnsupportedOperationException`), not a guess.
- **Fixed: scanning regression causing high ambiguous/unclear rates on
  clear photos, across all paper layouts.** Root cause: a coverage-band
  widened last version (to catch one specific thin-mark case) was too wide,
  so it started flagging normal full pencil marks as ambiguous. Reverted
  the over-correction, and added a separate center-darkness check
  (`CENTER_RADIUS_FRACTION`/`MIN_CENTER_COVERAGE`) that catches the
  original thin-mark case using a more specific signal (is the bubble's
  actual center dark?) instead of one blended coverage percentage. **This
  is a reasoned fix, not verified against real photos — please test all
  three paper layouts and report back.**
- **Fixed: a real-but-too-faint mark (e.g. a small partial shading) used to
  resolve identically to a completely blank bubble** — neither scored nor
  flagged for a second look. Added a genuinely-blank floor separate from
  the "counts as an answer" threshold, so the gap between them (real ink,
  just not enough) now gets the orange ambiguous strikethrough instead of
  silently vanishing. Scoring is unchanged (still excluded either way) —
  confirmed by reading `AnswerParser.kt`, which wasn't touched.
- **Fixed: sheet generator could silently build a different layout than
  what was on screen.** Typing a number directly into the Columns or
  Questions-per-column field (e.g. 40 when only 30 is valid) clamped the
  value actually used to build the sheet, but never corrected the
  displayed number — so the field could show "40" while the real sheet
  used "30," which is what caused sheets to wrap into an unexpected extra
  row of columns. Now the field corrects itself when you tap away, instead
  of showing a number that was never actually used.
- **Settings: added a "Show 'Subject Teacher:' line" toggle**, with a
  default name field that pre-fills new templates. Turning it off shortens
  the header table on the generated sheet without leaving a blank gap or
  moving anything the scanner depends on — the grid/bubble positions never
  shift, so this can't create a mismatch between the printed sheet and
  what the scanner expects.
- **About page reworked:** now shows a short, hand-picked list of genuine
  new features from recent versions instead of the full changelog (no bug
  fixes or internal notes), with justified body text on Android 8.0+.
  Wrapped in a scrolling container so it stays readable as that list grows
  — not fixed to one screenful.
- **Side drawer: added version number + developer name**, centered at the
  bottom, tapping it opens the changelog (same as the existing home screen
  footer). Changelog text is now justified on Android 8.0+.
- **Checked the perspective warp: it already runs on every single capture**,
  regardless of the live guide's status — confirmed by reading the code,
  this was never actually gated. What's still true (and was already a
  known, documented limitation): a sheet that's curled or bowed in the
  middle can still look skewed even with clean corners, since a 4-corner
  warp only guarantees those 4 points land correctly. Fixing that for real
  needs interior markers or a full mesh warp — a separate, bigger task, not
  done here.
- **Generate Template screen:** field label changed from "Test name" to
  "Test Title" (display only — nothing in how tests are saved changed).
  Pressing back now asks to confirm before discarding an unsaved template.
- Checked the "bold test title" report directly — the title was already
  correctly set to a bold typeface in the only place it's drawn. Couldn't
  reproduce an actual bug on inspection; added a defensive fallback
  (`isFakeBoldText`) for a known OEM font-substitution edge case, but
  flagging this honestly rather than claiming a fix for something I
  couldn't find broken.
- **Not independently verified this version:** Save Test and the fullscreen
  zoom viewer. Neither was touched by anything in this release, and there's
  no code-level reason they'd behave differently — but that's not the same
  as confirming they still work. Please test both directly.

## v1.7 — 2026-07-14

- **Zoom on the scan result screen replaced with a fullscreen viewer.**
  Double-tapping the small inline preview now opens the scanned sheet
  fullscreen (new `FullscreenImageViewer`), where pinch-zoom, pan, and
  double-tap-to-zoom all work without a parent `ScrollView` fighting for
  the touch stream — the small inline preview double-tap now opens this
  instead of zooming in place, since the small preview area was too
  cramped to be useful even when zoomed.
- **Perspective/deskew: confirmed already implemented, not missing.**
  `PerspectiveWarp.warp()` already runs `Matrix.setPolyToPoly` on every
  scan, pulling the 4 detected corners onto the template's expected
  rectangle — this already is the "trapezoid corners into a flat
  rectangle" transform. Investigated per a feature request that assumed
  it was absent; no code change needed here. The known, still-deferred
  limitation is unchanged: this only pins the 4 outer corners, so a sheet
  that's curled/bowed in the middle can still smear between them even
  with clean corner detection — that needs interior fiducial markers or a
  full mesh warp, out of scope unless raised again.
- Added a delete-scanned-sheet icon below the result legend (initially
  shipped hidden/unwired earlier in this same version, then wired up once
  autosave existed later in this session — see below).
- **Grading: widened the margin between "confidently blank" and
  "confidently filled" to catch small/partial marks that were still
  reading as a clean correct answer.** Raised `MIN_COVERAGE` (0.42→0.46),
  widened `MARGINAL_COVERAGE_BAND` (0.08→0.15) so a partial mark lands in
  the ambiguous zone instead of clearing straight to "filled," and raised
  `MIN_SECONDARY_COVERAGE` (0.30→0.38) so a faint second mark on a row is
  less likely to knock a genuinely clean single answer into a false
  "wrong"/ambiguous read. **This is a tuning pass based on visual review
  of two failure-case photos, not a confirmed fix verified against actual
  measured coverage numbers** — please report back whether small partial
  marks (like item #25 in the 25-item test) are now correctly flagged
  ambiguous, and whether clean full marks (like the 60-item test) stopped
  getting spurious red rings.

- **Settings is now real**, replacing the placeholder from earlier this
  version: default paper layout, choice count, question count, columns,
  and questions-per-column (all applied only to brand-new templates —
  editing an existing test keeps its own saved shape), plus the
  auto-save-scanned-sheet toggle.
- **Auto-save scanned sheets.** When the Settings toggle is on, each
  graded result is saved automatically the moment it's shown — not
  deferred to Scan Next/Done — stored as JPEG (not PNG) inside
  SharedPreferences to keep entries reasonably small, capped at 60 saved
  scans per test (oldest trimmed first) so this can't grow unbounded over
  a school year.
- **Delete-scanned-sheet icon is now live** (was hidden/unwired earlier
  in v1.7). Tapping it deletes that scan's autosave and moves straight to
  the next scan, matching how it's meant to work as a quick "that photo
  was bad, undo and retry" action.
- **Load Test: paper icon to review saved scans.** Any test with at
  least one autosaved scan now shows a small paper icon next to the
  trash icon; tapping it opens a list of that test's saved scans by
  date, and picking one opens it in the same fullscreen viewer used on
  the scan result screen.
- **Backup Data / Restore Data are real.** Backup exports every saved
  test and every autosaved scan into one JSON file (versioned format)
  and hands it to Android's share sheet — save to Drive, email it,
  whatever. Restore reads that file back via the system file picker
  (works with a file from Drive/Downloads/anywhere, not just a fixed
  app folder) and writes tests/scans back into storage, asking for
  confirmation before overwriting any test whose name you already have.
  Uses the exact same field-by-field parsing (with the same backward-
  compat fallbacks) as TestStorage itself, so a restored test behaves
  identically to one saved normally.

## v1.6 — 2026-07-14

- **Fixed: near-empty bubbles could still grade as "correct."** `BubbleGrader`
  now runs a second, FIXED-threshold coverage check (`HARD_MIN_COVERAGE`,
  0.15) independent of the per-sheet adaptive ink threshold. Previously a
  single miscalibrated adaptive threshold (from glare/shadow/uneven exposure)
  could inflate every coverage number on that photo at once, letting a
  barely-marked bubble clear `MIN_COVERAGE` relative to that same bad
  threshold. The hard floor can't be fooled the same way since it doesn't
  use that threshold at all.
- **Fixed: ambiguous marks now shown as an orange strikethrough across the
  whole row (A–D), not an orange ring on one bubble.** Ringing only the
  "highest-scoring" choice implied more confidence than the grader actually
  had about which bubble was intended. The correct answer still gets its
  own green ring on top. Legend swatch updated to match (🟧— instead of a
  plain orange circle).
- **Fixed: green checkmark next to question numbers was drawn one row too
  high.** It was offset upward by more than a full ring radius on top of
  its own position; now drawn at the label's true vertical center with only
  a small baseline correction (`Canvas.drawText` anchors to text baseline,
  not the glyph's visual center).
- **Fixed: pinch-to-zoom not working on the scan result image.**
  `ZoomableImageView` sits inside a `ScrollView`, which was intercepting the
  touch stream based on the first finger's motion before a second finger
  (the start of a pinch) ever reached the zoom view. Now calls
  `requestDisallowInterceptTouchEvent` once it claims the gesture.
- **Live alignment guide: widened paper-detection tolerances.** The
  paper-quad-to-guide match distance was tied to the same tight tolerance
  used for the final per-marker contour check, which — for small paper
  layouts like ¼-page — could be too tight in absolute pixels to ever
  refine the search center toward the real sheet position, silently falling
  back to a pure geometric guess with no positional help. Also lowered the
  paper-quad area floor (12%→6% of frame) since a ¼-page sheet at a normal
  holding distance often doesn't fill 12% of the frame. **Not fully
  verified on a real device yet** — this is a plausible, reasoned fix based
  on reading the tolerance chain, not a confirmed root cause; please report
  back whether it actually helps or the guide still doesn't track corners.
- Home screen: swapped the header's theme-cycle button for a standard
  hamburger icon opening a navigation drawer (Theme now lives inside it).
  Drawer currently has working navigation only — Backup Data, Restore Data,
  Settings, and About are placeholder screens ("coming soon"), not yet the
  full features requested. Full build-out is next.

## v1.5 — 2026-07-14

- **Grading rewritten to use coverage instead of point-sampling.**
  `BubbleGrader` now measures what FRACTION of a bubble's true printed area
  reads as ink (`bubbleCoverage`), rather than the darkness at one small
  sample point. A point sample can read dark enough for a tiny partial dot
  (high peak darkness, low actual coverage) and is more sensitive to the
  sample landing slightly off-bubble from any warp imprecision — coverage
  over the bubble's real radius is far more robust to both. New tunables:
  `MIN_COVERAGE` (0.42), `MIN_SECONDARY_COVERAGE` (0.30, flags double-marks),
  `MARGINAL_COVERAGE_BAND` (0.08, flags borderline fills as ambiguous rather
  than confidently accepting them).
- **Ambiguous marks never count as correct.** `AnswerParser.grade()` now
  explicitly treats any ambiguous result as not-correct regardless of
  whether it happens to match the key, and computes the score by counting
  wrong/ambiguous/blank first and deriving correct from what's left (an
  explicit "work backwards" derivation) — makes the stricter rule the
  natural default instead of an easy-to-miss special case.
- Tightened `BubbleGrader.requireConfident()`'s rejection thresholds
  (ambiguous rate 0.30→0.25, unclear rate 0.55→0.45) now that coverage-based
  ambiguity detection is more sensitive to real problems — a badly-warped
  or genuinely unclear scan should now more reliably get rejected with a
  retake prompt instead of silently producing a wrong score.
  **Note:** true perspective/flatness detection (catching a curled/non-flat
  sheet even when its 4 corners individually warp fine) would need either
  additional in-sheet fiducial markers or a more advanced multi-point/mesh
  warp — out of scope for this release; the tightened confidence gate above
  is the practical mitigation for now. Flagged as a real gap, not solved.
- **Diagonal cross-validation for the live alignment guide.** In
  `AlignmentAnalyzer`, `ContourMarkerValidator.findMarkerNear` now returns
  the matched centroid instead of just a boolean, so once BOTH members of a
  diagonal pair (top-left↔bottom-right, or top-right↔bottom-left) are
  found, their relationship is checked against what an actual rectangle's
  diagonals should do — share a midpoint, and be comparable in length. A
  match that passed its own local contour+geometry check but doesn't fit
  that relationship (e.g. a coincidentally square-ish cluster of bubbles)
  gets demoted back to "not found" on both ends of the failing diagonal.
- **Result screen improvements:**
  - New `ZoomableImageView` (custom, no external library) — pinch-to-zoom
    and pan on the scanned sheet, double-tap to reset, so a teacher can
    zoom in on any bubble to double-check it.
  - `ResultAnnotator` now also draws a green checkmark next to the question
    number for anything cleanly correct, so the numbers column alone shows
    what's right at a glance.
  - Legend reformatted to 2 lines (Correct/Wrong on line 1, Ambiguous on
    line 2).
- Fixed the Load Test **Cancel button being completely invisible** in
  Dark/Black themes. Root cause: `fixDialogButtonColors()` resolved
  `android:textColorPrimary` via the theme, but our Dark/Black themes never
  actually set that attribute (only Light theme does) — the resolved value
  wasn't reliable. Now uses `ThemePrefs.getTheme()` directly instead of
  attribute resolution.
- Load Test entries now show a one-line preferences summary (question
  count, choices, paper layout) and the save date under each test name.
- Answer key screen: added +/− steppers next to each question's points
  field, fixed unreadable Discard-dialog button text in Dark/Black (same
  fix pattern as Load Test's Cancel button), and reworded "Your taps on
  this screen haven't been saved yet." to "Changes haven't been saved yet."

## v1.4 — 2026-07-13

- **Rebuilt the live alignment check from scratch.** Template matching —
  even against the distinctive ring marker from v0.8 — still wasn't a
  strong enough discriminator against a dense grid of answer bubbles at
  tolerances loose enough to be usable live; the guide kept reacting to
  shaded/unshaded bubble rows almost as readily as the real corners. New
  two-stage pipeline in `AlignmentAnalyzer`:
  1. **Paper detection** (`findPaperQuadBufferPoints`): looks for the
     physical sheet's own boundary — the largest bright, roughly-
     quadrilateral contour in the frame — and uses its actual corners as
     search centers when found, falling back to the geometric guide
     position per-corner otherwise.
  2. **Contour + geometry validation** (new `ContourMarkerValidator`):
     within a small window at each corner, checks for a contour with a
     hole (our ring marker's topology — a filled bubble has no hole, ruling
     those out immediately) AND validates it's actually square (polygon
     approximation vertex count, bounding-box aspect ratio, extent) — a
     circle, filled or not, can't satisfy the square check even though a
     ring shape gives it a hole too.
  This is a fundamentally different signal (topology + shape) than
  brightness or correlation, so it doesn't share template matching's
  weakness of scoring a cluster of circles as "close enough" to a square.
  `MarkerDetector` (the final check on the captured photo, already proven
  accurate) is unchanged — only the live hint's approach was replaced.
- **Result screen rebuilt ZipGrade-style.** The scanned sheet image itself
  now shows color-coded rings directly on the bubbles (green = correct
  answer, red = a wrong mark, orange = ambiguous), drawn by a new
  `ResultAnnotator`, replacing the separate itemized per-question text
  list. The annotated, rectified sheet is now the primary result view
  rather than a small thumbnail next to a text breakdown.
- Load Test now sorts by most recently saved (newest first) and shows the
  save date under each test name in smaller text.
- Fixed unreadable dialog button text (Cancel, etc.) in Dark/Black themes
  — `AlertDialog` buttons default to `colorPrimary` as their text color,
  same root cause as several earlier button-contrast fixes.
- Fixed the ½ cross paper layout showing noticeably bigger on-screen guide
  squares than ½ length or ¼ page — the guide size was tied to each
  layout's own letterboxed frame width, which varies a lot between a wide
  landscape tile and a narrow portrait one. Anchored to the view's own
  width instead so all three look the same size.
- Fixed Scan Next/Done button text being unreadable in Dark/Black themes.

## v1.3 — 2026-07-12

- **Fixed the version number not opening the changelog.** In
  `activity_main.xml`, the fixed footer was declared BEFORE the ScrollView.
  In a `FrameLayout`, later-declared children draw on top and get touch
  priority in overlapping regions — so the ScrollView (spanning the full
  screen) was silently swallowing taps meant for the footer underneath it,
  even in the ScrollView's own empty bottom padding where nothing is
  visibly drawn there. Reordered so the footer is declared last.
- Home screen content below the header now vertically centers in the
  available space (via `fillViewport` + a weighted, center-gravity
  container) instead of always starting right under the header.
- **Widened the live alignment guide's search range.** `AlignmentAnalyzer`
  was only checking 3 scales (0.7x-1.4x) per frame, versus `MarkerDetector`
  (the final, proven-working check on the actual captured photo) checking
  8 scales (0.5x-2.2x). That gap was the likely reason the guide rarely
  turned green even when a sheet was clearly well-aligned — the live check
  just wasn't looking at the marker's actual size. Widened to 5 scales
  across the same broader range, still cheap enough to run per-frame.
  (Noted for later: a rotationally-distinct fiducial — e.g. an L-shaped
  cluster of 3 small squares per corner, rotated differently at each
  corner — would likely help further and is worth revisiting if this isn't
  enough on its own; skipped for now since it needs a new printed design.)
- **Redesigned the scan result screen**, modeled after ZipGrade's layout:
  score shown large at the top (always visible, not part of any scrolling
  content), Scan Next/Done pinned at the bottom (always visible), with only
  the captured photo and per-question breakdown scrolling in between. Fixes
  the buttons getting pushed off-screen and effectively unreachable on
  longer tests, where the old single non-scrolling column could overflow
  past the screen height with no way to reach what got clipped off the
  bottom.
- Question numbers and choice letters on the printed sheet now stay a
  fixed, legible size regardless of how dense the bubble grid gets —
  previously they shrank along with the bubbles on a test with enough
  questions to trigger the auto-shrink-to-fit. Only the bubbles and their
  spacing shrink now; text size is tied to the tile's physical page size
  only, not content density.

## v1.2 — 2026-07-12

- **Fixed the v1.1 build failure.** `MarkerTemplateFactory.OUTER_MARGIN_FRACTION`
  and `RING_THICKNESS_FRACTION` were plain `Double` literals (no `f` suffix),
  but `SheetGenerator.drawMarkers` multiplies them with `Float` values and
  passes the result straight into `Canvas.drawRect(Float, Float, Float,
  Float, Paint)`. Kotlin doesn't implicitly narrow `Double` to `Float`, so
  that's a compile error. Fixed by declaring both constants as `Float`.
- **Paper layout overhaul.** Removed "Full page." Renamed "½ page" to
  "½ cross" (the existing top/bottom stacked layout, a crosswise fold) and
  added a new "½ length" option — two copies side by side instead, for a
  lengthwise fold. New `PaperLayout` enum replaces the old
  `sheetsPerPage: Int` throughout. Column limits per layout: up to 6 for
  ½ cross, 3 for ½ length, 3 for ¼ page. Live preview and the real PDF both
  follow whichever is selected.
- **Answer key creation moved** onto the Generate Answer Sheet Template
  screen. Tap "Create Answer Key" for a dialog choice of "Create Manually"
  or "Scan Answer Key Instead" — both used to be separate home-screen
  buttons. Whatever key is built there returns together with the template
  when you tap Use This Template.
- **Delete saved tests.** Load Test now shows a 🗑 next to each entry, with
  a confirmation step before deleting.
- Columns and questions-per-column are directly typeable, not just +/−
  steppers (carried over from the intended v1.1 change).
- Fixed unreadable "Answer Key Preview" button text in Dark/Black themes
  (needed the same explicit `backgroundTint` + `textColor` treatment as the
  other action buttons).
- Version number and developer credit moved to a fixed footer pinned to the
  bottom of the home screen, instead of scrolling away with the content
  above.
- Renamed the app from "MCQ Scanner" to "MCQ Quick Check."
- Replaced the school logo and name in the header with the new app logo
  and a larger, more prominent app name. Replaced the washed-out home
  screen background photo with a new exam-day photo.

## v1.1 — 2026-07-11

- **Low-light grading accuracy.** `BubbleGrader` now computes an adaptive
  darkness threshold per sheet via `OtsuThreshold` (finds the natural split
  between "mostly unmarked" and "the one filled choice per question" in
  that photo's own score distribution) instead of a fixed brightness value.
  A fixed threshold worked great on a backlit iPad screen but not on real
  paper under normal room lighting — a dimmer photo reads darker across the
  board, blank bubbles included, which a fixed absolute value can't account
  for. Falls back to the old fixed value if the computed threshold looks
  implausible (e.g. a uniformly dark/bright sheet with no real separation).
- **Marker redesign.** Corner markers are now a hollow "ring" (solid black
  border, white center) instead of a solid filled square — see
  `MarkerTemplateFactory`. Real-device testing showed the live alignment
  guide reacting to shaded/unshaded answer bubbles almost as readily as the
  actual corners; a solid square isn't THAT different from a cluster of
  dark circles at typical match tolerances, but a ring's two concentric
  contrast transitions are. (Barcodes were considered but aren't actually a
  good fit for a CORNER fiducial — reliably decoding one itself needs the
  photo already roughly de-skewed, which is exactly the problem the corner
  markers exist to solve in the first place.)
- Fixed the ½-page template preview being distorted — `renderPreviewBitmap`
  always assumed full-page (portrait) proportions regardless of paper size;
  a ½-page tile is actually landscape-shaped.
- Moved Generate & Share PDF onto the "Generate Answer Sheet Template"
  screen, right below Use This Template, next to the preview that shows
  exactly what it's about to produce.
- Columns and questions-per-column are now directly typeable (EditText),
  not just +/− steppers. Renamed "Rows per column" to "Questions per
  column".
- Removed the border box around the bubble grid (`drawGridBox`) — it ran
  directly through the corner guide markers.
- Added a Subject Teacher field on the template screen (typed once,
  applies to the whole printed sheet — not a per-student blank line).
  Printed as a third header-table row filling what used to be dead space
  next to the taller Score cell, closing the table border on all sides.
- Scan results now show the correct answer letter in red immediately after
  any remark that isn't a checkmark (incorrect, ambiguous, or blank) —
  nothing extra shown for correct answers.

## v1.0 — 2026-07-10

- **Detection accuracy fix.** Template matching is great at reliably finding
  the right REGION (that's its whole strength over the old brightness-only
  approach), but its reported position is quantized to the search grid —
  not precise enough for the perspective warp that follows. Small errors
  there were showing up downstream as misaligned bubble sampling, which
  read as "blank" even on bubbles that were clearly, fully shaded. Added a
  refinement step after the coarse match: Otsu thresholding + centroid
  within a small local window, which locates the marker's true center to
  sub-pixel precision. This is the main fix in this release.
- Loosened `PaperSurroundCheck.WHITE_THRESHOLD` (165 → 140) — it was likely
  too strict for normal indoor lighting, causing false *negatives* on
  genuine sheets, not just correctly rejecting random scenes.
- Removed the big connecting rectangle from the live scan guide. Only the 4
  corner squares remain — less visual clutter, and they're what you
  actually align to.
- **New "Generate Answer Sheet Template" screen.** Test name, question
  count, choice count, and paper size moved here from the home screen.
  Added new columns/rows controls (`SheetTemplate.rowsPerBlockRange` /
  `maxBlocksPerRowGroupRange` cap them to sensible values depending on
  question count and paper size) and a live preview that re-renders on
  every change via `SheetGenerator.renderPreviewBitmap` — the exact same
  drawing code the real PDF uses, so the preview can't drift from what
  actually prints. If an answer key is already set for the test, a second
  preview mode shows the sheet with the correct bubbles filled in, as a
  preview of the actual reference sheet.
- Home screen now shows a one-line summary of the configured test instead
  of the raw input fields, with a button opening the new template screen.
- Added the school facade photo as a washed-out home-screen background
  (full-opacity image behind a scrim using the theme's own window
  background color at ~93% opacity) — reads as a faint texture rather than
  competing with foreground text in any of the 3 themes.

## v0.9 — 2026-07-09

- Widened alignment tolerance 4x via a shared `GuideGeometry.TOLERANCE_SCALE`
  constant, used by both the drawn guide square size and the actual live
  matching search radius — the guide box now honestly represents how much
  positional slop is actually accepted, instead of being purely decorative.
- Added `PaperSurroundCheck`: a second, independent gate alongside shape
  matching. A match is only accepted if the area around it also looks like
  plain light paper (high average brightness), not just roughly
  marker-shaped. Template matching alone couldn't tell "the right shape
  sitting in the middle of a blank sheet" apart from "the right shape as
  part of some unrelated dark, textured scene" (grass, walls, tables,
  random objects) — this does.
- Fixed unreadable button text in Dark/Black themes on several home-screen
  buttons (Create Answer Key, Scan Answer Key, Generate & Share PDF,
  Save/Load Test). Root cause: these are plain `Button` widgets with an
  explicit `backgroundTint`, which — unlike the Material default button
  style — doesn't automatically pick a contrasting text color to match.
- Suppressed Android's system autofill icon appearing on action buttons
  (via `importantForAutofill="noExcludeDescendants"` on each screen's root
  view) — these are action buttons, not form fields, so the icon was just
  clutter, and looked like a green marker next to the button text.
- Restricted native libraries to `arm64-v8a` and `armeabi-v7a` only (real
  phone CPU architectures) via `ndk { abiFilters }`, dropping the
  emulator-only `x86`/`x86_64` builds OpenCV's AAR includes by default.
  Should noticeably shrink the APK size increase from v0.8's OpenCV upgrade.

## v0.8 — 2026-07-09

- Replaced marker detection's approach entirely: instead of a hand-rolled
  darkness-centroid + bounding-box heuristic, it now uses real OpenCV
  template matching (`Imgproc.matchTemplate`, normalized cross-correlation,
  searched at several scales per corner) against a synthetic reference
  image of the marker. This is the same core technique the open-source
  OMRChecker project uses (see its `CropOnMarkers.py`) — added via OpenCV's
  official Maven Central Android package (`org.opencv:opencv:4.9.0`, no
  native SDK download needed). Template matching correlates actual shape,
  not just brightness, which is why the darkness-only approach kept
  false-matching dark backgrounds (grass, walls, tables, random objects)
  and missing real markers under uneven lighting — no amount of threshold
  tuning was going to fix that, since it was never checking shape at all.
- Fixed the bigger issue with real sheets: found and restored a step that
  had been dropped in an earlier rewrite — cropping the captured photo to
  the on-screen guide region (with padding) before running detection.
  Without it, detection was searching a fixed-size window of the corners of
  the WHOLE photo, but the guide only covers part of the frame (there's
  real background around it), so on a normal handheld photo the actual
  markers often sat outside where detection was even looking. This was the
  main reason real printed sheets were failing to scan and grade at all.
- The live per-corner green-highlight hint now uses the same template
  matching, searching a small window at each corner's exact expected
  position (shared geometry with the guide overlay) instead of scanning a
  broad quadrant for anything dark.

## v0.7 — 2026-07-08

- Removed auto-capture. Two attempts at tuning the live "is this aligned"
  check (guided by screen recordings) still misfired in real testing —
  triggering on non-sheet objects, or firing mid-adjustment while the
  sheet was still being positioned. Rather than keep guessing at
  thresholds that can't be verified without a real device, capture is
  manual again (tap the button), which was already reliable before
  auto-capture was added.
- The corner guide squares still turn green live as a visual aid, checking
  each corner for an actual small solid blob rather than just "is it dark
  here" — so you can see when it looks lined up before tapping Capture
  yourself.
- Fixed one more invisible-text case in Dark/Black themes: the "Result"
  title on the scan screen.

## v0.6 — 2026-07-08

- Added real-time alignment detection while scanning. A new CameraX
  ImageAnalysis pipeline (AlignmentAnalyzer) samples live camera frames at
  the same 4 corner targets as the on-screen guide (shared via
  GuideGeometry, so they can never drift out of sync) and checks each spot
  for marker-like darkness — deliberately loose ("just enough" overlap
  counts), since this is only a live hint. Each corner square turns green
  independently as it looks aligned.
- Capture now happens automatically once all 4 corners have stayed aligned
  for ~4 consecutive checks (~600ms), via a debounce counter, so a quick
  pass-through doesn't trigger a blurry shot. Manual capture (tap the
  button) still works at any time.
- Added vibration feedback (View.performHapticFeedback) on both
  auto-capture and manual capture — no extra permission needed.
- Loosened MarkerDetector's validation thresholds (fill ratio, blob size
  range, aspect tolerance, search window) — the v0.4 values were tuned by
  reasoning about the math rather than against real photos, and turned out
  too strict for normal paper texture, print quality, and lighting. Still
  keeps the core blob-tightness and shape checks that reject non-sheet
  photos, just with realistic tolerance.
- Removed the underscore blanks from the printed sheet's Name / Date /
  Gr. & Section / Score cells now that they have real table borders (from
  v0.5) — the border is enough of a "write here" indicator on its own.

## v0.5 — 2026-07-08

- Fixed the camera not capturing what the live preview showed. CameraX's
  Preview and ImageCapture use cases default to different fields of view
  (Preview shows a cropped/zoomed FILL_CENTER view; ImageCapture defaults to
  the full uncropped sensor frame) — so a sheet that filled the on-screen
  guide frame perfectly came out tiny and off-center in the actual photo,
  with the corner markers nowhere near where MarkerDetector searches for
  them. Fixed by binding both use cases through a shared CameraX ViewPort,
  making capture WYSIWYG with the preview. This was the bug blocking
  scanning entirely.
- Fixed bubbles overlapping on ½-page sheets. Bubble radius used to be
  derived from page width alone, but a ½-page sheet's row pitch shrinks
  (two sheets stacked into one page's height) while its width doesn't —
  so bubbles stayed full-size while rows packed tighter, and adjacent rows
  started touching. Bubble size is now derived from the tighter of the
  actual row pitch (height-based) and column spacing (width-based) for
  whatever paper size is being rendered, so it's correct for full, ½, and
  ¼ page alike.
- Redesigned the details header into a real bordered table instead of
  underscore blanks: Name | Date on one row, Gr. & Section | Score on the
  next, with the Score cell taller than the rest for writing room. All
  four cells are vertically centered, left-aligned.
- Moved the test title below the details table (previously at the very
  top), with the top corner markers repositioned to flank the title line
  instead of sitting at the page's top edge — freed up by the table no
  longer needing that space.
- Fixed more invisible text in Dark/Black themes: the school name and
  version number on the home screen, and the title on the changelog
  screen, all had the same colorPrimary-as-text bug fixed on the
  answer-key screen in v0.4.

## v0.4 — 2026-07-08

- Fixed the scan overlay guide not matching a ½-page sheet's actual shape —
  it was always drawn as a fixed inset square, regardless of paper size. It
  now follows each paper size's real proportions (full, ½, and ¼ page each
  have their own aspect ratio).
- Much stricter sheet detection: MarkerDetector now validates that each
  corner marker is actually a small, solid, mostly-square blob (tight
  bounding box + fill ratio check), not just "the darkest spot in this
  corner" — the old version would happily accept a photo of a keyboard as a
  valid sheet. It also checks the 4 detected corners form a plausible
  sheet-shaped rectangle before accepting them.
- Added BubbleGrader.requireConfident() as a final safety net: if too many
  graded answers come out ambiguous or blank, the app reports that instead
  of a misleading score.
- Fixed invisible text in the Dark and Black themes on the answer-key
  screen — several elements were tinted with colorPrimary, which in the
  Black theme is a near-black surface color meant for backgrounds, not
  text. Text now uses the theme's own text color; interactive accents
  (Save button, selected bubbles) use the gold accent color, which stays
  readable across all 3 themes.

## v0.3 — 2026-07-07

- Fixed a crash when tapping "Create Answer Key Here" — SheetTemplate used a
  `by lazy` cached field that doesn't survive Android's real serialization of
  Intent extras (which happens even within the same app). Switched it to a
  plain recomputed property. This should also fix Save Test / Load Test,
  since they depend on the same object being constructed correctly.
- Added a box around the Name/Grade/Section/Date/Score details area, and a
  separate box around the whole bubble grid, on the printed sheet.
- Removed the "Down each column" / "Across each row" numbering option —
  always numbers down each column now.
- Scanning now happens inside the app with a live camera preview (CameraX)
  instead of handing off to the phone's system camera app, with on-screen
  guide squares for lining up the sheet's corner markers before capture.
- Scan results now appear in a panel right in the scan screen, with a "Scan
  Next" button to keep going without leaving the screen.

## v0.2 — 2026-07-07

- Redesigned the printed answer sheet: bigger, bolder bubbles and text in a
  formal sans-serif style, condensed spacing instead of stretching to fill
  the page.
- Every block of questions now uses the same row spacing as item 1 and item
  11, even a partial last block (e.g. 21-25) — no more oddly spread-out rows.
- If the sheet has extra vertical space, it's centered on the page instead of
  being stretched to fill it.
- Added Grade and Section fields below Name, and Date / Score fields on the
  right.
- Removed the typed answer-key field. Added "Create Answer Key Here" — a
  tap-the-bubble screen generated from your question count, right above
  "Scan Answer Key Instead".
- Added customizable points per item on the new answer-key screen (defaults
  to 1 pt each, with an "apply to all" shortcut).
- Scores are now reported in points earned / points possible, not just items
  correct.

## v0.1 — 2026-07-07

- Redesigned sheet layout: compact ZipGrade-style blocks of 10 questions
  stacked in row-groups, no student-ID bubble grid, to save paper.
- Added per-test paper-size option: Full page, ½ page, or ¼ page. Half/quarter
  tile multiple independent copies (each with their own corner markers) onto
  one physical page — cut apart before handing out.
- Added "Scan Answer Key" — capture a photo of a filled-in key sheet instead
  of typing the answer key by hand. Flags blanks/ambiguous bubbles so you can
  double-check before using the detected key.
- Fixed camera not opening (blank screen on tapping Capture) — Android 11+
  requires a package-visibility `<queries>` declaration for the camera
  intent to resolve; without it the intent silently did nothing.
- Question numbering is generated dynamically from however many questions
  the test has, rather than a fixed count.

## v0.0 — 2026-07-07

- Fresh restart of MCQ Scanner as a true OMR (bubble-sheet) app.
- Ported OMR architecture from the earlier prototype: `SheetTemplate`,
  `SheetGenerator`, `MarkerDetector`, `PerspectiveWarp`, `BubbleGrader`.
- Added Tarlac National High School branding: logo on the home screen and
  as the launcher icon, maroon/gold accent theme (Light/Dark/Black variants).
- Added in-app changelog, accessible by tapping the version number.
- Developed by Sir_JPagdi.

### Known open items
- Completely untested on a real device — first real test will likely
  require tuning `MarkerDetector.DARK_THRESHOLD` / `MIN_WEIGHT` /
  `SEARCH_WINDOW` and `BubbleGrader.FILLED_THRESHOLD` / `AMBIGUOUS_GAP`.
- Single-page sheets only, no multi-page support yet.
- No student ID/name bubbling yet.
