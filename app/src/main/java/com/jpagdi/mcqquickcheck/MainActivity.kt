package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.drawerlayout.widget.DrawerLayout

/**
 * Home screen — the Saved Tests list itself (replaces the old
 * button-based home screen; SavedTestsListActivity, which used to be a
 * separate picker reached via a "Load Test" button, is now fully
 * superseded by this and has been removed). Tapping a test's row opens
 * TestDetailActivity, the per-test hub for everything else (template,
 * answer key, scans, summary/analysis, scanning).
 *
 * Still owns the "+ new test" flow (GenerateSheetActivity ->
 * auto-save-as-test, including the same-title conflict prompts) since
 * that's inherently a home-screen-level action, not a per-test one.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvVersion: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var container: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvSavedTestsTitle: TextView
    private lateinit var btnToggleSelect: ImageView
    private lateinit var btnDeleteSelected: ImageView

    private var selectMode = false
    private val selectedTests = mutableSetOf<String>()

    // Transient state for the "+ new test" flow only — GenerateSheetActivity
    // reports back what was confirmed, this activity saves it. Not a
    // "currently loaded test" concept anymore; nothing else on this screen
    // reads these fields for display.
    private var pendingTemplate: SheetTemplate? = null
    private var pendingPaperLayout: PaperLayout = PaperLayout.QUARTER
    private var pendingAnswerKey: List<Int>? = null
    private var pendingPoints: List<Int>? = null
    // v1.2.0: classes picked on the exam-setup screen, saved with the test.
    private var pendingClassIds: List<String> = emptyList()
    // v1.2.5: keys of sets 2..4 coming back from the generator; written in commitSaveTest.
    private var pendingSetKeys: Map<Int, List<Int>> = emptyMap()

    private val generateSheetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val newTemplate = result.data?.getSerializableExtra(GenerateSheetActivity.EXTRA_RESULT_TEMPLATE) as? SheetTemplate
            val newLayoutName = result.data?.getStringExtra(GenerateSheetActivity.EXTRA_RESULT_PAPER_LAYOUT)
            val newKey = result.data?.getIntArrayExtra(GenerateSheetActivity.EXTRA_RESULT_ANSWER_KEY)
            val newPoints = result.data?.getIntArrayExtra(GenerateSheetActivity.EXTRA_RESULT_POINTS)
            pendingClassIds = result.data?.getStringArrayExtra(GenerateSheetActivity.EXTRA_RESULT_CLASS_IDS)?.toList() ?: emptyList()
            pendingSetKeys = SetKeyStore.decode(result.data?.getStringExtra(GenerateSheetActivity.EXTRA_RESULT_SET_KEYS))
            if (newTemplate != null) {
                pendingTemplate = newTemplate
                pendingPaperLayout = PaperLayout.fromStorageName(newLayoutName)
                if (newKey != null) {
                    pendingAnswerKey = newKey.toList()
                    pendingPoints = newPoints?.toList() ?: List(newKey.size) { 1 }
                }
                // v2.4: confirming a template saves it as a test in the same
                // step. saveCurrentTest() handles the same-title conflict prompt.
                saveCurrentTest()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Fixes the "my scans disappeared after updating" report: v1.9's
        // storage rewrite left pre-v1.9 scans unreadable (not deleted) —
        // see ScannedSheetStorage.migrateLegacyScansIfNeeded for detail.
        // Additive/idempotent, safe to call every launch.
        ScannedSheetStorage.migrateLegacyScansIfNeeded(this)
        SheetGenerator.ensureFontLoaded(this)
        // v3.0: sweep leftover cache scratch files on every fresh app
        // open — see CacheSweeper's doc comment for what this catches on
        // top of the main leak fix (ScanActivity now deletes its own
        // capture file immediately after each attempt).
        CacheSweeper.sweepOnColdStart(this, savedInstanceState)

        tvVersion = findViewById(R.id.tvVersion)
        drawerLayout = findViewById(R.id.drawerLayout)
        container = findViewById(R.id.savedTestsContainer)
        tvEmpty = findViewById(R.id.tvSavedTestsEmpty)
        tvSavedTestsTitle = findViewById(R.id.tvSavedTestsTitle)
        btnToggleSelect = findViewById(R.id.btnToggleSelect)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)

        // v3.6.10: Typeface set here in code, not via android:fontFamily in
        // XML. AppCompatActivity's view inflater backports fontFamily
        // resolution to every TextView it inflates (see the app-wide
        // Montserrat wiring in themes.xml) by force-applying the theme's
        // font after inflation — on this app's minSdk that backport wins
        // over a per-view android:fontFamily XML attribute, not the other
        // way around, so this view kept rendering Montserrat no matter
        // what the XML said. Setting typeface directly in Kotlin runs
        // after that backport and can't be overridden by it.
        // (The matching sidebar titles are wired the same way, but in
        // DrawerHelper.attach() below instead of here — that's the one
        // place shared by every screen with sidebar access, not just this
        // one, so it's set once there rather than copy-pasted into each
        // Activity.)
        val pressStart2P = ResourcesCompat.getFont(this, R.font.press_start_2p)
        findViewById<TextView>(R.id.tvHeaderTitleLine1).also {
            it.typeface = pressStart2P
            it.requestLayout()
        }
        findViewById<TextView>(R.id.tvHeaderTitleLine2).also {
            it.typeface = pressStart2P
            it.requestLayout()
        }

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        // The version text is plain text; the changelog is opened by a
        // long-press on "Version x.y.z" in About.
        tvVersion.text = "v$versionName"

        findViewById<ImageView>(R.id.btnOpenDrawer).also {
            DrawerHelper.attach(this, drawerLayout, it)
        }
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
        resizeScrimToMatchHeader()
        findViewById<ImageView>(R.id.ivLogo).setOnClickListener {
            DrawerHelper.replayWelcomeAnimation(this)
        }

        findViewById<TextView>(R.id.btnAddNewTest).setOnClickListener {
            launchBlankGenerateSheet()
        }

        btnToggleSelect.setOnClickListener { toggleSelectMode() }
        btnDeleteSelected.setOnClickListener {
            HapticUtils.confirm(it)
            confirmDeleteSelected()
        }

        // v2.9: exit confirmation. OnBackPressedCallback (not the old
        // overridden onBackPressed()) is what actually intercepts a
        // gesture-nav swipe-back the same way it intercepts a button
        // back-press — gesture nav on Android 10+ is dispatched through
        // this same callback system, not a separate code path, so one
        // handler covers both input methods with no device-specific
        // branching needed. Only registered on this Activity (the home
        // screen), so every other screen's back behavior is unaffected —
        // this is intentionally NOT a DrawerHelper-wide change.
        onBackPressedDispatcher.addCallback(this) {
            confirmExit()
        }

        rebuildRows()
    }

    private fun confirmExit() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Exit Project A.S.S.E.S.S.?")
            .setMessage("Any unsaved changes on this screen have already been saved automatically.")
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    override fun onResume() {
        super.onResume()
        // A test could have been added/edited/deleted, or a scan saved,
        // since this screen was last shown.
        rebuildRows()
        // Update prompt: the first open of each day checks GitHub in the
        // background (UpdateChecker.autoCheckIfDue). If that finishes
        // while this screen is up, the listener fires; if an update was
        // already cached from an earlier check, it's shown right away.
        UpdateChecker.autoUpdateListener = { maybeShowUpdatePrompt() }
        maybeShowUpdatePrompt()
    }

    override fun onPause() {
        super.onPause()
        UpdateChecker.autoUpdateListener = null
    }

    /** Only one home-screen update prompt on screen at a time. */
    private var promptShowing = false

    /** At most once per calendar day (UpdateChecker.getUpdateToPromptToday
     *  enforces that). "Update Now" hands off to About, which already
     *  owns the notes/download/install flow, instead of duplicating it. */
    private fun maybeShowUpdatePrompt() {
        if (isFinishing || isDestroyed || promptShowing) return
        val update = UpdateChecker.getUpdateToPromptToday(this) ?: return
        UpdateChecker.markPromptedToday(this)
        promptShowing = true
        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("Version ${update.versionName} of Project A.S.S.E.S.S. is ready to download.")
            .setPositiveButton("Update Now") { _, _ ->
                startActivity(Intent(this, AboutActivity::class.java))
            }
            .setNeutralButton("Skip This Version") { _, _ ->
                UpdateChecker.dismissUpdate(this, update.versionCode)
            }
            .setNegativeButton("Later", null)
            .setOnDismissListener {
                promptShowing = false
            }
            .show()
        DialogColors.fix(this, dialog)
    }

    /** Deliberately launches GenerateSheetActivity with no existing-template
     *  extras at all — the app's own definition of "brand new template." */
    private fun launchBlankGenerateSheet() {
        generateSheetLauncher.launch(Intent(this, GenerateSheetActivity::class.java))
    }

    // --- Saved Tests list ---

    private fun toggleSelectMode() {
        selectMode = !selectMode
        selectedTests.clear()
        btnDeleteSelected.visibility = if (selectMode) View.VISIBLE else View.GONE
        rebuildRows()
    }

    private fun confirmDeleteSelected() {
        if (selectedTests.isEmpty()) {
            Toast.makeText(this, "Select at least one test first", Toast.LENGTH_SHORT).show()
            return
        }
        val names = selectedTests.toList()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete ${names.size} test${if (names.size == 1) "" else "s"}?")
            .setMessage("This can't be undone. Every saved scan for ${if (names.size == 1) "this test" else "these tests"} will be deleted too.")
            .setPositiveButton("Delete") { _, _ ->
                names.forEach { name ->
                    TestStorage.deleteTest(this, name)
                    ScannedSheetStorage.deleteAllScansForTest(this, name)
                }
                selectMode = false
                selectedTests.clear()
                btnDeleteSelected.visibility = View.GONE
                rebuildRows()
            }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    private fun rebuildRows() {
        container.removeAllViews()
        // "Most recent activity" = whichever is later: the test's own
        // last-saved time, or the most recent scan saved against it.
        val summaries = TestStorage.listTestSummaries(this)
            .filter { !ArchiveStore.isTestArchived(this, it.name) }   // v1.2.5: archived tests live under "Archived tests"
            .map { summary ->
                val lastScanMillis = ScannedSheetStorage.listScansForTest(this, summary.name)
                    .maxOfOrNull { it.savedAtMillis } ?: 0L
                summary to maxOf(summary.savedAtMillis, lastScanMillis)
            }
            .sortedByDescending { it.second }
            .map { it.first }

        tvEmpty.visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
        // v3.1: "Saved Tests" section heading — shown only when there's
        // actually a list to head, same condition as tvEmpty but inverted.
        tvSavedTestsTitle.visibility = if (summaries.isEmpty()) View.GONE else View.VISIBLE
        if (summaries.isEmpty()) { addArchivedRow(); return }

        val dp = resources.displayMetrics.density
        val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy \u00b7 h:mm a", java.util.Locale.getDefault())

        summaries.forEach { summary ->
            val name = summary.name
            // Rounded-card list style: bg_card_rounded resolves
            // ?attr/colorSurfaceCard per the active theme (Light/Dark/
            // Black) — no branching needed here in code. Deliberately a
            // fixed theme tone, NOT an accent_gold/accent_maroon tag, so
            // it doesn't move when the user changes their accent color.
            // clipToOutline keeps the ripple/press feedback inside the
            // rounded corners instead of spilling into square corners;
            // elevation gives a light shadow so the card reads as raised
            // rather than just a flat-colored rectangle.
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                gravity = Gravity.CENTER_VERTICAL
                background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card_rounded)
                clipToOutline = true
                elevation = 2 * dp
                val rippleAttr = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleAttr, true)
                foreground = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, rippleAttr.resourceId)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * dp).toInt() }
            }

            if (selectMode) {
                val checkbox = CheckBox(this).apply {
                    tag = "accent_gold"
                    isChecked = selectedTests.contains(name)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedTests.add(name) else selectedTests.remove(name)
                    }
                }
                row.addView(checkbox)
            }

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            // v2.9: bumped up from 16f/11f/11f — teacher feedback was that
            // the saved-test list ran small. Metadata lines kept smaller
            // than the title (visual hierarchy), just each a few sp larger
            // than before.
            textColumn.addView(TextView(this).apply { text = name; textSize = 19f })
            val scanCount = ScannedSheetStorage.listScansForTest(this@MainActivity, name).size
            textColumn.addView(TextView(this).apply {
                text = "${summary.preferencesLabel} ($scanCount)"; textSize = 14f; alpha = 0.65f
            })
            // v3.7: date/time made smaller than the preferences line above
            // it (was the same 14f as preferences) — the date is the
            // least important of the three lines, so it now sits at the
            // bottom of the size hierarchy: 19f name, 14f preferences,
            // 12f date.
            textColumn.addView(TextView(this).apply {
                text = if (summary.savedAtMillis > 0) dateFormat.format(java.util.Date(summary.savedAtMillis)) else ""
                textSize = 12f; alpha = 0.65f
            })
            row.addView(textColumn)

            // Whole row is the tap target (not just the title) — a larger,
            // more forgiving touch target than title-only.
            row.setOnClickListener {
                if (selectMode) {
                    val checkbox = (row.getChildAt(0) as? CheckBox) ?: return@setOnClickListener
                    checkbox.isChecked = !checkbox.isChecked
                } else {
                    startActivity(
                        Intent(this, TestDetailActivity::class.java)
                            .putExtra(TestDetailActivity.EXTRA_TEST_NAME, name)
                    )
                }
            }
            row.setOnLongClickListener {
                if (!selectMode) showTestMenu(name)
                true
            }

            container.addView(row)
        }
        addArchivedRow()
        // v2.8 fold-in fix: select-mode checkboxes are built HERE, fresh,
        // every time this function runs — which is always AFTER the one-
        // time applyAccents() walk in onCreate(). Tagging them alone isn't
        // enough since they didn't exist yet when that walk happened; this
        // re-walk picks up anything new. Idempotent for already-tagged
        // views elsewhere on the screen (re-applying the same color is a
        // no-op visually).
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
    }

    /** v1.2.5 — press-and-hold a test: rename it, or archive it (hide it without deleting anything). */
    private fun showTestMenu(name: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Rename", "Archive")) { _, which ->
                if (which == 0) {
                    promptRenameTest(name)
                } else {
                    ArchiveStore.setTestArchived(this, name, true)
                    Toast.makeText(this, "Archived — find it under Archived tests", Toast.LENGTH_SHORT).show()
                    rebuildRows()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    /** v1.2.5 — the "Archived tests (n)" entry under the list, only when something is archived. */
    private fun addArchivedRow() {
        val count = TestStorage.listTestNames(this).count { ArchiveStore.isTestArchived(this, it) }
        if (count == 0) return
        container.addView(ClassUi.card(this, "Archived tests", "$count test${if (count == 1) "" else "s"} · restore or delete") {
            startActivity(Intent(this, ArchivedTestsActivity::class.java))
        })
    }

    /** Uses ThemePrefs.getTheme(context) directly for the dialog buttons —
     *  theme.resolveAttribute() is known-unreliable for this in the app's
     *  Dark/Black themes. TextInputLayout (not a raw EditText) for the same
     *  reason as the Rename Test dialog fix elsewhere in this app — a plain
     *  EditText falls back to a stray platform underline with no inset. */
    private fun promptRenameTest(oldName: String) {
        val density = resources.displayMetrics.density
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox)
        ).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
        }
        val input = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            setText(oldName)
            setSelection(text?.length ?: 0)
        }
        inputLayout.addView(input)
        // v3.0 FIX: this outlined box's stroke/hint color was coming from
        // the base theme's colorPrimary (a fixed maroon), same bug class
        // as every other maroon-that-doesn't-follow-accent issue in this
        // app — TextInputLayout's default Material style reads that theme
        // attribute directly, with no awareness of AccentColorPrefs at
        // all. Set explicitly to the user's chosen accent (Group A) so it
        // matches every other maroon-tagged element instead of standing
        // out as the one thing that didn't change.
        val accent = AccentColorPrefs.getGroupA(this)
        inputLayout.boxStrokeColor = accent
        inputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(accent)
        // v3.1.5 FIX: this field is a TextInputLayout, not a raw EditText —
        // tintCursorDrawable() (used elsewhere for plain EditTexts) can't
        // win against TextInputLayout's own internal cursor-color sync.
        // See AccentColorPrefs.tintTextInputLayoutCursor's doc comment.
        AccentColorPrefs.tintTextInputLayoutCursor(inputLayout, accent)
        // v3.1 (item 3): the cursor (above) and the drag "handle" (teardrop
        // selection locator) are two separate drawables/attributes — fixing
        // the cursor didn't touch the handle, which was still following the
        // theme's static gold. See AccentColorPrefs.tintTextSelectHandles.
        AccentColorPrefs.tintTextSelectHandles(input, accent)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename test")
            .setView(inputLayout)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Duplicate") { _, _ -> duplicateTest(oldName) }
            .setPositiveButton("Rename") { _, _ ->
                val newName = (input.text?.toString() ?: "").trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name can't be blank", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Renaming has to update THREE places that use testName as
                // a lookup key — see TestStorage.renameTest's own doc
                // comment for the full audit.
                val renamed = TestStorage.renameTest(this, oldName, newName)
                if (!renamed) {
                    Toast.makeText(this, "That name is already used by another saved test", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ScannedSheetStorage.renameTestReferences(this, oldName, newName)
                rebuildRows()
            }
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** v1.1.1: duplicates a test's template/answer key/points/competencies
     *  under an auto-generated "(Copy)" name — deliberately does NOT touch
     *  ScannedSheetStorage at all, which is what leaves the duplicate with
     *  no scanned sheets or subfolders, per explicit request (that store
     *  is keyed separately by test name and is simply never written to
     *  here). Option 1 from the naming discussion: auto-name immediately,
     *  no extra prompt — the teacher renames it afterward the same way
     *  they'd rename any test, if they want to. */
    private fun duplicateTest(oldName: String) {
        val original = TestStorage.loadTestOrNull(this, oldName)
        if (original == null) {
            Toast.makeText(this, "Couldn't load that test to duplicate", Toast.LENGTH_SHORT).show()
            return
        }
        val newName = uniqueDuplicateName(oldName)
        val duplicate = original.copy(template = original.template.copy(testName = newName))
        TestStorage.saveTest(this, duplicate)
        SetKeyStore.copy(this, oldName, newName)
        SetCompetencyStore.copy(this, oldName, newName)
        Toast.makeText(this, "Duplicated as \"$newName\"", Toast.LENGTH_SHORT).show()
        rebuildRows()
    }

    /** "{base} (Copy)", or "{base} (Copy 2)", "(Copy 3)", ... if that's
     *  already taken (e.g. duplicating the same test more than once). */
    private fun uniqueDuplicateName(baseName: String): String {
        val existing = TestStorage.listTestNames(this).toSet()
        var candidate = "$baseName (Copy)"
        var counter = 2
        while (candidate in existing) {
            candidate = "$baseName (Copy $counter)"
            counter++
        }
        return candidate
    }

    // --- "+ new test" save flow (unchanged from before the home-screen merge) ---

    private fun requireTemplate(): SheetTemplate? {
        val t = pendingTemplate
        if (t == null) {
            Toast.makeText(this, "Something went wrong setting up the template — try again", Toast.LENGTH_SHORT).show()
        }
        return t
    }

    private fun saveCurrentTest() {
        val t = requireTemplate() ?: return
        val key = pendingAnswerKey
        // An answer key is no longer REQUIRED to save — a teacher should be
        // able to save a blank template and come back to the answer key
        // later. Still block on a MISMATCHED key specifically (one that
        // exists but doesn't match this template's question count).
        if (key != null && key.size != t.questionCount) {
            Toast.makeText(
                this,
                "Answer key has ${key.size} items — doesn't match ${t.questionCount} questions. Recreate it before saving.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val points = pendingPoints ?: List(key?.size ?: 0) { 1 }
        val test = SavedTest(t, key ?: emptyList(), pendingPaperLayout, points, classIds = pendingClassIds)

        if (TestStorage.testExists(this, t.testName)) {
            showTitleConflictDialog(test)
        } else {
            commitSaveTest(test)
        }
    }

    private fun commitSaveTest(test: SavedTest) {
        TestStorage.saveTest(this, test)
        SetKeyStore.replaceAll(this, test.template.testName, if (test.template.hasSets) pendingSetKeys else emptyMap())
        pendingSetKeys = emptyMap()
        Toast.makeText(this, "Saved \"${test.template.testName}\"", Toast.LENGTH_SHORT).show()
        rebuildRows()
    }

    /** First prompt in the title-conflict flow. OVERWRITE leads to a
     *  second, explicit confirmation (destructive — deletes the existing
     *  test's scans too, see showOverwriteConfirmDialog). RENAME lets the
     *  teacher pick a new title inline and saves as a separate test. */
    private fun showTitleConflictDialog(pendingTest: SavedTest) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Test already exists.")
            .setMessage("Do you want to overwrite the current test or rename your new test?")
            .setNegativeButton("OVERWRITE") { _, _ -> showOverwriteConfirmDialog(pendingTest) }
            .setPositiveButton("RENAME") { _, _ -> showRenameForSaveDialog(pendingTest) }
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    private fun showOverwriteConfirmDialog(pendingTest: SavedTest) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Overwrite this test?")
            .setMessage("This will permanently delete the existing test and all data attached to it.")
            .setNegativeButton("NO") { _, _ -> showTitleConflictDialog(pendingTest) }
            .setPositiveButton("YES") { _, _ ->
                ScannedSheetStorage.deleteAllScansForTest(this, pendingTest.template.testName)
                // v1.2.0: the overwritten test's learner scores go with it.
                LearnerResultStorage.deleteForTest(this, pendingTest.template.testName)
                commitSaveTest(pendingTest)
            }
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    private fun showRenameForSaveDialog(pendingTest: SavedTest) {
        val input = android.widget.EditText(this).apply {
            setText(pendingTest.template.testName)
            setSelection(text.length)
            // Same accent-color fix as promptRenameTest's outlined box —
            // a plain EditText's underline follows colorAccent by default,
            // same fixed-maroon-not-following-accent issue.
            backgroundTintList = android.content.res.ColorStateList.valueOf(AccentColorPrefs.getGroupA(this@MainActivity))
            // v3.1 (item 3): same drag-handle fix as promptRenameTest's
            // outlined box — this is a distinct "Rename test" text field
            // (shown during the Save flow) and wasn't covered by that fix.
            AccentColorPrefs.tintTextSelectHandles(this, AccentColorPrefs.getGroupA(this@MainActivity))
            // v3.1.3 FIX: this dialog's cursor was never tinted at all —
            // only the underline and drag handle were covered above. Found
            // while fixing the sibling promptRenameTest dialog's cursor bug.
            AccentColorPrefs.tintCursorDrawable(this, AccentColorPrefs.getGroupA(this@MainActivity))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename test")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("RENAME") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val renamed = pendingTest.copy(template = pendingTest.template.copy(testName = newName))
                if (TestStorage.testExists(this, newName)) {
                    showTitleConflictDialog(renamed)
                } else {
                    commitSaveTest(renamed)
                }
            }
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** AlertDialog buttons default to colorPrimary as their text color, which
     *  is unreadable in Dark/Black themes. Uses our own known theme state
     *  directly rather than theme.resolveAttribute() (unreliable here). */
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

    /** v2.8 fold-in fix: scrimTop/scrimBottom in activity_main.xml had
     *  hardcoded 140dp bounds left over from before the header was pinned/
     *  restructured (two-line title, different padding) — so the dimmer
     *  scrim band no longer actually lined up with the header's real
     *  height. Rather than re-guess a new fixed dp value (the same class
     *  of bug, just a different wrong number), this measures headerRow's
     *  REAL rendered height after layout and resizes both scrim views to
     *  match exactly — self-correcting if the header's content ever
     *  changes again. post{} is needed because view.height is 0 until
     *  after the first layout pass completes. */
    private fun resizeScrimToMatchHeader() {
        val headerRow = findViewById<View>(R.id.headerRow)
        val scrimTop = findViewById<View>(R.id.scrimTop)
        val scrimBottom = findViewById<View>(R.id.scrimBottom)
        headerRow.post {
            val headerHeight = headerRow.height
            if (headerHeight <= 0) return@post // layout pass hadn't happened yet; leave XML fallback in place
            scrimTop.layoutParams = scrimTop.layoutParams.apply { height = headerHeight }
            scrimTop.requestLayout()
            (scrimBottom.layoutParams as ViewGroup.MarginLayoutParams).topMargin = headerHeight
            scrimBottom.requestLayout()
        }
    }
}
