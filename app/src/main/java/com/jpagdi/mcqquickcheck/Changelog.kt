package com.jpagdi.mcqquickcheck

data class ChangelogEntry(val version: String, val date: String, val notes: List<String>)

/**
 * Curated "what's new" for the About page — genuine new features/capabilities
 * only, from the last 3 MAJOR versions (e.g. 3.8/3.7/3.6, not every dot
 * release), in the user's own words rather than a copy of the full
 * changelog. Deliberately hand-picked and kept separate from
 * Changelog.entries below: that list mixes real features with bug fixes,
 * tuning passes, and internal/debug notes (useful for a developer tracking
 * history, but not what a teacher wants to read as "what's new"). A simple
 * keyword filter over the full changelog couldn't reliably tell "added X"
 * from "fixed X" from "changed how X is measured internally," so this is
 * maintained by hand — update it when a real new capability ships, not
 * automatically alongside every changelog entry.
 *
 * v3.8 rule change: the WINDOW is still 3 major versions, but a dot
 * release's own genuine "New:" item now gets its OWN line under its
 * parent major version's comment marker, instead of being silently
 * folded into that major version's single existing line. (Previously —
 * see v3.6's dot releases below for the old pattern — a dot release's
 * new item had no line of its own at all.) A feature-sized addition
 * should also generally just bump to a new major version instead of
 * shipping as a dot release, per this same conversation.
 */
object WhatsNew {
    val items = listOf(
        // v1.0.0 reset: this list is a ONE-TIME hand-picked seed covering
        // the app's core scanning/grading capability as of the reset —
        // not a "last 3 major versions" window, since there's no prior
        // major-version history left to roll from right after a reset.
        // Deliberately limited to what the scanner actually DOES (sheet
        // generation, capture, grading, analysis) — organizational/admin
        // features (folders, sorting, rename, backup) and UI redesign
        // notes were left out on purpose. Starting at whatever version
        // ships next, this goes back to the normal rolling last-3-major-
        // versions rule described in this object's own doc comment above.
        "Generate answer sheets for your choice of paper size — Letter, A4, or Folio — with four sheets automatically tiled onto each physical page to save paper.",
        "Build an answer key by tapping bubbles directly in the app, or by scanning an already-filled key sheet instead.",
        "Scan and auto-grade filled answer sheets using your phone's camera — no separate scanner needed.",
        "Capture scans using your phone's volume buttons, with an optional shutter sound and a \"Faster scanning\" mode (Settings) for classroom-sized stacks.",
        "Handles up to 100 questions per test, with automatic bubble-size calibration for reliable reads.",
        "Flags ambiguous or unclear marks instead of silently guessing — review and resolve them by hand.",
        "Item Analysis after grading — difficulty (P) and discrimination (D) index per question, plus whole-class test-result analysis (number/percent correct, Mastery Level, Least Learned rank), exportable to .xlsx.",
        // v1.1.0: last entry removed per explicit instruction ("Built-in
        // test sheets to stress-test your printer/scanner setup..." — a
        // dev-only feature anyway), replaced with these 5.
        "Bulk-move or bulk-delete scanned sheets — long-press a row's send or trash icon on the Scanned Sheets screen, then select/unselect all with one tap.",
        "Optional Parent/Guardian signature line on printed answer sheets (Settings toggle).",
        "New 1200px scanned-sheet storage resolution option, alongside the existing 1600/1800/2000px choices.",
        "Backup/Restore now handles much larger datasets without crashing, and Restore Data offers to clear existing data first to avoid import conflicts.",
        "Save List — saves a folder's entire scanned-sheets list, including rows off-screen, as one full-resolution image.",
        // v1.1.1: appended only, per explicit instruction — nothing above removed.
        "Item Analysis: group items into competencies and see Most/Least Learned Competencies, with a Mastery Level and rank per competency.",
        "Duplicate a test from the home screen — long-press it, then Duplicate.",
        // v1.2.0 (classes): this edition has no plans or quotas, see the v1.0.0 (Project A.S.S.E.S.S.) entry.
        "Classes: enroll class rosters, assign them to an exam, and each scan's score is saved to the right learner — with an optional ID row on answer sheets. Class score export to Excel is on the Full Year plan.",
        "Learners: search any learner (Classes > Learners) and see every test saved under their ID. Press and hold a test or a class to archive it without deleting anything.",
        // v1.2.1: genuine new capability, own line per this object's rule.
        "The home screen now tells you when a newer version is ready — checked on your first open each day — so you no longer have to open About to find out.",
        // Project A.S.S.E.S.S. v1.0.0: items for this edition's real additions.
        "Test sets: print 2–4 sets per test with a \"Set:\" bubble row; the scanner reads each student's set and grades with that set's key.",
        "Set answer keys can be typed or scanned, and competencies can be placed on different item numbers per set; item analysis can show one set or all sets pooled.",
        "Everything is included — no subscription, scan limit or license check — and routine use works offline."
    )
}


/**
 * In-app mirror of CHANGELOG.md. Keep both in sync when bumping versionName.
 * Newest entry first.
 */
object Changelog {
    /**
     * v1.0.0 reset. Starts fresh here — this is now the ONLY list that
     * grows going forward. Everything before the reset lives in
     * legacyEntries below, frozen, never appended to again. See the doc
     * comment on legacyEntries for why this split exists instead of one
     * continuously-growing list.
     */
    val entries = listOf(
        ChangelogEntry(
            version = "1.0.0 (Project A.S.S.E.S.S.)",
            date = "2026-11",
            notes = listOf(
                "New: Project A.S.S.E.S.S., a school edition built from MCQ Quick Check v1.2.5. Every feature is available with no subscription, scan limit or license check, and routine preparing, scanning, scoring and analysis work without internet.",
                "Changed: the only online feature is the optional update check against the project's GitHub releases. It only reads the latest release; no learner or test data is sent.",
                "Changed: Android's automatic cloud backup is off so learner records stay on the device. Use Backup Data (in the app) to keep a copy.",
                "Includes test sets, per-set answer keys and competencies, learners and archiving, and tablet landscape (scanning stays portrait).",
                "The entries below are the earlier MCQ Quick Check history this edition is built from."
            )
        ),
        ChangelogEntry(
            version = "1.2.5",
            date = "2026-11-10",
            notes = listOf(
                "New: Learners. Open Classes, then Learners: everyone enrolled in your classes in one searchable list (by name or ID). Tap a learner to see every test saved under their ID, newest first, with score and percent, their average, best and lowest, a small trend line, and the saved sheet of each test one tap away. It only reads results the app already keeps, so nothing changes in how you scan, and it stays available if a plan ends.",
                "New: archive tests and classes. Press and hold a test on the main screen (Archive) or a class on the Classes screen to hide it without deleting anything: its scans, scores and learners are kept, still count in the Learners database, and come back from \"Archived tests\" / \"Archived classes\" with Restore (or Delete permanently from there). Archived classes are left out of the exam class picker, unless an exam already uses one, and exams already using an archived class keep working.",
                "New: test sets. In a test's template screen, \"Test sets\" lets you print 2, 3 or 4 sets (X Y, X Y Z or W X Y Z). The sheet gets a \"Set:\" bubble row under the title, in the same bubbles as the answers. Set X (W with four sets) uses the test's own answer key; add a key for each other set with its \"Set key\" card. Students shade their set and the scanner grades the sheet with that set's key. If the set bubble is blank, double-marked or has no key yet, the app asks \"Which set is this sheet?\" instead of guessing. The set is kept with each saved scan, and set keys follow the test through rename, duplicate, delete and Backup / Restore. Points per item are shared by all sets.",
                "New: set answer keys can be scanned. Each \"Set key\" card offers Create Manually or Scan Answer Key Instead, same as the main key.",
                "New: competencies per set. In Competencies, a test with sets gets an \"Item numbers for\" line: pick a set and place the same competencies on that set's item numbers (it starts as a copy of the first set). Competency names are shared.",
                "New: item analysis by set. A test with sets gets a \"Showing\" line under the subtitle: pick one set on its own (using that set's competency item numbers), or all sets pooled (each sheet counts with its own set's item numbers).",
                "New: tablet landscape. Screens now rotate on tablets without losing what you were doing, and on wide screens the content stays in a centered column instead of stretching edge to edge. Scanning stays portrait-only. The side menu is capped at 360 dp wide."
            )
        ),
        ChangelogEntry(
            version = "1.2.4",
            date = "2026-11-06",
            notes = listOf(
                "Improved: the ID reader has a better \"eye\". Besides the coarse pixel grid it used before, it now describes each digit by the direction of its pen strokes (HOG), which tells apart shapes the grid blurred together (a 6 against a G, a 4 against a 9). On learners the model had never seen it reads about 91% of digits right, against 88% for the old description (it fixed 39 digits the old one got wrong and broke 18). In a simulation of a 40-learner class, 95% of sheets link to the right learner on their own with no wrong links, and about 1 in 20 needs a manual check (about 1 in 10 before). Older model files still load.",
                "Improved: the model is retrained on about 1,100 real handwritten digits from two classes' scanned sheets, up from 120. A model trained on one class alone already read a different class's sheets at 93% of digits, so the reader carries over between classes; the second class adds variety. A sample that had been labelled wrongly (a \"b\"-style 0 filed as a 6) is corrected. The digit images stay in tools/id_model/real_samples/ for future retraining. The model file is about 700 KB (it was 140 KB) because it now reads 324 numbers per digit.",
                "Fixed: sheets of learners who are NOT enrolled in the class could link to a look-alike enrolled learner (same school prefix, different last digits): in a test, 4 of 45 stray sheets did. The automatic link now needs the read to agree with the roster ID almost digit for digit (the old rule tolerated several clearly-different digits). Genuine sheets that link on their own go from 92% to 85% in the same test, so a few more ask for a one-tap confirmation; strays that link by mistake drop from 9% to 2%. A stray whose ID differs from an enrolled learner's by a single clearly-written digit can still slip through, and a wrong link can be removed from the class sheet."
            )
        ),
        ChangelogEntry(
            version = "1.2.3",
            date = "2026-10-16",
            notes = listOf(
                "Fixed: the ID reader couldn't read real student handwriting — a sheet with all 12 digits written came back as twelve \"?\". The old cleanup shrank each box and threw away every ink blob touching the edge, on the theory that those were the printed box lines; but real digits are big, so they touched the edge too (and any digit touching or crossing a printed line fused with it), and were discarded as \"lines\". The reader now looks a little beyond each box, removes the printed lines themselves, and keeps the digit — large writing, small writing, writing that overlaps the box lines, and a few pixels of misalignment are all handled. The scaled-to-fit digit features are unchanged, so the trained model is unchanged too.",
                "Fixed: far too many scans landed in \"Needs ID check\" (about half of a class). The digit reader gets roughly 5 of every 6 handwritten digits right, and LRNs share most of their digits, so the old rule — link only when the best learner beats the runner-up by a wide margin — sent most scans to a manual tap. Retuned: the margin is lower (4 -> 2), and a learner who already has a score on this exam is slightly less likely to be the owner of the next sheet. In simulation this cuts the manual-tap pile from roughly 35-55% to roughly 10-20% of scans, at a cost of about one wrongly linked sheet in 200-400. A sheet whose best match already has a score still asks before replacing it, and a wrong score can be removed from the class sheet.",
                "New: \"RE-CHECK IDS\" on the class sheet, above the unmatched scans. It re-reads every unmatched sheet from its saved image and links the ones that now match, in several passes (each newly linked learner helps settle the next sheet, so the last few resolve by elimination). It also runs quietly after you confirm a sheet by hand.",
                "New: ID reading now writes to the same diagnostics log as the bubble scanner, so Send Diagnostic Report carries it. Per scan it records the confidence of every box, why a box was blank, the matcher's gate values and its decision; for scans linked automatically or confirmed by you, it also lists which boxes the reader got wrong as single \"true>read\" digit pairs (e.g. 6>0). It never records a learner ID, a name, or a digit string, so a report can't identify anyone.",
                "New: manual matching right after the scan. When a sheet's ID can't be matched confidently, a \"Whose sheet is this?\" dialog opens at once: the handwritten ID boxes cut from the sheet sit at the top, and under them — lined up digit for digit — what the reader saw and each candidate's ID, with every digit that disagrees shown in red, so a one-digit difference between near-identical LRNs is easy to spot. Tap the right learner, or \"Someone else…\" to search the roster by name or ID, or \"Decide later\" to leave it under Needs ID check. The same dialog opens from the unmatched cards on the class sheet, and the candidate list is now up to 5 learners.",
                "New: a light-grey \"write inside here\" zone is now printed inside every ID box of newly generated sheets, so learners keep their digits off the box lines (a digit touching a line was the reader's hardest case). Sheets you have already printed are unchanged and still read.",
                "Hardened: the ID reader straightens handwriting slant before reading (on digits sheared up to ~25 degrees the model went from 96% to 99.7% right; upright digits unchanged) and is stricter about which ink belongs to a box, so a speck of printed line can no longer stretch a digit into something else. Every unmatched scan now keeps its ID read with it, so re-checking after you confirm a sheet is instant and no longer needs the saved image; the RE-CHECK IDS button still re-reads saved images with the current reader.",
                "New: the \"Whose sheet is this?\" dialog — the handwritten ID next to the suggested learners — now opens right after a scan whose ID couldn't be matched confidently, so you can assign the sheet to a learner on the scan page itself. Cancel leaves it under Needs ID check on the class sheet.",
                "Changed: suggested matches only offer learners who don't have a score on this exam yet. Learners who already have one are listed last under \"Other learners\", tagged \"Already scored\".",
                "Improved: the ID digit model is retrained on 120 real handwritten digits from ten learners' sheets. On three sheets it had never seen it read 78% of digits right, against 56% for the previous model. The labelled digit images are kept in tools/id_model/real_samples/ so every retraining includes them.",
                "Note: reading a digit and recognising it are separate steps. On the test sheet this brings 12 unreadable boxes to 10 of 12 correct; oddly written digits (a filled-in 6, a \"b\"-style 6) can still be misread by the generic model. The roster matcher absorbs a few wrong digits, and tools/id_model/train_id_model.py --samples can teach the model your learners' own handwriting (crops are now cut WITH their margin — see that script's header)."
            )
        ),
        ChangelogEntry(
            version = "1.2.1",
            date = "2026-10-06",
            notes = listOf(
                "New: the app now checks for updates on the first open of each calendar day (device local time) instead of a rolling 24 hours, and tells you on the home screen when a newer version is ready: Update Now (opens About, which handles the download and install), Skip This Version, or Later. At most one prompt per day. A check that fails — offline, rate-limited — no longer uses up the day; the next launch retries after 30 minutes.",
            )
        ),
        ChangelogEntry(
            version = "1.2.0",
            date = "2026-09-27",
            notes = listOf(
                "Changed: the Most/Least Learned Competencies tables in the Item Analysis preview and exported .xlsx now have one row per competency (not one per item). The Item No. cell lists that competency's items as ranges, e.g. \"1-15, 18, 21-24\", followed by the competency and its rank.",
                "New: Classes — enroll your class rosters (ID, name and gender) by typing learners in or importing a CSV/XLSX file, assign classes to an exam, and each scan's score is saved to the right learner automatically. Every class has its own score sheet per exam that you can sort by name, score, ID or gender; exporting it to Excel is a Full Year plan feature. Optional: switch it on under Settings > Learner identification; your existing classes stay viewable if your plan ends.",
                "New: ID row on answer sheets — with Learner identification on, newly generated sheets get one extra header row where each learner writes their ID, one digit per box (4 to 12 digits, set with a stepper in Settings, default 6; a DepEd LRN is 12). The Score box grows beside it and is left blank for writing the score. Sheets you already printed are unchanged. The scanner matches the handwriting against your enrolled learners and asks you to confirm anything it isn't sure of.",
                "Backup and Restore now include your classes, learners and per-exam scores."
            )
        ),
        ChangelogEntry(
            version = "1.1.1",
            date = "2026-09-19",
            notes = listOf(
                "New: competency grouping for Item Analysis. \"Set Up Competencies\" (Item Analysis screen) lets you name competencies and check off which items belong to each. Adds a Competency column to the preview and exported .xlsx, plus a whole-class Mastery Level and a Most-Learned-first rank per competency (separate from the existing per-item Least Learned rank, which is unchanged).",
                "New: exported Item Analysis .xlsx gains two more tables below the main one (2-row gap, columns B-D and F-H) — \"Most Learned Competencies\" and \"Least Learned Competencies\", one row per item, matching the standard DepEd paper format so they can be copied straight into it. Only appear when at least one competency has been set up for that test.",
                "The exported .xlsx's sort/filter dropdowns (AutoFilter) now only span the main table's own rows, so sorting or filtering it can never shift or hide the two competency summary tables below.",
                "Competency names wrap within their cell instead of overflowing or getting clipped, both in the main table and the two summary tables.",
                "New: Duplicate — long-press a test on the home screen, then Duplicate alongside Rename/Cancel. Copies the template, answer key, points, and any competencies you've set up, under an auto-generated \"(Copy)\" name; scanned sheets and subfolders are never copied."
            )
        ),
        ChangelogEntry(
            version = "1.1.0",
            date = "2026-09-12",
            notes = listOf(
                "Renamed the app's package ID from com.jpagdi.mcqscanner to com.jpagdi.mcqquickcheck. BREAKING for existing installs: Android treats a package ID change as an entirely different app, so this build cannot be installed over the old one. Use Backup Data to export everything BEFORE switching, install this build fresh (uninstalling the old one first), then Restore Data to bring your saved tests, keys, and settings back.",
                "Fixed: Backup Data, Export Scans, and other file exports could crash with an out-of-memory error on larger datasets — Download now streams the finished file to disk instead of holding the whole thing in memory first.",
                "Fixed: Restore Data could crash the same way on larger backup files — now reads and applies a backup in a streaming pass instead of parsing the whole file into memory up front.",
                "Restore Data now asks to clear existing saved tests/scans before importing (instead of merging onto them), to avoid the conflicts that could previously cause a failed or corrupted restore.",
                "New: bulk-move and bulk-delete for scanned sheets — long-press a row's send or trash icon on the Scanned Sheets screen to select several at once, with a select/unselect-all toggle for either action.",
                "New: optional Parent/Guardian signature line on printed answer sheets, just below the scannable area — off by default, toggle it on in Settings. Doesn't appear in the on-screen preview, only on the actual generated sheet.",
                "New: 1200px scanned-sheet storage resolution option, alongside the existing 1600/1800/2000px choices.",
                "New: Save List button on the Scanned Sheets screen — saves that folder's entire list, including rows off-screen, as one full-resolution image file.",
                "Exported scanned-sheet zip filenames now include the folder name, so separately exported folders are easier to tell apart in Downloads.",
                "Item Analysis's exported .xlsx now includes whole-class test-result columns after D Recommendation: Number of Correct Response, % of Correct Response, Mastery Level (Mastered/Nearly Mastered/Below Mastery), and Least Learned (Rank) — ranking every item from 1 (least mastered) to n (most mastered)."
            )
        ),
        ChangelogEntry(
            version = "1.0.0",
            date = "2026-08-08",
            notes = listOf(
                "First stable release. Redesigned interface throughout — rounded cards, clearer icons, more consistent look across every screen. Added swipe left/right haptic feedback while reviewing scanned sheets. Fixed: Backup Data was silently skipping the storage-resolution and font-size settings — both are now included. Security: release builds are now signed with a real, private key instead of the same debug key used for local development (which was committed to this public repo) — this changes the app's signature, so anyone with an existing install will need to uninstall once before this update installs. Also enabled code shrinking/obfuscation on release builds. Built on months of iteration, testing, and fixes — tap \"Show more\" below to see that full history."
            )
        )
    )

    /**
     * Everything from v0.0 through v4.0.2 — the app's entire development
     * history before the v1.0.0 reset. FROZEN as of the reset: never
     * appended to again, so there's no need to ever compare a version
     * string against this list to decide "is this before or after the
     * reset" — entries above are new, entries here are old, and that's
     * decided once, structurally, rather than re-derived from version
     * numbers every time the changelog renders.
     */
    val legacyEntries = listOf(
        ChangelogEntry(
            version = "4.0.2",
            date = "2026-08-08",
            notes = listOf(
                "Fixed: v4.0.1 failed to build — ItemAnalysisActivity.kt used Toast without importing it (missed because text-based checks don't catch missing imports; this needs an actual compile to catch). Added the missing import."
            )
        ),
        ChangelogEntry(
            version = "4.0.1",
            date = "2026-08-08",
            notes = listOf(
                "Fixed: changing the font-size setting only updated the Settings screen itself live — every other already-open screen only picked up the new size the next time it was actually opened. Every screen now also re-applies the current font size when resumed (e.g. backing out of Settings), so returning to any screen after a font-size change reflects it immediately.",
                "Fixed: Item Analysis and Test Detail's folder-swipe row, table, and action buttons visibly jumped position depending on whether the current folder/page had data yet — the summary/subtitle text areas above them fully collapsed or shrank when empty, since they were sized purely by their own (much shorter) placeholder text. Those areas now reserve enough height for their longest realistic content, so nothing shifts based on data being present or not.",
                "Fixed: Item Analysis's Export button used to disappear entirely (along with the whole table area) whenever there wasn't enough scan data yet. It's now always visible; tapping it with no data shows a prompt instead of running an export."
            )
        ),
        ChangelogEntry(
            version = "4.0.0",
            date = "2026-08-07",
            notes = listOf(
                "New: system-wide font-size preference (Settings > FONT SIZE) — Very Small / Small / Medium (default) / Large. Rescales every screen's text and every rounded-card's vertical padding instantly, with no reopen/restart needed, by capturing each view's original XML size once and recalculating from that base every time the setting changes (so switching sizes repeatedly never compounds). Deliberately scoped to this app's own UI only — never touches the printed or previewed answer sheets, which stay governed entirely by SheetTemplate's fixed geometry.",
                "Fixed: the answer sheet preview (Generate/Edit Template screen) had regressed to the system font instead of Montserrat. Root cause: the font only ever got loaded from MainActivity's startup code, and the preview's own render function never loaded it itself — so if the preview screen was reached without MainActivity having already run in that session, it silently fell back to the system font with no error. The preview now ensures its own font is loaded before drawing. Printed/downloaded PDFs were never affected by this."
            )
        ),
        ChangelogEntry(
            version = "3.9.6",
            date = "2026-08-06",
            notes = listOf(
                "New: the \"Gr. & Section:\" label on all answer sheets, all paper sizes, is now simply \"Section:\" — reads naturally for university users, not just K-12.",
                "Fixed: table label text (\"Name:\", \"Date:\", \"Section:\", \"Score:\") and a capital \"Q\" in the test title could render wrong on the downloaded PDF only — letters with no gap between them, or Q as a solid filled disc — even though the in-app preview always looked correct. Three earlier general-purpose attempts at this (disabling font features, removing double-bolding, per-character positioning, even rendering as a bitmap image instead of PDF text) never fixed it and were reverted. This is a narrower, hand-verified fix instead: the exact letter-pairs confirmed to collapse in each of the 4 fixed labels get a small manual extra gap in the PDF only, and the title's Q is drawn as the correctly-rendering \"O\" shape with its tail added back on top."
            )
        ),
        ChangelogEntry(
            version = "3.9.5",
            date = "2026-08-06",
            notes = listOf(
                "Fixed: student details table was off-center horizontally on generated answer sheets (GRID_LEFT/GRID_RIGHT margins were 5%/3% instead of equal — the bubble grid always re-centered its own content so this never showed there, but the table spans the full width directly and inherited the asymmetry).",
                "Fixed: table label text and the test title could render with overlapping/crowded letters on the downloaded PDF (looked fine in the in-app preview) — PdfDocument doesn't always use the custom Montserrat font's real glyph widths when spacing a drawText() call; now each character is positioned manually using measured widths so PDF spacing can't drift from what's shown in the preview."
            )
        ),
        ChangelogEntry(
            version = "3.9.4",
            date = "2026-08-06",
            notes = listOf(
                "New: the student details box (Name/Date/Gr. & Section/Score) on printed and previewed answer sheets now has rounded corners instead of square ones."
            )
        ),
        ChangelogEntry(
            version = "3.9.3",
            date = "2026-08-06",
            notes = listOf(
                "Fixed: after successfully installing an update, the About screen kept showing \"Update Available\" for the version just installed, even after fully closing and reopening the app. The cached update from before the install was only cleared by a fresh network check (throttled to once a day) or an explicit dismiss — nothing checked it against what's actually installed now. Now checks the real installed version first and clears the stale cache immediately if it's already caught up.",
                "Removed push.sh from the repo root (no longer needed)."
            )
        ),
        ChangelogEntry(
            version = "3.9.2",
            date = "2026-08-06",
            notes = listOf(
                "Fixed: a downloaded update APK stayed in the app's private storage indefinitely after being installed, with no cleanup. The app now clears out any leftover update APK on every launch — by that point it's either already been installed (dead weight) or the install was canceled (a fresh download next time is simpler than trying to resume a half-finished one). Skips any download still genuinely in progress, so this can't race an update that's mid-download or was just handed to the installer."
            )
        ),
        ChangelogEntry(
            version = "3.9.1",
            date = "2026-08-06",
            notes = listOf(
                "Fixed: after Download was tapped on a found update, the app was supposed to automatically prompt to install once the APK finished downloading — confirmed on a real device that this silently never happened if the app was backgrounded while the download was still running (e.g. switching over to check the system Downloads app). The completion listener was tied to the app's own process and didn't survive it being killed in the background; the system's download notification still appeared (that part is OS-level, independent of this app), but tapping it there just reopened this app instead of installing anything. Replaced with a manifest-declared receiver that survives process death, so the install prompt now reliably appears regardless of whether the app was in the foreground when the download finished."
            )
        ),
        ChangelogEntry(
            version = "3.9.0",
            date = "2026-08-06",
            notes = listOf(
                "New: Update Checker. The About screen's Check for Updates button now actually checks GitHub for newer releases (was a \"Coming soon!\" placeholder). The app also checks silently in the background on launch, at most once every 24 hours — never interrupts anything, and any update it finds is simply waiting on the About screen next time it's opened. A manual tap always checks fresh, regardless of that 24h window. If a newer version is found, Download fetches the APK and hands it to the system installer; \"Skip This Version\" stops the nag for that specific release without silencing genuinely newer ones later. Re-adds the INTERNET permission (removed in v3.1.4) — this check is read-only and sends no scan data, sheets, or personal information anywhere; the About screen's description was updated to say so plainly.",
                "Fixed: the \"Delete Sheet\" / \"Save & Proceed\" buttons shown after an unclear scan still used plain unstyled buttons, missed by the earlier app-wide rounded-card button redesign. Converted to match, and changed from side-by-side (with \"Delete Sheet\" forced onto two lines just to match its neighbor's height) to each being its own full-width row — removes that forced line break entirely. The delete icon now tracks the same contrast-matched color as its label text instead of staying a fixed color regardless of the chosen accent."
            )
        ),
        ChangelogEntry(
            version = "3.8.1",
            date = "2026-08-05",
            notes = listOf(
                "Fixed: Scanned Sheets' swipe left/right on the folder name was broken — the label's own long-press-to-rename listener was claiming touch before the row's swipe gesture ever saw it. Long-press-to-rename still works exactly as before.",
                "Changed: Item Analysis — relocated the folder nav row for thumb reach, from directly under the title to below the analysis result and above the Export button. Title stays fixed at the top. Applied the same swipe fix defensively.",
                "Fixed: \"main\" showing lowercase instead of \"Main\" in Item Analysis's folder label, subtitle, and export dialog."
            )
        ),
        ChangelogEntry(
            version = "3.8.0",
            date = "2026-08-04",
            notes = listOf(
                "Changed: Auto-save scanned sheets is now ON by default. Updated its description to say \"Scanned Sheets\" instead of the old \"Load Test\" screen name.",
                "Changed: default storage resolution changed to 1800px (was 2000px).",
                "Added: Item Analysis per-subfolder navigation — a frozen row above the table with chevrons and swipe left/right cycles through \"All\" and each individual subfolder, recomputing the table each time. The test/subfolder subtitle is now centered.",
                "Fixed: a stale error message pointed users to the old \"Load Test\" screen, which no longer exists — now points to the Saved Tests home screen."
            )
        ),
        ChangelogEntry(
            version = "3.7.7",
            date = "2026-08-04",
            notes = listOf(
                "Changed: reversed course on button text casing — every rounded-card button is now ALL CAPS for consistency. Covers Scanned Sheets, Item Analysis, Save Settings, Generate PDF, Capture Photo, Scan Next, Done, Test Summary, Grant Camera Permission, Use This as Answer Key, Export, and the shared Back/Export Backup/Choose Backup File/Save Answer Key/Apply to All/Check for Updates/Send Diagnostic Report buttons.",
                "Changed: renamed \"Answer Sheet Template\" to \"Answer Sheets\" and \"Scan Filled Answer Sheets\" to \"Scan Answer Sheets\", everywhere both appear.",
                "Changed: bolded Top Panel Accent and Button Accent button text in Settings."
            )
        ),
        ChangelogEntry(
            version = "3.7.6",
            date = "2026-08-04",
            notes = listOf(
                "Changed: bolded every button's text, for consistency with icon+text buttons that were already bold — fixed at the shared centerIconAndText helper, covering every plain-Button icon+text pairing at once.",
                "Changed: switched five buttons from maroon-group to gold-group accent, with contrast-adaptive icon/text colors — Export Backup, Choose Backup File, Save Settings, Check for Updates, and the scan result preview's Scan Next button (Done stays maroon).",
                "Fixed: Save Settings was silently rendering as all-caps (\"SAVE SETTINGS\") despite its source text already being Title Case — a leftover textAllCaps=\"true\" the earlier casing sweep didn't catch."
            )
        ),
        ChangelogEntry(
            version = "3.7.5",
            date = "2026-08-04",
            notes = listOf(
                "Added: Scanned Sheets — long-press the folder name (in the swipeable row with the chevron buttons) to rename the current folder. Renaming Main isn't allowed, and renaming onto an existing folder's name is blocked with a warning. The folder tabs' existing long-press-to-remove is unchanged.",
                "Added: About screen — Check for Updates button, between Send Diagnostic Report and Back. Currently shows \"Coming soon!\" — no update check exists yet.",
                "Fixed: Generate/Edit Answer Sheet Template's Generate PDF button was \"GENERATE PDF\" — now Title Case, keeping PDF itself all-caps. Audited Create/Edit Answer Key and every sidebar-linked screen — all were already consistent."
            )
        ),
        ChangelogEntry(
            version = "3.7.4",
            date = "2026-08-04",
            notes = listOf(
                "Changed: Create/Edit Answer Key — Save Answer Key button now uses the rounded-card style (was a plain default button shape with just a color tint).",
                "Changed: About screen — app title, version, tagline, \"What's New\" heading, and \"Legal & Licenses\" heading are now bold.",
                "Changed: Scanned Sheets — the folder/subfolder name in the swipeable nav row (with the chevron buttons) is now bold.",
                "Changed: Generate/Edit Template — the preview note text size now matches the \"Create Answer Key\" screen's title text size.",
                "Fixed: Generate/Edit Template — the Preview button's text was one size smaller (14sp) than the screen's other action buttons; now matches Save This Template's 15sp."
            )
        ),
        ChangelogEntry(
            version = "3.7.3",
            date = "2026-08-04",
            notes = listOf(
                "Changed: About screen — Send Diagnostic Report and Back swapped positions (Send Diagnostic Report now on top). Both buttons' icon+text are now centered as a unit.",
                "Changed: Create/Edit Answer Key — Apply to All moved to its own row below the Points per item stepper, which now fits on one line. Apply to All is now a maroon-group rounded-card button (was unstyled) with centered icon+text; Save Answer Key's icon+text are now centered too.",
                "Changed: converted the remaining old-style buttons to the app's rounded-card, centered icon+text design — camera permission screen's Grant Camera Permission (now maroon-group, contrast-adaptive); scan result preview's Scan Next and Done; detected-key screen's Use This as Answer Key (switched to gold group), Scan Next, and Done."
            )
        ),
        ChangelogEntry(
            version = "3.7.2",
            date = "2026-08-03",
            notes = listOf(
                "Fixed: the +/- stepper icons in Generate/Edit Answer Sheet Template were off-center inside their circles — missing android:padding=\"0dp\" and android:textSize=\"18sp\", which Settings' matching steppers already had. Copied Settings' proven attributes over. Both screens' steppers now share the exact same styling and size (36dp) and continue following the Top Panel (maroon) accent, same as v3.7.1 — a separate attempt at a fixed, accent-independent color for these buttons is parked pending a decision, not shipped in this build.",
                "Fixed: several icons/text had become invisible against their own accent-colored background — a container (a maroon header bar, or a card following the gold Button Accent) and an icon/text sitting on top of it were both tinted to the same accent group, so the icon/text matched its own background exactly and disappeared. Affected: Test Detail's header icons and its Scan Filled Answer Sheets card; Scanned Sheets' header icons and its Test Summary card; Item Analysis's header icon and its Export button. Fixed with new tags that compute readable contrast against the accent color instead of matching it.",
                "Fixed: Item Analysis Export button's icon wasn't rendering — same gold-on-gold contrast bug as above.",
                "Fixed: Capture Photo (live Scan screen) and Scan Filled Answer Sheets (Scanned Sheets screen) icon/text were hardcoded black with no adaptivity. Both now go white on a dark/darker chosen accent, black on a light/lighter one.",
                "Changed: Scan Filled Answer Sheets' icon replaced with a dedicated bubble-sheet glyph (scan-corner frame around a grid of bubbles), matching the icon set's existing style. Also used on the live Scan screen and in Settings, since all four share the same asset.",
                "Changed: Test Detail's Scanned Sheets row icon replaced with a new asset.",
                "Changed: Test Detail's Answer Sheet Template row icon replaced with a checkmark-over-bubble-grid glyph.",
                "Changed: Scanned Sheets screen's Test Summary card icon replaced with a checkmark-badge-over-bubble-sheet glyph, cropped tight to its bounding box so it doesn't render smaller than the icon set's other assets.",
                "Changed: Generate/Edit Answer Sheet Template — \"Preview\" is now all-caps (\"PREVIEW\"), and \"Use This Template & Save Test\" is now \"SAVE THIS TEMPLATE\" (all-caps).",
                "Changed: Generate/Edit Answer Sheet Template's Create/Edit Answer Key button now reads \"CREATE ANSWER KEY\" / \"EDIT ANSWER KEY\" (all caps) — also fixes a mismatch where the edit-mode label previously read \"Edit Answer Sheet\" despite the button being for the answer key.",
                "Changed: Settings' Save Settings button now has a check icon and all-caps text, matching Save This Template's style.",
                "Changed: removed the small status dot from folder/subfolder tabs on the Scanned Sheets screen."
            )
        ),
        ChangelogEntry(
            version = "3.7.1",
            date = "2026-08-03",
            notes = listOf(
                "Reverted: Number of Items/Columns/Questions per column +/- stepper buttons — an attempt to hardcode them permanently gold didn't render correctly on a real device, so they're back to following the Top Panel (maroon) accent, same as they did in v3.7.",
                "Changed: stepper button circles made smaller — Generate/Edit Template's steppers 44dp to 36dp, Settings' steppers 40dp to 34dp."
            )
        ),
        ChangelogEntry(
            version = "3.7",
            date = "2026-08-01",
            notes = listOf(
                "New: rounded-card list style — the Home screen's Saved Tests rows and the Test Detail screen's Answer Sheet Template / Scanned Sheets / Item Analysis rows are now themed cards with icons and a chevron, instead of flat rows or solid colored buttons. Card color follows your Light/Dark/Black theme choice only — it does not change with your accent color, by design.",
                "New: custom icons for Answer Sheet Template, Scanned Sheets, Item Analysis, and the Scan Filled Answer Sheets button.",
                "Changed: the Scanned Sheets scan count moved out of the row's own label text into a small badge next to it.",
                "Fixed: Test Detail's Scanned Sheets / Item Analysis icons were swapped — the two image files' actual content had been reversed relative to their filenames.",
                "Changed: Test Detail's Scan Filled Answer Sheets rebuilt as a rounded card with its icon inline, following the Button Accent (gold) group.",
                "New: Generate/Edit Answer Sheet Template screen redesigned — header and the bottom Use This Template & Save Test / Generate PDF buttons are now frozen outside the scrolling area. Number of Items is one grouped rounded-pill stepper. Columns/Questions per column now sit inside a boxed LAYOUT card with its own icon. Added icons to Create/Edit Answer Key and both preview buttons. Create/Edit Answer Key and Generate PDF follow the Top Panel (maroon) accent; Blank Preview, Answer Key Preview, and Use This Template & Save Test follow the Button (gold) accent.",
                "New: Scanned Sheets screen redesigned — each scan is now a rounded card with score/percentage split into two labels (percentage in a fixed highlight gold, not accent-tied). Folder tabs rebuilt as rounded pills with a status dot and scan-count badge. Test Summary follows the Top Panel (maroon) accent; Scan Filled Answer Sheets follows the Button (gold) accent.",
                "New: sidebar redesigned — header centered with a new \"SCAN • CHECK • REVIEW\" tagline, rows rebuilt as rounded cards with icons, grouped under DATA / PREFERENCES / SUPPORT section headers (PREFERENCES was \"Appearance\").",
                "Changed: Backup Data, Restore Data, Help/FAQ, and About's shared action buttons now use the app's rounded-card style, full width.",
                "New: Generate/Edit Answer Sheet Template — icons added to Use This Template & Save Test and Generate PDF; Blank Preview now uses a closed-eye icon (Answer Key Preview keeps the open eye) so the two are distinguishable at a glance; A–D/A–E is now a pill toggle instead of plain radio buttons; the +/- stepper buttons are now fully circular.",
                "New: Settings screen redesigned to match the Generate/Edit Template screen's conventions — frozen header and frozen Save Settings/Back buttons, settings grouped into TEMPLATE DEFAULTS / SCAN BEHAVIOR / STORAGE / ACCENT COLORS cards with icons, matching pill toggle and circular stepper styling.",
                "New: A–D/A–E and storage-resolution pill toggles now follow the Button (gold) accent properly — checked-state fill and text contrast are both computed from the actual chosen accent color.",
                "New: pointed \"Back\" buttons — every screen's actual Back button now uses a custom shape with a pointed left edge and a back-arrow icon, instead of the standard rounded-card corners.",
                "New: icons added across the board — Item Analysis Export, every button on the live Scan screen, Backup/Restore's primary actions, About's Send Diagnostic Report, and the Accent Color picker's buttons.",
                "Changed: sidebar reverted to a plain list — the rounded-card treatment added earlier in v3.7 was removed; rows are back to a plain full-width list with icons.",
                "Fixed: several buttons' icons (Use This Template & Save Test, Generate PDF, Create/Edit Answer Key, Blank Preview, Answer Key Preview, Capture Photo, Test Summary, Item Analysis Export) weren't actually rendering — a plain Button's drawableStart + drawableTint isn't reliable in this app. Rebuilt each as a centered icon+text row instead, which also makes icon and text genuinely center together as a unit (a Button's compound drawable never really does this).",
                "Changed: Back buttons reverted from the pointed-edge shape (added earlier in v3.7) back to the standard rounded-card shape; their icon+text now center together as a unit too, same as the button rebuild above but done via an inline icon baked into the button's own text instead of changing the button's type, since Back buttons are shared across five screens.",
                "Fixed: Scan Filled Answer Sheets' text/icon color is now computed for contrast against whatever gold shade is active (was hardcoded black with no fallback) — white is the new default.",
                "Changed: the Scanned Sheets count badge and the scanned-sheet percentage now use the app's actual default gold (previously the badge tracked the user's chosen accent, and the percentage used an unrelated lighter gold shade) — both are fixed regardless of accent choice, per explicit instruction.",
                "Changed: sidebar icons, Settings' gold-accent icons and all +/- steppers, and Generate/Edit Template's LAYOUT icon and +/- steppers now render in the app's fixed default gold, independent of the user's chosen accent color.",
                "Fixed: header icon contrast (menu, export, delete, add-folder) on Test Detail, Item Analysis, and Scanned Sheets — some were still following the active Light/Dark/Black theme's text color instead of the maroon bar's own, same underlying issue as the earlier v3.7 header-icon fix, now completed across all three screens.",
                "Changed: on the Home screen, date/time text is now smaller than the preferences line above it. On Scanned Sheets, each scan's score/percentage/date-time are now three separate lines (percentage smaller, date+time guaranteed to stay on one line).",
                "Fixed: Scan Filled Answer Sheets' text/icon color is no longer biased toward a fixed default — it's purely computed from whatever gold shade is active (white on a darker accent, black on a lighter one).",
                "Changed: Generate/Edit Answer Sheet Template's Blank Preview and Answer Key Preview buttons merged into one Preview toggle — closed-eye icon shows the blank sheet, tap to switch to the open-eye icon and the filled-in answer key, tap again to switch back."
            )
        ),
        ChangelogEntry(
            version = "3.6.11",
            date = "2026-08-01",
            notes = listOf(
                "Reverted a mistake from v3.6.10's last fix: collapsing the sidebar title into one auto-sized line also changed \"MCQ\"'s size and merged both lines, which wasn't asked for. Back to two separate lines, \"MCQ\" and \"Quick Check,\" sized to match the home screen header exactly.",
                "Fixed: sidebar widened to roughly half the screen width (computed from the actual device screen, not a guessed fixed size), giving \"Quick Check\" real room instead of being squeezed.",
                "Fixed: \"Developed by Sir_JPagdi\" was clipping in the sidebar footer — same cause as \"Quick Check,\" the drawer was just too narrow. The wider drawer fixes this too."
            )
        ),
        ChangelogEntry(
            version = "3.6.10",
            date = "2026-08-01",
            notes = listOf(
                "Fixed: the home screen header and sidebar app titles still weren't rendering in Press Start 2P even after v3.6.9's line-split and an earlier attempt at removing bold styling. Root cause was neither of those — it's AppCompatActivity's view inflater, which backports app-wide font resolution to every TextView by force-applying the theme's Montserrat font after inflation, and on this app's minSdk that backport wins over a per-view font attribute set in the layout XML rather than losing to it. Fixed by setting these three TextViews' Typeface directly in code instead, after inflation, where it can't be overridden.",
                "Fixed: with the font now actually rendering, \"QUICK CHECK\" clipped against the sidebar's fixed width — Press Start 2P's glyphs run much wider than Montserrat per character. Downsized \"QUICK CHECK\"/\"Quick Check\" and made \"MCQ\" match exactly between the home screen header and the sidebar, instead of being tuned independently.",
                "Fixed: the sidebar still rendered Montserrat instead of Press Start 2P on every screen except the home screen — the earlier fix only set it in MainActivity, but the sidebar is shared across every screen via one helper function. Moved the fix there instead so it applies everywhere sidebar access exists.",
                "Fixed: \"MCQ\" still overlapped \"Quick Check\" in the sidebar even after the previous fix. Real cause: swapping the font after the initial layout pass doesn't reliably force the line below to reposition before the next draw, so it could still paint against the first line's stale measured height. Collapsed the sidebar title from two stacked lines into one single-line TextView (\"MCQ Quick Check\") to remove that whole class of bug, sized with autosize instead of another fixed guess so it's guaranteed to fit any screen width."
            )
        ),
        ChangelogEntry(
            version = "3.6.9",
            date = "2026-08-01",
            notes = listOf(
                "Fixed: the sidebar's app title was clipping — \"MCQ Quick Check\" in Press Start 2P at a single fixed size ran wider than the drawer's fixed 300dp width and cut off mid-word (\"Check\" missing entirely). Split into two separate lines, sized independently — \"MCQ\" larger (20sp), \"Quick Check\" smaller (11sp) — mirroring the home screen panel's existing two-line MCQ / QUICK CHECK split, just with its own tuned sizes."
            )
        ),
        ChangelogEntry(
            version = "3.6.8",
            date = "2026-08-01",
            notes = listOf(
                "Fixed: the app was bundling actual Century Gothic font files. Century Gothic is a commercial Monotype typeface, not freely redistributable — caught during a pre-Play-Store-publish review. Replaced app-wide (theme font + generated PDF answer sheets, same two wiring points as before) with Montserrat, from Google Fonts under the SIL Open Font License, which explicitly permits bundling in and redistributing with an app. License text kept at licenses/fonts/Montserrat-OFL.txt for the record.",
                "Changed: the home screen's top panel title and the sidebar's app title now use Press Start 2P (also SIL OFL, licenses/fonts/PressStart2P-OFL.txt), an 8-bit/arcade-style display font — just those two spots, not the general app font, which stays Montserrat everywhere else."
            )
        ),
        ChangelogEntry(
            version = "3.6.7",
            date = "2026-07-31",
            notes = listOf(
                "Fixed: A4/Folio quadrants had unequal top/bottom margins once actually cut apart — a top-row quadrant had a big gap above its Name/Date table and almost none above the grid's last row; a bottom-row quadrant had the opposite. Root cause: all 4 quadrants shared ONE canvas transform centered on the whole physical page, which kept the page's own outermost top/bottom margins equal but let all of A4/Folio's extra height land as one lump above the top-row quadrants and one lump below the bottom-row quadrants, with nothing near the shared middle cut line. Each quadrant now gets its own transform, centered independently within its own quarter of the physical page — verified numerically that every quadrant gets equal margin on all 4 sides for Letter (unchanged), A4 (18pt), and Folio (36pt). The dashed cut line is now drawn once, directly at the true physical half in both axes."
            )
        ),
        ChangelogEntry(
            version = "3.6.6",
            date = "2026-07-31",
            notes = listOf(
                "Changed: the trash icon on the Scanned Sheets fullscreen preview moved from top-left to bottom-right, just below the image — top corners are the hardest spot to reach one-handed on a large phone, bottom corners are the easiest."
            )
        ),
        ChangelogEntry(
            version = "3.6.5",
            date = "2026-07-31",
            notes = listOf(
                "Confirmed: the QUARTER quadrant margin fix from v3.6.4 also covers stress-test/sample sheets — both real sheets and stress sheets go through the same SheetGenerator functions, so this was already true as of that release; no separate fix needed here.",
                "Changed: each stress-test config now packs all 4 of its variations onto ONE physical page's 4 quadrants, instead of 4 separate pages each repeating a single pattern across all 4 quadrants. Cuts the stress-test batch from ~45 pages down to ~18 — 3 of every page's 4 quadrants used to be identical copies of the same one variation. Removed SheetGenerator.generateStressTest(), now redundant with generateMultiVariant()'s new optional per-quadrant defects parameter."
            )
        ),
        ChangelogEntry(
            version = "3.6.4",
            date = "2026-07-31",
            notes = listOf(
                "New: a trash icon on the Scanned Sheets fullscreen preview lets a scan be deleted right from there, with the same confirmation as deleting from the list itself.",
                "Fixed: QUARTER-layout quadrants now get an equal content margin on all 4 sides after cutting apart, including the page's own outer edges — previously only the internal cut edges had zero margin reserved; the page's outer edges had whatever margin the template's own fractions happened to leave, which wasn't necessarily the same amount. The dashed cut line itself is unchanged — still exactly on the true half/half boundary — only the printed content backs off from it.",
                "Removed ResultAnnotator.markManualResolution() — orphaned since v3.6.3 removed its only caller (the old editable ambiguous-answer flow on a saved scan). No behavior change; internal cleanup only."
            )
        ),
        ChangelogEntry(
            version = "3.6.3",
            date = "2026-07-31",
            notes = listOf(
                "Fixed: resolving an ambiguous question from the Scanned Sheets folder (an already-saved scan) is no longer possible. Assigning an answer to an ambiguous bubble was only ever meant to be available on the live result screen right after capture, before saving — reviewing a saved scan later now just shows which questions were left unresolved (struck through), with no way to edit them.",
                "Fixed: the actual root cause of the stress-test/sample sheet bubble gap mismatch — bubble SIZE, not paper size. Real sheets set a new template's bubble size to 2.68mm; the batch generator's SheetTemplate construction never set this at all and silently fell back to the class default (2.2mm) instead — a smaller bubble, and since spacing is derived from bubble diameter, a tighter gap too. Last release's paper-size fix was real but wasn't the actual source of this particular mismatch."
            )
        ),
        ChangelogEntry(
            version = "3.6.2",
            date = "2026-07-31",
            notes = listOf(
                "Fixed: stress-test and sample answer sheets (the hidden About-screen batch generator) always printed at a fixed Letter-size page, ignoring whatever paper size is actually set for real sheet generation (Letter/A4/Folio). On anything other than Letter this put the bubbles at different physical margins than a real generated sheet on the same paper stock, even though the underlying layout math was identical — the two PDF paths now apply the exact same paper-size scale/center transform.",
                "Removed swipe-to-change-folder from the Scanned Sheets header bar and from the scan list itself — three versions of trying to make that reliable (title bar only, then the whole list) kept hitting edge cases with how touch events land on a scrollable list. Replaced with a dedicated non-scrollable row above the Test Summary button: < and > buttons plus swipe-anywhere-on-that-row, neither of which compete with the list's own scrolling since the row isn't part of it."
            )
        ),
        ChangelogEntry(
            version = "3.6.1",
            date = "2026-07-31",
            notes = listOf(
                "Fixed: tapping the Columns or Rows +/- steppers on the Generate Sheet screen left the \"Up to X questions per column...\" hint text frozen at whatever range was true before the tap, even though the field and the live preview below it had already moved on to the new value — only the question-count stepper was refreshing that hint text; the other two steppers never were.",
                "Fixed: swiping between folders on the Scanned Sheets screen only registered when the swipe started on top of an actual scan row — swiping over the blank space below the last row (or anywhere in a short/empty folder) did nothing. Root cause was a standard Android ScrollView quirk: a touch that starts on empty space with no view under it bypasses the swipe-detection logic entirely for that gesture. The scan list now fills the full screen height even when it has few or no rows, so every point on screen has something under it and swipe works the same everywhere.",
                "New: 3 more stress-test/sample sheet configurations in the hidden About-screen batch generator (long-press the title) — 30 items/2 columns, 60 items/3 columns, and 90 items/4 columns, alongside the existing tier-edge configs. Updated the batch generator's own popup message, which was still describing the old \"every paper layout, 10-120 items\" behavior from before v3.4/v3.6.",
                "New: scanned sheet storage resolution preference (Settings) — choose 2000px (default, same as before), 1800px, or 1600px for the long edge of a scanned sheet's saved photo. Lower uses less storage per scan; doesn't affect scans already saved."
            )
        ),
        ChangelogEntry(
            version = "3.6",
            date = "2026-07-31",
            notes = listOf(
                "New: answer sheets now support up to 100 questions (was 90), by adding a 4th column tier. New tiers: 10-25 items = 1 column, 26-50 = 2 columns, 51-75 = 3 columns, 76-100 = 4 columns (was 10-30/31-60/61-90 across 1-3 columns). Max items per column dropped from 30 to 25 to make room, which also means bubbles are drawn slightly larger than before, since the same page height is now split across fewer rows.",
                "Fixed: answer sheet columns were being stretched to fill the full width of the sheet even after v3.5.1 fixed the bubble-to-bubble spacing within a column — on any sheet using fewer than the maximum column count, this left a large dead gap between the last bubble of one column and the number label of the next, instead of that column simply ending where its content ends. Columns are now sized to their real content width and the whole row of columns is centered on the page, the same \"don't stretch, center the leftover space\" treatment row spacing already got in v3.5.1.",
                "Fixed (found during testing, not reported): the fix above could very slightly overlap the 4th column with the printed page edge specifically at the largest bubble-size setting combined with a 100-question, 4-column sheet — added the same automatic shrink-to-fit safety net that was already protecting against a sheet running too tall, now also covering sheets that would run too wide.",
                "Fixed: swipe between folders on the Scanned Sheets screen now works anywhere over the scan list itself, not just the title bar at the very top — the title bar was too small and too high up to reach comfortably with one hand. The title bar swipe still works too.",
                "New: an export icon next to the delete icon on the Test Detail screen. Packages a test's scanned sheets into a zip file, ready to Share or Download — if the test has folders beyond Main, choose to package everything or just one folder first."
            )
        ),
        ChangelogEntry(
            version = "3.5.1",
            date = "2026-07-30",
            notes = listOf(
                "Fixed: the actual bug in \"column gap\" — spacing BETWEEN individual A/B/C/D bubbles was still stretching to fill the block's width, even after v3.5's fix (which only fixed the gap between side-by-side blocks and the number-label width, not this). Bubble-to-bubble spacing is now a genuine fixed pitch (one bubble-width gap between bubbles), the same on every sheet regardless of column count — this is also the single formula BubbleGrader/ResultAnnotator read at scan time, so detection stays in lockstep with what's printed.",
                "Fixed: row gap is now a true fixed constant — one per-row pitch, tuned so a full 30-row column (the largest any current layout ever produces) fills the available grid height exactly, used identically on every sheet and every item count. Previously this was recomputed from the printed bubble size instead of the column's available space, which is what left the \"2-4 line\" gap of unused space at top/bottom on a 30-question sheet. A 10- or 20-question sheet uses this same fixed pitch too — it just naturally has fewer rows, so it doesn't reach the bottom of the grid, same as before.",
                "Changed: About page's Test Sheets generator inherits both fixes above automatically — no separate change needed, since it builds sheets through the same SheetTemplate/SheetGenerator code the fixes live in.",
                "Fixed: swipe on the Scanned Sheets screen wasn't registering. It was attached only to the title text, whose actual touchable area is just as wide as \"Scanned Sheets\" itself — too small and inconsistent a target. Moved to the whole header bar; the hamburger/add-folder/select-mode icons inside it are unaffected since Android hands a tap to those buttons first, before the header bar's own swipe listener ever sees it."
            )
        ),
        ChangelogEntry(
            version = "3.5",
            date = "2026-07-30",
            notes = listOf(
                "Fixed: Delete Sheet button (unclear-scan review screen) now follows the gold accent group instead of a hardcoded red that ignored both accent color choices — matches every other button on this screen. Also forced its label onto two lines (\"Delete\" / \"Sheet\") so it's the same height as Save & Proceed next to it, which was already wrapping to two lines at that width.",
                "Fixed: answer sheet column gap and question-number label width are now fixed to one real bubble's width, instead of a proportional fraction of the block's own width. Previously side-by-side columns had NO gap at all (bubbles from adjacent columns could butt right up against each other) and the \"12.\" label shrank/grew depending on how many columns the sheet had, with no relation to the actual printed bubble size. Both now measure exactly one bubble-diameter on every layout.",
                "Changed: Download paper size moved out of Settings and into a popup shown right when you tap GENERATE PDF, before the Share/Download prompt — you now confirm Letter/A4/Folio at the moment you actually export instead of it being a background setting that's easy to forget was ever changed. Your last choice is still remembered as the default.",
                "New: swipe left/right on the Scanned Sheets screen's title bar to move between Main and each subfolder — same order as the folder tabs. Separate from the existing swipe-through-scans gesture on the fullscreen viewer, which is unchanged.",
                "Changed: About page's Test Sheets generator now produces 5 milestone item counts (10/20/30/60/90 — one per column-tier boundary) instead of one sheet per 10-question step, and generates a stress-test batch (realistic ambiguous marks) for all 5 of those, not just 60."
            )
        ),
        ChangelogEntry(
            version = "3.4",
            date = "2026-07-29",
            notes = listOf(
                "Fixed: \"MCQ Quick Check isn't responding\" crash (OutOfMemoryError) when exporting a backup on a test with a lot of saved scans. Backups are now written to the file incrementally instead of building the whole thing in memory at once; old scans are also shrunk during export to keep the backup file smaller.",
                "New: an unclear scan is no longer a dead end — shows the full graded preview (tentative score, color-coded image), lets you resolve any ambiguous bubbles by hand, then asks Delete Sheet or Save & Proceed, instead of forcing a retake.",
                "New: scanning an answer key that comes back unclear now still shows the detected key (unclear questions marked \"?\") and lets you use it — fills in what was read correctly and tells you which questions still need manual entry.",
                "Fixed: a crash where scanning a key with even one unclear question would fail to fill in ANY answer, not just the unclear one.",
                "Confirmed: no cap on scanned sheets per test (unlimited as of v3.3) — the backup-export fix above is what makes this sustainable long-term.",
                "Changed: saved scans are now stored at a smaller resolution automatically, to help keep storage growth in check as the per-test scan count grows. Only affects the saved copy, not what you see right after scanning.",
                "Removed: ½ cross and ½ length paper layouts — ¼ page is now the only layout, with its question-count range extended from 10–60 to 10–90 to cover what those two used to handle. An existing test using either removed layout can no longer be opened; it needs to be recreated rather than being auto-converted, since sheets already scanned for it were captured against the old layout's shape. Restoring a backup with such a test now skips it instead of restoring it broken.",
                "Removed: the \"Faster scanning\" toggle in Settings — capture is now always the faster mode, confirmed working correctly against real scanned/graded sheets.",
                "Removed: the bubble size slider on Generate Sheet — bubble size is now fixed at 2.68mm for new sheets. Editing an existing test keeps that test's original bubble size unchanged."
            )
        ),
        ChangelogEntry(
            version = "3.3",
            date = "2026-07-29",
            notes = listOf(
                "Removed: the per-test cap on saved scans (previously 60, oldest-first eviction) — it was silently dropping scans for any full class set, defeating class-wide Test Summary / Item Analysis. No replacement cap.",
                "New: standardized bubble size — a \"Bubble size\" slider (1.72–2.68mm radius) on the Generate Sheet screen replaces the old fixed sizing, which drew bubbles far larger than a real printed OMR bubble needs (worst on dense sheets like 30 questions on a quarter-page). Row/column spacing now scale with the chosen size too. Baked into each test's own template permanently at creation time, so changing the slider later never affects previously-generated sheets.",
                "New: download paper size preference (Settings > Download paper size) — Letter, A4, or Folio (8.5\"×13\"). Printing-only: uniformly scaled and centered onto the chosen page, so scanning is unaffected.",
                "New: ambiguous questions (orange strikethrough) can be manually resolved by tapping the student's actual bubble (A–D or A–E) — on the scan result screen right after capture, or later from the Scanned Sheets folder. Updates score and Item Analysis data immediately; unresolved stays counted as ambiguous.",
                "Removed: the \"Subject Teacher\" line/toggle, on request. The header table is always the shorter 2-row layout now.",
                "Fixed (found while removing the above): a saved test's Subject Teacher setting was never actually persisted through Backup Data or app restarts — every reload silently reset it to default. Moot now that the feature's gone, noted in case anything else in this area seemed to \"forget\" settings before."
            )
        ),
        ChangelogEntry(
            version = "3.2.1",
            date = "2026-07-28",
            notes = listOf(
                "Fixed: Backup Data never included accent colors (Top Panel / Button accent) — a restore brought tests back but silently reset custom colors to default. Also now backs up folder names directly, so a folder created but never scanned into yet survives a restore too.",
                "Fixed: the \"Add folder\" dialog's input box (underline, blinking cursor, drag handle) was never wired into the accent color system — stuck on the base theme's default instead of your chosen accent, same bug class as several other dialogs fixed in earlier versions.",
                "Changed: tap-to-capture on the live camera preview is now a double tap instead of a single tap, to avoid accidental captures while just holding the phone up to frame the sheet. The dedicated Capture button and volume-key capture are unchanged.",
                "New: volume up/down now also advances to the next scan while reviewing a result, so a full capture-review-next cycle can be driven by volume keys alone.",
                "Changed: the \"Faster scanning\" toggle moved above Auto-save in Settings, since it's the fix for \"scanning feels slow\" and was easy to miss buried further down.",
                "Fixed: What's New (About screen) still listed several versions-old items instead of 3.0/3.1/3.2's actual new features."
            )
        ),
        ChangelogEntry(
            version = "3.2",
            date = "2026-07-26",
            notes = listOf(
                "First stable release. Renamed the app's package ID from com.tnhs.mcqscanner to com.jpagdi.mcqscanner, marking independence from TNHS branding and preparing for future commercialization. BREAKING for existing installs: Android treats a package ID change as an entirely different app, so this build cannot be installed over the old one. Use Backup Data to export everything BEFORE switching, install this build fresh (uninstalling the old one first), then Restore Data to bring your saved tests, keys, and settings back.",
                "New optional Settings toggle: \"Faster scanning\" — switches ImageCapture from CAPTURE_MODE_MAXIMIZE_QUALITY to CAPTURE_MODE_MINIMIZE_LATENCY. Off by default. Added after observing 3-5s capture times on a lower-end device.",
                "New: volume up/down now also captures on the scan screen, alongside the dedicated button and tap-anywhere-on-preview.",
                "Export Backup now offers a Cancel/Share/Download choice instead of going straight to a Share-only intent, matching every other export flow in the app.",
                "New: camera shutter sound on capture, with a Settings toggle (on by default).",
                "Fixed: \"Save Answer Key\" button text was hardcoded black, unreadable against a dark chosen accent color. Now computes proper contrast automatically.",
                "New: each diagnostic log entry now records tap-to-result latency and whether Faster Scanning was on, for real data on whether that toggle helps."
            )
        ),
        ChangelogEntry(
            version = "3.1.5",
            date = "2026-07-26",
            notes = listOf(
                "Fixed (for real this time): the blinking cursor in the \"Rename test\" dialog and the Send Diagnostic Report feedback field still showed the theme's maroon after v3.1.4's fix. Root cause found: both fields are wrapped in Material's TextInputLayout (the outlined-box style), which runs its own internal cursor-color sync tied to the box's stroke color — that sync overrides anything set directly on the child EditText, no matter how or when it's reapplied, which is why every previous attempt (mutate(), deferred post{}, focus-listener reapply) still lost. Now uses TextInputLayout's own setCursorColor() API instead of fighting its internal sync from outside. The Save-flow \"Rename test\" dialog (a plain EditText, not TextInputLayout) is unaffected by this specific bug and was not touched."
            )
        ),
        ChangelogEntry(
            version = "3.1.4",
            date = "2026-07-26",
            notes = listOf(
                "Replaced Send Diagnostic Report's backend entirely: it no longer uploads to a Google Apps Script / Drive pipeline (which never reliably worked across several deployment attempts). It now opens your email app with a generated report (app/device info, scanner config, recent scan log, and your note) attached and pre-addressed, so you review and send it yourself. No server to host, no deployment to keep working. Removed the now-unused INTERNET permission along with it.",
                "Fixed: the blinking cursor in the \"Rename test\" dialog (and its save-flow sibling) still showed the theme's maroon after v3.1.3's fix. That fix's single deferred re-tint could still lose a timing race against the dialog's own auto-focus/keyboard show-up. Now re-applies the tint whenever the field gains focus, not just once after attach."
            )
        ),
        ChangelogEntry(
            version = "3.1.3",
            date = "2026-07-26",
            notes = listOf(
                "Fixed: Send Diagnostic Report was pointing at a stale/incorrect backend deployment URL. Updated to the confirmed-working deployment.",
                "Fixed: the blinking cursor in the \"Rename test\" dialog (and its save-flow sibling, which never had a cursor fix at all) still showed the theme's fixed maroon instead of the chosen accent color. The earlier cursor-tint code was missing a mutate() call before tinting and ran before the field was attached/focused, so the tint didn't reliably stick — same underlying gap fixed in the Send Diagnostic Report feedback box's cursor."
            )
        ),
        ChangelogEntry(
            version = "3.1.2",
            date = "2026-07-26",
            notes = listOf(
                "Removed the menu button added to the scan (camera) screen in an earlier revision — this screen doesn't need one.",
                "Fixed: Send Diagnostic Report's feedback box (cursor, drag handle, box outline, and label text) wasn't reliably following the accent color when it changed. Now theme-adaptive instead — black in Light theme, white in Dark/Black — matching ordinary body text rather than the accent."
            )
        ),
        ChangelogEntry(
            version = "3.1.1",
            date = "2026-07-26",
            notes = listOf(
                "Fixed: Send Diagnostic Report could say \"sent successfully\" even when nothing actually reached Drive — it was only checking the HTTP status code, and Google Apps Script always returns 200 even from its own error handler. Now checks the server's actual success/failure response.",
                "Fixed: the feedback field's label and placeholder text were overlapping on the Send Diagnostic Report dialog.",
                "Fixed: swiping the Test Summary to a folder with zero scanned sheets (including Main, when everything was actually filed under a different folder) made the whole summary section disappear and the buttons below jump up. Now shows a plain \"scan at least one sheet\" message instead, so the layout stays put.",
                "Fixed: text input fields (test title, question count, subject teacher, columns/rows on Generate/Edit Template; default question count and teacher name in Settings) were never wired into the accent color system at all — always showed the fixed default gold underline regardless of your chosen accent. Also fixed the same gap on the Send Diagnostic Report dialog's outlined box, missed when that dialog was first built.",
                "Fixed: those same text input fields' blinking cursor and drag handle still didn't follow the accent color even after the underline was fixed — the underline, cursor, and drag handle turned out to be three separate things that all needed tinting, not one.",
                "Removed \"Main\" as its own page when swiping the Test Summary — its stats are the same as \"All Folders\" once real folders are in use, so showing it separately was redundant.",
                "Send Diagnostic Report now sends device/app version alongside the report, matching the upgraded backend that stores reports in Drive and Sheets with analytics."
            )
        ),
        ChangelogEntry(
            version = "3.1",
            date = "2026-07-25",
            notes = listOf(
                "Fixed: scans failing with \"Couldn't find the sheet\" in low light — diagnosed from a real diagnostics log and sample photos. The check that confirms a marker match is sitting on real paper (not random dark clutter in the scene) used a fixed brightness floor that ordinary low-light indoor photos couldn't clear even when the marker shape itself was found correctly. Loosened the floor; still rejects non-paper clutter.",
                "Diagnostics log is now capped at the most recent 100 scan attempts and reads newest-first, instead of growing forever.",
                "Fixed: Test Summary swipe between folders on the Test Detail screen wasn't working — a missing gesture-detector override.",
                "Fixed: rename dialog's drag handle (the teardrop selection locator) was still the theme's default color even after the cursor color was fixed. Android 9+ only.",
                "Fixed: \"Blank Preview\" and \"Answer Key Preview\" buttons were different heights on the Generate/Edit Template screen.",
                "Corrected: last version's hamburger-icon fix tinted every hamburger to your accent color — that wasn't the actual request. Every hamburger, including the home screen, now matches the screen's own title text color instead.",
                "Added: the scan (camera capture) screen now has a menu button — every other screen already had one.",
                "Fixed: the gold color on the Create/Edit Answer Key screen didn't follow your chosen accent — both the selected-bubble fill and the \"Save Answer Key\" button were reading the theme's fixed gold.",
                "Added: \"Saved Tests\" heading above the test list on the home screen.",
                "Help / FAQ: fixed a reference to a \"New Test\" button that doesn't exist (it's the + button), bolded question lines, and added an entry explaining Send Diagnostic Report.",
                "Replaced Export Scan Diagnostics with Send Diagnostic Report — instead of sharing/downloading the log yourself, one tap sends an anonymous report (device/app info, recent scan activity, and an optional note from you) directly to us, to help improve scan accuracy. Never includes student names, answer sheets, photos, PDFs, or exam content. The old \"Clear Diagnostics Log\" long-press option is unchanged."
            )
        ),
        ChangelogEntry(
            version = "3.0",
            date = "2026-07-24",
            notes = listOf(
                "Fixed: scanner accuracy — diagnosed from a real diagnostics log. Washed-out/low-contrast photos (dim lighting, thin pencil) were having a correct, confident low-contrast reading thrown away and replaced with a threshold too strict for that photo, causing real marks to read as blank.",
                "Fixed: cache buildup — every scan attempt, including rejected ones, was leaving a full-resolution photo behind with no cleanup. Fixed, plus an added cleanup sweep on app open.",
                "Added: custom app font (Century Gothic), replacing the system font throughout the app and on generated PDF answer sheets.",
                "Fixed: rename dialog's input box was always maroon regardless of your chosen accent color.",
                "Fixed: hamburger menu icon now follows your chosen accent color.",
                "Changed: Test Summary — removed the redundant test title and maroon background; plain theme-adaptive text now, swipe left/right between All Folders and each folder's own summary.",
                "Added: haptic feedback on every delete button, tap or long-press."
            )
        ),
        ChangelogEntry(
            version = "2.9",
            date = "2026-07-24",
            notes = listOf(
                "Fixed: footer text still looked faded after two prior attempts — root cause was the Dark/Black themes never defining a solid text color at all, fixed at the theme level.",
                "Fixed: \"Use This as Answer Key\" button had unreadable black text on a maroon button — two color rules were overriding each other.",
                "Fixed: the folder-choice prompt before scanning had gone missing — restored. Tests with only Main skip it; tests with extra folders ask which one to use.",
                "Added: exit confirmation when leaving the home screen (button or gesture back).",
                "Added: a system notification confirming when a file is saved to Downloads.",
                "Home screen: bigger text on the saved-test list.",
                "Changed: Generate PDF, Export Item Analysis, Export Diagnostics, and Generate Test Sheets are now one button each, prompting Share or Download afterward instead of separate buttons.",
                "Fixed: downloading a generated file was saving a second, permanent hidden copy inside the app's own storage alongside the Downloads copy — only the Downloads copy remains now.",
                "Changed: Test Detail screen — removed the Test Summary button/popup in favor of an on-screen summary; Item Analysis is now full-width in the space that freed up.",
                "Added: Help / FAQ in the sidebar.",
                "Added: haptic feedback on popup prompts and confirmations throughout the app.",
                "Compatibility: permission requests now degrade safely by Android version; fixed a pre-existing bug where sharing Item Analysis exports on Android versions below 10 would have crashed (a required file location was never registered)."
            )
        ),
        ChangelogEntry(
            version = "2.8",
            date = "2026-07-22",
            notes = listOf(
                "Fixed: the welcome animation's status bar was still black instead of matching the video, and the footer credit line was still faded — both now fixed for real.",
                "Fixed: gold checkboxes on the Saved Tests and Scanned Sheets selection screens now follow the Button Accent color.",
                "Home screen: logo and title nudged up slightly for better spacing.",
                "Fixed: an edited Answer Sheet Template wasn't actually saving when opened from Test Detail — it now genuinely persists, including handling a rename correctly.",
                "Fixed: a theme change from the sidebar didn't hold on other already-open screens; a saved accent color now also shows up everywhere immediately, no unrelated theme switch needed to force it.",
                "Fixed: Dark theme's background was maroon-tinted, not neutral — now a genuine charcoal gray.",
                "The splash screen now matches its own video's background for a seamless fullscreen look.",
                "Tap the logo (home screen or sidebar) to replay the welcome animation.",
                "Accent color picker: fixed a clipped-looking preview corner and centered the swatch grid.",
                "New app icon.",
                "Fixed: the preview for an unclear/ambiguous scan now shows the same processed image the scanner actually judged, instead of an earlier-pipeline-stage version.",
                "Fixed: rescanning the exact same answer sheet could flip between accepted and rejected — the scanner now checks how trustworthy its own reading is on each photo and leans on a steadier reference value when a reading looks shaky.",
                "Home screen: the header is narrower, stays put while you scroll the test list, and the title is now two lines.",
                "Scanned Sheets: long-press any scan's trash icon to select multiple and delete them together — a normal tap still deletes just that one.",
                "Settings: two new accent color pickers (Top Panel Accent, Button Accent) with a color grid, a lighten/darken slider, and their own Revert to Default, applied across every screen.",
                "Replaced: the splash animation with a new launch video — the crop-to-fill playback and timing behind it is unchanged, only the clip itself is new.",
                "Replaced: the app logo with a new pixel-art \"</MCQ/>\" mark, shown wherever the old logo appeared (home screen header, navigation drawer).",
                "Replaced: the launcher icon — it previously reused the same transparent logo file as the in-app logo, so it now has its own dedicated icon with a proper background instead of showing transparent against the home screen wallpaper.",
                "Removed: an old, unused school-facade image left over from an earlier version — it had no references anywhere in the app."
            )
        ),
        ChangelogEntry(
            version = "2.7",
            date = "2026-07-20",
            notes = listOf(
                "Added: Item Analysis — difficulty (P) and discrimination (D) index per question, using Kelley's 27% high/low group split, with interpretation and recommendation text per the tlconestoga.ca rubric. Needs scans saved from this version onward (older scans didn't record per-question correctness); older scans are silently excluded rather than guessed at.",
                "Added: Export Item Analysis as a real .xlsx file. Downloads directly to the device's Downloads folder on Android 10+; on Android 9 and below it shares the file instead (writing straight to Downloads on older Android needs a permission this app doesn't otherwise use).",
                "Changed: the home screen is now the Saved Tests list itself — tapping a test opens a dedicated screen for it (Answer Sheet Template, Scanned Sheets, Test Summary, Item Analysis, Scan Filled Answer Sheets, and a delete option). The old separate \"Load Test\" picker screen is gone, fully replaced by this.",
                "Added: Select mode with bulk delete, on both the Saved Tests list and Scanned Sheets, each with a confirmation naming exactly what's about to be removed.",
                "Added: sort scanned sheets by date or score, ascending or descending — remembers your last choice.",
                "Added: the app's menu (Backup/Restore, Theme, Settings, About) is now reachable from every screen via the menu icon, not just the home screen.",
                "Changed: editing an existing test's template now clearly reads as editing (\"Edit Answer Sheet Template\" / \"Edit Answer Sheet\") instead of reusing the same wording as creating a brand new one.",
                "Changed: Test Summary's Mean and Median now show as plain numbers, and the \"Item Analysis coming soon\" note is gone now that it's a real feature.",
                "Fixed: the Scanned Sheets screen's own title still said \"Saved Scans\" even though the button that opens it had already been renamed — now consistent.",
                "Changed: every screen now uses the same washed-out background as the home screen, instead of a plain solid background."
            )
        ),
        ChangelogEntry(
            version = "2.6",
            date = "2026-07-19",
            notes = listOf(
                "Added: scanned sheets now go through shadow removal, local contrast boost, and sharpening before grading and preview — closer to a phone's built-in \"scan document\" mode, using the same clean version for both scoring and what's shown/saved (not the raw dewarped photo).",
                "Added: a Test Summary button (Main and every subfolder) showing count, high/low score, mean, median, mode, standard deviation, and pass rates at 75% and 50% for that folder's scans — Main's summary includes every subfolder's scans, not just Main's own."
            )
        ),
        ChangelogEntry(
            version = "2.5",
            date = "2026-07-18",
            notes = listOf(
                "Added: a Legal & Licenses section on the About screen — copyright notice for the app itself, plus required attribution for OpenCV and AndroidX (both Apache License 2.0). Also added a LICENSE file at the repo root.",
                "Fixed: \"Use This Template & Save Test\" button now always wraps as \"Use This Template\" / \"& Save Test\" instead of an uncontrolled line break.",
                "Added: a +/- stepper next to the number-of-questions field on Generate Answer Sheet and Settings.",
                "Changed: sheet-size guidance at the 60-question boundary now suggests switching sheet sizes instead of a generic min/max message.",
                "Fixed: Overwrite/Rename dialog buttons were swapped from their labels — OVERWRITE is now genuinely on the left, RENAME on the right.",
                "Added: Backup Data now includes Settings defaults and app theme, so Restore brings your setup back too.",
                "Fixed: ½ cross saved-scan thumbnails were cropped into an extreme sliver — now proportioned like ¼ page's crop.",
                "Fixed: subfolder tab text was unreadable on Dark/Black themes.",
                "Changed: \"Scan Filled Sheets\" button renamed to \"Scan Filled Answer Sheets.\"",
                "Added: a \"Scan Filled Answer Sheets\" button on the Saved Scans screen, scanning directly into whichever folder is currently being viewed."
            )
        ),
        ChangelogEntry(
            version = "2.4",
            date = "2026-07-18",
            notes = listOf(
                "Changed: confirming a template on the Generate Answer Sheet screen now saves it as a test automatically — the button reads \"Use This Template and Save Test.\" The separate \"Save Test\" button on the home screen is gone.",
                "Added: saving a test with a title that already exists now prompts to Overwrite or Rename, with a second confirmation before an overwrite (which deletes the existing test's scans too).",
                "Fixed: the printed score on a scanned result is now right-aligned to the score cell instead of left-anchored right after the label — on HALF_LENGTH sheets it could run off the visible area entirely. This also structurally removes the earlier Subject/Teacher-toggle position bug, not just patches around it.",
                "Fixed: \"Use This as Answer Key\" button text was unreadable on Dark and Black themes.",
                "Changed: item counts and column counts you can choose when creating a sheet are now tied together and more restricted, based on real-device scan diagnostics. QUARTER: 10 items = 1 column, 11-20 items = 2 columns, 21-60 items = 3 columns. HALF_LENGTH: 60-90 items, 3 columns only (max item count lowered from 120). HALF_CROSS: 60-75 items = 5 columns, 76-90 items = 6 columns (max item count lowered from 120). These column counts are no longer a free choice — each item count now has exactly one valid column count. This applies to real test creation, not just the prefilled test-sheet generator tool.",
                "Added: a new prefilled stress-test sheet (QUARTER, 60 items, 3 columns) in the test-sheet generator with deliberately messy marks scattered through it — some blank, some lightly/partially filled, some overflowing past the bubble outline, some with two bubbles marked on the same question — to test scanning against realistic messy sheets, not just clean ones."
            )
        ),
        ChangelogEntry(
            version = "2.3",
            date = "2026-07-18",
            notes = listOf(
                "Changed: overhauled the test-sheet generator's column/item-count set and added multiple fill-pattern variations per sheet (testing tool only, not part of real test scoring).",
                "Fixed: scanned half-page sheets (both layouts) were rendering compressed/soft — now save and preview at full resolution.",
                "Fixed: the printed score's position was shifting right when the Subject/Teacher line was toggled on — now prints in the same place either way.",
                "Changed: new, much shorter welcome-screen animation and retimed fade.",
                "Added: scan diagnostics logging, for tracking down scans that get flagged unclear/ambiguous even though they look fine. Every scan attempt (successful, rejected, or failed) is now logged with the internal numbers BubbleGrader used to decide. Share the log from About > Share Scan Diagnostics (long-press that button to clear the log and start fresh). No detection or threshold changed — this is instrumentation only, to gather real evidence before any tuning."
            )
        ),
        ChangelogEntry(
            version = "2.2",
            date = "2026-07-17",
            notes = listOf(
                "Fixed: the correct-answer bubble in the answer key preview and printed sheet now shows as fully shaded instead of a hollow ring.",
                "Changed: the welcome-animation on/off switch in this Changelog screen is now a normal visible control instead of a hidden tap strip.",
                "Changed: the live corner guide during scanning now turns fully green based on the phone's tilt sensor — when it's held flat and level over the sheet, all 4 guides light up together, instead of relying only on the camera-image check that wasn't always catching a good alignment.",
                "Changed: scanned sheets are now un-skewed using a sharper perspective-correction method, closer to the crisp output of a phone's built-in \"scan document\" mode.",
                "Added: a batch tool (long-press the About screen title) that generates ~30 prefilled test PDFs across every paper layout and item count, for exercising the scanner end to end.",
                "Changed: replaced the welcome-screen animation clip and retimed its fade-out to match the new clip's length.",
                "Fixed: a stray horizontal line under the text field in the Rename Test dialog."
            )
        ),
        ChangelogEntry(
            version = "2.1",
            date = "2026-07-16",
            notes = listOf(
                "Fixed \"Scan Answer Key Instead\" crashing and being unusable — a safety check meant only for grading was also blocking answer-key scanning, where starting from 0 key items is normal, not an error. Also hardened camera cleanup against any other early-exit crash of the same kind.",
                "Fixed the on-sheet score still looking the same size after the last update — found the actual cause (a size cap that was quietly smaller than intended) and re-derived it directly against the real cell height.",
                "New: Saved Tests sort by most recent activity — editing, saving, or scanning a test all count.",
                "New: long-press a saved test to rename it — its scans and folders move with the rename.",
                "New: sheet templates can be saved without an answer key set up yet.",
                "New: item-count limits per paper size — 60 for quarter-page, 120 for half-page (both layouts).",
                "New: an optional welcome animation on app launch."
            )
        ),
        ChangelogEntry(
            version = "2.0",
            date = "2026-07-16",
            notes = listOf(
                "Fixed scanned sheets from before this update looking like they'd vanished — v1.9 changed the storage format and the new code couldn't read the old one. Old scans are now automatically recognized and restored on next launch. Nothing was ever deleted.",
                "Fixed swipe between saved scans not working — the listener was attached to a view that could never actually receive the touch, since the zoomed image underneath always claimed it first. Rebuilt so it reliably works now.",
                "New: dedicated \"Saved Tests\" screen — Load Test now opens a full screen instead of a popup.",
                "New: folders are reachable even with zero scans in them, so you can set them up before scanning anything.",
                "New: move a saved scan to a different folder.",
                "New: \"+\" button on the home screen starts a brand-new blank test without disturbing the currently loaded one.",
                "New: items-per-column limits now scale to your column count, rounded to sensible steps, with an automatic columns/rows suggestion as you type.",
                "New: answer key items can be set to 0 points, with a confirmation prompt so it's never accidental. The bulk \"Points per item\" default field got its own +/- stepper, still floored at 1.",
                "Changed: on-sheet score is now 4x bigger, bold, and green.",
                "Changed: saved-scan thumbnails are properly sized so the Name/Section crop is actually readable.",
                "Changed: About page now shows a \"SCAN CHECK REVIEW\" tagline."
            )
        ),
        ChangelogEntry(
            version = "1.9",
            date = "2026-07-15",
            notes = listOf(
                "Fixed pinch-zoom and double-tap zoom not working at all — the touch handler was rejecting every gesture right from its first touch.",
                "Fixed unclear%/ambiguous% sometimes showing over 100%, which was also a major cause of sheets needing repeated rescans.",
                "Addressed same-sheet-scores-differently-each-time by making the ink-detection threshold less sensitive to photo-to-photo noise (not yet confirmed by repeated rescans).",
                "Addressed a clearly, fully shaded bubble sometimes getting flagged ambiguous, by making the \"second mark\" check care about how dominant the real mark is (not yet confirmed).",
                "Fixed a saved test being gradable against the wrong total (e.g. \"X / 40\" on a 30-item sheet) — now checked and blocked with a clear message before scanning starts.",
                "Addressed a HALF_LENGTH-specific capture bug (garbled/mismatched photos) by fixing a camera-setup race condition (not yet confirmed on a real device).",
                "Every unshaded/blank bubble now gets the orange ambiguous strikethrough too, not just faint marks — scanning/scoring itself is unaffected, only the visual flag changed.",
                "Sheet generator's max items-per-column is now based on your actual column count instead of a flat number, with an automatic layout suggestion when you enter the item count.",
                "Saved scans have a real list screen now (thumbnail, score, date, swipe between them) instead of a popup, plus folders to organize them.",
                "Score and percentage are now printed directly on the scanned sheet image."
            )
        ),
        ChangelogEntry(
            version = "1.8",
            date = "2026-07-14",
            notes = listOf(
                "Fixed a Settings screen crash (two elements were missing required layout attributes).",
                "Fixed a scanning regression that was flagging normal, clearly-shaded marks as ambiguous on all paper layouts — reverted an over-wide threshold from last version and added a more specific check instead.",
                "Fixed a real-but-faint mark being treated exactly like a totally blank bubble — it's now flagged for a second look instead of silently vanishing. Scoring is unchanged.",
                "Fixed the sheet generator sometimes building a different layout than what was shown on screen, which could cause an unexpected extra row of columns.",
                "Settings: added a toggle for the \"Subject Teacher:\" line, with a default name field.",
                "About page: now shows a short list of real new features instead of the full changelog, and scrolls properly as that list grows.",
                "Side drawer: added version + developer name at the bottom, tap to view the changelog.",
                "Confirmed the perspective warp already runs on every capture — a curled/bowed sheet can still look skewed in the middle even with clean corners, which is a known, separate limitation, not fixed by this.",
                "Generate Template screen: field renamed to \"Test Title,\" and back now confirms before discarding an unsaved template."
            )
        ),
        ChangelogEntry(
            version = "1.7",
            date = "2026-07-14",
            notes = listOf(
                "Zoom on the scan result: double-tap now opens the sheet fullscreen instead of zooming the small inline preview — pinch/pan/double-tap-zoom all work there without fighting the page's own scrolling.",
                "Confirmed the perspective/deskew warp already runs on every scan (pulls the 4 detected corners into a flat rectangle) — it wasn't missing. Still-known limit: a sheet curled in the middle can smear even with clean corners, since only the outer 4 points are pinned; a full fix needs interior markers or a mesh warp, still out of scope for now.",
                "Added a delete-scanned-sheet icon below the result legend (shipped hidden/unwired earlier in this same version, then wired up once autosave existed later in this session).",
                "Grading: widened the gap between \"blank\" and \"filled\" so small/partial marks land in the ambiguous zone instead of reading as a clean correct answer, and made a faint second mark on a row less likely to falsely flag a clean single answer. This is a tuning pass based on reviewing failure-case photos, not a confirmed fix — please report back if item-25-style partial marks and false ambiguous flags are better now.",
                "Settings is now real: default paper layout, choices, question count, columns, questions/column, plus the auto-save-scanned-sheet toggle — applies to new templates only, doesn't change any test you already saved.",
                "Auto-save scanned sheets: when the Settings toggle is on, each graded result is saved automatically as soon as it's shown (not deferred to Scan Next/Done), so the new trash icon below the legend can undo that save immediately if a scan came out bad, then moves on to the next sheet.",
                "Load Test: tests with at least one saved scan now show a small paper icon that opens a picker of that test's saved scans, each reviewable fullscreen.",
                "Backup Data / Restore Data are real: exports all saved tests and scanned sheets into one JSON file via Android's share sheet (save to Drive, email it, etc.), and Restore reads that file back in, asking before overwriting any test with a name you already have."
            )
        ),
        ChangelogEntry(
            version = "1.6",
            date = "2026-07-14",
            notes = listOf(
                "Fixed a near-empty bubble sometimes still grading as \"correct\" — added a second, fixed-threshold coverage check that can't be fooled by a miscalibrated per-photo ink threshold.",
                "Ambiguous marks are now shown as an orange strikethrough across the whole row instead of an orange ring on one bubble, since ringing one choice implied more certainty than the grader had. The correct answer still gets its own green ring.",
                "Fixed the green checkmark next to question numbers appearing one row too high.",
                "Fixed pinch-to-zoom not working on the scan result image.",
                "Widened the live alignment guide's paper-detection tolerances for small (e.g. 1/4-page) layouts — not yet confirmed fixed on a real device, please report back.",
                "Home screen: theme toggle moved into a new hamburger menu. Backup/Restore/Settings/About are placeholders for now — full versions coming next."
            )
        ),
        ChangelogEntry(
            version = "1.5",
            date = "2026-07-14",
            notes = listOf(
                "Grading rewritten to use coverage (what fraction of a bubble's actual area is ink) instead of a small point sample — fixes light/partial marks being over-counted and stray double-marks being under-flagged.",
                "Ambiguous marks now NEVER count as correct, even if they happen to land on the right bubble — previously ambiguity was just a side note. Scoring now explicitly counts wrong/ambiguous/blank first and derives correct from what's left.",
                "Tightened the low-confidence rejection thresholds — a badly-warped or unclear scan is now more likely to be rejected with a retake prompt instead of silently producing a wrong score.",
                "Added diagonal cross-validation to the live alignment guide: the top-left/bottom-right pair and top-right/bottom-left pair must actually look like a rectangle's diagonals (shared midpoint, matching length) once both are found — filters out matches that looked locally plausible but don't fit the sheet's real geometry.",
                "Result screen: the scanned sheet can now be pinch-zoomed and panned to double-check any bubble closely. Added green checkmarks next to correctly-answered question numbers. Legend reformatted to 2 lines.",
                "Fixed the Load Test Cancel button being completely invisible in Dark/Black themes (our Dark/Black themes never actually set android:textColorPrimary, so the previous fix's attribute lookup wasn't reliable — now uses our own known theme state directly).",
                "Load Test entries now show question count/choices/paper layout and the save date under each test name.",
                "Answer key screen: added +/− steppers for each question's points, fixed unreadable Discard-dialog button text in Dark/Black, reworded the discard-confirmation message."
            )
        ),
        ChangelogEntry(
            version = "1.4",
            date = "2026-07-13",
            notes = listOf(
                "Rebuilt the live alignment check from scratch: instead of template matching (which still confused a dense grid of bubbles with the corner markers), it now first tries to detect the physical paper's own edges in the frame, then confirms an actual marker at each corner via contour hierarchy (does the shape have a hole?) plus geometry (is it actually square, not round?) — a fundamentally different, stronger signal than brightness or shape-correlation.",
                "Result screen rebuilt ZipGrade-style: the scanned sheet itself now shows color-coded rings directly on the bubbles — green for the correct answer, red for a wrong mark, orange for anything ambiguous — replacing the separate itemized text list.",
                "Load Test now sorts by most recently saved (newest first) and shows the save date under each test name.",
                "Fixed unreadable dialog button text (Cancel, etc.) in Dark/Black themes.",
                "Fixed the ½ cross paper layout showing noticeably bigger on-screen guide squares than ½ length or ¼ page — now consistent across all three.",
                "Fixed Scan Next/Done button text being unreadable in Dark/Black themes."
            )
        ),
        ChangelogEntry(
            version = "1.3",
            date = "2026-07-12",
            notes = listOf(
                "Fixed the version number not opening the changelog: the fixed footer was declared before the ScrollView in the layout, so the ScrollView (drawn on top) was silently swallowing taps meant for it, even in its own empty bottom padding. Reordered so the footer draws on top.",
                "Home screen buttons now vertically center in the space below the header instead of always starting right under it.",
                "Widened the live alignment guide's search range to match the final scanner's proven range (was only checking 3 zoom levels, now checks 5 across a much wider range) — should turn green far more reliably when the sheet is actually aligned.",
                "Redesigned the scan result screen: score shown big at the top (always visible), Scan Next/Done pinned at the bottom (always visible), with only the photo and per-question breakdown scrolling in between — fixes the buttons getting pushed off-screen on longer tests.",
                "Question numbers and choice letters on the printed sheet now stay a fixed, legible size regardless of how dense the layout gets — only the bubbles themselves shrink to fit more content."
            )
        ),
        ChangelogEntry(
            version = "1.2",
            date = "2026-07-12",
            notes = listOf(
                "Fixed a build-breaking bug in the v1.1 marker redesign: two constants in MarkerTemplateFactory were plain Doubles but got multiplied with Float values and passed straight into Canvas.drawRect (which requires Float) — Kotlin doesn't implicitly narrow Double to Float, so this failed the build. Fixed by declaring them as Float from the start.",
                "Paper layout overhaul: removed Full page as an option. Renamed ½ page to ½ cross (the existing top/bottom stacked layout) and added a new ½ length option (side-by-side halves instead, for a lengthwise fold). Column limits: up to 6 for ½ cross, 3 for ½ length, 3 for ¼ page.",
                "Answer key creation moved onto the Generate Answer Sheet Template screen — tap Create Answer Key for a choice of Create Manually or Scan Answer Key Instead. Whatever key you build there now returns together with the template.",
                "Load Test now supports deleting a saved test directly from the list (🗑 next to each entry, with a confirmation step).",
                "Columns and questions-per-column can now be typed directly, not just adjusted with +/−.",
                "Fixed unreadable Answer Key Preview button text in Dark/Black themes.",
                "Version number and developer credit moved to a fixed footer at the bottom of the home screen instead of scrolling with the content.",
                "Renamed the app to MCQ Quick Check.",
                "Replaced the school logo/name in the header with the new app logo and a larger app name, and swapped the washed-out home screen background photo for a new exam-day photo."
            )
        ),
        ChangelogEntry(
            version = "1.1",
            date = "2026-07-11",
            notes = listOf(
                "Bubble grading now adapts its darkness threshold to each photo's own lighting (Otsu's method on that sheet's actual darkness scores) instead of a fixed brightness value — should meaningfully improve accuracy in dim lighting, not just bright/studio conditions.",
                "Redesigned the corner marker as a hollow ring (was a solid square) — much harder to confuse with rows of answer bubbles, which was causing the live alignment guide to react to shaded/unshaded bubbles almost as readily as the real corners.",
                "Fixed the ½-page template preview rendering distorted/broken — it was always assuming full-page proportions regardless of paper size.",
                "Moved Generate & Share PDF onto the template screen, right below Use This Template.",
                "Columns and questions-per-column can now be typed directly, not just adjusted with +/−. Renamed \"Rows per column\" to \"Questions per column\".",
                "Removed the border box around the bubble grid — it ran directly through the corner guides.",
                "Added a Subject Teacher field (typed once when generating the template) — printed on the sheet, filling what used to be dead space next to the Score cell and closing the table's border on all sides.",
                "Scan results now show the correct answer in red next to anything that wasn't marked correct (incorrect, ambiguous, or blank) — nothing shown for correct answers."
            )
        ),
        ChangelogEntry(
            version = "1.0",
            date = "2026-07-10",
            notes = listOf(
                "Fixed the real accuracy problem behind 'blanks even on clearly shaded bubbles': template matching alone only located markers to the resolution of its search grid, which wasn't precise enough for the perspective warp. Added a refinement step (Otsu thresholding + centroid) that locks onto the marker's true center after the coarse match, which should meaningfully improve grading accuracy.",
                "Loosened the white-paper-surround threshold — it was likely rejecting genuine sheets under normal (non-studio) indoor lighting, not just random scenes.",
                "Removed the big connecting rectangle from the live scan guide — just the 4 corner squares now, which are what you actually align to.",
                "New 'Generate Answer Sheet Template' screen: test name, question count, choices, and paper size moved here from the home screen, plus new columns/rows controls (with sensible limits per paper size) and a live preview that re-renders using the exact same drawing code as the real printed PDF — so the preview never drifts from what actually prints. If an answer key is already set, a second preview mode shows it filled in as a reference sheet.",
                "Home screen now shows a summary of the currently configured test instead of the raw fields, with a button to open the new template screen.",
                "Added the school facade photo as a washed-out background on the home screen (photo + a theme-colored scrim on top, so it reads as a faint texture in any of the 3 themes rather than competing with the text)."
            )
        ),
        ChangelogEntry(
            version = "0.9",
            date = "2026-07-09",
            notes = listOf(
                "Widened alignment tolerance 4x (shared by the on-screen guide size and the actual matching search radius, so what you see is honestly what's accepted) — should make it noticeably easier to land a capture.",
                "Added a second check alongside shape matching: the area around a matched marker must actually look like plain white/light paper, not just be roughly the right shape. This should stop the guide from lighting up on random dark scenes (grass, walls, tables, objects) that happened to have a square-ish dark patch but no actual sheet of paper around it.",
                "Fixed unreadable button text in Dark/Black themes on the home screen (Create Answer Key, Scan Answer Key, Generate & Share PDF, Save/Load Test) — same root cause as earlier text-contrast fixes, just missed on these specific buttons.",
                "Suppressed Android's system autofill icon showing up on action buttons (the stray green marks next to button labels) — these are buttons, not form fields.",
                "Restricted the app's native libraries to real-device CPU architectures only (dropping emulator-only x86/x86_64), cutting the APK size down substantially from the OpenCV upgrade in v0.8."
            )
        ),
        ChangelogEntry(
            version = "0.8",
            date = "2026-07-09",
            notes = listOf(
                "Replaced marker detection's hand-rolled darkness-heuristic with real OpenCV template matching (multi-scale, per corner) — the same core technique the open-source OMRChecker project uses. This is a shape check, not just a brightness check, so it should no longer mistake dark backgrounds (grass, walls, tables, random objects) for a marker, and should be more forgiving of real markers under uneven lighting.",
                "Restored cropping the captured photo to the on-screen guide region (plus padding) before running detection — this had been lost in an earlier rewrite, which meant detection was searching a fixed window of the WHOLE photo and could miss the real sheet entirely whenever it didn't fill the frame edge to edge. This was the main reason real sheets were failing to scan.",
                "The live green-corner hint now uses the same template-matching approach, searching a small window at each corner's exact expected position instead of a broad quadrant."
            )
        ),
        ChangelogEntry(
            version = "0.7",
            date = "2026-07-08",
            notes = listOf(
                "Removed auto-capture. Two attempts at tuning the live 'is this aligned' check (from screen recordings) still misfired on real devices — capturing on non-sheet objects, or firing while the sheet was still being positioned. Rather than keep guessing at thresholds I can't test myself, capture is manual again (tap the button), which was already reliable before auto-capture was added.",
                "The corner guide squares still turn green live as a visual aid — checking each corner for an actual small solid blob, not just 'is it dark here' — so you can see when it looks lined up before tapping Capture yourself.",
                "Fixed more invisible text in Dark/Black themes (the 'Result' title on the scan screen had the same colorPrimary-as-text bug as before)."
            )
        ),
        ChangelogEntry(
            version = "0.6",
            date = "2026-07-08",
            notes = listOf(
                "Added real-time alignment detection while scanning: each corner guide turns green live as the camera sees the sheet's marker in roughly the right spot — no need for pixel-perfect alignment, just enough overlap counts.",
                "Capture now happens automatically once all 4 corners stay aligned for about half a second. Manual capture (tap the button) still works too.",
                "Added vibration feedback on both auto-capture and manual capture.",
                "Loosened the post-capture marker validation thresholds — v0.4's checks were tuned by reasoning alone and turned out too strict for real paper/lighting; still rejects clutter, just with more real-world tolerance.",
                "Removed the underscore blanks from the printed sheet's Name/Date/Gr.&Section/Score cells now that they have real borders — the border is enough."
            )
        ),
        ChangelogEntry(
            version = "0.5",
            date = "2026-07-08",
            notes = listOf(
                "Fixed the camera not capturing what the live preview showed — preview and capture now share the same field of view (a CameraX ViewPort), so a sheet that filled the guide frame on screen no longer comes out tiny/off-center in the actual photo. This was the bug blocking scanning entirely.",
                "Fixed bubbles overlapping on ½-page sheets — bubble size is now derived from the tighter of actual row pitch and column spacing for each paper size, instead of page width alone.",
                "Redesigned the details header into a real bordered table: Name/Date on one row, Gr. & Section/Score on the next (Score cell taller, for writing room), all cells vertically centered.",
                "Moved the test title below the details table, with the top corner markers repositioned to flank it instead of sitting at the very top edge.",
                "Fixed more invisible text in Dark/Black themes (school name and version number on the home screen, title on the changelog screen) — same colorPrimary-as-text issue as the answer-key screen fix in v0.4."
            )
        ),
        ChangelogEntry(
            version = "0.4",
            date = "2026-07-08",
            notes = listOf(
                "Fixed the scan overlay guide not matching a ½-page sheet's actual shape — it now follows each paper size's real proportions (full, ½, and ¼ page).",
                "Much stricter sheet detection: the scanner now checks that each corner marker is actually a small solid square (not just 'the darkest spot nearby'), and rejects photos where the 4 corners don't form a plausible sheet outline — so pointing the camera at a random object no longer produces a fake score.",
                "Added a final sanity check after grading: if too many answers come out ambiguous or blank, the app now says so instead of reporting a misleading score.",
                "Fixed invisible text in the Dark and Black themes on the answer-key screen — some text and bubble outlines were using a color meant for backgrounds, not text."
            )
        ),
        ChangelogEntry(
            version = "0.3",
            date = "2026-07-07",
            notes = listOf(
                "Fixed a crash when tapping 'Create Answer Key Here' — the printable sheet layout was silently failing to pass between screens; save/load a test should now work reliably too.",
                "Added a boxed border around the Name/Grade/Section/Date/Score details area, and a separate box around the whole bubble grid, on the printed sheet.",
                "Removed the 'Down each column' / 'Across each row' numbering option — questions are always numbered down each column now.",
                "Scanning now happens inside the app with a live camera preview instead of jumping out to your phone's camera app, with on-screen guide squares to help line up the sheet's corner markers before capturing.",
                "The scan result now appears in a dedicated panel right after capture, with a 'Scan Next' option to keep grading sheets without leaving the screen."
            )
        ),
        ChangelogEntry(
            version = "0.2",
            date = "2026-07-07",
            notes = listOf(
                "Redesigned the printed answer sheet: bigger, bolder bubbles and text in a formal sans-serif style, condensed spacing instead of stretching to fill the page.",
                "Every block of questions now uses the same row spacing as item 1 and item 11, even a partial last block (e.g. 21-25) — no more oddly spread-out rows.",
                "If the sheet has extra vertical space, it's centered on the page instead of being stretched to fill it.",
                "Added Grade and Section fields below Name, and Date / Score fields on the right.",
                "Removed the typed answer-key field. Added 'Create Answer Key Here' — a tap-the-bubble screen generated from your question count, right above 'Scan Answer Key Instead'.",
                "Added customizable points per item on the new answer-key screen (defaults to 1 pt each, with an 'apply to all' shortcut).",
                "Scores are now reported in points earned / points possible, not just items correct."
            )
        ),
        ChangelogEntry(
            version = "0.1",
            date = "2026-07-07",
            notes = listOf(
                "Redesigned sheet layout: compact ZipGrade-style blocks of 10 questions, no student-ID grid, to save paper.",
                "Added per-test paper-size option: Full page, ½ page, or ¼ page (multiple sheets tiled per physical page, cut apart before handing out).",
                "Added 'Scan Answer Key' — capture a filled key sheet instead of typing the key by hand.",
                "Fixed camera not opening (blank screen) on Android 11+ by declaring package-visibility <queries> for the camera intent.",
                "Sheets are numbered dynamically based on however many questions the test has."
            )
        ),
        ChangelogEntry(
            version = "0.0",
            date = "2026-07-07",
            notes = listOf(
                "Fresh restart of MCQ Scanner as a true OMR (bubble-sheet) app.",
                "Ported OMR architecture: SheetTemplate, SheetGenerator, MarkerDetector, PerspectiveWarp, BubbleGrader.",
                "Added Tarlac National High School branding: logo on home screen and launcher icon, maroon/gold accent theme.",
                "Added in-app changelog, accessible by tapping the version number.",
                "Developed by Sir_JPagdi."
            )
        )
    )
}
