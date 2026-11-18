package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Shows the computed Item Analysis table (ItemAnalysis.kt) for either an
 * entire test (folder = null, matches Test Summary's "Main aggregates
 * every subfolder" behavior) or one specific folder. Same computation
 * engine either way — this Activity is presentation + export only.
 *
 * v3.7.8: added per-subfolder navigation — chevrons + swipe on a frozen
 * row above the (scrollable) table, cycling through "All" (the original/
 * default combined view, folder = null) plus every individual folder,
 * same UX pattern as Scanned Sheets' folderNavRow. EXTRA_FOLDER still
 * controls which folder this screen OPENS on — TestDetailActivity's
 * launch site still always omits it (opens on "All"), so the entry point
 * behavior is unchanged; navigation from there is entirely in-screen via
 * this new row.
 */
class ItemAnalysisActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_NAME = "testName"
        const val EXTRA_FOLDER = "folder" // absent/null = all folders combined
        private const val ALL_FOLDERS_LABEL = "All"
    }

    private lateinit var testName: String
    private var folder: String? = null
    // v1.2.5 (Test sets): null = all sets pooled, otherwise one set's own scans.
    private var setFilter: Int? = null
    private var setPickerView: TextView? = null
    private var result: ItemAnalysis.Result? = null
    // v1.1.0: current sort state for the preview table — null column
    // means "unsorted" (question-number order, same as before this
    // feature existed).
    private var sortColumnIndex: Int? = null
    private var sortAscending = true
    private lateinit var savedTest: SavedTest

    // v3.7.8: cycling order is "All" (folder=null) first, then whatever
    // listFoldersForTest returns (Main, then subfolders alphabetically —
    // Main is always present per that function's own contract). Rebuilt
    // fresh each time refreshForFolder() runs rather than cached once in
    // onCreate, since a folder could in principle be renamed/removed by
    // another screen while this Activity is merely paused, not finished
    // (Android doesn't recreate a backgrounded-then-resumed Activity by
    // default) — stale entries here would let the user swipe onto a
    // folder name that no longer exists.
    private fun folderCycleList(): List<String?> =
        listOf<String?>(null) + ScannedSheetStorage.listFoldersForTest(this, testName)

    // v2.9: see GenerateSheetActivity's identical property for why this
    // is a no-op result handler.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        // v1.1.1: reloads savedTest in case "Set Up Competencies" changed
        // its competencies list while this screen was backgrounded — the
        // isInitialized guards handle onResume firing once more right
        // after onCreate calls finish() early (testName extra missing,
        // or the test itself couldn't be loaded) before onDestroy
        // actually stops the lifecycle.
        if (::testName.isInitialized && ::savedTest.isInitialized) {
            TestStorage.loadTestOrNull(this, testName)?.let { savedTest = it }
            refreshForFolder()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_analysis)

        testName = intent.getStringExtra(EXTRA_TEST_NAME) ?: run { finish(); return }
        folder = intent.getStringExtra(EXTRA_FOLDER)

        DrawerHelper.attach(
            this,
            findViewById(R.id.drawerLayout),
            findViewById(R.id.btnBack)
        )
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        val loaded = TestStorage.loadTestOrNull(this, testName)
        if (loaded == null) {
            finish()
            return
        }
        savedTest = loaded

        findViewById<View>(R.id.btnFolderPrev).setOnClickListener { swipeFolder(-1) }
        findViewById<View>(R.id.btnFolderNext).setOnClickListener { swipeFolder(1) }
        findViewById<View>(R.id.btnSetUpCompetencies).setOnClickListener {
            startActivity(
                Intent(this, CompetencySetupActivity::class.java)
                    .putExtra(CompetencySetupActivity.EXTRA_TEST_NAME, testName)
            )
        }
        findViewById<LinearLayout>(R.id.folderNavRow).setOnTouchListener { _, event ->
            folderSwipeGestureDetector.onTouchEvent(event)
        }
        // v3.8.2: this label has no click/long-click listener of its own
        // (unlike Scanned Sheets' equivalent, which uses long-press to
        // rename), so it isn't currently claiming touch at the child
        // level the way Scanned Sheets' was. Forwarding its touch stream
        // to the same detector anyway, defensively — if a listener is
        // ever added to this label later (e.g. matching Scanned Sheets'
        // rename gesture), this prevents the exact same swipe-blocked-on-
        // the-label bug from recurring here without anyone having to
        // remember why.
        findViewById<TextView>(R.id.tvFolderNavLabel).setOnTouchListener { _, event ->
            folderSwipeGestureDetector.onTouchEvent(event)
            false
        }

        addSetPickerIfNeeded()
        refreshForFolder()
    }

    /** v1.2.5: for a test with sets, a tappable line under the subtitle that
     *  switches between all sets pooled and each set on its own. */
    private fun addSetPickerIfNeeded() {
        if (!savedTest.template.hasSets) return
        val subtitle = findViewById<TextView>(R.id.tvItemAnalysisSubtitle)
        val parent = subtitle.parent as? LinearLayout ?: return
        val picker = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = android.view.Gravity.CENTER
            textSize = 15f
            setPadding(16, 12, 16, 12)
            setTextColor(AccentColorPrefs.getGroupA(this@ItemAnalysisActivity))
            setOnClickListener { HapticUtils.confirm(it); showSetPickerDialog() }
        }
        parent.addView(picker, parent.indexOfChild(subtitle) + 1)
        setPickerView = picker
    }

    private fun setFilterLabel(): String {
        val f = setFilter ?: return "All sets"
        return "Set ${savedTest.template.setLabels().getOrNull(f) ?: "?"}"
    }

    private fun showSetPickerDialog() {
        val labels = savedTest.template.setLabels()
        val options = (listOf("All sets (pooled)") + labels.map { "Set $it" }).toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Analyze")
            .setSingleChoiceItems(options, (setFilter ?: -1) + 1) { dialog, which ->
                setFilter = if (which == 0) null else which - 1
                dialog.dismiss()
                refreshForFolder()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Moves [folder] by [delta] positions through folderCycleList(),
     *  wrapping around at either end — same shape as Scanned Sheets'
     *  swipeFolder, but cycling folder (String?) instead of
     *  selectedFolder (String), since "All" (null) is a valid stop here. */
    private fun swipeFolder(delta: Int) {
        val cycle = folderCycleList()
        if (cycle.size <= 1) return
        val idx = cycle.indexOf(folder).let { if (it < 0) 0 else it }
        folder = cycle[((idx + delta) % cycle.size + cycle.size) % cycle.size]
        HapticUtils.confirm(findViewById(R.id.tvFolderNavLabel))
        refreshForFolder()
    }

    private val folderSwipeGestureDetector by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            // Must return true or onFling() below never fires — same fix
            // as SavedScansListActivity/TestDetailActivity's identical
            // gesture detectors.
            override fun onDown(e: android.view.MotionEvent): Boolean = true

            override fun onFling(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 80 && kotlin.math.abs(velocityX) > 200) {
                    if (dx < 0) swipeFolder(1) else swipeFolder(-1)
                    return true
                }
                return false
            }
        })
    }

    /** Recomputes the item analysis result for the CURRENT [folder] and
     *  re-renders everything that depends on it — the frozen nav label,
     *  the centered subtitle, the empty state, and the table itself.
     *  Called from onCreate (initial load) and swipeFolder (navigation);
     *  kept as one function so those two call sites can't drift out of
     *  sync with each other. */
    /** v3.8.2: single source of truth for how [folder] is shown to the
     *  user — used by the nav label AND both subtitle branches below, so
     *  the Main-capitalization fix can't drift out of sync between them
     *  the way it briefly did (nav label got fixed, subtitle didn't,
     *  until this got pulled into one function). Returns null for the
     *  "All" case so callers can still special-case whether to show a
     *  folder suffix at all (the subtitle only appends " — X" when
     *  there's an actual folder to name). */
    private fun displayFolderName(f: String?): String? = when (f) {
        null -> null
        ScannedSheetStorage.MAIN_FOLDER -> "Main"
        else -> f
    }

    private fun refreshForFolder() {
        findViewById<TextView>(R.id.tvFolderNavLabel).text =
            displayFolderName(folder) ?: ALL_FOLDERS_LABEL

        setPickerView?.text = "Showing: ${setFilterLabel()}  ▾"
        val scans = ScannedSheetStorage.listScansForTest(this, testName, folder)
            .filter { setFilter == null || it.setIndex == setFilter }
        // v1.2.5: one set on its own uses that set's competency item numbers;
        // all sets pooled applies each scan's own set mapping.
        val sf = setFilter
        result = if (!savedTest.template.hasSets) {
            ItemAnalysis.compute(scans, savedTest.template.questionCount, savedTest.competencies)
        } else if (sf != null) {
            ItemAnalysis.compute(
                scans, savedTest.template.questionCount,
                SetCompetencyStore.resolve(this, savedTest, sf)
            )
        } else {
            ItemAnalysis.compute(
                scans, savedTest.template.questionCount, savedTest.competencies,
                SetCompetencyStore.resolveAll(this, savedTest)
            )
        }

        val subtitleView = findViewById<TextView>(R.id.tvItemAnalysisSubtitle)
        val emptyView = findViewById<TextView>(R.id.tvEmptyState)
        val exportButton = findViewById<View>(R.id.btnExportItemAnalysis)

        val r = result
        // v3.9.9 fix: the folder-swipe row and Export button used to
        // visibly jump position depending on whether the current folder
        // had enough data — because the ScrollView holding the table and
        // the Export button both fully collapsed to GONE (their weighted
        // height simply wasn't allocated) whenever there wasn't enough
        // data yet, on top of tvEmptyState popping in as an extra sibling
        // with its own separate height. Now the ScrollView and Export
        // button are ALWAYS visible — the empty state renders as a single
        // message row inside the same table container instead of a
        // separate view outside it — so the layout structure never
        // changes shape between "has data" and "not enough data yet".
        // Tapping Export with no data now prompts instead of running a
        // real export (see confirmExport below and the button's click
        // listener at the end of this function).
        emptyView.visibility = View.GONE
        findViewById<View>(R.id.itemAnalysisScrollView).visibility = View.VISIBLE
        exportButton.visibility = View.VISIBLE

        if (r == null) {
            subtitleView.text = savedTest.template.testName + (displayFolderName(folder)?.let { " — $it" } ?: "") +
                (if (savedTest.template.hasSets) " — ${setFilterLabel()}" else "")
            renderEmptyTable()
            exportButton.setOnClickListener {
                Toast.makeText(this, "Need item analysis data to export — scan at least 2 sheets first.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val excludedNote = if (r.totalExcluded > 0) {
            " (${r.totalExcluded} older scan${if (r.totalExcluded == 1) "" else "s"} skipped — saved before item-level tracking was added)"
        } else {
            ""
        }
        subtitleView.text = buildString {
            append(savedTest.template.testName)
            displayFolderName(folder)?.let { append(" — ").append(it) }
            if (savedTest.template.hasSets) append(" — ").append(setFilterLabel())
            append("\n${r.totalUsable} scans analyzed · high/low groups of ${r.highGroupSize}$excludedNote")
        }

        renderTable(r)

        exportButton.setOnClickListener { confirmExport(savedTest, r) }
    }

    /** Renders the same empty-state message the old separate tvEmptyState
     *  view used to show, but AS the table container's content instead of
     *  a sibling view — keeps the ScrollView's occupied space constant
     *  regardless of whether there's real data, which is what stops the
     *  Export button below it from shifting position. */
    private fun renderEmptyTable() {
        val container = findViewById<LinearLayout>(R.id.itemAnalysisTableContainer)
        container.removeAllViews()
        val message = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Not enough scored scans yet to run item analysis (need at least 2 with item-level data)."
            setPadding(16, 16, 16, 16)
            setTextColor(currentTextColor())
        }
        container.addView(message)
    }

    private fun renderTable(r: ItemAnalysis.Result) {
        val container = findViewById<LinearLayout>(R.id.itemAnalysisTableContainer)
        container.removeAllViews()

        // v1.1.0: tap-to-sort on Item/P/D/Least Learned — sorts the
        // underlying ItemStats list itself (not just the displayed
        // strings), so numeric columns sort numerically (e.g. P=0.9
        // before P=0.15) rather than as text. sortableColumns maps a
        // header index to the ItemStats field it sorts by.
        // v1.1.1: indices shifted by 1 from Mastery/Least Learned onward
        // to make room for the new Competency column at index 8 — the
        // fields being sorted (leastLearnedRank etc.) are unchanged,
        // only which header position triggers them moved.
        val sortableColumns: Map<Int, (ItemAnalysis.ItemStats) -> Comparable<*>> = mapOf(
            0 to { item: ItemAnalysis.ItemStats -> item.questionNumber },
            4 to { item: ItemAnalysis.ItemStats -> item.p },
            6 to { item: ItemAnalysis.ItemStats -> item.d },
            10 to { item: ItemAnalysis.ItemStats -> item.leastLearnedRank }
        )
        val sortedItems = sortColumnIndex?.let { colIndex ->
            val selector = sortableColumns[colIndex]
            if (selector == null) {
                r.items
            } else {
                val comparator = compareBy(selector)
                r.items.sortedWith(if (sortAscending) comparator else comparator.reversed())
            }
        } ?: r.items

        // v1.1.1: Competency inserted between D Interp. and Mastery, per
        // explicit request.
        val baseHeaders = listOf("Item", "High", "Low", "Total", "P", "P Interp.", "D", "D Interp.", "Competency", "Mastery", "Least Learned")
        val headers = baseHeaders.mapIndexed { index, label ->
            if (index == sortColumnIndex) label + if (sortAscending) " \u25B2" else " \u25BC"
            else label
        }
        val rowsText = sortedItems.map { item ->
            listOf(
                item.questionNumber.toString(),
                item.correctHigh.toString(),
                item.correctLow.toString(),
                item.correctTotal.toString(),
                String.format("%.2f", item.p),
                item.pLabel,
                String.format("%.2f", item.d),
                item.dLabel,
                item.competencyName,
                item.masteryLevel,
                item.leastLearnedRank.toString()
            )
        }

        // v1.1.0: P Interp./D Interp./Mastery columns sized to fit their
        // own actual text ("Nearly Mastered", "Should Discriminate
        // Better", etc.) instead of sharing the same fixed 72dp minWidth
        // every other column still uses — per explicit request, only
        // these three change; Item/High/Low/Total/P/D/Least Learned stay
        // exactly as they were (see buildRow's columnWidths fallback).
        // Measured against baseHeaders (no sort-arrow suffix) so a
        // column's width doesn't jump by a few px depending on whether
        // it currently happens to be the sorted one.
        // v1.1.1: Competency (8) added to this same fit-to-content set —
        // competency names vary in length far more than any other column
        // here, so sharing the fixed 72dp width would truncate/wrap most
        // of them unreadably.
        val fitColumns = setOf(5, 7, 8, 9)
        val columnWidths: Map<Int, Int> = baseHeaders.indices
            .filter { it in fitColumns }
            .associateWith { colIndex -> measureColumnWidth(baseHeaders[colIndex], rowsText.map { it[colIndex] }) }

        val headerRow = buildRow(headers, columnWidths, isHeader = true, sortableColumnIndices = sortableColumns.keys) { colIndex ->
            if (sortColumnIndex == colIndex) sortAscending = !sortAscending
            else {
                sortColumnIndex = colIndex
                sortAscending = true
            }
            renderTable(r)
        }
        container.addView(headerRow)

        rowsText.forEachIndexed { i, cells ->
            val row = buildRow(cells, columnWidths)
            row.setOnClickListener { showItemDetail(sortedItems[i]) }
            container.addView(row)
        }

        // v1.1.1: same two tables as the exported .xlsx's below-the-main-
        // table summaries — shown here too since they sit naturally in
        // the same scrolling container, right after the main table's own
        // rows, no side-by-side column juggling needed the way the xlsx
        // needed (that was specifically to dodge Excel's AutoFilter
        // disturbing shared rows — not a concern for a plain vertical
        // list of views). Only shown when at least one competency has
        // been set up for this test, same as the xlsx.
        if (r.competencySummaries.isNotEmpty()) {
            val mostLearnedOrder = r.competencySummaries.sortedWith(compareBy({ it.rank }, { it.name }))
            val leastLearnedOrder = r.competencySummaries.sortedWith(compareByDescending<ItemAnalysis.CompetencySummary> { it.rank }.thenBy { it.name })

            addCompetencySummarySection(container, "Most Learned Competencies", mostLearnedOrder)
            addCompetencySummarySection(container, "Least Learned Competencies", leastLearnedOrder)
        }
    }

    private fun addCompetencySummarySection(container: LinearLayout, title: String, rows: List<ItemAnalysis.CompetencySummary>) {
        val density = resources.displayMetrics.density
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(currentTextColor())
            setPadding(0, (20 * density).toInt(), 0, (8 * density).toInt())
        }
        container.addView(titleView)

        val summaryHeaders = listOf("Item No.", "Competency", "Rank")
        val rowsText = rows.map { listOf(ItemAnalysisXlsxExporter.formatItemRanges(it.itemNumbers), it.name, it.rank.toString()) }
        // Competency name column (index 1) fits its own content, same
        // reasoning as the main table's own Competency column above.
        val columnWidths = mapOf(
            0 to measureColumnWidth(summaryHeaders[0], rowsText.map { it[0] }),
            1 to measureColumnWidth(summaryHeaders[1], rowsText.map { it[1] })
        )

        container.addView(buildRow(summaryHeaders, columnWidths, isHeader = true))
        rowsText.forEach { cells -> container.addView(buildRow(cells, columnWidths)) }
    }

    /** Widest rendered width among a column's header + every row's value
     *  for that column, plus buildRow's own left+right cell padding —
     *  uses a throwaway TextView (not a raw Paint) purely so its default
     *  text size/typeface — and therefore this measurement — automatically
     *  tracks whatever FontScaleController has already applied to this
     *  Activity, the same as the real cells being sized. */
    private fun measureColumnWidth(header: String, values: List<String>): Int {
        val density = resources.displayMetrics.density
        val measurer = TextView(this)
        measurer.setTypeface(measurer.typeface, android.graphics.Typeface.BOLD)
        var maxWidth = measurer.paint.measureText(header)
        measurer.setTypeface(measurer.typeface, android.graphics.Typeface.NORMAL)
        values.forEach { maxWidth = maxOf(maxWidth, measurer.paint.measureText(it)) }
        val horizontalPadding = 10f * density * 2 // matches buildRow's setPadding(10dp, _, 10dp, _)
        return (maxWidth + horizontalPadding).toInt()
    }

    private fun buildRow(
        cells: List<String>,
        columnWidths: Map<Int, Int> = emptyMap(),
        isHeader: Boolean = false,
        sortableColumnIndices: Set<Int> = emptySet(),
        onHeaderClick: ((Int) -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val density = resources.displayMetrics.density
        val colorRes = if (isHeader) {
            AccentColorPrefs.getGroupA(this)
        } else {
            android.graphics.Color.TRANSPARENT
        }
        row.setBackgroundColor(colorRes)
        cells.forEachIndexed { index, text ->
            val tv = TextView(this).apply {
                this.text = text
                if (isHeader) setTextColor(AccentColorPrefs.textColorFor(colorRes))
                setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
                minWidth = columnWidths[index] ?: (72 * density).toInt()
                gravity = Gravity.CENTER
                if (isHeader) {
                    setTextColor(android.graphics.Color.WHITE)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    if (index in sortableColumnIndices && onHeaderClick != null) {
                        isClickable = true
                        setOnClickListener { HapticUtils.confirm(it); onHeaderClick(index) }
                    }
                } else {
                    setTextColor(currentTextColor())
                }
            }
            row.addView(tv)
        }
        return row
    }

    /** Regular rows need a theme-aware text color — reuses the same
     *  ThemePrefs-based pattern as the rest of the app's dialog-button
     *  fix, rather than a hardcoded color that would vanish in Dark/Black. */
    private fun currentTextColor(): Int = when (ThemePrefs.getTheme(this)) {
        AppTheme.LIGHT -> android.graphics.Color.parseColor("#221111")
        else -> android.graphics.Color.WHITE
    }

    private fun showItemDetail(item: ItemAnalysis.ItemStats) {
        val message = buildString {
            append("Difficulty (P): ").append(String.format("%.2f", item.p)).append(" — ").append(item.pLabel)
            append("\n").append(item.pRecommendation)
            append("\n\nDiscrimination (D): ").append(String.format("%.2f", item.d)).append(" — ").append(item.dLabel)
            append("\n").append(item.dRecommendation)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Item ${item.questionNumber}")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    private fun confirmExport(savedTest: SavedTest, r: ItemAnalysis.Result) {
        // Plan gate (Free = on-screen only; any paid plan may export to Excel).
        val dialog = AlertDialog.Builder(this)
            .setTitle("Export Item Analysis")
            // v3.8.2: uses displayFolderName so this reads "(Main)"
            // instead of "(main)" — the exported FILENAME below
            // deliberately keeps the raw lowercase folder value, since
            // that's a file system name rather than UI display text and
            // changing its casing risks inconsistency with files already
            // exported under the old naming.
            .setMessage("Generate an Excel (.xlsx) file with this item analysis for \"${savedTest.template.testName}\"${displayFolderName(folder)?.let { " ($it)" } ?: ""}?")
            .setPositiveButton("Generate") { _, _ -> generateXlsxThenPrompt(savedTest, r) }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    /** v2.9: was exportAndShare(), which auto-decided Download vs. Share
     *  with no user choice. Now builds the bytes once, writes a scratch
     *  copy to cacheDir (mirrors GenerateSheetActivity's PDF flow — see
     *  its doc comment for why cacheDir specifically), then hands off to
     *  the shared FileExportPrompt so the user picks Share or Download
     *  themselves, same as the other three export flows. */
    private fun generateXlsxThenPrompt(savedTest: SavedTest, r: ItemAnalysis.Result) {
        val bytes = ItemAnalysisXlsxExporter.buildXlsx(savedTest.template.testName, folder, r)
        val fileName = "ItemAnalysis_${savedTest.template.testName}${folder?.let { "_$it" } ?: ""}${if (savedTest.template.hasSets) "_" + setFilterLabel() else ""}.xlsx"
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, fileName)
        file.writeBytes(bytes)

        FileExportPrompt.show(
            activity = this,
            title = "Item analysis ready",
            fileName = fileName,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workingFile = file,
            shareTitle = "Share item analysis",
            onDownloadComplete = { file.delete() }
        )
        if (DownloadNotifier.needsPermissionRequest(this)) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Same fix used elsewhere in this app — AlertDialog buttons default to
     *  colorPrimary as text color, unreadable in Dark/Black themes. */
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
