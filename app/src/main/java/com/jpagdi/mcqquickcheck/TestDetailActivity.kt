package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Hub screen for one test — reached by tapping a test's row on the Saved
 * Tests screen. Every per-test action (template, answer key, scans,
 * summary/analysis, scanning) lives here instead of being scattered
 * across the old home screen's buttons.
 */
class TestDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_NAME = "testName"
    }

    private lateinit var testName: String
    private var savedTest: SavedTest? = null

    // v2.8 fold-in fix (item 8): THIS is the actual bug — GenerateSheetActivity
    // never writes to TestStorage itself, by design (see its own useTemplate()):
    // it packages the edited template/key into a result Intent and expects
    // the CALLER to persist it, exactly like MainActivity's new-test flow
    // already does correctly via its own registerForActivityResult launcher.
    // openAnswerSheetTemplate() below was using a plain startActivity() with
    // no result launcher at all — so every edit made on that screen was
    // built, returned, and then silently thrown away; TestStorage was never
    // touched, and Test Detail kept showing the old template forever.
    private val editTemplateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        @Suppress("DEPRECATION")
        val newTemplate = result.data?.getSerializableExtra(GenerateSheetActivity.EXTRA_RESULT_TEMPLATE) as? SheetTemplate
        val newLayoutName = result.data?.getStringExtra(GenerateSheetActivity.EXTRA_RESULT_PAPER_LAYOUT)
        val newKey = result.data?.getIntArrayExtra(GenerateSheetActivity.EXTRA_RESULT_ANSWER_KEY)
        val newPoints = result.data?.getIntArrayExtra(GenerateSheetActivity.EXTRA_RESULT_POINTS)
        if (newTemplate == null) return@registerForActivityResult

        val updated = SavedTest(
            template = newTemplate,
            // A key not coming back in the result means it no longer
            // matches the (possibly changed) question count — same "needs
            // recreating" case GenerateSheetActivity's own tvKeyStatus
            // already warns about, so clearing it here is consistent
            // rather than silently keeping a now-invalid key around.
            answerKey = newKey?.toList() ?: emptyList(),
            paperLayout = PaperLayout.fromStorageName(newLayoutName),
            points = newPoints?.toList() ?: newKey?.let { List(it.size) { 1 } } ?: emptyList(),
            // v1.2.0: classes assigned on the edit screen (it was seeded with
            // this test's current list, so the result is the full new list).
            classIds = result.data?.getStringArrayExtra(GenerateSheetActivity.EXTRA_RESULT_CLASS_IDS)?.toList()
                ?: savedTest?.classIds.orEmpty()
        )

        // Handle the test being renamed while editing: TestStorage.saveTest
        // is a plain upsert keyed by test name, so saving under a NEW name
        // would leave the OLD entry behind as an orphan rather than
        // actually renaming it.
        // v1.2.5: per-set competency placements ride along (deleteTest below
        // would drop them); sets that no longer exist are discarded.
        val keptSetCompetencies = SetCompetencyStore.all(this, testName).filterKeys { it < newTemplate.setCount }
        if (newTemplate.testName != testName) {
            // v1.2.0: move this test's learner scores to the new name FIRST —
            // deleteTest() below clears the old name's results.
            LearnerResultStorage.renameTestReferences(this, testName, newTemplate.testName)
            // v1.2.5: deleteTest() below also drops the old name's set keys; the
            // edited keys are written under the new name right after saving.
            TestStorage.deleteTest(this, testName)
            testName = newTemplate.testName
        }
        TestStorage.saveTest(this, updated)
        SetKeyStore.replaceAll(
            this, newTemplate.testName,
            if (newTemplate.hasSets) SetKeyStore.decode(result.data?.getStringExtra(GenerateSheetActivity.EXTRA_RESULT_SET_KEYS)) else emptyMap()
        )
        SetCompetencyStore.replaceAll(this, newTemplate.testName, if (newTemplate.hasSets) keptSetCompetencies else emptyMap())
        Toast.makeText(this, "Answer sheet template updated", Toast.LENGTH_SHORT).show()
        // onResume() already reloads and re-renders from TestStorage, so no
        // separate refresh call needed here — it fires right after this.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_detail)

        testName = intent.getStringExtra(EXTRA_TEST_NAME) ?: run { finish(); return }

        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        DrawerHelper.attach(this, drawerLayout, findViewById(R.id.btnBack))
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
        findViewById<View>(R.id.btnExportScans).setOnClickListener { HapticUtils.confirm(it); ScannedSheetExporter.promptThenExport(this, testName) }
        findViewById<View>(R.id.btnDeleteTest).setOnClickListener { HapticUtils.confirm(it); confirmDeleteTest() }

        findViewById<View>(R.id.btnAnswerSheetTemplate).setOnClickListener { openAnswerSheetTemplate() }
        findViewById<View>(R.id.btnScannedSheets).setOnClickListener { openScannedSheets() }
        findViewById<View>(R.id.btnItemAnalysis).setOnClickListener { openItemAnalysis() }
        findViewById<View>(R.id.btnScanFilledSheets).setOnClickListener { startScanningAllFolders() }
        findViewById<View>(R.id.summarySwipeContainer).setOnTouchListener { _, event ->
            summaryGestureDetector.onTouchEvent(event)
        }
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        // Reload every time — answer key/template/points could have
        // changed via any of the screens this launches into.
        val t = try {
            TestStorage.loadTest(this, testName)
        } catch (e: TestStorage.LegacyLayoutException) {
            // v3.4: this test was created with a PaperLayout value that no
            // longer exists (HALF_CROSS/HALF_LENGTH — see PaperLayout.kt's
            // doc comment for why this is a deliberate rejection, not an
            // auto-migration). e.message is already a complete, teacher-
            // facing sentence — see LegacyLayoutException's own message.
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (t == null) {
            finish()
            return
        }
        savedTest = t
        renderHeader(t)
        // v3.6.12: btnScannedSheets is now a rounded-card row (icon + label
        // + chevron), not a Button — the scan count moved out to its own
        // badge TextView (tvScannedSheetsCount) instead of being folded
        // into the row's own label text.
        val scanCount = ScannedSheetStorage.listScansForTest(this, testName).size
        findViewById<TextView>(R.id.tvScannedSheetsCount).text = scanCount.toString()
        refreshInlineSummary()
        refreshClassesCard(t)
    }

    // v1.2.0 (Classes feature): a "Classes" card under Item Analysis showing
    // which enrolled classes take this exam. Built in code (the big layout
    // XML is untouched) and rebuilt each onResume. Shown while Classes is
    // active, or read-only when the test already has classes assigned (a
    // lapsed plan or switch-off never hides existing records).
    private var classesCard: View? = null

    private fun refreshClassesCard(t: SavedTest) {
        classesCard?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        classesCard = null
        val active = ClassesAccess.isActive(this)
        if (!active && t.classIds.isEmpty()) return
        val anchor = findViewById<View>(R.id.btnItemAnalysis)
        val parent = anchor.parent as? android.view.ViewGroup ?: return
        val digits = t.template.idDigits
        val sub = ClassAssignDialog.summary(this, t.classIds) +
            if (digits > 0) " · $digits-digit ID" else " · sheet has no ID row"
        val card = ClassUi.card(this, "CLASSES", sub) { openClassesCard() }
        (card.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = ClassUi.dp(this, 12)
        parent.addView(card, parent.indexOfChild(anchor) + 1)
        classesCard = card
    }

    private fun openClassesCard() {
        val t = savedTest ?: return
        val active = ClassesAccess.isActive(this)
        val classes = ClassStorage.getClasses(this, t.classIds)
        val labels = ArrayList<String>()
        classes.forEach { labels.add("Scores: ${it.name}") }
        if (active) labels.add("Assign classes…")
        if (labels.isEmpty()) return
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Classes")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < classes.size) {
                    startActivity(
                        Intent(this, ClassSheetActivity::class.java)
                            .putExtra(ClassSheetActivity.EXTRA_TEST_NAME, testName)
                            .putExtra(ClassSheetActivity.EXTRA_CLASS_ID, classes[which].id)
                    )
                } else {
                    assignClasses(t)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        DialogColors.fix(this, dialog)
    }

    private fun assignClasses(t: SavedTest) {
        ClassAssignDialog.show(this, t.classIds, t.template.idDigits) { chosen ->
            val updated = t.copy(classIds = chosen)
            TestStorage.saveTest(this, updated)
            savedTest = updated
            refreshClassesCard(updated)
            if (chosen.isNotEmpty() && t.template.idDigits == 0) {
                val note = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("This sheet has no ID row")
                    .setMessage(
                        "Scans of this exam's current sheets can't be matched to learners automatically because they have no ID row. " +
                        "Open Answer Sheets and save it once — a sheet edited with classes assigned gets an ID row — then print the new sheets. " +
                        "Until then you can still link scores by hand from each class's score sheet."
                    )
                    .setPositiveButton("OK", null)
                    .create()
                note.show()
                DialogColors.fix(this, note)
            }
        }
    }

    // v3.0: which "page" of the summary is currently showing — index 0 is
    // always "All Folders" (folder=null, population SD), followed by one
    // page per actual folder (including Main) in listFoldersForTest order.
    // Rebuilt every onResume (folders/scans can change between visits),
    // but the current PAGE INDEX itself is preserved across that rebuild
    // (clamped to the new page count) so swiping to a folder and then
    // coming back from, say, Item Analysis doesn't reset you to page 0.
    private var summaryPageIndex = 0

    // v3.1 FIX: Main used to be included here as its own swipeable page,
    // separate from "All Folders" (null) — but Main's own summary is
    // functionally the same thing as All Folders' summary once real
    // folders exist (per Sir Julius: they're "just the same"), so showing
    // it as a second, separate page was redundant. Real custom folders
    // each still get their own page; only MAIN_FOLDER itself is filtered
    // out of this list.
    private fun summaryPages(): List<String?> =
        listOf(null) + ScannedSheetStorage.listFoldersForTest(this, testName)
            .filter { it != ScannedSheetStorage.MAIN_FOLDER }

    /** v3.0: replaces the flat "all folders combined" inline summary with
     *  a swipeable one — same underlying stats (TestSummaryDialog.
     *  buildSummaryText, unchanged computation), one folder at a time.
     *  Test title removed from the body (redundant with this screen's own
     *  header) and the maroon accent panel removed in favor of plain
     *  theme-adaptive text, per explicit request. */
    private fun refreshInlineSummary() {
        val pages = summaryPages()
        if (summaryPageIndex >= pages.size) summaryPageIndex = 0
        val folder = pages[summaryPageIndex]
        val labelView = findViewById<TextView>(R.id.tvSummaryFolderLabel)
        val bodyView = findViewById<TextView>(R.id.tvInlineSummary)
        val body = TestSummaryDialog.buildSummaryText(this, testName, folder, includeTitle = false)
        val folderLabel = folder ?: "All Folders"

        // v3.1 FIX: previously both views went GONE whenever the current
        // page had zero scored scans (buildSummaryText returns null) —
        // e.g. swiping to a folder nothing has been scanned into yet, or
        // to Main specifically when every scan actually landed in a
        // different named folder (Main isn't forced to mirror "All
        // Folders" — a test can legitimately have zero Main-folder scans
        // once real folders are in use, same as SavedScansListActivity
        // already allows). Collapsing both views to GONE shrank this whole
        // section to zero height, visually yanking the buttons below up
        // right under the header. Now this section always renders SOMETHING
        // with real height — either the real stats or a plain empty-state
        // line — so the buttons' position never depends on which page has
        // data.
        if (pages.size > 1) {
            labelView.text = "◂ $folderLabel ▸"
            labelView.visibility = View.VISIBLE
        } else {
            labelView.visibility = View.GONE
        }
        bodyView.text = body ?: if (folder == null) {
            "Scan at least one sheet to see a summary."
        } else {
            "Scan at least one sheet into \"$folderLabel\" to see its summary."
        }
        bodyView.visibility = View.VISIBLE
    }

    private fun showSummaryPage(delta: Int) {
        val pages = summaryPages()
        if (pages.size <= 1) return
        summaryPageIndex = ((summaryPageIndex + delta) % pages.size + pages.size) % pages.size
        HapticUtils.confirm(findViewById(R.id.summarySwipeContainer))
        refreshInlineSummary()
    }

    private val summaryGestureDetector by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            // v3.1 (item 2) FIX: SimpleOnGestureListener.onDown() defaults to
            // returning false. When a View's OnTouchListener returns false
            // for ACTION_DOWN, that view gets NO further events (ACTION_MOVE/
            // ACTION_UP) for that gesture at all — so onFling() below could
            // never fire, no matter how the swipe itself was written. This is
            // a well-known GestureDetector integration pitfall, not a mystery
            // — returning true here is all that was missing.
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                // Require a clearly-horizontal, clearly-intentional swipe —
                // otherwise a slightly-diagonal scroll gesture on this
                // screen's ScrollView would keep misfiring page changes.
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 80 && kotlin.math.abs(velocityX) > 200) {
                    if (dx < 0) showSummaryPage(1) else showSummaryPage(-1)
                    return true
                }
                return false
            }
        })
    }

    private fun renderHeader(t: SavedTest) {
        findViewById<TextView>(R.id.tvTestDetailTitle).text = t.template.testName
        findViewById<TextView>(R.id.tvTestDetailPreferences).text = buildString {
            append(layoutDisplayName(t.paperLayout))
            append(" · ${t.template.questionCount} items")
            append(" · ${t.template.choiceCount} choices")
            append(" · ${t.template.maxBlocksPerRowGroup} col")
            if (t.answerKey.isEmpty()) append(" · No answer key yet")
        }
    }

    /** v3.4: QUARTER is now the only PaperLayout value (HALF_CROSS/
     *  HALF_LENGTH removed). Kept as a `when` over the enum rather than
     *  inlined so this stays exhaustive if a layout is ever added back. */
    private fun layoutDisplayName(layout: PaperLayout): String = when (layout) {
        PaperLayout.QUARTER -> "Quarter Sheet"
    }

    private fun openAnswerSheetTemplate() {
        val t = savedTest ?: return
        val intent = Intent(this, GenerateSheetActivity::class.java)
            .putExtra(GenerateSheetActivity.EXTRA_EXISTING_TEMPLATE, t.template)
            .putExtra(GenerateSheetActivity.EXTRA_EXISTING_PAPER_LAYOUT, t.paperLayout.name)
            .putExtra(GenerateSheetActivity.EXTRA_EXISTING_CLASS_IDS, t.classIds.toTypedArray())
        if (t.answerKey.isNotEmpty()) {
            intent.putExtra(GenerateSheetActivity.EXTRA_ANSWER_KEY, t.answerKey.toIntArray())
            intent.putExtra(GenerateSheetActivity.EXTRA_ANSWER_KEY_POINTS, t.points.toIntArray())
        }
        editTemplateLauncher.launch(intent)
    }

    private fun openScannedSheets() {
        startActivity(
            Intent(this, SavedScansListActivity::class.java)
                .putExtra(SavedScansListActivity.EXTRA_TEST_NAME, testName)
        )
    }

    private fun openItemAnalysis() {
        startActivity(
            Intent(this, ItemAnalysisActivity::class.java)
                .putExtra(ItemAnalysisActivity.EXTRA_TEST_NAME, testName)
        )
    }

    /** v2.9 FIX: this used to always hardcode MAIN_FOLDER with no choice
     *  at all — the folder-picker step referenced in SavedScansListActivity's
     *  own comments ("unlike MainActivity's version") had been lost from
     *  here at some point before this pass; nothing else in the app
     *  currently launches a NEW grading scan session with a folder choice.
     *  Restored per spec: if the test has no folders beyond Main, skip
     *  the prompt entirely and just scan into Main (no pointless dialog
     *  for the common case) — if the teacher HAS created subfolders,
     *  ask which one new scans should land in before starting the
     *  camera, since silently defaulting to Main there would put scans
     *  in the wrong place with no warning. */
    private fun startScanningAllFolders() {
        val t = savedTest ?: return
        if (t.answerKey.isEmpty()) {
            android.widget.Toast.makeText(this, "Set up an answer key first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        ActiveTest.current = t
        val folders = ScannedSheetStorage.listFoldersForTest(this, testName)
        if (folders.size <= 1) {
            // Only Main exists — nothing to choose, proceed straight to the
            // camera, per spec ("if no folder, it's ok to proceed with scan").
            launchScan(ScannedSheetStorage.MAIN_FOLDER)
        } else {
            val labels = folders.map { if (it == ScannedSheetStorage.MAIN_FOLDER) "Main" else it }.toTypedArray()
            val dialog = AlertDialog.Builder(this)
                .setTitle("Save scans to which folder?")
                .setItems(labels) { _, which -> launchScan(folders[which]) }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
            fixDialogButtonColors(dialog)
        }
    }

    private fun launchScan(folder: String) {
        startActivity(
            Intent(this, ScanActivity::class.java)
                .putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_GRADE)
                .putExtra(ScanActivity.EXTRA_SCAN_FOLDER, folder)
        )
    }

    private fun confirmDeleteTest() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete test")
            .setMessage("Delete \"$testName\"? This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                TestStorage.deleteTest(this, testName)
                ScannedSheetStorage.deleteAllScansForTest(this, testName)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    /** Same fix used everywhere else in this app — AlertDialog buttons
     *  default to colorPrimary text, unreadable in Dark/Black themes. */
    private fun fixDialogButtonColors(dialog: AlertDialog) {
        HapticUtils.confirm(dialog.window?.decorView)
        val color = if (ThemePrefs.getTheme(this) == AppTheme.LIGHT) {
            android.graphics.Color.parseColor("#221111")
        } else {
            android.graphics.Color.WHITE
        }
        for (which in intArrayOf(
            android.content.DialogInterface.BUTTON_POSITIVE,
            android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL
        )) {
            dialog.getButton(which)?.setTextColor(color)
        }
        // v2.8 fold-in fix: the loop above only ever recolored the BUTTONS.
        // The dialog's title/message text still fell back to the theme's
        // own textColorPrimary/textColorSecondary, which is unset/wrong in
        // this app's themes the same way button text was (see this
        // function's own doc comment) — invisible in Light theme
        // specifically for message text, and unreliable in Dark/Black too.
        // android.R.id.message is part of the core Android alert dialog
        // layout AppCompat reuses, so it's a stable id to reach into here.
        // androidx.appcompat.R.id.alertTitle is the equivalent stable,
        // public id for the title in AppCompat's alert layout — not a
        // private/internal resource, safe to reference directly.
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
