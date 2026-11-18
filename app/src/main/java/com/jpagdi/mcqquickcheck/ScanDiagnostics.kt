package com.jpagdi.mcqquickcheck

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnosis-only instrumentation (v2.4) for the "scan reads inconsistently"
 * feedback from real-device testing — some scans the user judges clearly
 * filled and well-lit still get flagged unclear/ambiguous, and this has
 * been hardest to pin down on HALF_LENGTH specifically (one scan succeeded
 * once and hasn't since). This does NOT change any BubbleGrader threshold
 * or classification behavior — it only records values BubbleGrader already
 * computes, so a real batch of scans can be compared side by side before
 * anything gets tuned. See BubbleGrader.SheetDiagnostics / the diagnostics
 * fields on QuestionResult for what's being read here.
 *
 * Every processPhoto() attempt gets logged — accepted, rejected as unclear,
 * markers-not-found, and generic errors — appended as one block to a
 * single rolling log file (not one file per scan), so the user can export
 * one file that covers a whole testing session and see failed vs.
 * successful attempts side by side.
 *
 * Same FileProvider/share-sheet pattern as BackupDataActivity and
 * TestSheetBatchGenerator; the "diagnostics" subfolder is registered in
 * file_paths.xml the same way "test_sheets" had to be — an unregistered
 * folder crashes on share rather than failing gracefully, per the lesson
 * from v2.2.
 *
 * v3.1: two changes to how entries are written, both in the same write
 * path so there's one single place that decides "how a new attempt gets
 * recorded":
 *  - Capped at MAX_LOG_ENTRIES (100) — previously this file grew forever
 *    with zero trimming, across every device/testing session ever run.
 *    100 recent entries is enough to diagnose a testing session without
 *    the file growing without bound.
 *  - Newest-first ordering — previously appendText() put each new entry
 *    at the END of a plain-text file (oldest-first top to bottom). Now
 *    every write reads the existing entries, puts the new one at the
 *    TOP, and rewrites the whole (capped) file. Cheap at a 100-entry cap;
 *    would NOT be cheap at the old unbounded size, which is part of why
 *    capping and reordering belong in the same change.
 */
object ScanDiagnostics {

    private const val LOG_FILE_NAME = "scan_diagnostics_log.txt"
    private const val MAX_LOG_ENTRIES = 100

    private fun logFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
        return File(dir, LOG_FILE_NAME)
    }

    /** Splits the existing log file back into individual entry blocks (each
     *  starting with its own "==== timestamp ====" header) so a new entry
     *  can be prepended and the whole set re-capped. A log written before
     *  this v3.1 change is still plain oldest-first text — those old
     *  entries just ride along at the bottom of the list until they age
     *  out of the 100-entry cap; no migration needed beyond that. */
    private fun readExistingBlocks(context: Context): List<String> {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) return emptyList()
        val content = file.readText()
        return content.split(Regex("(?=^==== )", RegexOption.MULTILINE)).filter { it.isNotBlank() }
    }

    fun logAttempt(
        context: Context,
        testName: String,
        paperLayout: PaperLayout,
        columns: Int,
        questionCount: Int,
        outcome: String, // "ACCEPTED", "REJECTED_UNCLEAR", "MARKERS_NOT_FOUND", "ERROR"
        rejectionMessage: String? = null,
        diagnostics: BubbleGrader.SheetDiagnostics? = null,
        results: List<BubbleGrader.QuestionResult>? = null,
        // v3.1 (item 8): per-corner Otsu threshold + pass/fail data from
        // MarkerDetector, only meaningful (and only ever passed) for a
        // MARKERS_NOT_FOUND outcome — see MarkerDetector.lastCornerDiagnostics.
        // This is diagnostic instrumentation only; it doesn't change what
        // MarkerDetector accepts or rejects.
        markerDiagnostics: List<MarkerDetector.CornerDiagnostic>? = null,
        // v3.2: tap-to-result latency in ms — added to confirm whether
        // MINIMIZE_LATENCY actually helped vs the old MAXIMIZE_QUALITY
        // default, back when that was a Settings toggle. v3.4: the toggle
        // itself is gone (capture mode is now permanently fixed at
        // MINIMIZE_LATENCY, confirmed working against real scan/grading
        // data) — kept logging latency itself since that's still real
        // per-attempt data worth having, just no longer paired with an
        // on/off flag that would now always read the same value.
        captureLatencyMs: Long? = null
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("==== $timestamp ====\n")
        sb.append("Test: $testName | Layout: ${paperLayout.name} | Columns: $columns | Items: $questionCount\n")
        sb.append("Outcome: $outcome\n")
        if (rejectionMessage != null) sb.append("Message: $rejectionMessage\n")
        if (captureLatencyMs != null) {
            sb.append("Capture-to-result: ${captureLatencyMs}ms\n")
        }

        if (diagnostics != null) {
            sb.append(
                "Ink threshold used: %.1f (raw adaptive: %s, fallback used: %s) | bubbleRadiusPx: %d | Otsu separability: %s (%s confidence)\n".format(
                    diagnostics.blendedInkThreshold,
                    diagnostics.rawAdaptiveThreshold?.let { "%.1f".format(it) } ?: "n/a",
                    diagnostics.usedFallbackThreshold,
                    diagnostics.bubbleRadiusPx,
                    diagnostics.otsuSeparability?.let { "%.2f".format(it) } ?: "n/a",
                    diagnostics.confidenceTier
                )
            )
        }

        if (results != null) {
            val realProblems = results.count { it.isAmbiguous && !it.isGenuineBlank }
            val blanks = results.count { it.isGenuineBlank }
            sb.append("Real problems: $realProblems | Blanks: $blanks | Total items: ${results.size}\n")

            // Only flagged items get itemized — a clean 120-item sheet
            // would otherwise bury the actually-useful rows under a wall
            // of "everything's fine" lines.
            val flagged = results.filter { it.isAmbiguous }
            if (flagged.isNotEmpty()) {
                sb.append("Flagged items (Q: maxCov/hardCov/centerCov | 2ndHigh/marginal/thinOffCenter/hardFail/blank):\n")
                flagged.forEach { r ->
                    sb.append(
                        "  Q%d: %.2f/%.2f/%.2f | %s/%s/%s/%s/%s\n".format(
                            r.questionNumber, r.maxCoverage, r.hardCoverage, r.centerCoverage,
                            r.secondHighEnough, r.marginal, r.thinOrOffCenter, r.failsHardFloor, r.isGenuineBlank
                        )
                    )
                }
            }
        }

        if (markerDiagnostics != null && markerDiagnostics.isNotEmpty()) {
            sb.append("Marker corners (score/otsuThreshold/pass/reason):\n")
            markerDiagnostics.forEach { d ->
                sb.append(
                    "  %s: score=%.3f otsu=%s pass=%s%s%s\n".format(
                        d.corner,
                        d.bestTemplateScore,
                        d.otsuThreshold?.let { "%.1f".format(it) } ?: "n/a",
                        d.passed,
                        d.failureReason?.let { " reason=$it" } ?: "",
                        if (d.rejectedByPaperGate) " [PAPER_GATE_REJECT]" else ""
                    )
                )
            }
        }
        sb.append("\n")

        writeRollingEntry(context, sb.toString())
    }

    /** Records ID-linking diagnostics without writing learner IDs or read digits. */
    fun logIdNote(context: Context, testName: String, note: String) {
        logIdEvent(context, testName, "Note: $note")
    }

    fun logIdMatch(
        context: Context,
        testName: String,
        outcome: String,
        digits: Int,
        rosterSize: Int,
        read: IdReader.Read,
        match: IdMatcher.Match,
        selectedId: String?,
        runnerUpId: String?
    ) {
        logIdEvent(
            context, testName,
            "Outcome: $outcome | ID digits: $digits | Readable: ${read.readableCount} | " +
                "Roster: $rosterSize | Candidates: ${match.candidates.size} | " +
                "Selected: ${selectedId != null} | Runner-up: ${runnerUpId != null}"
        )
    }

    fun logIdRecheck(context: Context, testName: String, linked: Int, remaining: Int, noImage: Int) {
        logIdEvent(context, testName, "Re-check: linked=$linked | remaining=$remaining | no stored read=$noImage")
    }

    fun logIdConfirmed(
        context: Context,
        testName: String,
        readDigits: String,
        learnerId: String,
        candidateIds: List<String>
    ) {
        logIdEvent(
            context, testName,
            "Teacher confirmed an ID | Read length: ${readDigits.length} | " +
                "Learner ID length: ${learnerId.length} | Candidates: ${candidateIds.size}"
        )
    }

    private fun logIdEvent(context: Context, testName: String, details: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        writeRollingEntry(context, "==== $timestamp ====\nTest: $testName | ID linking\n$details\n\n")
    }

    @Synchronized
    private fun writeRollingEntry(context: Context, entry: String) {
        val capped = (listOf(entry) + readExistingBlocks(context)).take(MAX_LOG_ENTRIES)
        logFile(context).writeText(capped.joinToString(""))
    }

    /** True if there's a non-empty log worth sharing — used to grey out /
     *  message the share action instead of silently doing nothing. */
    fun hasLog(context: Context): Boolean {
        val file = logFile(context)
        return file.exists() && file.length() > 0
    }

    // v3.1 (Send Diagnostic Report): the report generator needs the same
    // rolling log this object already maintains — exposing read access
    // here instead of building a second, separate history store, per
    // "reuse existing diagnostic generation, don't duplicate."
    fun logText(context: Context): String {
        val file = logFile(context)
        return if (file.exists()) file.readText() else ""
    }

    // v3.1: exportThenPrompt() (local share/download of this log) removed
    // here — its only caller, About screen's button, now goes through
    // DiagnosticUploadService instead (see
    // AboutActivity.showSendDiagnosticReportDialog). Removed rather than
    // left as dead code, since replacing this exact local-export path was
    // the whole point of this pass, not leaving it orphaned.

    /** Lets the user start a fresh log before a new testing session, so an
     *  old session's entries don't pad out the next share. */
    fun clearLog(context: Context) {
        val file = logFile(context)
        if (file.exists()) file.delete()
    }
}
