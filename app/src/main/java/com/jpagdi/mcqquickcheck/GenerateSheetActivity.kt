package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Dedicated screen for configuring a test's sheet template — test name,
 * question count, subject teacher, choice count, paper layout, and
 * columns/questions-per-column — with a live preview that re-renders on
 * every change using the exact same drawing code as the real printed PDF
 * (SheetGenerator.renderPreviewBitmap), so what's shown here always matches
 * what actually prints. Generate & Share PDF also lives here now, right next
 * to the preview that shows exactly what it's about to produce.
 *
 * Answer key creation also lives here now (tap Create Answer Key -> choose
 * Create Manually or Scan Answer Key Instead) rather than being split across
 * separate buttons on the home screen — both the template AND whatever key
 * was created in this session are returned together to MainActivity.
 */
class GenerateSheetActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXISTING_TEMPLATE = "existing_template"
        const val EXTRA_EXISTING_PAPER_LAYOUT = "existing_paper_layout"
        const val EXTRA_ANSWER_KEY = "answer_key"
        const val EXTRA_ANSWER_KEY_POINTS = "answer_key_points"

        const val EXTRA_RESULT_TEMPLATE = "result_template"
        const val EXTRA_RESULT_PAPER_LAYOUT = "result_paper_layout"
        const val EXTRA_RESULT_ANSWER_KEY = "result_answer_key"
        const val EXTRA_RESULT_POINTS = "result_points"

        /** v1.2.0: ids of the enrolled classes this exam is assigned to
         *  (ClassStorage ids), passed in when editing and back out in the result. */
        const val EXTRA_EXISTING_CLASS_IDS = "existing_class_ids"
        const val EXTRA_RESULT_CLASS_IDS = "result_class_ids"
        // v1.2.5 (Test sets): keys of sets 2..4, encoded by SetKeyStore.encode.
        const val EXTRA_RESULT_SET_KEYS = "result_set_keys"

        /** v1.2.0: free-plan item cap, enforced in clampQuestionCountForLayout
         *  below regardless of what the paper layout itself could otherwise
         *  fit. Paid plans (featuresLocked == false) are unaffected. */

        /** Suggests a columns/rows pair for [questionCount] on [layout] —
         *  used as an autofill when the teacher enters a question count, so
         *  they don't have to manually work out a valid columns+rows
         *  combination themselves. Aims for a near-square-ish grid: tries
         *  every valid column count for this layout (1 up to the layout's
         *  max) and picks the one whose resulting column height (rows) is
         *  closest to the column count itself — a sheet that's roughly as
         *  tall (in items) as it is wide (in columns) reads as more
         *  "balanced" than one very tall column or many very short ones,
         *  and incidentally tends to minimize empty trailing cells in the
         *  last, partially-filled column too. This is a heuristic, not a
         *  strict optimization — "best fits" wasn't given a precise
         *  definition in the request, so this is a reasonable, explainable
         *  interpretation of it, not the only possible one.
         */
        // Note: no longer called internally as of the v2.0 sequencing
        // change (etQuestionCount's focus-loss handler now only re-fits
        // rows for the CURRENT columns, not both) — kept as a public
        // companion function since it still directly implements item 9's
        // near-square autofill math and may be wired back in later.
        fun suggestColumnsAndRows(questionCount: Int, layout: PaperLayout): Pair<Int, Int> {
            val n = questionCount.coerceAtLeast(1)
            if (n <= 10) return 1 to n
            val maxColumns = effectiveColumnsRange(n, layout).last
            var bestColumns = 1
            var bestRows = SheetTemplate.rowsPerBlockRange(n, 1).first
            var bestScore = Double.MAX_VALUE
            for (c in 1..maxColumns) {
                // Use the LOW end of rowsPerBlockRange (the smallest valid
                // multiple of 5 that still fits everything), not a raw
                // ceil(n/c) — this is what makes 45 items / 3 columns land
                // on the even 15-15-15 split instead of the lopsided
                // 20-20-5 the raw nearest-10 ceiling alone would suggest.
                val rows = SheetTemplate.rowsPerBlockRange(n, c).first
                val score = kotlin.math.abs(rows - c).toDouble()
                if (score < bestScore) {
                    bestScore = score
                    bestColumns = c
                    bestRows = rows
                }
            }
            return bestColumns to bestRows
        }

        /** v2.4: column count is now a function of BOTH layout and item
         *  count — each tier collapses to a single valid value, not a
         *  range to pick within. Delegates entirely to SheetTemplate (the
         *  single source of truth for this) rather than duplicating the
         *  tier logic here; the old local "10 or fewer items forces 1
         *  column" special case is now just tier 1 of QUARTER's rule
         *  inside SheetTemplate.maxBlocksPerRowGroupRange. */
        fun effectiveColumnsRange(questionCount: Int, layout: PaperLayout): IntRange =
            SheetTemplate.maxBlocksPerRowGroupRange(questionCount, layout)
    }

    private lateinit var etTestName: EditText
    private lateinit var etQuestionCount: EditText
    private lateinit var rgChoiceCount: RadioGroup
    private lateinit var etColumnsValue: EditText
    private lateinit var etRowsValue: EditText
    private lateinit var tvLayoutHint: TextView
    private lateinit var tvPreviewNote: TextView
    private lateinit var tvKeyStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var btnPreview: android.view.View
    private lateinit var ivPreviewToggleIcon: ImageView
    // v3.4: bubbleSizeMm is now fixed, not slider-adjustable — see
    // BubbleSizePrefs.FIXED_MM. Set once in onCreate below (from
    // existingTemplate.bubbleSizeMm when editing, so an existing test's
    // ORIGINAL baked-in size is preserved untouched even if it's not
    // 2.68mm — or FIXED_MM for a brand new template) and never changed
    // afterward, since there's no UI left to change it.
    private var currentBubbleSizeMm: Float = BubbleSizePrefs.FIXED_MM

    // v1.2.0 (Classes feature): digits of this sheet's ID row (0 = none) and
    // the classes assigned to the exam. A NEW sheet gets the ID row (digit
    // count from the Settings stepper) whenever Classes is active; an
    // EXISTING test keeps the digits it was created with, except that
    // assigning a class to a test that has no ID row adds one (see
    // currentIdDigits) — an explicit edit, reprinting is already implied.
    private var existingIdDigits = 0
    private var editingExistingTest = false
    private var selectedClassIds: MutableList<String> = mutableListOf()
    private var classesCardSubtitle: TextView? = null

    // v1.2.5 (Test sets)
    private var setCount = 1
    private var setKeys: MutableMap<Int, List<Int>> = mutableMapOf()
    private var setKeysHolder: android.widget.LinearLayout? = null
    private var pendingSetKeyIndex = -1
    private var scanningSetIndex = 0

    private val setKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingSetKeyIndex > 0) {
            val key = result.data?.getIntArrayExtra(AnswerKeyActivity.EXTRA_RESULT_KEY)
            if (key != null) {
                setKeys[pendingSetKeyIndex] = key.toList()
                Toast.makeText(this, "Set key saved", Toast.LENGTH_SHORT).show()
                rebuildSetsCards()
            }
        }
        pendingSetKeyIndex = -1
    }

    /** The ID-row digit count the template being built should carry. */
    private fun currentIdDigits(): Int {
        if (!ClassesAccess.isActive(this)) return existingIdDigits
        if (existingIdDigits > 0) return existingIdDigits
        return if (!editingExistingTest || selectedClassIds.isNotEmpty()) SettingsPrefs.getIdDigits(this) else 0
    }

    private var columns = 3
    private var rows = 10
    private var showingKeyPreview = false
    private var answerKey: List<Int>? = null
    private var answerPoints: List<Int>? = null
    private var suppressFieldEvents = false

    private val createKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val key = result.data?.getIntArrayExtra(AnswerKeyActivity.EXTRA_RESULT_KEY)
            val points = result.data?.getIntArrayExtra(AnswerKeyActivity.EXTRA_RESULT_POINTS)
            if (key != null) {
                answerKey = key.toList()
                answerPoints = points?.toList() ?: List(key.size) { 1 }
                onKeyUpdated()
                Toast.makeText(this, "Answer key saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // v2.9: no-op result handler — declining just means the "saved to
    // Downloads" notification won't show later; the file itself still
    // saves fine either way, so there's nothing to react to here.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val scanKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scannedKey = result.data?.getStringExtra(ScanActivity.EXTRA_SCANNED_KEY)
        // v1.2.5: 0 = the test's own key, 1..3 = a set's key (see openSetKeyEditor).
        val targetSet = scanningSetIndex
        scanningSetIndex = 0
        if (result.resultCode == RESULT_OK && scannedKey != null) {
            val choiceCount = if (rgChoiceCount.checkedRadioButtonId == R.id.rb5Choices) 5 else 4
            try {
                // v3.3: parseKeyTokens (not parseKey) so a "?" token from
                // an unclear-but-saved key scan (blank/ambiguous bubble —
                // see ScanActivity.showKeyScanResult) is skipped rather
                // than crashing the whole parse. The resulting key can be
                // shorter than questionCount; that's expected here, not an
                // error — onKeyUpdated()'s existing length check catches a
                // genuinely mismatched key before grading, this toast just
                // tells the teacher up front what's missing.
                val tokens = scannedKey.split(Regex("[,\\n\\s]+")).filter { it.isNotBlank() }
                val parsed = AnswerParser.parseKeyTokens(tokens, choiceCount)

                // SAFETY CHECK: answerKey is a flat List<Int>, positionally
                // indexed 1:1 against question number (see onKeyUpdated's
                // key.size != count check above) — no way to represent a
                // gap. Dropping a "?" from the MIDDLE of the list would
                // silently shift every later question's answer back by one
                // position — e.g. a "?" at Q11 out of 30 would make the
                // parsed Q12 answer land at index 11 and get graded as if
                // it were Q12's, corrupting every question after the gap.
                // Only safe to auto-fill when every unresolved question is
                // at the trailing end (nothing resolved comes after it).
                // Deliberately blocking rather than a full gap-aware
                // answerKey type change — that touches grading/storage/
                // rendering across the app, out of scope here.
                val lastResolvedTokenIndex = tokens.indices.lastOrNull { tokens[it].trim() != "?" }
                val hasMidKeyGap = parsed.unresolvedQuestionNumbers.any { qNum ->
                    lastResolvedTokenIndex != null && (qNum - 1) < lastResolvedTokenIndex
                }

                if (hasMidKeyGap) {
                    Toast.makeText(
                        this,
                        "Scan had unclear answers in the middle of the key (Q${parsed.unresolvedQuestionNumbers.joinToString(", ")}) " +
                            "— couldn't safely auto-fill without risking a misalignment. Please enter the key manually or retake the scan.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    if (targetSet > 0) {
                        setKeys[targetSet] = parsed.answers
                        rebuildSetsCards()
                    } else {
                        answerKey = parsed.answers
                        answerPoints = List(parsed.answers.size) { 1 }
                        onKeyUpdated()
                    }
                    val message = if (parsed.unresolvedQuestionNumbers.isEmpty()) {
                        "Answer key filled from scan — please double-check it"
                    } else {
                        "Answer key filled from scan, but Q${parsed.unresolvedQuestionNumbers.joinToString(", ")} " +
                            "couldn't be read clearly — please fill ${if (parsed.unresolvedQuestionNumbers.size == 1) "it" else "those"} in and double-check the rest"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Scanned key error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_sheet)

        DrawerHelper.attach(
            this,
            findViewById(R.id.drawerLayout),
            findViewById(R.id.btnOpenDrawer)
        )
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        etTestName = findViewById(R.id.etTestName)
        etQuestionCount = findViewById(R.id.etQuestionCount)
        rgChoiceCount = findViewById(R.id.rgChoiceCount)
        etColumnsValue = findViewById(R.id.etColumnsValue)
        etRowsValue = findViewById(R.id.etRowsValue)
        tvLayoutHint = findViewById(R.id.tvLayoutHint)
        tvPreviewNote = findViewById(R.id.tvPreviewNote)
        tvKeyStatus = findViewById(R.id.tvKeyStatus)
        ivPreview = findViewById(R.id.ivPreview)
        btnPreview = findViewById(R.id.btnPreview)
        ivPreviewToggleIcon = findViewById(R.id.ivPreviewToggleIcon)
        syncPreviewToggleIcon()

        @Suppress("DEPRECATION")
        val existingTemplate = intent.getSerializableExtra(EXTRA_EXISTING_TEMPLATE) as? SheetTemplate
        val existingLayoutName = intent.getStringExtra(EXTRA_EXISTING_PAPER_LAYOUT)
        val keyArray = intent.getIntArrayExtra(EXTRA_ANSWER_KEY)
        val pointsArray = intent.getIntArrayExtra(EXTRA_ANSWER_KEY_POINTS)
        answerKey = keyArray?.toList()
        answerPoints = pointsArray?.toList() ?: answerKey?.let { List(it.size) { 1 } }

        if (existingTemplate != null) {
            // Editing an existing test's template (launched from
            // TestDetailActivity's Answer Sheet Template button) — same
            // screen, same fields, just relabeled so it doesn't read as
            // "starting from scratch" when it isn't.
            findViewById<TextView>(R.id.tvGenerateScreenTitle).text = "Edit Answer Sheet Template"
            findViewById<TextView>(R.id.tvCreateKeyLabel).text = "Edit Answer Key"

            etTestName.setText(existingTemplate.testName)
            etQuestionCount.setText(existingTemplate.questionCount.toString())
            rgChoiceCount.check(if (existingTemplate.choiceCount == 5) R.id.rb5Choices else R.id.rb4Choices)
            columns = existingTemplate.maxBlocksPerRowGroup
            rows = existingTemplate.rowsPerBlock
            // v3.4: preserves this test's ORIGINAL baked-in bubble size —
            // per explicit product decision, an existing test does NOT get
            // silently forced to the new FIXED_MM value just because the
            // slider that used to let a teacher pick a different size is
            // gone. MarkerDetector/BubbleGrader read bubbleSizeMm from
            // this saved template, not any live setting (see
            // BubbleSizePrefs.kt), so this is the only place a change here
            // could matter, and it deliberately doesn't change anything.
            currentBubbleSizeMm = existingTemplate.bubbleSizeMm
            existingIdDigits = existingTemplate.idDigits
            editingExistingTest = true
            setCount = existingTemplate.setCount
            setKeys = SetKeyStore.all(this, existingTemplate.testName).toMutableMap()
        } else {
            // New template (not editing an existing one): pre-fill from
            // Settings defaults instead of whatever the XML/field defaults
            // happened to be. Doesn't touch the existingTemplate branch
            // above at all — editing an existing test's shape is unrelated
            // to what a brand NEW test starts out as.
            val defaults = SettingsPrefs.getDefaults(this)
            etQuestionCount.setText(defaults.questionCount.toString())
            rgChoiceCount.check(if (defaults.choiceCount == 5) R.id.rb5Choices else R.id.rb4Choices)
            columns = defaults.columns
            rows = defaults.questionsPerColumn
            currentBubbleSizeMm = BubbleSizePrefs.FIXED_MM
        }

        findViewById<Button>(R.id.btnQuestionCountMinus).setOnClickListener { adjustQuestionCount(-1) }
        findViewById<Button>(R.id.btnQuestionCountPlus).setOnClickListener { adjustQuestionCount(1) }
        findViewById<Button>(R.id.btnColumnsMinus).setOnClickListener { adjustColumns(-1) }
        findViewById<Button>(R.id.btnColumnsPlus).setOnClickListener { adjustColumns(1) }
        findViewById<Button>(R.id.btnRowsMinus).setOnClickListener { adjustRows(-1) }
        findViewById<Button>(R.id.btnRowsPlus).setOnClickListener { adjustRows(1) }

        findViewById<android.view.View>(R.id.btnCreateKey).setOnClickListener { showCreateKeyDialog() }
        selectedClassIds = (intent.getStringArrayExtra(EXTRA_EXISTING_CLASS_IDS)?.toMutableList()) ?: mutableListOf()
        addClassesCard()
        addSetsCards()

        btnPreview.setOnClickListener { togglePreview() }

        findViewById<android.view.View>(R.id.btnUseTemplate).setOnClickListener { useTemplate() }
        findViewById<android.view.View>(R.id.btnGeneratePdf).setOnClickListener { showPaperSizePickerThenGenerate() }
        if (DownloadNotifier.needsPermissionRequest(this)) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        etTestName.addTextChangedListener(afterChange = { refreshPreview() })
        etQuestionCount.addTextChangedListener(afterChange = {
            // Keep the internal question count usable for refreshPreview()
            // on every keystroke (so the preview stays live), but DON'T run
            // autofill or sync the Columns/Rows fields' displayed text here
            // — that would rewrite them on every digit typed (e.g. typing
            // "120" one digit at a time would suggest a layout for "1",
            // then "12", then finally "120"), which is visibly disruptive.
            // Autofill runs once, on focus-loss, below instead.
            refreshPreview()
        })
        etQuestionCount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val typed = etQuestionCount.text.toString().toIntOrNull()?.coerceAtLeast(1)
            if (typed != null) {
                val count = clampQuestionCountForLayout(typed)
                if (count != typed) etQuestionCount.setText(count.toString())
                // Sequencing (v2.0): this no longer overwrites the column
                // count. The teacher sets columns themselves — either by
                // hand, or via the Settings-defaults prefill for a brand
                // new template (a separate code path in onCreate that's
                // unaffected by this listener) — and only ROWS gets
                // re-fit here, for whatever column count is already
                // chosen. Uses the smallest valid multiple-of-5 fit
                // (rowsPerBlockRange(...).first), same "prefer the even
                // split" rule as suggestColumnsAndRows.
                rows = SheetTemplate.rowsPerBlockRange(count, columns).first
            }
            clampColumnsAndRows()
            refreshPreview()
        }
        etColumnsValue.addTextChangedListener(afterChange = {
            if (suppressFieldEvents) return@addTextChangedListener
            val typed = etColumnsValue.text.toString().toIntOrNull()
            if (typed != null) {
                // Clamp the value actually used for layout math immediately,
                // so the preview is always correct even while the user is
                // still typing — but DON'T rewrite the field's displayed
                // text here, since that would fight the user mid-keystroke
                // (e.g. typing "40" one digit at a time, having it jump to
                // "30" after the first valid-but-out-of-range digit lands).
                // The field gets visually corrected on focus-loss instead
                // (see setOnFocusChangeListener below) — that's the point
                // where a form field settling to a valid value reads as
                // normal, not as the field fighting you.
                val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
                columns = typed.coerceIn(effectiveColumnsRange(count, paperLayout()))
                // Same reasoning as adjustColumns() — changing columns
                // changes the max items-per-column too, so rows needs a
                // fresh clamp against the new column count. Only updates
                // the internal value here (not the displayed text), same
                // "don't fight the user mid-typing" rule as columns itself.
                rows = rows.coerceIn(SheetTemplate.rowsPerBlockRange(count, columns))
                refreshPreview()
            }
        })
        etColumnsValue.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) setFieldTextSilently(etColumnsValue, columns.toString())
        }
        etRowsValue.addTextChangedListener(afterChange = {
            if (suppressFieldEvents) return@addTextChangedListener
            val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
            val typed = etRowsValue.text.toString().toIntOrNull()
            if (typed != null) {
                rows = typed.coerceIn(SheetTemplate.rowsPerBlockRange(count, columns))
                refreshPreview()
            }
        })
        etRowsValue.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            // Snap to the nearest valid multiple of 5 on settle, same
            // "don't fight the user mid-typing, correct on focus-loss"
            // pattern used everywhere else in this screen.
            val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
            rows = nearestValidMultipleOf5(rows, SheetTemplate.rowsPerBlockRange(count, columns))
            setFieldTextSilently(etRowsValue, rows.toString())
        }
        rgChoiceCount.setOnCheckedChangeListener { _, _ -> refreshPreview() }

        clampColumnsAndRows()
        onKeyUpdated()
        refreshPreview()

        // Back gesture (system back button/gesture, not an on-screen Back
        // button — this screen doesn't have one) now confirms before
        // discarding whatever's been typed, instead of silently closing.
        onBackPressedDispatcher.addCallback(this) {
            val confirmDialog = AlertDialog.Builder(this@GenerateSheetActivity)
                .setTitle("Discard answer sheet?")
                .setMessage("Changes haven't been saved yet.")
                .setPositiveButton("DISCARD") { _, _ ->
                    // isEnabled = false + reinvoking lets the system handle
                    // the actual back navigation normally, rather than
                    // calling finish() directly — safer if some other
                    // OnBackPressedCallback ever gets added to this
                    // activity later, since this only removes THIS
                    // callback from the chain instead of assuming it's the
                    // only thing back should do.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                .setNegativeButton("KEEP GENERATING", null)
                .create()
            confirmDialog.show()
            fixDialogButtonColors(confirmDialog)
        }
    }

    /** Same fix as other activities' fixDialogButtonColors — AlertDialog
     *  buttons default to colorPrimary as text color, unreadable in
     *  Dark/Black themes since those themes never set
     *  android:textColorPrimary. Resolving it via theme.resolveAttribute()
     *  wasn't reliable (tried and reverted in an earlier version); using
     *  ThemePrefs.getTheme() directly instead. */
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

    private fun showCreateKeyDialog() {
        val count = etQuestionCount.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "Enter a valid number of questions first", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Create Answer Key")
            .setItems(arrayOf("Create Manually", "Scan Answer Key Instead")) { _, which ->
                val template = buildTemplate() ?: return@setItems
                if (which == 0) {
                    val intent = Intent(this, AnswerKeyActivity::class.java)
                        .putExtra(AnswerKeyActivity.EXTRA_TEMPLATE, template)
                    val existingKey = answerKey
                    if (existingKey != null && existingKey.size == template.questionCount) {
                        intent.putExtra(AnswerKeyActivity.EXTRA_EXISTING_KEY, existingKey.toIntArray())
                        intent.putExtra(
                            AnswerKeyActivity.EXTRA_EXISTING_POINTS,
                            (answerPoints ?: List(existingKey.size) { 1 }).toIntArray()
                        )
                    }
                    createKeyLauncher.launch(intent)
                } else {
                    ActiveTest.current = SavedTest(template, emptyList(), paperLayout())
                    val intent = Intent(this, ScanActivity::class.java)
                        .putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_KEY)
                    scanKeyLauncher.launch(intent)
                }
            }
            .show()
    }

    private fun onKeyUpdated() {
        val key = answerKey
        val count = etQuestionCount.text.toString().toIntOrNull()
        tvKeyStatus.text = when {
            key == null -> "No answer key set yet"
            count != null && key.size != count ->
                "Answer key has ${key.size} items — doesn't match $count questions. Recreate it."
            else -> {
                val total = (answerPoints ?: List(key.size) { 1 }).sum()
                "Answer key set — ${key.size} items, $total pts total"
            }
        }
        // v3.7: was btnPreviewKey.isEnabled/alpha, disabling the Answer Key
        // Preview button entirely when no key existed yet. Now that Blank
        // Preview and Answer Key Preview are one toggle button, "disabled"
        // doesn't really apply the same way — instead, syncPreviewToggleIcon()
        // just keeps the icon in sync with showingKeyPreview, and
        // togglePreview() below refuses to switch to key mode (with a
        // toast) when there's nothing to preview.
        syncPreviewToggleIcon()
        if (key == null) {
            tvPreviewNote.visibility = android.view.View.VISIBLE
            tvPreviewNote.text = "Create an answer key above to preview it filled in."
        } else {
            tvPreviewNote.visibility = android.view.View.GONE
        }
    }

    /** Clamps [count] into this layout's [min, max] question-count range,
     *  showing a Toast when clamping actually occurs. Shared by the typed-
     *  input focus-loss handler AND the -/+ steppers so a stepper tap and
     *  a typed value can never land on a different answer for the same
     *  out-of-range count.
     *
     *  v3.4: simplified now that QUARTER is the only PaperLayout value
     *  (HALF_CROSS/HALF_LENGTH removed) — the old "switch to a different
     *  sheet size" wording no longer makes sense since there's nothing
     *  left to switch to; both messages now just state the actual
     *  min/max (10/90, per SheetTemplate's v3.4 range change), not a
     *  hardcoded "60" that would now be wrong at the max end. */
    private fun clampQuestionCountForLayout(count: Int): Int {
        var result = count.coerceAtLeast(1)
        val layout = paperLayout()
        var maxForLayout = SheetTemplate.maxQuestionCountForLayout(layout)
        if (result > maxForLayout) {
            result = maxForLayout
            val reason = "${layout.label} sheets support up to $maxForLayout items"
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        }
        val minForLayout = SheetTemplate.minQuestionCountForLayout(layout)
        if (result < minForLayout) {
            result = minForLayout
            Toast.makeText(this, "${layout.label} sheets need at least $minForLayout items", Toast.LENGTH_SHORT).show()
        }
        return result
    }

    /** Stepper for the question-count field itself (item 2, v2.5) — steps
     *  by 1, not 5, since this is a raw item count rather than a per-column
     *  fit like rows. Routes every adjustment through the same clamp+Toast
     *  function the typed-input path uses, so the steppers can't produce a
     *  count the text field's own validation would have rejected. */
    private fun adjustQuestionCount(delta: Int) {
        val current = etQuestionCount.text.toString().toIntOrNull() ?: 1
        val count = clampQuestionCountForLayout(current + delta)
        setFieldTextSilently(etQuestionCount, count.toString())
        rows = SheetTemplate.rowsPerBlockRange(count, columns).first
        setFieldTextSilently(etRowsValue, rows.toString())
        clampColumnsAndRows()
        refreshPreview()
    }

    private fun adjustColumns(delta: Int) {
        val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
        val range = effectiveColumnsRange(count, paperLayout())
        columns = (columns + delta).coerceIn(range)
        setFieldTextSilently(etColumnsValue, columns.toString())
        // Changing columns changes the max items-per-column too (the cap is
        // now n/columns rounded to the nearest 10, editable down to a
        // multiple of 5) — re-fit rows to the smallest valid multiple of 5
        // for the new column count, so it can't be left holding a value
        // that was valid for the OLD column count but isn't anymore.
        rows = SheetTemplate.rowsPerBlockRange(count, columns).first
        setFieldTextSilently(etRowsValue, rows.toString())
        // v3.6.1 fix: this stepper never used to call clampColumnsAndRows(),
        // so tvLayoutHint ("Up to X questions per column...") kept showing
        // whatever range was true before this tap — stale text next to a
        // preview that had already moved on. clampColumnsAndRows() re-derives
        // from the fields just set above, so it's a no-op on columns/rows
        // here; its only real effect is refreshing the hint text.
        clampColumnsAndRows()
        refreshPreview()
    }

    private fun adjustRows(delta: Int) {
        val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
        val range = SheetTemplate.rowsPerBlockRange(count, columns)
        // Steps by 5, since any value that isn't a multiple of 5 isn't a
        // valid items-per-column choice under the new rule (n<=10's
        // single-value range is an exception where delta has no effect,
        // since range.first == range.last already).
        val stepped = (rows + delta * 5)
        rows = nearestValidMultipleOf5(stepped, range)
        setFieldTextSilently(etRowsValue, rows.toString())
        // v3.6.1 fix: same missing hint-refresh bug as adjustColumns()
        // above — this is the stepper that actually reproduced it (repeated
        // taps on the Rows "+" button left the hint frozen at an old,
        // narrower range while the field and preview kept climbing).
        clampColumnsAndRows()
        refreshPreview()
    }

    /** Snaps [value] to the nearest multiple of 5 that still falls inside
     *  [range] (both range bounds are themselves guaranteed multiples of 5,
     *  or equal to n itself in the n<=10 case where the range is a single
     *  value) — used any time rows is set from something that might not
     *  land on a valid step (typed text, stepper arithmetic). */
    private fun nearestValidMultipleOf5(value: Int, range: IntRange): Int {
        if (range.first == range.last) return range.first
        val clamped = value.coerceIn(range)
        val rounded = ((clamped + 2) / 5) * 5
        return rounded.coerceIn(range)
    }

    /** Clamps columns/rows into range whenever question count changes,
     *  so a value that was valid before doesn't silently become invalid.
     *  v3.4: the "switching paper layout" scenario this originally guarded
     *  against no longer applies (QUARTER is the only layout now), but the
     *  max-count re-check still matters for question count itself. */
    private fun clampColumnsAndRows() {
        var count = etQuestionCount.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val maxForLayout = SheetTemplate.maxQuestionCountForLayout(paperLayout())
        if (count > maxForLayout) {
            count = maxForLayout
            setFieldTextSilently(etQuestionCount, count.toString())
        }
        val colsRange = effectiveColumnsRange(count, paperLayout())
        columns = columns.coerceIn(colsRange)
        // Columns must be clamped BEFORE computing rowsRange, since the
        // rows cap now depends on the (possibly just-clamped) column count.
        val rowsRange = SheetTemplate.rowsPerBlockRange(count, columns)
        rows = nearestValidMultipleOf5(rows, rowsRange)
        setFieldTextSilently(etRowsValue, rows.toString())
        setFieldTextSilently(etColumnsValue, columns.toString())
        tvLayoutHint.text = if (count <= 10)
            "10 or fewer items: single column holding all $count"
        else
            "Up to ${rowsRange.last} questions per column (multiples of 5, min ${rowsRange.first}), ${colsRange.last} columns for this paper layout"
    }

    /** Updates a field's text without re-triggering its own change listener
     *  (used when the STEPPER buttons or clamping change the value — typing
     *  directly into the field still goes through the normal listener). */
    private fun setFieldTextSilently(field: EditText, text: String) {
        suppressFieldEvents = true
        if (field.text.toString() != text) field.setText(text)
        suppressFieldEvents = false
    }

    /** v3.4: QUARTER is now the only PaperLayout value (HALF_CROSS/
     *  HALF_LENGTH removed, along with the now-pointless rgPaperLayout
     *  radio group that used to pick between them — see PaperLayout.kt).
     *  Kept as a function (not inlined at every call site) so every
     *  existing caller below stays unchanged if a second layout is ever
     *  reintroduced. */
    private fun paperLayout(): PaperLayout = PaperLayout.QUARTER

    /** v1.2.0: "Classes" card under the answer-key button — which enrolled
     *  classes take this exam. Shown only while Classes is active (Semester /
     *  Full-Year plan AND the Settings switch on). Inserted from code so the
     *  big layout XML stays untouched. */
    private fun addClassesCard() {
        if (!ClassesAccess.isActive(this)) return
        val anchor = findViewById<android.view.View>(R.id.btnCreateKey)
        val parent = anchor.parent as? android.widget.LinearLayout ?: return
        val card = ClassUi.card(this, "Classes", "None assigned") {
            val digits = currentIdDigits().takeIf { it > 0 } ?: SettingsPrefs.getIdDigits(this)
            ClassAssignDialog.show(this, selectedClassIds, digits) { chosen ->
                selectedClassIds = chosen.toMutableList()
                refreshClassesCard()
                refreshPreview()
            }
        }
        // ClassUi.card's text column is child 0; its subtitle is that column's 2nd child.
        classesCardSubtitle = (card.getChildAt(0) as android.widget.LinearLayout).getChildAt(1) as? TextView
        (card.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin = ClassUi.dp(this, 10)
        parent.addView(card, parent.indexOfChild(anchor) + 1)
        refreshClassesCard()
    }

    private fun refreshClassesCard() {
        val digits = currentIdDigits().takeIf { it > 0 } ?: SettingsPrefs.getIdDigits(this)
        val summary = ClassAssignDialog.summary(this, selectedClassIds)
        classesCardSubtitle?.apply {
            text = "$summary · ID row: $digits digits"
            textSize = 13f
            alpha = 0.65f
        }
    }

    // ---- v1.2.5 (Test sets) ------------------------------------------------

    /** Inserts the "Sets" card (and, below it, one key card per extra set)
     *  under the answer-key button. Built from code like the Classes card so
     *  the big layout XML stays untouched. */
    private fun addSetsCards() {
        val anchor = findViewById<android.view.View>(R.id.btnCreateKey)
        val parent = anchor.parent as? android.widget.LinearLayout ?: return
        val holder = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ClassUi.dp(this@GenerateSheetActivity, 10) }
        }
        setKeysHolder = holder
        parent.addView(holder, parent.indexOfChild(anchor) + 1)
        rebuildSetsCards()
    }

    private fun setCardSummary(): String =
        if (setCount <= 1) "Off — one answer key"
        else "$setCount sets (${SheetTemplate.setLabelsFor(setCount).joinToString(", ")}) — students shade their set"

    private fun rebuildSetsCards() {
        val holder = setKeysHolder ?: return
        holder.removeAllViews()
        holder.addView(ClassUi.card(this, "Test sets", setCardSummary()) { showSetCountDialog() })
        if (setCount <= 1) return
        val labels = SheetTemplate.setLabelsFor(setCount)
        val questions = etQuestionCount.text.toString().toIntOrNull()
        holder.addView(
            ClassUi.card(
                this, "Set ${labels[0]}",
                "Uses the main answer key above"
            )
        )
        for (i in 1 until setCount) {
            val k = setKeys[i]
            val status = when {
                k == null -> "No key yet — tap to create"
                questions != null && k.size != questions -> "Key has ${k.size} items, not $questions — tap to recreate"
                else -> "Key set — ${k.size} items"
            }
            holder.addView(ClassUi.card(this, "Set ${labels[i]} key", status) { openSetKeyEditor(i) })
        }
    }

    private fun showSetCountDialog() {
        val options = arrayOf("No sets", "2 sets (X, Y)", "3 sets (X, Y, Z)", "4 sets (W, X, Y, Z)")
        AlertDialog.Builder(this)
            .setTitle("Test sets")
            .setSingleChoiceItems(options, setCount - 1) { dialog, which ->
                setCount = which + 1
                // keys of sets that no longer exist are dropped
                setKeys = setKeys.filterKeys { it < setCount }.toMutableMap()
                rebuildSetsCards()
                refreshPreview()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Same choice the main key offers: type the key in, or scan a sheet of
     *  that set with the right answers shaded. */
    private fun openSetKeyEditor(index: Int) {
        val template = buildTemplate()
        if (template == null) {
            Toast.makeText(this, "Enter a valid number of questions first", Toast.LENGTH_SHORT).show()
            return
        }
        val label = template.setLabels().getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle("Answer key — Set $label")
            .setItems(arrayOf("Create Manually", "Scan Answer Key Instead")) { _, which ->
                if (which == 0) openSetKeyManual(index, template, label)
                else openSetKeyScan(index, template, label)
            }
            .show()
    }

    private fun openSetKeyScan(index: Int, template: SheetTemplate, label: Char) {
        scanningSetIndex = index
        ActiveTest.current = SavedTest(template, emptyList(), paperLayout())
        val intent = Intent(this, ScanActivity::class.java)
            .putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_KEY)
            .putExtra(ScanActivity.EXTRA_KEY_LABEL, "Set $label")
        scanKeyLauncher.launch(intent)
    }

    private fun openSetKeyManual(index: Int, template: SheetTemplate, label: Char) {
        pendingSetKeyIndex = index
        val intent = Intent(this, AnswerKeyActivity::class.java)
            .putExtra(AnswerKeyActivity.EXTRA_TEMPLATE, template.copy(testName = "${template.testName} — Set $label"))
        val existing = setKeys[index]
        if (existing != null && existing.size == template.questionCount) {
            intent.putExtra(AnswerKeyActivity.EXTRA_EXISTING_KEY, existing.toIntArray())
        }
        setKeyLauncher.launch(intent)
    }

    private fun buildTemplate(): SheetTemplate? {
        val name = etTestName.text.toString().ifBlank { "Untitled Test" }
        val count = etQuestionCount.text.toString().toIntOrNull()
        if (count == null || count <= 0) return null
        val choiceCount = if (rgChoiceCount.checkedRadioButtonId == R.id.rb5Choices) 5 else 4

        return SheetTemplate(
            testName = name,
            questionCount = count,
            choiceCount = choiceCount,
            numbering = NumberingOrder.COLUMN_MAJOR,
            rowsPerBlock = rows,
            maxBlocksPerRowGroup = columns,
            bubbleSizeMm = currentBubbleSizeMm,
            idDigits = currentIdDigits(),
            setCount = setCount
        )
    }

    /** v3.7: Blank Preview / Answer Key Preview merged into one toggle
     *  button per explicit request. Tapping flips showingKeyPreview and
     *  swaps the eye icon (closed = blank, open = answer key) — but
     *  refuses to switch to key mode if there's no usable answer key yet,
     *  same guard refreshPreview() already had for the mismatched-size
     *  case, just surfaced earlier so the icon doesn't flip to "open" for
     *  a preview that's about to bounce back to blank anyway. */
    private fun togglePreview() {
        if (!showingKeyPreview) {
            val key = answerKey
            val template = buildTemplate()
            if (key == null || template == null || key.size != template.questionCount) {
                Toast.makeText(this, "Create an answer key above to preview it filled in.", Toast.LENGTH_SHORT).show()
                return
            }
        }
        showingKeyPreview = !showingKeyPreview
        syncPreviewToggleIcon()
        refreshPreview()
    }

    private fun syncPreviewToggleIcon() {
        ivPreviewToggleIcon.setImageResource(
            if (showingKeyPreview) R.drawable.ic_eye else R.drawable.ic_eye_closed
        )
    }

    private fun refreshPreview() {
        val template = buildTemplate() ?: return
        val previewKey = if (showingKeyPreview) answerKey?.takeIf { it.size == template.questionCount } else null
        if (showingKeyPreview && previewKey == null) {
            Toast.makeText(this, "Answer key doesn't match the current question count", Toast.LENGTH_SHORT).show()
            showingKeyPreview = false
            syncPreviewToggleIcon()
        }
        val widthPx = 800
        val bitmap = SheetGenerator.renderPreviewBitmap(template, paperLayout(), widthPx, previewKey, context = this)
        ivPreview.setImageBitmap(bitmap)
    }

    private fun useTemplate() {
        val template = buildTemplate()
        if (template == null) {
            Toast.makeText(this, "Enter a valid number of questions", Toast.LENGTH_SHORT).show()
            return
        }
        val resultIntent = Intent()
            .putExtra(EXTRA_RESULT_TEMPLATE, template)
            .putExtra(EXTRA_RESULT_PAPER_LAYOUT, paperLayout().name)
            .putExtra(EXTRA_RESULT_CLASS_IDS, selectedClassIds.toTypedArray())
            .putExtra(
                EXTRA_RESULT_SET_KEYS,
                SetKeyStore.encode(setKeys.filter { (i, k) -> i in 1 until template.setCount && k.size == template.questionCount })
            )
        val key = answerKey
        if (key != null && key.size == template.questionCount) {
            resultIntent.putExtra(EXTRA_RESULT_ANSWER_KEY, key.toIntArray())
            resultIntent.putExtra(EXTRA_RESULT_POINTS, (answerPoints ?: List(key.size) { 1 }).toIntArray())
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /** v2.9: merged Generate&Share + Download into one GENERATE PDF button.
     *  Writes the PDF once, to context.cacheDir (NOT getExternalFilesDir's
     *  persistent "pdfs" folder like before) — that folder is what was
     *  causing the "saves to both Downloads AND Android/data" report:
     *  the old generateAndDownloadPdf() wrote a permanent copy there
     *  first and never cleaned it up, on top of the Downloads copy. Cache
     *  is scratch space by definition; the Download branch below deletes
     *  it explicitly once the Downloads copy is confirmed written, so
     *  only one persistent copy (Downloads) survives. The Share branch
     *  leaves it in cache since FileProvider needs a real file for as
     *  long as the receiving app might read it — cache is still the
     *  right place for that, it's just not immediately deleted. */
    /** v3.5: paper size used to be a Settings toggle picked ahead of time and
     *  easy to forget was ever set — the teacher had no visibility into which
     *  size was active at the moment they actually exported. It's now
     *  confirmed right here, before the existing Share/Download prompt.
     *  The choice still persists via SettingsPrefs (pre-selected next time)
     *  so this isn't a fresh decision every single export, just a visible
     *  one — and Backup/Restore's stored paperSize field keeps working
     *  unchanged since it's the same underlying pref. */
    private fun showPaperSizePickerThenGenerate() {
        val sizes = PaperSize.entries.toTypedArray()
        val current = SettingsPrefs.getPaperSizePreference(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Paper size")
            .setSingleChoiceItems(sizes.map { it.label }.toTypedArray(), sizes.indexOf(current)) { d, which ->
                SettingsPrefs.setPaperSizePreference(this, sizes[which])
                d.dismiss()
                generatePdfThenPrompt(sizes[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    private fun generatePdfThenPrompt(paperSize: PaperSize) {
        val template = buildTemplate()
        if (template == null) {
            Toast.makeText(this, "Enter a valid number of questions", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = File(cacheDir, "pdfs").apply { mkdirs() }
        val file = File(dir, "${template.testName}.pdf")

        // v1.1.0: Letter-size downloads don't carry the Parent/Guardian
        // signature line — showParentSignatureLine is simply false
        // whenever paperSize is LETTER, regardless of the setting, so
        // the sheet never claims to include something it can't. This
        // used to also fire a separate Toast explaining why; that's
        // gone now — the same sentence is folded into the "PDF Ready"
        // dialog's own message below instead of a second, easy-to-miss
        // notification.
        val signatureLineWanted = SettingsPrefs.isParentSignatureLineEnabled(this)
        val showParentSignatureLine = signatureLineWanted && paperSize != PaperSize.LETTER
        val letterSignatureLineNote = if (signatureLineWanted && paperSize == PaperSize.LETTER) {
            " Note: the Parent/Guardian signature line isn't included on Letter-size downloads."
        } else {
            ""
        }

        SheetGenerator.generate(
            template, file, paperLayout(),
            paperSize = paperSize,
            showParentSignatureLine = showParentSignatureLine
        )
        // FileExportPrompt's Download path now streams straight from
        // `file` (see its doc comment) — no longer needs a `bytes`
        // ByteArray handed to it, so this doesn't bother reading the PDF
        // back into memory just to throw it away.
        val fileName = "${template.testName}_${paperSize.name}.pdf".replace(Regex("[^A-Za-z0-9_.-]"), "_")

        FileExportPrompt.show(
            activity = this,
            title = "PDF Ready",
            fileName = fileName,
            mimeType = "application/pdf",
            workingFile = file,
            shareTitle = "Share answer sheet PDF",
            message = "$fileName is ready.$letterSignatureLineNote",
            onDownloadComplete = { file.delete() }
        )
    }
}

/** Small helper so text-change listeners can be added as a single trailing lambda. */
private fun EditText.addTextChangedListener(afterChange: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = afterChange()
    })
}
