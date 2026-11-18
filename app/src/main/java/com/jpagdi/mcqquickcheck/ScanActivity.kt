package com.jpagdi.mcqquickcheck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scans a sheet using a live in-app camera preview (CameraX) instead of
 * handing off to the system Camera app. ScanOverlayView shows framing guides
 * that turn green per-corner as AlignmentAnalyzer detects good alignment on
 * live frames — a visual aid only; capture is manual (tap the button).
 * MarkerDetector then does the real, careful validation on the actual
 * captured photo (cropped to the guide region first — see
 * cropToGuideRegion). Results are shown in a panel that covers the preview
 * once a photo has been processed.
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_GRADE = "grade"
        const val MODE_KEY = "key"
        const val EXTRA_SCANNED_KEY = "scanned_key"
        // v1.2.5 (Test sets): optional label shown in the title of a key scan, e.g. "Set Y".
        const val EXTRA_KEY_LABEL = "key_label"
        const val EXTRA_SCAN_FOLDER = "scan_folder"

        /** Extra margin cropped around the guide rectangle, as a fraction of its
         *  own width/height, to tolerate the sheet not being pixel-perfectly
         *  aligned with the guide. */
        private const val CROP_PADDING_FRACTION = 0.08f

        /** Cap on how many times startCamera() will retry waiting for
         *  previewView.viewPort to become non-null before giving up and
         *  proceeding without it (see the retry logic in startCamera()). */
        private const val MAX_VIEWPORT_RETRIES = 5
    }

    private lateinit var previewView: PreviewView
    private lateinit var permissionContainer: LinearLayout
    private lateinit var resultPanel: LinearLayout
    private lateinit var ivPreview: ZoomableImageView

    /** Whatever bitmap is currently shown in [ivPreview] — kept separately
     *  because double-tap now opens a fullscreen viewer (FullscreenImageViewer)
     *  that needs the full-resolution bitmap directly, not whatever Android
     *  handed back from the ImageView's drawable. */
    private var currentResultBitmap: Bitmap? = null

    /** Storage key of the current result's autosaved copy, if autosave is on
     *  and a save actually happened — null otherwise. Used by the delete
     *  icon to know what to remove. Reset each time a new scan starts. */
    private var currentAutosaveKey: String? = null

    // v3.3 — ambiguous-answer resolution (live session):
    // currentProcessedBitmap is the warped scan BEFORE ResultAnnotator draws
    // anything on it, kept so a resolved question can be re-annotated from a
    // clean slate rather than drawing another mark on top of the old one.
    // currentScanResults is BubbleGrader's raw (unresolved) output for the
    // current scan. currentAmbiguousAssignments maps 1-based question number
    // -> the choice index the teacher manually picked for it; only entries
    // for questions that were actually ambiguous ever get added. All three
    // are reset at the start of every new capture (see processPhoto).
    private var currentProcessedBitmap: Bitmap? = null
    private var currentScanResults: List<BubbleGrader.QuestionResult> = emptyList()
    private val currentAmbiguousAssignments = mutableMapOf<Int, Int>()

    // v1.2.5 (Test sets): which set the current sheet is graded as (0 = the
    // test's own key). Read from the printed "Set:" bubbles, or picked by the
    // teacher when the row is unclear. Reset by showCameraUi().
    private var currentSetIndex = 0

    /** The answer key to grade with for [set]. Set 0 (and any test without
     *  sets) is the test's own key; sets 1..3 come from SetKeyStore and are
     *  only usable when their length matches the sheet. */
    private fun keyForSet(active: SavedTest, set: Int): List<Int>? =
        if (set <= 0) active.answerKey
        else SetKeyStore.get(this, active.template.testName, set)
            ?.takeIf { it.size == active.template.questionCount }

    private fun activeKey(active: SavedTest): List<Int> = keyForSet(active, currentSetIndex) ?: active.answerKey

    private fun setLabel(active: SavedTest, set: Int): String =
        active.template.setLabels().getOrNull(set)?.toString() ?: "?"

    /** Works out which set this sheet is, then runs [onReady]. A sheet without
     *  sets goes straight through. An unreadable set row (blank, double-marked)
     *  or a set with no key yet asks the teacher instead of guessing. */
    private fun withSetResolved(active: SavedTest, processed: Bitmap, onReady: () -> Unit) {
        val run: () -> Unit = {
            try {
                onReady()
            } catch (e: Exception) {
                tvScoreHeader.text = "Error"
                tvResult.text = "${e.message}"
            }
            Unit
        }
        if (!active.template.hasSets) {
            currentSetIndex = 0
            run()
            return
        }
        val read = BubbleGrader.readSet(processed, active.template).index
        if (read != null && keyForSet(active, read) != null) {
            currentSetIndex = read
            run()
            return
        }
        val reason = if (read != null) "Set ${setLabel(active, read)} has no answer key yet." else "The set bubble isn't clear on this sheet."
        askWhichSet(active, reason, run)
    }

    private fun askWhichSet(active: SavedTest, reason: String, onReady: () -> Unit) {
        val labels = active.template.setLabels()
        val items = labels.indices.map { i ->
            "Set ${labels[i]}" + if (keyForSet(active, i) == null) "  (no key)" else ""
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Which set is this sheet?")
            .setMessage(reason)
            .setCancelable(false)
            .setItems(items) { _, which ->
                if (keyForSet(active, which) == null) {
                    askWhichSet(active, "Set ${labels[which]} has no answer key yet. Add it from the test's template screen, or pick another set.", onReady)
                } else {
                    currentSetIndex = which
                    onReady()
                }
            }
            .setNegativeButton("Discard scan") { _, _ -> showCameraUi() }
            .show()
    }
    private lateinit var llAmbiguousPanel: LinearLayout

    // v3.4: populated by refreshGradeAndAnnotation(active, suppressAutosave
    // = true) on a REJECTED_UNCLEAR result, kept in sync every time the
    // teacher resolves an ambiguous bubble. Consumed by btnSaveUnclear's
    // click handler; cleared whenever the review prompt is dismissed
    // (Delete, Save, or a fresh capture starts) so stale data can never be
    // saved against a different scan.
    private var pendingUnclearSummary: AnswerParser.GradeSummary? = null
    private var pendingUnclearRemainingAmbiguous: Set<Int>? = null

    /** v3.4: the LowConfidenceException's message, stashed once when the
     *  unclear-review flow starts, so refreshGradeAndAnnotation can
     *  re-prefix it onto tvScoreHeader/tvResult every time it's called —
     *  including the SECOND, THIRD, etc. call that happens each time the
     *  teacher resolves one more ambiguous bubble, not just the first. */
    private var pendingUnclearMessage: String? = null

    // v1.2.0 (Classes feature) — learner-link state for the CURRENT scan.
    // linkAttempted: ID has been read/matched for this scan (done once, at
    // the first grade, never re-read when a bubble is resolved).
    // linkedId/linkedClassId: set when the score was recorded on a learner.
    // pendingLinkId: set when the scan was filed under "Needs ID check".
    // linkStatusLine: one-line status appended under the score. All reset
    // by showCameraUi() for the next scan.
    private var linkAttempted = false
    private var linkedId: String? = null
    private var linkedClassId: String? = null
    private var pendingLinkId: String? = null
    private var linkStatusLine: String? = null

    private lateinit var unclearPromptRow: LinearLayout
    private lateinit var normalButtonRow: LinearLayout
    private lateinit var btnDeleteUnclear: View
    private lateinit var btnSaveUnclear: View

    private lateinit var btnDeleteScannedSheet: android.widget.ImageView
    private lateinit var tvResult: TextView
    private lateinit var tvScoreHeader: TextView
    private lateinit var btnCapture: android.view.View
    private lateinit var tvScanTitle: TextView
    private lateinit var tvOverlayHint: TextView
    private lateinit var scanOverlay: ScanOverlayView

    /** v3.2.1: tap-to-capture on the live camera preview was changed from a
     *  single tap to a double tap, since a single tap was catching accidental
     *  touches while just holding the phone up to frame the sheet. The
     *  dedicated capture button and volume-key capture are untouched — this
     *  only affects the tap-on-preview path. Built lazily (not in onCreate)
     *  since `this` as the Context isn't needed until first touch anyway. */
    private val previewDoubleTapDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (btnCapture.visibility == View.VISIBLE) {
                    hapticFeedback()
                    capturePhoto()
                }
                return true
            }
        })
    }

    private var mode: String = MODE_GRADE
    private var lastScannedKeyText: String? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var alignmentAnalyzer: AlignmentAnalyzer

    // Flat-device override for the live corner guide. AlignmentAnalyzer's
    // per-frame CV confidence check has repeatedly proven unreliable at
    // lighting up the guide squares even when the phone genuinely is
    // aligned over the sheet. Rather than keep tuning that CV path, we
    // trust the device's own tilt sensor instead: when it reports the
    // phone is flat (camera facing straight down at the sheet, screen
    // facing up — the natural way this app is held while scanning), we
    // force all 4 guide squares green regardless of what that frame's CV
    // check produced. When NOT flat, the CV result still passes through
    // unchanged — this is additive, not a replacement of checkAlignment().
    private lateinit var sensorManager: SensorManager
    private var flatDetectorSensor: Sensor? = null
    @Volatile private var deviceIsFlat = false

    // Tolerance for "flat": the sensor's Z-axis component must dominate
    // the total gravity vector by at least this fraction. 0.99 ≈ within
    // about 8° of level. This is a starting value — it can't be tuned
    // properly without an actual device in hand, so treat it as the first
    // thing to adjust if the guide triggers too eagerly or too reluctantly.
    private val FLAT_DOT_THRESHOLD = 0.99f

    private val flatDetectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val norm = kotlin.math.sqrt(x * x + y * y + z * z)
            deviceIsFlat = norm > 0f && (kotlin.math.abs(z) / norm) >= FLAT_DOT_THRESHOLD
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var isCapturing = false
    // v3.2: set right before takePicture() fires, read in logScanDiagnostics()
    // to compute tap-to-result latency for the diagnostic report - added
    // after a co-teacher's device showed 3-5s captures with no real data
    // to confirm whether fastCaptureMode actually helps.
    private var captureStartTimeMs = 0L
    private var viewPortRetryCount = 0

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionContainer.visibility = View.GONE
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan sheets", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        // v2.8 fold-in fix: this screen's views (btnCapture tagged
        // accent_gold, btnUseAsKey tagged accent_maroon — v3.7.3: now
        // accent_gold, see the layout's own comment) were tagged back
        // when the accent system was first built, but nothing ever
        // actually called applyAccents() here — so the tags sat inert and
        // this screen kept showing the original hardcoded maroon status
        // bar / gold capture button regardless of the user's chosen
        // colors. This one call handles both the tagged-view retinting
        // AND the status bar (Group A), same as every other screen.
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // TYPE_GRAVITY is pre-filtered (isolates gravity from linear
        // acceleration) and is what we want; TYPE_ACCELEROMETER is a
        // fallback for the rare device that doesn't expose it.
        flatDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_GRADE
        Thread { OpenCvSupport.ensureInitialized() }.start()

        previewView = findViewById(R.id.previewView)
        scanOverlay = findViewById(R.id.scanOverlay)
        permissionContainer = findViewById(R.id.permissionContainer)
        resultPanel = findViewById(R.id.resultPanel)
        ivPreview = findViewById(R.id.ivPreview)
        ivPreview.onRequestFullscreen = {
            currentResultBitmap?.let { FullscreenImageViewer.show(this, it) }
        }
        tvResult = findViewById(R.id.tvResult)
        tvScoreHeader = findViewById(R.id.tvScoreHeader)
        btnCapture = findViewById(R.id.btnCapture)
        tvScanTitle = findViewById(R.id.tvScanTitle)
        tvOverlayHint = findViewById(R.id.tvOverlayHint)
        btnDeleteScannedSheet = findViewById(R.id.btnDeleteScannedSheet)
        llAmbiguousPanel = findViewById(R.id.llAmbiguousPanel)
        btnDeleteScannedSheet.setOnClickListener {
            HapticUtils.confirm(it)
            val key = currentAutosaveKey
            if (key != null) {
                ScannedSheetStorage.deleteScan(this, key)
                currentAutosaveKey = null
                Toast.makeText(this, "Scanned sheet deleted", Toast.LENGTH_SHORT).show()
            }
            showCameraUi()
        }

        // v3.4: Delete Sheet / Save & Proceed prompt for an unclear scan
        // that still has a real (if uncertain) reading — see
        // showUnclearReviewPrompt(). Delete just discards and returns to
        // the camera. Save reuses the SAME grading/annotation state
        // refreshGradeAndAnnotation(suppressAutosave=true) already built
        // (including any bubbles the teacher resolved via llAmbiguousPanel
        // first) and persists it via ScannedSheetStorage directly — NOT
        // autosaveResultIfEnabled, since tapping this button is an
        // explicit save request regardless of the Autosave setting.
        unclearPromptRow = findViewById(R.id.unclearPromptRow)
        normalButtonRow = findViewById(R.id.normalButtonRow)
        btnDeleteUnclear = findViewById(R.id.btnDeleteUnclear)
        btnSaveUnclear = findViewById(R.id.btnSaveUnclear)
        btnDeleteUnclear.setOnClickListener {
            HapticUtils.confirm(it)
            pendingUnclearSummary = null
            pendingUnclearRemainingAmbiguous = null
            pendingUnclearMessage = null
            showCameraUi()
        }
        btnSaveUnclear.setOnClickListener {
            HapticUtils.confirm(it)
            val bitmap = currentResultBitmap
            val summary = pendingUnclearSummary
            val testName = ActiveTest.current?.template?.testName
            if (bitmap != null && summary != null && testName != null) {
                val folder = intent.getStringExtra(EXTRA_SCAN_FOLDER) ?: ScannedSheetStorage.MAIN_FOLDER
                val savedId = ScannedSheetStorage.saveScan(
                    this, testName, bitmap, folder,
                    summary.earnedPoints, summary.totalPoints, summary.perQuestion,
                    pendingUnclearRemainingAmbiguous, currentSetIndex
                )
                // v1.2.0: the teacher explicitly kept this unclear scan, so
                // this is when its learner score gets recorded.
                ActiveTest.current?.let { active ->
                    val sheet = currentProcessedBitmap
                    if (sheet != null && ClassLinker.appliesTo(this, active)) {
                        currentAutosaveKey = savedId
                        linkOrUpdateLearner(active, summary.earnedPoints, summary.totalPoints, sheet)
                    }
                }
                Toast.makeText(this, "Saved. You can review or delete it later from Scanned Sheets.", Toast.LENGTH_SHORT).show()
            }
            pendingUnclearSummary = null
            pendingUnclearRemainingAmbiguous = null
            pendingUnclearMessage = null
            showCameraUi()
        }

        if (ActiveTest.current == null) {
            Toast.makeText(this, "No test loaded — set up a test on the home screen first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Guards against a stale/mismatched save: if the answer key's
        // length doesn't match what the sheet template itself declares as
        // its question count, grading would silently use the KEY's length
        // as the denominator (see AnswerParser.grade — totalPoints sums
        // points.take(key.size)), which can produce a score like "X / 40"
        // on a sheet whose title and printed grid both say "30 Items." This
        // mismatch was possible to save before the answer-key/template
        // consistency was enforced at save time; this is a grade-time
        // safety net so an already-mismatched saved test gets flagged
        // instead of silently producing a misleading denominator.
        // MODE_GRADE only — this check exists to stop GRADING against a
        // stale/wrong-length key (see the comment above). MODE_KEY's whole
        // purpose is to BUILD a key that's currently empty or wrong-length
        // — "0 items vs 30 items" is that mode's normal starting state, not
        // an error, so it must skip this check entirely rather than get
        // blocked by it before the camera even initializes.
        if (mode == MODE_GRADE) {
            ActiveTest.current?.let { active ->
                val declaredCount = active.template.questionCount
                val keyCount = active.answerKey.size
                if (declaredCount != keyCount) {
                    Toast.makeText(
                        this,
                        "\"${active.template.testName}\" has a mismatched answer key ($keyCount items) vs. its sheet ($declaredCount items). Please re-open this test from the Saved Tests home screen and rebuild the answer key before scanning.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return
                }
            }
        }

        scanOverlay.paperLayout = ActiveTest.current?.paperLayout ?: PaperLayout.QUARTER

        tvScanTitle.text = if (mode == MODE_KEY) {
            "Scan Answer Key" + (intent.getStringExtra(EXTRA_KEY_LABEL)?.let { " — $it" } ?: "")
        } else "Scan Filled Sheet"

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnCapture.setOnClickListener {
            hapticFeedback()
            capturePhoto()
        }
        // v3.2: tap-anywhere-on-preview-to-capture, in addition to the
        // dedicated button. v3.2.1: changed from single tap to double tap
        // (see previewDoubleTapDetector above) — a single tap was firing on
        // accidental touches while just holding the phone up. Still gated on
        // btnCapture's own visibility (the existing signal for "camera UI is
        // the active state, not the result/review UI"), and capturePhoto()'s
        // own isCapturing guard still protects against races with the button
        // or volume-key capture.
        previewView.setOnTouchListener { _, event ->
            previewDoubleTapDetector.onTouchEvent(event)
            true
        }
        // v3.7.3: btnGrantPermission is now a LinearLayout card (rounded-
        // card icon+text pattern, see activity_scan.xml), not a Button —
        // findViewById<View> instead of <Button>, since only
        // setOnClickListener (a View method) is used here.
        findViewById<View>(R.id.btnGrantPermission).setOnClickListener {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        // v3.7.3: btnScanNext/btnDone are now LinearLayout cards (rounded-
        // card icon+text pattern), not Buttons — findViewById<View>
        // instead of <Button>, since only setOnClickListener is used.
        findViewById<View>(R.id.btnScanNext).setOnClickListener { showCameraUi() }
        findViewById<View>(R.id.btnDone).setOnClickListener { finish() }

        // v3.7.3: btnUseAsKey is now a LinearLayout card (rounded-card
        // icon+text pattern, gold accent), not a Button — findViewById<View>
        // instead of <Button>, since only setOnClickListener/visibility
        // (View members) are used on it.
        val btnUseAsKey = findViewById<View>(R.id.btnUseAsKey)
        // v2.9 FIX: this used to force textColor based on the overall
        // app THEME (black on Light, white on Dark/Black) — but this
        // button is tagged accent_maroon, so AccentColorPrefs.applyAccents()
        // (called above, earlier in this same onCreate) already tints its
        // background to the user's chosen accent color AND sets a
        // correctly-contrasting text color via its own luminance-aware
        // textColorFor(groupA) — that's the right fix, since text needs
        // to contrast the BUTTON's background color, not the app theme's
        // background. On Light theme with a dark accent color (e.g. the
        // default maroon), those two logics disagreed: applyAccents chose
        // white (correct, readable), then this block ran after and forced
        // it back to near-black (wrong — that's the actual bug reported:
        // black text on a maroon button). Deleting this block instead of
        // patching it, since applyAccents already does this correctly and
        // per-view special-casing was the thing fighting it.
        if (mode == MODE_KEY) {
            btnUseAsKey.setOnClickListener {
                val key = lastScannedKeyText
                if (key == null) {
                    Toast.makeText(this, "Capture a photo of the answer key first", Toast.LENGTH_SHORT).show()
                } else {
                    val resultIntent = Intent().putExtra(EXTRA_SCANNED_KEY, key)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        } else {
            btnUseAsKey.visibility = View.GONE
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionContainer.visibility = View.VISIBLE
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Wait for the PreviewView to actually be laid out before reading
            // its viewPort/size — both are derived from the view's real
            // on-screen dimensions and are unreliable until layout has
            // happened at least once.
            previewView.post {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // v3.4: was a Settings toggle (SettingsPrefs.
                // isFastCaptureModeEnabled) between this and
                // CAPTURE_MODE_MAXIMIZE_QUALITY, opt-in because
                // MINIMIZE_LATENCY hadn't been validated against real scan
                // data yet as of v3.2 (MarkerDetector was proven working
                // against MAXIMIZE_QUALITY's output specifically). Now
                // fixed at MINIMIZE_LATENCY per explicit request, after
                // confirming real sheets scan and grade correctly with it
                // on — not just that capture felt equally fast either way.
                // MAXIMIZE_QUALITY kept below (commented, not deleted) in
                // case there's ever a reason to switch back.
                val captureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                // val captureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                val capture = ImageCapture.Builder()
                    .setCaptureMode(captureMode)
                    .build()
                imageCapture = capture

                alignmentAnalyzer = AlignmentAnalyzer(
                    viewWidth = previewView.width,
                    viewHeight = previewView.height,
                    paperLayout = ActiveTest.current?.paperLayout ?: PaperLayout.QUARTER
                ) { corners -> onAlignmentResult(corners) }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                analysis.setAnalyzer(cameraExecutor, alignmentAnalyzer)

                val viewPort = previewView.viewPort
                if (viewPort == null) {
                    // ViewPort isn't ready yet — this can happen if the
                    // view hasn't fully settled through layout by the time
                    // this post{} callback runs (more than one layout pass
                    // queued, e.g. during activity transition/window inset
                    // settling). Retry on the next layout pass rather than
                    // silently proceeding without it: per the comment
                    // below, binding without a ViewPort means ImageCapture
                    // falls back to the camera's full uncropped sensor
                    // frame while the preview/guide shows a cropped view —
                    // the two stop matching, and a captured photo can end
                    // up looking like a completely different crop than
                    // what the guide box showed. This was the most likely
                    // cause of HALF_LENGTH's narrow guide box producing a
                    // garbled/mismatched capture, since a viewport mismatch
                    // is most visually obvious on layouts with an extreme
                    // aspect ratio.
                    //
                    // Capped at a handful of retries so a device where
                    // viewPort somehow never becomes available (unverified
                    // whether this can actually happen) can't spin forever
                    // — falls back to the old proceed-anyway behavior
                    // rather than leaving the scan screen stuck.
                    if (viewPortRetryCount < MAX_VIEWPORT_RETRIES) {
                        viewPortRetryCount++
                        previewView.post { startCamera() }
                        return@post
                    }
                }
                viewPortRetryCount = 0

                // THE FIX for capture matching preview: without a shared
                // ViewPort, ImageCapture defaults to the camera's full
                // uncropped sensor frame, while PreviewView (scaleType
                // FILL_CENTER) shows a cropped/zoomed-in view. Binding all
                // three use cases through the same ViewPort makes capture (and
                // the analyzer) WYSIWYG with the preview.
                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .addUseCase(analysis)
                if (viewPort != null) {
                    useCaseGroupBuilder.setViewPort(viewPort)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroupBuilder.build()
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not start camera: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Called (on a background thread) with each corner's live alignment result.
     *  NOTE: auto-capture was removed after real-device testing showed the live
     *  check firing on non-sheet objects (a mug, an empty desk) even after a
     *  first attempt to fix it — tuning a live heuristic blind, without being
     *  able to test on a real camera, wasn't working. The green/gold squares
     *  are now a visual aid only; capture is manual (tap the button), which
     *  was already working reliably before auto-capture was added. */
    private fun onAlignmentResult(corners: BooleanArray) {
        runOnUiThread {
            if (resultPanel.visibility == View.VISIBLE) return@runOnUiThread
            // Flat-device override: when the tilt sensor says the phone is
            // level, trust that over this frame's CV result and light up
            // all 4 guides. When not flat, the CV result passes through
            // unchanged — this only ever adds true, never removes it.
            scanOverlay.cornersAligned = if (deviceIsFlat) BooleanArray(4) { true } else corners
        }
    }

    private fun hapticFeedback() {
        previewView.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        if (isCapturing) return

        isCapturing = true
        captureStartTimeMs = System.currentTimeMillis()

        val dir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        btnCapture.isEnabled = false
        btnCapture.alpha = 0.5f
        // v3.2: plays the app's own shutter sound (res/raw/camera_shutter.mp3)
        // rather than the system MediaActionSound used in an earlier pass —
        // a fresh MediaPlayer per capture, released via setOnCompletionListener
        // once playback finishes, since holding one player across multiple
        // captures isn't worth the complexity for a ~1s one-shot sound.
        if (SettingsPrefs.isCameraSoundEnabled(this)) {
            val player = android.media.MediaPlayer.create(this, R.raw.camera_shutter)
            player?.setOnCompletionListener { it.release() }
            player?.start()
        }
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        btnCapture.isEnabled = true
                        btnCapture.alpha = 1f
                        isCapturing = false
                        processPhoto(file)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        btnCapture.isEnabled = true
                        btnCapture.alpha = 1f
                        isCapturing = false
                        Toast.makeText(this@ScanActivity, "Capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // v3.2: volume up/down as a physical capture button, alongside the
    // existing tap-anywhere-on-preview and dedicated btnCapture. Same
    // "camera UI is the active state" gate as previewView's touch
    // listener above, and capturePhoto()'s own isCapturing guard still
    // protects against this racing either of the other two triggers.
    // v3.2.1: extended so the SAME buttons also advance to the next scan
    // once the result/review panel is showing — mirrors btnScanNext
    // (showCameraUi()), gated on resultPanel's visibility exactly like
    // btnDone/btnScanNext already are. This means the full flow (capture,
    // review, next) can be driven by volume keys alone, matching the
    // feature request: "volume button to capture, then in the preview,
    // another volume button to scan next."
    // Returning true consumes the event so the system volume-level
    // overlay doesn't also pop up over the camera preview or result panel.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (btnCapture.visibility == View.VISIBLE) {
                hapticFeedback()
                capturePhoto()
                return true
            }
            if (resultPanel.visibility == View.VISIBLE) {
                hapticFeedback()
                showCameraUi()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showCameraUi() {
        resultPanel.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        scanOverlay.visibility = View.VISIBLE
        tvScanTitle.visibility = View.VISIBLE
        tvOverlayHint.visibility = View.VISIBLE
        btnCapture.visibility = View.VISIBLE

        // Reset the visual alignment guide for the next sheet.
        scanOverlay.cornersAligned = BooleanArray(4)

        // Fresh scan starting — whatever was autosaved (or not) for the
        // previous result no longer applies.
        currentAutosaveKey = null
        resetLearnerLinkState()
        btnDeleteScannedSheet.visibility = View.GONE
        currentProcessedBitmap = null
        currentScanResults = emptyList()
        currentSetIndex = 0
        currentAmbiguousAssignments.clear()
        llAmbiguousPanel.visibility = View.GONE

        // v3.4: also clears any pending unclear-review save state — e.g. if
        // the volume key triggers showCameraUi() (see onKeyDown) while the
        // unclear-review prompt is showing, that's equivalent to tapping
        // Delete Sheet, not a silent bypass that leaves stale data around
        // for a future scan to accidentally save.
        pendingUnclearSummary = null
        pendingUnclearRemainingAmbiguous = null
        pendingUnclearMessage = null
        unclearPromptRow.visibility = View.GONE
        normalButtonRow.visibility = View.VISIBLE
    }

    private fun showResultUi() {
        previewView.visibility = View.GONE
        scanOverlay.visibility = View.GONE
        tvScanTitle.visibility = View.GONE
        tvOverlayHint.visibility = View.GONE
        btnCapture.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
        // Trash icon only makes sense once autosave is on AND this result
        // has actually been through autosaveCurrentScanIfEnabled() at least
        // once — shown/hidden precisely by that function, not here; a fresh
        // result starts with nothing saved yet even if the setting is on.
        btnDeleteScannedSheet.visibility = View.GONE

        // v3.4: default to the normal button row every time a new result
        // starts processing — showUnclearReviewPrompt() switches to the
        // unclear-prompt row explicitly, only for REJECTED_UNCLEAR grading-
        // mode results.
        unclearPromptRow.visibility = View.GONE
        normalButtonRow.visibility = View.VISIBLE
    }

    /** v3.4: switches the bottom button row from the normal Scan Next/Done
     *  pair to the Delete Sheet/Save & Proceed pair. Called only from the
     *  LowConfidenceException catch block in processPhoto(), grading mode
     *  only — an ACCEPTED result never reaches this, it keeps using
     *  refreshGradeAndAnnotation's existing autosave-if-setting-on
     *  behavior unchanged. The actual grading/annotation/ambiguous-panel
     *  state was already built by refreshGradeAndAnnotation(active,
     *  suppressAutosave = true) before this is called — this function only
     *  toggles which button row is visible. */
    private fun showUnclearReviewPrompt() {
        normalButtonRow.visibility = View.GONE
        unclearPromptRow.visibility = View.VISIBLE
    }

    private fun resetLearnerLinkState() {
        linkAttempted = false
        linkedId = null
        linkedClassId = null
        pendingLinkId = null
        linkStatusLine = null
    }

    /** v1.2.0 (Classes feature): the one entry point from the scan flow into
     *  ClassLinker. First call for a scan reads the ID and records/files the
     *  score; later calls (score changed) just keep that record in sync.
     *  Silent on success — a confident match only appends a small status line;
     *  the one exception is a rescan of an already-scored learner, which asks.
     *  Never throws into the scan flow: a failure here must not cost the
     *  teacher the graded result they just got. */
    private fun linkOrUpdateLearner(active: SavedTest, earned: Int, total: Int, sheet: Bitmap) {
        if (!ClassLinker.appliesTo(this, active)) return
        try {
            if (linkAttempted) {
                ClassLinker.updateScore(
                    this, active.template.testName, linkedId, pendingLinkId, linkedClassId,
                    earned, total, currentAutosaveKey
                )
                return
            }
            linkAttempted = true
            when (val outcome = ClassLinker.link(this, active, sheet, earned, total, currentAutosaveKey)) {
                is ClassLinker.Outcome.Skipped -> Unit
                is ClassLinker.Outcome.Linked -> {
                    linkedId = outcome.learner.learnerId
                    linkedClassId = ClassStorage.findLearner(
                        ClassStorage.getClasses(this, active.classIds), outcome.learner.learnerId
                    )?.first?.id
                    setLinkStatus("✓ Linked: ${outcome.learner.name} (${outcome.className})")
                }
                is ClassLinker.Outcome.NeedsCheck -> {
                    pendingLinkId = outcome.pendingId
                    setLinkStatus("⚠ ${outcome.reason} — pick the learner later from the class sheet")
                }
                is ClassLinker.Outcome.Duplicate -> askReplaceExistingScore(outcome)
            }
        } catch (e: Exception) {
            // Swallowed on purpose; see doc comment above.
        }
    }

    private fun setLinkStatus(line: String) {
        linkStatusLine = line
        tvResult.text = "${tvResult.text}\n\n$line"
    }

    private fun askReplaceExistingScore(d: ClassLinker.Outcome.Duplicate) {
        val old = d.existing
        val new = d.pendingResult
        val dialog = AlertDialog.Builder(this)
            .setTitle("${d.learner.name} already has a score")
            .setMessage(
                "This exam already has ${old.earnedPoints} / ${old.totalPoints} recorded for ${d.learner.name}. " +
                "Replace it with this scan's ${new.earnedPoints} / ${new.totalPoints}?"
            )
            .setNegativeButton("KEEP OLD") { _, _ ->
                setLinkStatus("Kept the earlier score for ${d.learner.name}")
            }
            .setPositiveButton("REPLACE") { _, _ ->
                ClassLinker.replaceExisting(this, d)
                linkedId = d.learner.learnerId
                linkedClassId = d.classId
                setLinkStatus("✓ Linked: ${d.learner.name} (${d.className}) — score replaced")
            }
            .setCancelable(false)
            .create()
        dialog.show()
        DialogColors.fix(this, dialog)
    }

    /** Autosaves the given result bitmap immediately, if the Settings
     *  toggle is on, right as a graded result becomes the current display —
     *  not deferred to Scan Next/Done. That's what lets the trash icon
     *  actually delete something real when tapped ("delete and proceed to
     *  scan next" per the feature request only makes sense if a save has
     *  already happened by the time that icon is visible). */
    private fun autosaveResultIfEnabled(
        bitmap: Bitmap, earnedPoints: Int? = null, totalPoints: Int? = null,
        perQuestion: List<Boolean?>? = null, ambiguousQuestions: Set<Int>? = null
    ) {
        if (!SettingsPrefs.isAutosaveScannedSheetEnabled(this)) return
        val testName = ActiveTest.current?.template?.testName ?: return
        val folder = intent.getStringExtra(EXTRA_SCAN_FOLDER) ?: ScannedSheetStorage.MAIN_FOLDER
        val id = ScannedSheetStorage.saveScan(this, testName, bitmap, folder, earnedPoints, totalPoints, perQuestion, ambiguousQuestions, currentSetIndex)
        currentAutosaveKey = id
        if (id != null) {
            btnDeleteScannedSheet.visibility = View.VISIBLE
        }
    }

    /** v3.3: rebuilds the graded/annotated result from currentScanResults +
     *  currentAmbiguousAssignments — called once right after grading, and
     *  again every time the teacher taps a bubble in the ambiguous-answer
     *  panel to resolve a question. Assigned questions are graded normally
     *  against the answer key (no special credit), same as any other mark.
     *
     *  @param suppressAutosave v3.4: true for the REJECTED_UNCLEAR review
     *  path (see showUnclearReviewPrompt() / the LowConfidenceException
     *  catch block) — that path shows this exact same grading/annotation/
     *  ambiguous-resolve UI, but must NOT silently autosave before the
     *  teacher explicitly chooses Delete Sheet or Save & Proceed. Default
     *  false preserves the original ACCEPTED-path behavior exactly (every
     *  existing call site is unaffected). When true, resolving an
     *  ambiguous bubble on an unclear scan still re-grades/re-annotates
     *  live, it just doesn't touch ScannedSheetStorage until Save &
     *  Proceed is tapped. */
    private fun refreshGradeAndAnnotation(active: SavedTest, suppressAutosave: Boolean = false) {
        val processed = currentProcessedBitmap ?: return
        val effectiveResults = currentScanResults.map { r ->
            currentAmbiguousAssignments[r.questionNumber]?.let { assigned ->
                r.copy(chosenIndex = assigned, isAmbiguous = false)
            } ?: r
        }
        val summary = AnswerParser.grade(activeKey(active), effectiveResults, active.points)
        val percent = if (summary.totalPoints > 0) (summary.earnedPoints * 1000 / summary.totalPoints) / 10.0 else 0.0
        tvScoreHeader.text = "${summary.earnedPoints} / ${summary.totalPoints} = $percent%"

        // ZipGrade-style feedback: color-coded rings drawn directly on the
        // rectified sheet image (green = correct answer's bubble, red = a
        // wrong mark, orange = ambiguous) — this replaces a separate
        // itemized text list, since the colors on the sheet itself already
        // show what's right/wrong/unclear per question. Always redrawn from
        // the pristine pre-annotation `processed` bitmap (not the previous
        // annotated one) so resolving a question never layers a second mark
        // on top of an old one.
        val annotated = ResultAnnotator.annotate(
            processed, active.template, effectiveResults, activeKey(active),
            summary.earnedPoints, summary.totalPoints
        )
        ivPreview.setImageBitmap(annotated)
        currentResultBitmap = annotated
        tvResult.text = "🟢 Correct answer     🔴 Wrong mark\n🟧— Ambiguous — please double-check"
        if (active.template.hasSets) tvResult.text = "Graded as Set ${setLabel(active, currentSetIndex)}\n${tvResult.text}"
        linkStatusLine?.let { tvResult.text = "${tvResult.text}\n\n$it" }

        // v3.4: re-apply the "this scan was flagged unclear" warning EVERY
        // time this function runs while suppressAutosave is true — not
        // just the first call. Resolving an ambiguous bubble calls this
        // function again (see the AmbiguousAnswerPanel callback below),
        // which would otherwise silently overwrite tvScoreHeader/tvResult
        // back to plain accepted-scan wording after the teacher's first
        // resolve, making the sheet look confidently accepted again even
        // though it never actually cleared requireConfident()'s gate.
        if (suppressAutosave) {
            val msg = pendingUnclearMessage
            if (msg != null) {
                tvScoreHeader.text = "⚠ Unclear (tentative) — ${tvScoreHeader.text}"
                tvResult.text = "⚠ $msg\n\n${tvResult.text}"
            }
        }

        val remainingAmbiguous = currentScanResults
            .filter { it.isAmbiguous && !currentAmbiguousAssignments.containsKey(it.questionNumber) }
            .map { it.questionNumber }
            .toSet()

        if (suppressAutosave) {
            // v3.4: stash what Save & Proceed should persist if/when the
            // teacher taps it, instead of saving now. Kept up to date on
            // every resolve so tapping Save & Proceed after resolving a
            // few bubbles saves the RESOLVED state, not the original one.
            pendingUnclearSummary = summary
            pendingUnclearRemainingAmbiguous = remainingAmbiguous
        } else {
            // Autosave: the FIRST call (right after grading) creates a new
            // entry; every call after that (resolving a question) overwrites
            // that SAME entry instead, so resolving never leaves a duplicate
            // copy of the scan behind.
            val existingId = currentAutosaveKey
            if (existingId == null) {
                autosaveResultIfEnabled(annotated, summary.earnedPoints, summary.totalPoints, summary.perQuestion, remainingAmbiguous)
            } else {
                val updated = ScannedSheetStorage.updateScanAfterResolution(
                    this, existingId, annotated, summary.perQuestion, remainingAmbiguous,
                    summary.earnedPoints, summary.totalPoints
                )
                if (!updated) {
                    // Entry vanished (e.g. deleted from the folder in the
                    // meantime) — fall back to a fresh save rather than losing
                    // the resolved answer silently.
                    autosaveResultIfEnabled(annotated, summary.earnedPoints, summary.totalPoints, summary.perQuestion, remainingAmbiguous)
                }
            }
        }

        // v1.2.0: link this score to a learner (first call) or keep an
        // already-linked score in sync (after an ambiguous bubble is
        // resolved). Not run for the unclear-review path — that one links
        // when the teacher actually saves (btnSaveUnclear).
        if (!suppressAutosave) {
            linkOrUpdateLearner(active, summary.earnedPoints, summary.totalPoints, processed)
        }

        val allAmbiguousQuestionNumbers = currentScanResults.filter { it.isAmbiguous }.map { it.questionNumber }
        AmbiguousAnswerPanel.populate(
            this, llAmbiguousPanel, allAmbiguousQuestionNumbers, active.template.choiceLetters,
            currentAmbiguousAssignments, AccentColorPrefs.getGroupB(this), currentTextColor()
        ) { qNum, choiceIndex ->
            currentAmbiguousAssignments[qNum] = choiceIndex
            refreshGradeAndAnnotation(active, suppressAutosave)
        }
    }

    /** Same pattern AnswerKeyActivity uses for its own dynamically-built
     *  bubble rows — the theme's own default text color, correct against
     *  that theme's background by design. */
    private fun currentTextColor(): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        return color
    }

    private fun processPhoto(file: File) {
        showResultUi()
        tvScoreHeader.text = "Processing…"
        tvResult.text = ""

        // Hoisted above the try block (v2.4) so the catch blocks below can
        // still log which test/layout was being scanned even when
        // detection or grading fails partway through — previously `active`
        // was declared inside the try, invisible to every catch clause.
        // Diagnostics-only change; the return-on-null behavior for a
        // missing active test is unchanged.
        val active = ActiveTest.current ?: run { file.delete(); return }

        // Hoisted alongside `active` (v2.4 diagnostics) — grade() has
        // already run and produced real per-question data by the time
        // requireConfident() throws LowConfidenceException, but that
        // `scanResults` was declared inside the try block and invisible to
        // its own catch clause. Without this, a REJECTED_UNCLEAR log entry
        // — the case this whole logging pass exists for — would only ever
        // record the summary message, never the itemized breakdown of
        // which questions tripped which flag. Stays null for failures
        // that happen before grade() runs (e.g. markers not found).
        var scanResultsForDiagnostics: List<BubbleGrader.QuestionResult>? = null

        // v3.4: same hoisting pattern as scanResultsForDiagnostics right
        // above, for the same reason — `processed` (the dewarped, CLAHE-
        // contrast-enhanced bitmap grade() actually judged) is what the
        // REJECTED_UNCLEAR review flow needs to set currentProcessedBitmap
        // to before it can call refreshGradeAndAnnotation(). Without this,
        // the LowConfidenceException catch block would have no bitmap to
        // build a review/save from.
        var processedBitmapForDiagnostics: Bitmap? = null

        // v3.0 FIX: every capturePhoto() call writes a new full-resolution
        // JPEG into cacheDir/images/ — that part was always intentional
        // (CameraX needs a real file target). What was missing is this
        // finally block: nothing ever deleted that file afterward, on ANY
        // outcome — accepted, rejected as unclear, markers-not-found, or a
        // hard error. A teacher doing a dozen+ retakes to get a good angle
        // (exactly what the sample diagnostics log showed — 50+ capture
        // attempts in one session) was accumulating 50+ full-res photos in
        // cache with no cleanup, which is the confirmed source of "saving
        // so much cache." The file is only ever read once, at the very top
        // of the try block below (decodeWithExifCorrection) — everything
        // after that works from in-memory Bitmaps, so it's always safe to
        // delete by the time this function returns, on every exit path.
        try {
            val fullBitmap = decodeWithExifCorrection(file)

            // Crop to a padded version of the same guide rectangle shown live
            // (GuideGeometry — shared with the overlay and AlignmentAnalyzer),
            // so MarkerDetector's corner search only has to look where the
            // sheet actually is, not the whole (often mostly background)
            // photo. Without this, the fixed search window can miss the real
            // markers entirely whenever the sheet doesn't fill the frame edge
            // to edge — which is normal for a handheld photo.
            val bitmap = cropToGuideRegion(fullBitmap)
            ivPreview.setImageBitmap(bitmap) // shown immediately as a fallback; replaced below once graded
            currentResultBitmap = bitmap

            val expectedAspect = SheetTemplate.sheetAspectRatio(active.paperLayout)
            val corners = MarkerDetector.detect(bitmap, expectedAspect)
            val (warpWidth, warpHeight) = warpOutputSize(bitmap, expectedAspect)
            val warped = PerspectiveWarp.warp(bitmap, corners, active.template, warpWidth, warpHeight)
            val processed = ImagePreprocessor.process(warped)
            processedBitmapForDiagnostics = processed
            // Set BEFORE grade()/requireConfident() run, not after — both
            // can throw (LowConfidenceException on an unclear scan is the
            // whole reason this matters), and until this line the only
            // bitmap ever shown was the pre-warp, pre-CLAHE crop set as a
            // fallback above. An unclear-scan review is exactly the case
            // where seeing the SAME image the grader actually judged
            // (dewarped AND contrast-processed) matters most — showing an
            // earlier-pipeline-stage image here would make a mark look
            // different than what the ambiguous/unclear call was based on.
            ivPreview.setImageBitmap(processed)
            currentResultBitmap = processed
            val scanResults = BubbleGrader.grade(processed, active.template)
            scanResultsForDiagnostics = scanResults
            BubbleGrader.requireConfident(scanResults)

            logScanDiagnostics(active, outcome = "ACCEPTED", results = scanResults)

            if (mode == MODE_KEY) {
                showKeyScanResult(scanResults)
            } else {
                currentProcessedBitmap = processed
                currentScanResults = scanResults
                currentAmbiguousAssignments.clear()
                withSetResolved(active, processed) { refreshGradeAndAnnotation(active) }
            }
        } catch (e: MarkerDetector.MarkersNotFoundException) {
            tvScoreHeader.text = "Couldn't find the sheet"
            tvResult.text = "Line the sheet's corner squares up with the guide (they don't need to match exactly) and make sure lighting is even.\n\n(${e.message})"
            // v3.1 (item 8): attaches per-corner Otsu/score data so a future
            // session can diagnose low-light MARKERS_NOT_FOUND failures with
            // real data — see MarkerDetector.lastCornerDiagnostics.
            logScanDiagnostics(
                active, outcome = "MARKERS_NOT_FOUND", rejectionMessage = e.message,
                results = scanResultsForDiagnostics, markerDiagnostics = MarkerDetector.lastCornerDiagnostics
            )
        } catch (e: BubbleGrader.LowConfidenceException) {
            // v3.4 CHANGE — was previously a dead end (text message only,
            // no score, no save path, whole sheet had to be retaken). Real
            // diagnostic evidence (a Huawei MNA-LX9 log, investigated in
            // detail before this change — the perspective-warp/lens-
            // distortion explanation a separate report suspected was
            // checked against real per-question data and ruled out) showed
            // sheets that trip requireConfident()'s ambiguous/unclear-rate
            // gate often still have a real, mostly-correct reading
            // underneath — the gate exists to catch a MISLEADING score, not
            // to imply nothing useful was read. Per explicit request: still
            // show the full graded preview (same tentative score, color-
            // coded annotated image, AND ambiguous-answer resolve panel an
            // ACCEPTED scan gets — resolving a bubble here can turn what
            // triggered the unclear flag into a clean answer before the
            // teacher even decides whether to save) with a Delete Sheet /
            // Save & Proceed choice instead of forcing a retake.
            // requireConfident()'s gate itself is UNCHANGED — this doesn't
            // loosen when it throws, it changes what happens after.
            val results = scanResultsForDiagnostics
            val processed = processedBitmapForDiagnostics
            if (mode == MODE_KEY) {
                // Per explicit request: still save what it DID read and
                // route into the normal "Use This as Answer Key" flow,
                // rather than a dead end — showKeyScanResult() already
                // renders "?" for every unresolved question and reveals
                // btnUseAsKey; that existing button IS the redirect to
                // manual edit (GenerateSheetActivity.scanKeyLauncher
                // receives EXTRA_SCANNED_KEY and fills answerKey there,
                // now via parseKeyTokens — see AnswerParser.kt). The only
                // change here is reaching showKeyScanResult() at all on
                // this outcome — previously only ACCEPTED did.
                if (results != null) {
                    val warning = "⚠ ${e.message}\n\n"
                    showKeyScanResult(results)
                    tvResult.text = warning + tvResult.text
                } else {
                    tvScoreHeader.text = "Unclear scan"
                    tvResult.text = "${e.message}"
                }
            } else if (results != null && processed != null) {
                currentProcessedBitmap = processed
                currentScanResults = results
                currentAmbiguousAssignments.clear()
                pendingUnclearMessage = e.message
                // suppressAutosave=true: builds the exact same grading/
                // annotation/ambiguous-panel state refreshGradeAndAnnotation
                // always has, but does NOT touch ScannedSheetStorage, and
                // prefixes the unclear warning onto tvScoreHeader/tvResult
                // (using pendingUnclearMessage set just above) — see that
                // function's doc comment. Also re-applies on every
                // subsequent call, e.g. after resolving an ambiguous
                // bubble, not just this first call. showUnclearReviewPrompt()
                // below only swaps which button row is visible; Save &
                // Proceed (bound in onCreate) is what actually persists,
                // reading currentResultBitmap + pendingUnclearSummary that
                // this call just populated.
                withSetResolved(active, processed) {
                    refreshGradeAndAnnotation(active, suppressAutosave = true)
                    showUnclearReviewPrompt()
                }
            } else {
                // grade() never ran — genuinely nothing to review or save,
                // same dead-end message as before this feature existed.
                tvScoreHeader.text = "Unclear scan"
                tvResult.text = "${e.message}"
            }
            logScanDiagnostics(active, outcome = "REJECTED_UNCLEAR", rejectionMessage = e.message, results = scanResultsForDiagnostics)
        } catch (e: Exception) {
            tvScoreHeader.text = "Error"
            tvResult.text = "${e.message}"
            logScanDiagnostics(active, outcome = "ERROR", rejectionMessage = e.message, results = scanResultsForDiagnostics)
        } finally {
            file.delete()
        }
    }

    /** Thin wrapper around ScanDiagnostics.logAttempt() — pulls the
     *  layout/column/item-count context from `active` so every call site
     *  above stays a one-liner. Diagnostics-only (v2.4); see
     *  ScanDiagnostics.kt's doc comment. requireConfident() may have
     *  already thrown by the time some callers reach this, so `results`
     *  and BubbleGrader.lastSheetDiagnostics are both nullable/optional
     *  here — a markers-not-found failure never got as far as grade(), so
     *  there's nothing to attach beyond the outcome and message. */
    private fun logScanDiagnostics(
        active: SavedTest,
        outcome: String,
        rejectionMessage: String? = null,
        results: List<BubbleGrader.QuestionResult>? = null,
        markerDiagnostics: List<MarkerDetector.CornerDiagnostic>? = null
    ) {
        // v3.2: tap-to-result latency, for real data on whether
        // fastCaptureMode actually helps rather than tuning blind. 0L
        // guard covers the (should-be-impossible) case this fires without
        // captureStartTimeMs ever having been set, rather than logging a
        // huge bogus elapsed time against epoch 0.
        val captureLatencyMs = if (captureStartTimeMs > 0L) {
            System.currentTimeMillis() - captureStartTimeMs
        } else null

        ScanDiagnostics.logAttempt(
            context = this,
            testName = active.template.testName,
            paperLayout = active.paperLayout,
            columns = active.template.maxBlocksPerRowGroup,
            questionCount = active.template.questionCount,
            outcome = outcome,
            rejectionMessage = rejectionMessage,
            diagnostics = BubbleGrader.lastSheetDiagnostics,
            results = results,
            markerDiagnostics = markerDiagnostics,
            captureLatencyMs = captureLatencyMs
        )
    }

    /**
     * Picks PerspectiveWarp's output dimensions from the tile's real
     * aspect ratio and the captured photo's own resolution, instead of
     * PerspectiveWarp's built-in default (SheetTemplate.PAGE_WIDTH x
     * PAGE_HEIGHT = 612x792 — PDF POINTS, not pixels; a tiny target
     * barely bigger than a postage stamp compared to a real phone photo).
     *
     * v3.4: HALF_LENGTH/HALF_CROSS removed — QUARTER is now the only
     * layout, and its tile aspect always equals a full page's, so the
     * distortion this originally fixed for the other two layouts no
     * longer applies to anything still in the app. Left as-is (aspect-
     * ratio-driven, not layout-branching) since it's correct and harmless
     * for the one layout that remains, and would need no change even if
     * a second layout were reintroduced later.
     *
     * Tradeoff worth knowing: full source-resolution output means a
     * bigger JPEG/base64 string per saved scan (ScannedSheetStorage.kt's
     * own comment already flags SharedPreferences has a soft size limit
     * per entry) — mitigated by ScannedSheetStorage's own downscale-on-
     * save step (v3.4), which shrinks the PERSISTED copy after grading,
     * not this function's output (which grading still needs at full
     * resolution).
     */
    private fun warpOutputSize(source: Bitmap, aspect: Float): Pair<Int, Int> {
        val longEdge = maxOf(source.width, source.height)
        return if (aspect >= 1f) {
            val w = longEdge
            val h = (w / aspect).toInt()
            w to h
        } else {
            val h = longEdge
            val w = (h * aspect).toInt()
            w to h
        }
    }

    /** Crops [bitmap] to GuideGeometry's frame for the current preview size/paper
     *  size, expanded by CROP_PADDING_FRACTION for tolerance. Relies on Preview,
     *  ImageCapture, and ImageAnalysis all sharing one ViewPort, so a fraction of
     *  previewView's own size lines up with the same fraction of the captured photo. */
    private fun cropToGuideRegion(bitmap: Bitmap): Bitmap {
        val vw = previewView.width.toFloat()
        val vh = previewView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return bitmap
        val paperLayout = ActiveTest.current?.paperLayout ?: PaperLayout.QUARTER
        val frame = GuideGeometry.computeFrame(vw, vh, paperLayout)

        val gl = frame.left / vw
        val gt = frame.top / vh
        val gr = (frame.left + frame.width) / vw
        val gb = (frame.top + frame.height) / vh
        val gw = gr - gl
        val gh = gb - gt
        if (gw <= 0f || gh <= 0f) return bitmap

        val cl = (gl - gw * CROP_PADDING_FRACTION).coerceIn(0f, 1f)
        val ct = (gt - gh * CROP_PADDING_FRACTION).coerceIn(0f, 1f)
        val cr = (gr + gw * CROP_PADDING_FRACTION).coerceIn(0f, 1f)
        val cb = (gb + gh * CROP_PADDING_FRACTION).coerceIn(0f, 1f)

        val left = (cl * bitmap.width).toInt()
        val top = (ct * bitmap.height).toInt()
        val right = (cr * bitmap.width).toInt()
        val bottom = (cb * bitmap.height).toInt()
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)

        return try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun showKeyScanResult(scanResults: List<BubbleGrader.QuestionResult>) {
        val letters = scanResults.map { r -> r.chosenIndex?.let { ('A' + it).toString() } ?: "?" }
        lastScannedKeyText = letters.joinToString(",")

        val blanks = scanResults.count { it.chosenIndex == null }
        val ambiguous = scanResults.count { it.isAmbiguous }
        tvScoreHeader.text = "Detected Key"

        val sb = StringBuilder()
        if (blanks > 0 || ambiguous > 0) {
            sb.append("⚠ $blanks blank, $ambiguous ambiguous — double-check before using.\n\n")
        }
        scanResults.forEach { r ->
            val letter = r.chosenIndex?.let { ('A' + it).toString() } ?: "?"
            val flag = if (r.isAmbiguous) " ⚠" else if (r.chosenIndex == null) " (blank)" else ""
            sb.append("Q${r.questionNumber}: $letter$flag\n")
        }
        tvResult.text = sb.toString()
        findViewById<View>(R.id.btnUseAsKey).visibility = View.VISIBLE
    }

    /** Decodes the captured JPEG and rotates it upright according to its EXIF orientation tag. */
    private fun decodeWithExifCorrection(file: File): Bitmap {
        val original = BitmapFactory.decodeFile(file.absolutePath)
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return if (matrix.isIdentity) original
        else Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        // Registered here (not onCreate) and unregistered in onPause so we
        // aren't holding a sensor listener open while this Activity isn't
        // actually the one on screen (e.g. backgrounded mid-scan).
        flatDetectorSensor?.let {
            sensorManager.registerListener(flatDetectorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(flatDetectorListener)
        // Reset rather than leave stale: if the guide was green from being
        // flat right before backgrounding, don't let it stay green on
        // resume before the sensor has reported fresh readings.
        deviceIsFlat = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // General-purpose guard, not just a patch for the MODE_KEY crash
        // above: ANY early finish() in onCreate() before cameraExecutor is
        // assigned (camera permission denial, ActiveTest.current == null,
        // etc.) would hit the same UninitializedPropertyAccessException
        // here without this check.
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}
