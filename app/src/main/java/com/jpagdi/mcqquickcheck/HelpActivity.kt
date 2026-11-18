package com.jpagdi.mcqquickcheck

import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Static tutorial/how-to content (v2.9). Reuses the shared placeholder
 * layout the same way AboutActivity/BackupDataActivity/RestoreDataActivity
 * do — a title + scrollable body + Back button, no new layout file needed.
 * Content is a plain string built in Kotlin rather than a strings.xml
 * resource, matching how About's description/license text already work
 * in this app, so it's easy to keep updating alongside new features
 * without touching resource files.
 */
class HelpActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)

        DrawerHelper.attach(
            this,
            findViewById(R.id.drawerLayout),
            findViewById(R.id.btnOpenDrawer)
        )
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Help / FAQ"

        val bodyView = findViewById<TextView>(R.id.tvPlaceholderBody)
        // v3.1: switched from a plain string to a SpannableStringBuilder so
        // category headers ("GETTING STARTED" etc.) and each question line
        // ("Q: ...") can be bolded — plain appendLine() text has no way to
        // vary style within itself. Answer lines stay regular weight so the
        // bold questions actually stand out when scanning the page.
        val body = android.text.SpannableStringBuilder()
        fun category(text: String) {
            val start = body.length
            body.append(text).append('\n')
            body.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, body.length - 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        fun question(text: String) {
            val start = body.length
            body.append(text).append('\n')
            body.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, body.length - 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        fun answer(text: String) {
            body.append(text).append('\n')
        }
        fun blank() {
            body.append('\n')
        }

        category("GETTING STARTED")
        // v3.1 FIX: this referenced a "New Test" button that doesn't exist —
        // the home screen has only the big round "＋" FAB (id btnAddNewTest),
        // no separate labeled button. Corrected to match what's actually there.
        question("Q: How do I create a new test?")
        answer("A: From the home screen, tap the + button in the bottom-right corner, set the number of questions, choices, and paper layout, then generate the answer sheet template.")
        blank()
        question("Q: How do I set the answer key?")
        answer("A: From a test's Answer Sheets screen, tap \"Create Answer Key\" to enter it by hand, or scan a filled-in key sheet with the camera and confirm it with \"Use This as Answer Key.\"")
        blank()
        question("Q: Can I add a parent/guardian signature line to printed answer sheets?")
        answer("A: Yes — turn on \"Parent/Guardian signature line\" in Settings. It won't show up in the on-screen preview, but it prints on the actual generated answer sheet, just below the scannable area.")
        blank()

        category("SCANNING")
        question("Q: How do I scan filled-in student sheets?")
        answer("A: Open the test, tap \"Scan Answer Sheets,\" and line each sheet up inside the on-screen guide. The four corner markers must be fully visible for a sheet to be accepted.")
        blank()
        question("Q: A sheet keeps getting rejected during scanning — what do I do?")
        answer("A: Make sure all four corner markers are visible and the sheet isn't heavily shadowed, crumpled, or at a steep angle. Good, even lighting matters more than a perfectly steady hand.")
        blank()
        question("Q: Can I organize scans into groups, like different class sections?")
        answer("A: Yes — from a test's \"Scanned Sheets\" screen, use \"Add folder\" to create named folders (e.g. per section), and move scans between them. If a test has folders beyond Main, you'll be asked which folder new scans should save to before the camera opens.")
        blank()
        question("Q: How do I move or delete several scanned sheets at once?")
        answer("A: On the Scanned Sheets screen, long-press a row's trash icon to bulk-delete, or long-press a row's send icon to bulk-move to another folder. Either way, use the checkmark icon in the header to select or clear everything currently shown.")
        blank()
        question("Q: What does \"Save List\" do on the Scanned Sheets screen?")
        answer("A: It saves the entire scanned-sheets list for that folder — including rows off the bottom of the screen — as one full-resolution image file you can download or share, handy for printing or keeping a plain record of scores.")
        blank()

        category("RESULTS & ANALYSIS")
        question("Q: Where do I see a test's overall results?")
        answer("A: Open the test — the summary (count, highest/lowest, mean, median, mode, standard deviation, and passing rates) shows directly on the Test Detail screen once at least one sheet has been scored.")
        blank()
        question("Q: What is Item Analysis?")
        answer("A: A per-question breakdown showing difficulty (P) and discrimination (D) for every item, plus whole-class results — number and percent of correct responses, a Mastery Level (Mastered/Nearly Mastered/Below Mastery), and a Least Learned rank from 1 (least mastered) to n (most mastered) — so you can spot questions that may need revising or reteaching. Open it from the Test Detail screen, and export it to Excel (.xlsx) if you want to keep or share it.")
        blank()

        category("CLASSES & LEARNER IDS (SEMESTER / FULL YEAR)")
        question("Q: What does the Classes feature do?")
        answer("A: You enroll each class once (ID, name and gender), assign it to an exam, and every scan's score is saved to the right learner automatically. Open a class's score sheet from the exam's Classes row to see each learner's score, sort the list, and export it to Excel. It is available on the Semester and Full-Year plans and switched on under Settings > Learner identification.")
        blank()
        question("Q: How do I add my learners?")
        answer("A: Sidebar > Classes > Add Class, then Import Roster to load a CSV or Excel file with columns for ID, Name (or Last Name and First Name) and Gender (M/F), or add learners one at a time. A DepEd LRN works as the ID. Format the ID column as Text first so Excel doesn't drop a leading zero. Rows with a missing or wrong-length ID are skipped and listed so you can fix them.")
        blank()
        question("Q: How does the app know which learner a sheet belongs to?")
        answer("A: With Learner identification on, newly generated sheets have an ID row of boxes under Section. Learners write their ID one digit per box. The scanner reads the handwriting and picks the closest match among the classes assigned to that exam. If it isn't sure it still grades the sheet and files it under \"Needs ID check\" on the class score sheet, where you tap the right learner.")
        blank()
        question("Q: How many ID digits can I use?")
        answer("A: 4 to 12, set with the stepper in Settings (default 6). The number is fixed into each sheet when you generate it, so changing it later only affects new sheets. Learners whose ID has a different length can't be matched to that exam's sheet, and you'll be warned when you assign the class.")
        blank()
        question("Q: Are my learners' names and IDs sent anywhere?")
        answer("A: No. Classes stay on your phone. They are not sent as part of diagnostic reports. They are included in Backup Data files, so keep those files private.")
        blank()

        category("EXPORTING & SAVING")
        question("Q: Where do exported/downloaded files go?")
        answer("A: Anything you choose \"Download\" for (answer sheet PDFs, item analysis, prefilled test sheets) saves to your phone's normal Downloads folder. \"Share\" instead hands the file directly to another app (Drive, email, etc.) without saving a separate copy on the phone.")
        blank()
        question("Q: How do I back up my tests so I don't lose them?")
        answer("A: From the sidebar, use \"Backup Data\" to save everything to a file, and \"Restore Data\" on this or another phone to load it back in. If that phone already has saved tests or scans, you'll be asked to confirm clearing them first so the restore starts from a clean slate instead of merging.")
        blank()

        category("PRIVACY & DIAGNOSTICS")
        question("Q: What does \"Send Diagnostic Report\" do?")
        answer("A: It securely sends an anonymous report to help improve the scanner's accuracy — device/app info, recent scan activity, and an optional note from you if you add one. It never includes student names, answer sheets, photos, PDFs, or exam content. Find it from the About screen; nothing is sent until you confirm and tap \"Send Report.\"")
        blank()

        category("APPEARANCE")
        question("Q: Can I change how the app looks?")
        answer("A: Yes — the sidebar's \"Theme\" row cycles Light/Dark/Black, and Settings lets you pick your own accent colors for buttons and highlights throughout the app.")
        blank()

        body.append("Still stuck? Long-press the app title on the About screen for a hidden test-sheet generator you can use to try out scanning without a real class, or check the changelog (tap the version number) to see what's changed recently.")
        bodyView.text = body
        // Same API-26 guard as About/other placeholder screens — this
        // app's minSdk is 24, and JUSTIFICATION_MODE_INTER_WORD doesn't
        // exist below API 26.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bodyView.justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
        }

        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
    }
}
