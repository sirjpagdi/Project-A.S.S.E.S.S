package com.jpagdi.mcqquickcheck

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Dedicated list screen for saved scans of one test, replacing the earlier
 * popup dialog. Shows folder tabs (always including MAIN_FOLDER) across the
 * top, and a scrollable list of scan cards below — each card shows a
 * cropped Name/Gr.&Section thumbnail, the score/percentage, and date/time,
 * per the requested layout. Tapping a card opens the swipe-enabled
 * fullscreen viewer starting at that scan, so swiping left/right moves
 * through every scan in the CURRENTLY SELECTED folder — a separate gesture
 * from the folder swipe (see folderSwipeGestureDetector / folderNavRow's
 * wiring in onCreate), which moves between Main and each subfolder instead.
 * Two different swipe zones on purpose: mixing "next scan" and "next
 * folder" onto the same gesture over the same view would make "which one
 * just happened" ambiguous.
 * v3.6.2: folder switching moved to a dedicated non-scrollable row
 * (folderNavRow, with < / > buttons and its own swipe) below the scan list,
 * replacing every earlier attempt to layer folder-swipe onto the
 * scrollable list itself (header bar in v3.5/3.5.1, the whole scan list via
 * HorizontalSwipeScrollView in v3.6/3.6.1) — those kept having edge cases
 * where the touch-interception trick didn't fire (e.g. a touch starting on
 * blank space below the last row). A row that isn't a ScrollView has no
 * such edge case to begin with.
 */
class SavedScansListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_NAME = "test_name"
    }

    private lateinit var testName: String
    private lateinit var folderTabsContainer: LinearLayout
    private lateinit var scanListContainer: LinearLayout
    private lateinit var tvEmptyState: TextView
    private var selectedFolder: String = ScannedSheetStorage.MAIN_FOLDER

    // Multi-select state — there's no dedicated toggle button on this screen
    // (unlike the Saved Tests list on Home). Long-pressing ANY row's trash
    // icon turns select mode on for the whole list; a normal tap on a trash
    // icon still does its old single-row delete. Cleared whenever the list
    // rebuilds for an unrelated reason (folder switch, resume) so stale
    // selections from a previous folder/session can't linger.
    // v1.1.0: same no-op permission-result pattern as BackupDataActivity/
    // ItemAnalysisActivity/GenerateSheetActivity — needed for
    // saveScanListAsImage()'s Download path notification, see those
    // files' identical property for the full reasoning.
    private val requestNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    private var selectMode = false
    private val selectedScanIds = mutableSetOf<String>()

    /** v1.1.0: which bulk action select mode is currently for — long-
     *  pressing a row's trash icon still means DELETE (unchanged); long-
     *  pressing a row's send/move icon now means MOVE. Both share the
     *  same selectMode/selectedScanIds/checkbox machinery above; this
     *  just decides which header action icon (btnDeleteSelectedScans vs
     *  btnMoveSelectedScans) is shown and what the confirm button does. */
    private enum class SelectAction { DELETE, MOVE }
    private var selectAction = SelectAction.DELETE

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_scans)

        DrawerHelper.attach(
            this,
            findViewById(R.id.drawerLayout),
            findViewById(R.id.btnOpenDrawer)
        )
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        testName = intent.getStringExtra(EXTRA_TEST_NAME) ?: run {
            finish(); return
        }

        findViewById<TextView>(R.id.tvSavedScansTitle).text = "Scanned Sheets — $testName"
        folderTabsContainer = findViewById(R.id.folderTabsContainer)
        scanListContainer = findViewById(R.id.scanListContainer)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        findViewById<ImageView>(R.id.btnAddFolder).setOnClickListener { promptAddFolder() }
        findViewById<android.view.View>(R.id.btnScanIntoFolder).setOnClickListener { startScanningIntoCurrentFolder() }
        findViewById<android.widget.ImageView>(R.id.btnSortScans).setOnClickListener { showSortMenu(it) }
        findViewById<android.view.View>(R.id.btnTestSummary).setOnClickListener { showTestSummary() }
        findViewById<ImageView>(R.id.btnCancelSelect).setOnClickListener { exitSelectMode() }
        findViewById<ImageView>(R.id.btnSelectAllScans).setOnClickListener { HapticUtils.confirm(it); toggleSelectAll() }
        findViewById<ImageView>(R.id.btnDeleteSelectedScans).setOnClickListener { HapticUtils.confirm(it); confirmDeleteSelectedScans() }
        findViewById<ImageView>(R.id.btnMoveSelectedScans).setOnClickListener { HapticUtils.confirm(it); confirmMoveSelectedScans() }
        findViewById<android.view.View>(R.id.btnSaveScanList).setOnClickListener { saveScanListAsImage() }
        // v3.6.2: every earlier swipe zone (header bar in v3.5/3.5.1, the
        // whole scan list via HorizontalSwipeScrollView in v3.6/3.6.1) is
        // gone — replaced by folderNavRow's < / > buttons below, which are
        // now the primary way to move between folders, with swipe-on-this-
        // row as a bonus. This row isn't a scrollable view, so the old
        // ACTION_DOWN-ownership problem that motivated HorizontalSwipeScroll
        // View doesn't apply here — a plain GestureDetector is safe and
        // simple, same as folderSwipeGestureDetector used to be on the
        // header bar.
        findViewById<ImageView>(R.id.btnFolderPrev).setOnClickListener { swipeFolder(-1) }
        findViewById<ImageView>(R.id.btnFolderNext).setOnClickListener { swipeFolder(1) }
        findViewById<LinearLayout>(R.id.folderNavRow).setOnTouchListener { _, event ->
            folderSwipeGestureDetector.onTouchEvent(event)
        }
        // v3.7.5: long-press the folder name itself (not the chevrons) to
        // rename the currently-viewed folder. Deliberately separate from
        // the folder TABS' existing long-press-to-REMOVE (line ~181
        // above, left untouched per explicit instruction) — this is a
        // different action on a different view. Guarded against
        // MAIN_FOLDER the same way promptRemoveFolder is, since Main
        // isn't a real stored folder and can't be renamed.
        val tvFolderNavLabel = findViewById<TextView>(R.id.tvFolderNavLabel)
        tvFolderNavLabel.setOnLongClickListener {
            if (selectedFolder != ScannedSheetStorage.MAIN_FOLDER) {
                HapticUtils.confirm(it)
                promptRenameFolder(selectedFolder)
            }
            true
        }
        // v3.8.2 FIX: swipe was broken for anyone starting the drag on
        // the label itself (the widest, most natural place a thumb lands
        // to swipe) — setOnLongClickListener above makes this View
        // clickable/longClickable, which means it claims touch events at
        // the CHILD level before folderNavRow's own setOnTouchListener
        // above ever sees them, so the row-level GestureDetector was
        // starved of input whenever a swipe began on the label. Only
        // starting the drag exactly on one of the chevron icons' padding
        // (outside the label's bounds) ever reached the row listener,
        // which is a small enough target that it read as "broken" rather
        // than "works from one spot." Forwarding the label's own touch
        // stream into the same detector fixes this without removing the
        // long-press-to-rename behavior — GestureDetector only reacts to
        // FLING, so a long-press still passes through untouched to
        // setOnLongClickListener above.
        tvFolderNavLabel.setOnTouchListener { _, event ->
            folderSwipeGestureDetector.onTouchEvent(event)
            false // don't consume — must return false so the long-click
                  // listener above still gets ACTION_DOWN/UP normally;
                  // the gesture detector only cares about the motion
                  // events, not about "owning" the touch.
        }

        rebuildFolderTabs()
        rebuildScanList()
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        if (selectMode) exitSelectMode()
        // Re-check in case a scan was autosaved into a new folder since
        // this screen was last shown (e.g. user scanned more sheets, came
        // back here).
        rebuildFolderTabs()
        rebuildScanList()
    }

    private fun rebuildFolderTabs() {
        val folders = ScannedSheetStorage.listFoldersForTest(this, testName)
        if (selectedFolder !in folders) selectedFolder = ScannedSheetStorage.MAIN_FOLDER

        // v3.6.2: folderNavRow's center label — the folder tabs above are
        // useful for jumping straight to a specific folder, but the nav row
        // is meant to work by feel (chevrons/swipe) without needing to look
        // up at the tabs, so it names its own current folder.
        findViewById<TextView>(R.id.tvFolderNavLabel).text =
            if (selectedFolder == ScannedSheetStorage.MAIN_FOLDER) "Main" else selectedFolder

        folderTabsContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        folders.forEach { folder ->
            val isSelected = folder == selectedFolder
            val label = if (folder == ScannedSheetStorage.MAIN_FOLDER) "Main" else folder
            val count = ScannedSheetStorage.listScansForTest(this, testName, folder).size
            val groupAColor = AccentColorPrefs.getGroupA(this@SavedScansListActivity)
            val isLight = ThemePrefs.getTheme(this@SavedScansListActivity) == AppTheme.LIGHT
            // Unselected-tab color used to be hardcoded #221111 (dark
            // text), which assumed a light background — same bug class
            // as the dialog-button-color fix elsewhere in this app.
            // Reads ThemePrefs.getTheme() directly rather than
            // theme.resolveAttribute(), since this app's Dark/Black
            // themes never actually set android:textColorPrimary.
            // Selected tab's background follows the Group A accent color
            // (Top Panel Accent) — NOT the gold group, so this is
            // unaffected by the "only Scan Filled Sheets follows gold"
            // instruction. Its text color is computed from that chosen
            // color's luminance instead of a fixed white.
            val tabTextColor =
                if (isSelected) AccentColorPrefs.textColorFor(groupAColor)
                else if (isLight) android.graphics.Color.parseColor("#221111")
                else android.graphics.Color.WHITE

            // v3.8 redesign: rounded pill (was a flat rectangle) with a
            // small status dot and a scan-count badge, matching the sample
            // redesign proposal. Built as GradientDrawables directly in
            // code rather than XML shape resources, since the
            // selected/unselected stroke and fill colors both depend on
            // runtime theme + accent state that XML can't express cleanly.
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 100f * dp
                    if (isSelected) {
                        setColor(groupAColor)
                    } else {
                        setColor(android.graphics.Color.TRANSPARENT)
                        setStroke(
                            (1.2f * dp).toInt(),
                            if (isLight) android.graphics.Color.parseColor("#33221111")
                            else android.graphics.Color.parseColor("#33FFFFFF")
                        )
                    }
                }
                val marginPx = (4 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = marginPx }
                setOnClickListener {
                    selectedFolder = folder
                    if (selectMode) exitSelectMode()
                    rebuildFolderTabs()
                    rebuildScanList()
                }
                if (folder != ScannedSheetStorage.MAIN_FOLDER) {
                    setOnLongClickListener { promptRemoveFolder(folder); true }
                }
            }

            tab.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(tabTextColor)
            })

            if (count > 0) {
                tab.addView(TextView(this).apply {
                    text = count.toString()
                    textSize = 12f
                    setTextColor(tabTextColor)
                    setPadding((6 * dp).toInt(), (1 * dp).toInt(), (6 * dp).toInt(), (1 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = (6 * dp).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 100f * dp
                        setColor(
                            if (isLight) android.graphics.Color.parseColor("#22221111")
                            else android.graphics.Color.parseColor("#22FFFFFF")
                        )
                    }
                })
            }

            folderTabsContainer.addView(tab)
        }
    }

    /** Moves selectedFolder by [delta] positions through the same
     *  Main-then-subfolders order the folder tabs already use, wrapping
     *  around at either end. */
    private fun swipeFolder(delta: Int) {
        val folders = ScannedSheetStorage.listFoldersForTest(this, testName)
        if (folders.size <= 1) return
        val idx = folders.indexOf(selectedFolder).let { if (it < 0) 0 else it }
        selectedFolder = folders[((idx + delta) % folders.size + folders.size) % folders.size]
        if (selectMode) exitSelectMode()
        HapticUtils.confirm(findViewById(R.id.tvSavedScansTitle))
        rebuildFolderTabs()
        rebuildScanList()
    }

    private val folderSwipeGestureDetector by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            // Must return true or onFling() below never fires — see the
            // identical fix (and full explanation) on TestDetailActivity's
            // summaryGestureDetector.
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

    private fun showSortMenu(anchor: android.view.View) {
        val popup = android.widget.PopupMenu(this, anchor)
        ScanSortOrder.values().forEachIndexed { index, order ->
            popup.menu.add(0, index, index, order.label)
        }
        popup.setOnMenuItemClickListener { item ->
            val chosen = ScanSortOrder.values()[item.itemId]
            SettingsPrefs.setScanSortOrder(this, chosen)
            rebuildScanList()
            true
        }
        popup.show()
    }

    /** Scans without a score (earnedPoints null) sort to the end
     *  regardless of score-based order, rather than being treated as 0 —
     *  an ungraded scan isn't the same as a 0/N score. */
    private fun sortScans(scans: List<ScannedSheetStorage.SavedScan>): List<ScannedSheetStorage.SavedScan> {
        return when (SettingsPrefs.getScanSortOrder(this)) {
            ScanSortOrder.DATE_DESC -> scans.sortedByDescending { it.savedAtMillis }
            ScanSortOrder.DATE_ASC -> scans.sortedBy { it.savedAtMillis }
            ScanSortOrder.SCORE_DESC -> scans.sortedWith(
                compareByDescending<ScannedSheetStorage.SavedScan> { it.earnedPoints != null }
                    .thenByDescending { if (it.totalPoints != null && it.totalPoints > 0) (it.earnedPoints ?: 0) * 1000 / it.totalPoints else -1 }
            )
            ScanSortOrder.SCORE_ASC -> scans.sortedWith(
                compareByDescending<ScannedSheetStorage.SavedScan> { it.earnedPoints != null }
                    .thenBy { if (it.totalPoints != null && it.totalPoints > 0) (it.earnedPoints ?: 0) * 1000 / it.totalPoints else Int.MAX_VALUE }
            )
        }
    }

    private fun rebuildScanList() {
        scanListContainer.removeAllViews()
        val scans = sortScans(ScannedSheetStorage.listScansForTest(this, testName, selectedFolder))

        tvEmptyState.visibility = if (scans.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        if (scans.isEmpty()) return

        val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault())

        scans.forEachIndexed { index, scan ->
            val row = layoutInflater.inflate(R.layout.row_saved_scan_card, scanListContainer, false)

            val thumbnail = ScannedSheetStorage.loadNameSectionThumbnail(this, scan.id)
            row.findViewById<ImageView>(R.id.ivScanThumbnail).setImageBitmap(thumbnail)

            val scoreText: String
            val percentText: String
            if (scan.earnedPoints != null && scan.totalPoints != null && scan.totalPoints > 0) {
                val percent = (scan.earnedPoints * 1000 / scan.totalPoints) / 10.0
                scoreText = "${scan.earnedPoints} / ${scan.totalPoints}"
                percentText = "$percent%"
            } else {
                scoreText = "Score not available"
                percentText = ""
            }
            row.findViewById<TextView>(R.id.tvScanScore).text = scoreText
            row.findViewById<TextView>(R.id.tvScanPercent).text = percentText
            row.findViewById<TextView>(R.id.tvScanDateTime).text = dateFormat.format(java.util.Date(scan.savedAtMillis))

            val checkbox = row.findViewById<android.widget.CheckBox>(R.id.cbScanSelect)
            checkbox.visibility = if (selectMode) android.view.View.VISIBLE else android.view.View.GONE
            checkbox.isChecked = selectedScanIds.contains(scan.id)
            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedScanIds.add(scan.id) else selectedScanIds.remove(scan.id)
                updateSelectedCountTitle()
                updateSelectAllIcon()
            }

            row.setOnClickListener {
                if (selectMode) {
                    checkbox.isChecked = !checkbox.isChecked
                } else {
                    openFullscreenAt(scans, index, dateFormat)
                }
            }
            // Normal tap: always deletes just this one row, in or out of
            // select mode — the checkbox is a separate selection mechanism,
            // not a replacement for the quick single-row delete.
            row.findViewById<ImageView>(R.id.btnScanDelete).apply {
                setOnClickListener { HapticUtils.confirm(it); confirmDeleteScan(scan, dateFormat) }
                setOnLongClickListener { HapticUtils.confirm(it); enterSelectMode(SelectAction.DELETE); true }
            }
            row.findViewById<ImageView>(R.id.btnScanMove).apply {
                setOnClickListener { promptMoveScan(scan) }
                // v1.1.0: long-press here starts a bulk MOVE, same pattern
                // as the trash icon's long-press starting a bulk DELETE.
                setOnLongClickListener { HapticUtils.confirm(it); enterSelectMode(SelectAction.MOVE); true }
            }

            scanListContainer.addView(row)
        }
        // v2.8 fold-in fix: same reasoning as MainActivity.rebuildRows() —
        // cbScanSelect is inflated fresh each time this runs, always after
        // the one-time applyAccents() walk in onCreate(), so tagging alone
        // wouldn't reach it without this re-walk.
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
    }

    private fun enterSelectMode(action: SelectAction) {
        if (selectMode && selectAction == action) return
        selectMode = true
        selectAction = action
        selectedScanIds.clear()
        findViewById<ImageView>(R.id.btnAddFolder).visibility = android.view.View.GONE
        findViewById<ImageView>(R.id.btnCancelSelect).visibility = android.view.View.VISIBLE
        findViewById<ImageView>(R.id.btnSelectAllScans).visibility = android.view.View.VISIBLE
        findViewById<ImageView>(R.id.btnDeleteSelectedScans).visibility =
            if (action == SelectAction.DELETE) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<ImageView>(R.id.btnMoveSelectedScans).visibility =
            if (action == SelectAction.MOVE) android.view.View.VISIBLE else android.view.View.GONE
        updateSelectedCountTitle()
        updateSelectAllIcon()
        rebuildScanList()
    }

    private fun exitSelectMode() {
        selectMode = false
        selectedScanIds.clear()
        findViewById<ImageView>(R.id.btnAddFolder).visibility = android.view.View.VISIBLE
        findViewById<ImageView>(R.id.btnCancelSelect).visibility = android.view.View.GONE
        findViewById<ImageView>(R.id.btnSelectAllScans).visibility = android.view.View.GONE
        findViewById<ImageView>(R.id.btnDeleteSelectedScans).visibility = android.view.View.GONE
        findViewById<ImageView>(R.id.btnMoveSelectedScans).visibility = android.view.View.GONE
        findViewById<TextView>(R.id.tvSavedScansTitle).text = "Scanned Sheets — $testName"
        rebuildScanList()
    }

    private fun updateSelectedCountTitle() {
        findViewById<TextView>(R.id.tvSavedScansTitle).text =
            "${selectedScanIds.size} selected"
    }

    /** v1.1.0: all scan ids currently visible in the selected folder's
     *  list — the same set enterSelectMode/rebuildScanList render rows
     *  for, so "select all" only ever selects what's actually on screen. */
    private fun currentFolderScanIds(): List<String> =
        ScannedSheetStorage.listScansForTest(this, testName, selectedFolder).map { it.id }

    /** Toggles between selecting everything currently visible and
     *  clearing the selection — a single icon doing double duty rather
     *  than two separate buttons, since exactly one of those two actions
     *  is ever useful at a time. */
    private fun toggleSelectAll() {
        val allIds = currentFolderScanIds()
        if (allIds.isNotEmpty() && selectedScanIds.containsAll(allIds)) {
            selectedScanIds.clear()
        } else {
            selectedScanIds.clear()
            selectedScanIds.addAll(allIds)
        }
        updateSelectedCountTitle()
        updateSelectAllIcon()
        rebuildScanList()
    }

    private fun updateSelectAllIcon() {
        val allIds = currentFolderScanIds()
        val allSelected = allIds.isNotEmpty() && selectedScanIds.containsAll(allIds)
        findViewById<ImageView>(R.id.btnSelectAllScans).contentDescription =
            if (allSelected) "Unselect all" else "Select all"
    }

    private fun confirmDeleteSelectedScans() {
        if (selectedScanIds.isEmpty()) {
            Toast.makeText(this, "Select at least one scan first", Toast.LENGTH_SHORT).show()
            return
        }
        val ids = selectedScanIds.toList()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete ${ids.size} scan${if (ids.size == 1) "" else "s"}?")
            .setMessage("This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                ids.forEach { ScannedSheetStorage.deleteScan(this, it) }
                exitSelectMode()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** v1.1.0: bulk version of promptMoveScan below — every selected scan
     *  is guaranteed to be in `selectedFolder` (the list only ever shows
     *  the currently selected folder's scans), so folder choices are
     *  filtered the same way promptMoveScan filters by a single scan's
     *  folder. */
    private fun confirmMoveSelectedScans() {
        if (selectedScanIds.isEmpty()) {
            Toast.makeText(this, "Select at least one scan first", Toast.LENGTH_SHORT).show()
            return
        }
        val ids = selectedScanIds.toList()
        val folders = ScannedSheetStorage.listFoldersForTest(this, testName)
            .filter { it != selectedFolder }
        if (folders.isEmpty()) {
            Toast.makeText(this, "No other folders to move these scans to yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = folders.map { if (it == ScannedSheetStorage.MAIN_FOLDER) "Main" else it }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Move ${ids.size} scan${if (ids.size == 1) "" else "s"} to folder")
            .setItems(labels) { _, which ->
                ids.forEach { ScannedSheetStorage.moveScan(this, it, folders[which]) }
                rebuildFolderTabs()
                exitSelectMode()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** v1.1.0: renders the ENTIRE scan list (not just what's currently
     *  visible on screen) into one image file — scanListContainer is a
     *  wrap_content LinearLayout inside a ScrollView, so Android already
     *  measures/lays out its full height regardless of viewport, and
     *  View.draw(Canvas) draws that whole laid-out content, not just the
     *  visible clip. Uses RGB_565 (2 bytes/pixel) instead of the default
     *  ARGB_8888 (4 bytes/pixel) and a hard size guard before allocating
     *  anything — a plain list screenshot needs no alpha channel, and
     *  after this conversation's whole run of real-device OOM crashes on
     *  this app's other export flows (see BackupDataActivity/
     *  ScannedSheetExporter/RestoreDataActivity's own doc comments), a
     *  single huge bitmap allocation for a very long list is exactly the
     *  same class of risk, not a hypothetical one. */
    /** v1.1.0: now composites a header (test title + folder name) above
     *  the captured list and a footer (app name + version) below it,
     *  per explicit request — see this file's own saveScanListAsImage
     *  history for the original bare-list version. Header/footer heights
     *  are added to the OOM size guard/estimate below, not just the
     *  captured list's own height, so the guard still reflects the real
     *  final bitmap size. */
    private fun saveScanListAsImage() {
        // v1.2.0: free-plan gate, checked first so a locked account gets
        // the upgrade prompt immediately rather than after doing the
        // (fairly expensive) list-measurement work below for nothing.
        if (scanListContainer.childCount == 0) {
            Toast.makeText(this, "No saved scans in this folder yet", Toast.LENGTH_SHORT).show()
            return
        }
        val width = scanListContainer.width
        val listHeight = scanListContainer.height
        if (width <= 0 || listHeight <= 0) {
            Toast.makeText(this, "List isn't ready yet — try again in a moment", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val padding = 16f * density
        val folderLabel = if (selectedFolder == ScannedSheetStorage.MAIN_FOLDER) "Main" else selectedFolder

        // v1.1.0 FIX: was using android.graphics.Typeface.DEFAULT_BOLD /
        // Paint's own default typeface — the phone's system font, not
        // this app's Montserrat (same font family SheetGenerator loads
        // for generated PDFs — see its ensureFontLoaded() doc comment).
        // ResourcesCompat.getFont() falls back to null on a genuinely
        // broken resource rather than throwing, so this still falls back
        // to a system sans-serif rather than crashing if that ever
        // happens — same defensive shape as SheetGenerator's own SANS
        // getters.
        val montserratRegular = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.montserrat_regular)
            ?: android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        val montserratBold = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.montserrat_bold)
            ?: android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)

        val maxTextWidth = width - 2 * padding

        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            textSize = 20f * density
            typeface = montserratBold
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val subtitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            isAntiAlias = true
            textSize = 14f * density
            typeface = montserratRegular
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val footerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            isAntiAlias = true
            textSize = 12f * density
            typeface = montserratRegular
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val dividerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f * density
        }

        // v1.1.0 FIX: a long test title at the fixed 20sp-equivalent size
        // could run past the bitmap's own width and get cut off, since
        // drawText never wraps or shrinks on its own. Shrinks textSize
        // down to fit (floored at a still-readable minimum), then falls
        // back to an ellipsis only if it's STILL too wide at that floor
        // (e.g. one absurdly long unbroken word) rather than shrinking
        // indefinitely toward unreadability. Same treatment applied to
        // the folder subtitle for the same reason, just less likely to
        // ever actually trigger given how short folder names usually are.
        val minTitleTextSize = 12f * density
        if (titlePaint.measureText(testName) > maxTextWidth) {
            val scale = maxTextWidth / titlePaint.measureText(testName)
            titlePaint.textSize = (titlePaint.textSize * scale).coerceAtLeast(minTitleTextSize)
        }
        val titleText = if (titlePaint.measureText(testName) > maxTextWidth) {
            android.text.TextUtils.ellipsize(testName, android.text.TextPaint(titlePaint), maxTextWidth, android.text.TextUtils.TruncateAt.END).toString()
        } else {
            testName
        }

        val subtitleFullText = "Folder: $folderLabel"
        val minSubtitleTextSize = 10f * density
        if (subtitlePaint.measureText(subtitleFullText) > maxTextWidth) {
            val scale = maxTextWidth / subtitlePaint.measureText(subtitleFullText)
            subtitlePaint.textSize = (subtitlePaint.textSize * scale).coerceAtLeast(minSubtitleTextSize)
        }
        val subtitleText = if (subtitlePaint.measureText(subtitleFullText) > maxTextWidth) {
            android.text.TextUtils.ellipsize(subtitleFullText, android.text.TextPaint(subtitlePaint), maxTextWidth, android.text.TextUtils.TruncateAt.END).toString()
        } else {
            subtitleFullText
        }

        val titleLineHeight = titlePaint.descent() - titlePaint.ascent()
        val subtitleLineHeight = subtitlePaint.descent() - subtitlePaint.ascent()
        val footerLineHeight = footerPaint.descent() - footerPaint.ascent()
        val headerHeight = (padding * 2 + titleLineHeight + 6f * density + subtitleLineHeight).toInt()
        val footerHeight = (padding * 1.5f + footerLineHeight).toInt()
        val totalHeight = headerHeight + listHeight + footerHeight

        val estimatedBytes = width.toLong() * totalHeight.toLong() * 2
        if (estimatedBytes > 150L * 1024 * 1024) {
            Toast.makeText(this, "This folder's list is too long to save as one image — try a smaller folder", Toast.LENGTH_LONG).show()
            return
        }

        val bitmap = try {
            android.graphics.Bitmap.createBitmap(width, totalHeight, android.graphics.Bitmap.Config.RGB_565)
        } catch (e: OutOfMemoryError) {
            Toast.makeText(this, "Not enough memory to save this list as an image", Toast.LENGTH_LONG).show()
            return
        }
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val centerX = width / 2f
        var baselineY = padding - titlePaint.ascent()
        canvas.drawText(titleText, centerX, baselineY, titlePaint)
        baselineY = padding + titleLineHeight + 6f * density - subtitlePaint.ascent()
        canvas.drawText(subtitleText, centerX, baselineY, subtitlePaint)
        canvas.drawLine(padding, headerHeight - 1f, width - padding, headerHeight - 1f, dividerPaint)

        canvas.save()
        canvas.translate(0f, headerHeight.toFloat())
        scanListContainer.draw(canvas)
        canvas.restore()

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        val footerText = "${getString(R.string.app_name)} v$versionName"
        val footerBaselineY = headerHeight + listHeight + padding * 0.75f - footerPaint.ascent()
        canvas.drawText(footerText, centerX, footerBaselineY, footerPaint)

        val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val safeTest = testName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val safeFolder = folderLabel.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val fileName = "${safeTest}_${safeFolder}_scan_list_$timestamp.png"
        val file = java.io.File(dir, fileName)
        try {
            file.outputStream().use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save the list image: ${e.message}", Toast.LENGTH_LONG).show()
            return
        } finally {
            bitmap.recycle()
        }

        FileExportPrompt.show(
            activity = this,
            title = "Scan list image ready",
            fileName = fileName,
            mimeType = "image/png",
            workingFile = file,
            shareTitle = "Share scan list",
            onDownloadComplete = { file.delete() }
        )
        if (DownloadNotifier.needsPermissionRequest(this)) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openFullscreenAt(
        scans: List<ScannedSheetStorage.SavedScan>,
        startIndex: Int,
        dateFormat: java.text.SimpleDateFormat
    ) {
        val bitmaps = scans.mapNotNull { ScannedSheetStorage.loadScan(this, it.id) }
        if (bitmaps.size != scans.size) {
            // At least one scan failed to decode — still show what did
            // load rather than blocking the whole viewer, but the index
            // math below assumes a 1:1 list, so bail to the simple case if
            // anything's missing rather than risk showing the wrong image
            // at the wrong index.
            val single = ScannedSheetStorage.loadScan(this, scans[startIndex].id)
            if (single != null) FullscreenImageViewer.show(this, single)
            else Toast.makeText(this, "Couldn't load that scan", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = scans.map { dateFormat.format(java.util.Date(it.savedAtMillis)) }
        FullscreenImageViewer.show(
            this, bitmaps, startIndex, labels,
            resolvableScans = scans,
            onDeleted = { rebuildScanList() }
        )
    }

    private fun confirmDeleteScan(scan: ScannedSheetStorage.SavedScan, dateFormat: java.text.SimpleDateFormat) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete this scan?")
            .setMessage("This scanned sheet from ${dateFormat.format(java.util.Date(scan.savedAtMillis))} will be permanently deleted. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                ScannedSheetStorage.deleteScan(this, scan.id)
                rebuildScanList()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** Lets the user move one scan to a different existing folder (or back
     *  to Main). Doesn't offer creating a new folder from here — that's
     *  what "Add folder" is for; this only picks among folders that
     *  already exist, matching how the folder tabs themselves work. */
    private fun promptMoveScan(scan: ScannedSheetStorage.SavedScan) {
        val folders = ScannedSheetStorage.listFoldersForTest(this, testName)
            .filter { it != scan.folder }
        if (folders.isEmpty()) {
            Toast.makeText(this, "No other folders to move this scan to yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = folders.map { if (it == ScannedSheetStorage.MAIN_FOLDER) "Main" else it }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Move scan to folder")
            .setItems(labels) { _, which ->
                ScannedSheetStorage.moveScan(this, scan.id, folders[which])
                rebuildFolderTabs()
                rebuildScanList()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    // v3.2.1: this dialog was still a bare EditText, never given the
    // accent-aware TextInputLayout treatment the rename dialogs in
    // MainActivity got in v3.0/v3.1.5 — so its underline, cursor, and
    // text-selection handle were stuck on whatever the base Material theme
    // defaults to instead of the user's chosen accent (Group B, matching
    // this screen's other action controls like TEST SUMMARY / SCAN FILLED
    // ANSWER SHEETS). Same three-call pattern as promptRenameTest.
    private fun promptAddFolder() {
        val density = resources.displayMetrics.density
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox)
        ).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
            hint = "Folder name"
        }
        val input = com.google.android.material.textfield.TextInputEditText(inputLayout.context)
        inputLayout.addView(input)
        val accent = AccentColorPrefs.getGroupB(this)
        inputLayout.boxStrokeColor = accent
        inputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(accent)
        AccentColorPrefs.tintTextInputLayoutCursor(inputLayout, accent)
        AccentColorPrefs.tintTextSelectHandles(input, accent)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add folder")
            .setView(inputLayout)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Enter a folder name", Toast.LENGTH_SHORT).show()
                } else {
                    ScannedSheetStorage.addFolder(this, testName, name)
                    selectedFolder = name
                    rebuildFolderTabs()
                    rebuildScanList()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    // v3.7.5: same accent-aware TextInputLayout treatment as promptAddFolder
    // above (boxStrokeColor/hintTextColor/cursor/selection-handle tinting) —
    // this dialog reuses that exact pattern rather than a bare EditText, so
    // renaming a folder doesn't look inconsistent with creating one.
    private fun promptRenameFolder(folder: String) {
        val density = resources.displayMetrics.density
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox)
        ).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
            hint = "Folder name"
        }
        val input = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            setText(folder)
            setSelection(folder.length)
        }
        inputLayout.addView(input)
        val accent = AccentColorPrefs.getGroupB(this)
        inputLayout.boxStrokeColor = accent
        inputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(accent)
        AccentColorPrefs.tintTextInputLayoutCursor(inputLayout, accent)
        AccentColorPrefs.tintTextSelectHandles(input, accent)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename folder")
            .setView(inputLayout)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                val existingFolders = ScannedSheetStorage.listFoldersForTest(this, testName)
                when {
                    name.isBlank() -> Toast.makeText(this, "Enter a folder name", Toast.LENGTH_SHORT).show()
                    name == folder -> { /* unchanged — nothing to do */ }
                    existingFolders.contains(name) -> Toast.makeText(this, "A folder named \"$name\" already exists", Toast.LENGTH_SHORT).show()
                    else -> {
                        ScannedSheetStorage.renameFolder(this, testName, folder, name)
                        selectedFolder = name
                        rebuildFolderTabs()
                        rebuildScanList()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    private fun promptRemoveFolder(folder: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Remove folder \"$folder\"?")
            .setMessage("Any scans in this folder will move to Main instead — they will NOT be deleted.")
            .setPositiveButton("Remove") { _, _ ->
                ScannedSheetStorage.removeFolder(this, testName, folder)
                selectedFolder = ScannedSheetStorage.MAIN_FOLDER
                rebuildFolderTabs()
                rebuildScanList()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** Item 9 (v2.5): lets a teacher scan directly from within a folder
     *  they're already viewing, instead of going back to MainActivity and
     *  picking the folder from a dialog. SavedScansListActivity only holds
     *  a testName string (unlike MainActivity, which keeps the loaded
     *  template/key/points in memory already), so this loads the full
     *  SavedTest fresh via TestStorage before doing anything else. */
    private fun startScanningIntoCurrentFolder() {
        val saved = TestStorage.loadTestOrNull(this, testName)
        if (saved == null || saved.answerKey.size != saved.template.questionCount) {
            // Same check/wording as MainActivity.startScanning() — a
            // teacher landing here from a test with no key yet (or a key
            // that no longer matches questionCount) needs the same nudge
            // back to Generate Answer Sheet Template.
            Toast.makeText(
                this,
                "Set up the answer key first from \"Generate Answer Sheet Template\"",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        ActiveTest.current = saved
        // No folder-picker dialog here, unlike MainActivity's version — the
        // teacher is already looking at the folder (Main or a subfolder)
        // they want scans to land in.
        val intent = android.content.Intent(this, ScanActivity::class.java)
            .putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_GRADE)
            .putExtra(ScanActivity.EXTRA_SCAN_FOLDER, selectedFolder)
        startActivity(intent)
    }

    /**
     * IMPORTANT: does NOT reuse the same listScansForTest(this, testName,
     * selectedFolder) call used to build the on-screen scan list above —
     * that call, when selectedFolder is MAIN_FOLDER, only returns scans
     * literally saved under "main" and does not aggregate subfolders.
     * TestSummaryDialog handles that distinction internally (folder=null
     * means "every folder combined").
     */
    private fun showTestSummary() {
        val isMain = selectedFolder == ScannedSheetStorage.MAIN_FOLDER
        TestSummaryDialog.show(this, testName, if (isMain) null else selectedFolder)
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
