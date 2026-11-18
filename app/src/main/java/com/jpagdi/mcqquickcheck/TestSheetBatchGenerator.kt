package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One-off batch generator for downloadable prefilled test sheets — used to
 * exercise the scanner/grader pipeline across every paper layout and a
 * range of item counts, not something a teacher would use day to day.
 * Entry point is a long-press on the About screen title (hidden the same
 * way this app already hides one-off dev switches, e.g. the changelog's
 * former hidden toggle before it was made visible) rather than a
 * permanent, visible feature — flagged as an assumption; make it a normal
 * button somewhere if you'd rather it be discoverable.
 *
 * Reuses SheetGenerator directly (single source of truth for layout), so
 * these sheets are pixel-identical in structure to what GenerateSheetActivity
 * produces — only difference is every sheet here has bubbles pre-marked.
 */
object TestSheetBatchGenerator {

    // Every sheet here uses 4 choices (A-D) — this project doesn't have a
    // "default" choice count to pull from elsewhere, so this is a
    // deliberate assumption, not read from any existing setting.
    private const val CHOICE_COUNT = 4

    // How many distinct fill-pattern variations get packed onto one
    // physical sheet — matches SheetGenerator's own per-layout region
    // count exactly (QUARTER tiles 4-up), so each variation lands in its
    // own quarter tile rather than needing any new tiling logic of our own.
    // v3.4: HALF_LENGTH/HALF_CROSS removed — QUARTER is the only case left.
    private fun variationCount(layout: PaperLayout): Int = when (layout) {
        PaperLayout.QUARTER -> 4
    }

    private data class Combo(val layout: PaperLayout, val columns: Int, val questionCount: Int)

    /**
     * v3.4 matrix (supersedes the v2.4 matrix below) — HALF_LENGTH/
     * HALF_CROSS removed entirely; QUARTER's tiers REPLACED (not extended)
     * per explicit product decision, matching SheetTemplate.
     * maxBlocksPerRowGroupRange's new tiers exactly:
     * - 10-30 items → 1 column
     * - 31-60 items → 2 columns
     * - 61-90 items → 3 columns
     * Every combo below matches a combination a teacher could actually
     * create in GenerateSheetActivity now — no test-only combos that don't
     * correspond to a real, reachable app state. Like the SheetTemplate
     * tiers themselves, the 31-90 range here is a fresh product decision,
     * not verified against real-device scan data.
     *
     * v2.4 matrix (superseded — kept for history, not re-enabled): QUARTER
     * n=10→1col, n=20→2col, n=30-60→3col; HALF_LENGTH n=60-90→3col;
     * HALF_CROSS n=60,70→5col, n=80,90→6col. Fully replaced, not layered
     * on top of.
     *
     * v2.3 matrix (original expansion, for reference only): QUARTER n=20-60
     * at BOTH 2 and 3 columns; HALF_LENGTH/HALF_CROSS n=70-120 with free
     * column choice (1-3 / 4-6 respectively). No longer valid at all.
     */
    /**
     * v3.5 matrix (supersedes the v3.4 matrix below) — per explicit request,
     * cut down from one combo per 10-question step (9 combos) to 5
     * milestone item counts: the low/mid/high end of the 1-column tier
     * (10/20/30) plus the top edge of the 2-column tier (60) and the top
     * edge of the 3-column tier (90). Same tier boundaries as
     * SheetTemplate.maxBlocksPerRowGroupRange, just fewer points sampled
     * along them — still one real, reachable combo per column count, just
     * not every step in between.
     *
     * v3.4 matrix (superseded — kept for history, not re-enabled):
     * - 10-30 items step 10 → 1 column
     * - 40-60 items step 10 → 2 columns
     * - 70-90 items step 10 → 3 columns
     *
     * v2.4 matrix (original expansion, for reference only): QUARTER n=20-60
     * at BOTH 2 and 3 columns; HALF_LENGTH/HALF_CROSS n=70-120 with free
     * column choice (1-3 / 4-6 respectively). No longer valid at all.
     */
    /**
     * v3.6 matrix (supersedes the v3.5 matrix below) — adds the new 4th
     * column tier and re-anchors every milestone to the new 25/50/75/100
     * boundaries (was 30/60/90): the low/mid/high end of the 1-column tier
     * (10/15/25) plus the top edge of the 2-column tier (50), the top edge
     * of the 3-column tier (75), and the top edge of the new 4-column tier
     * (100). Same "one real, reachable combo per column count" approach as
     * v3.5, just re-pointed at SheetTemplate.maxBlocksPerRowGroupRange's
     * new tiers.
     *
     * v3.5 matrix (superseded — kept for history, not re-enabled): per
     * explicit request, cut down from one combo per 10-question step (9
     * combos) to 5 milestone item counts: the low/mid/high end of the
     * 1-column tier (10/20/30) plus the top edge of the 2-column tier (60)
     * and the top edge of the 3-column tier (90). Same tier boundaries as
     * SheetTemplate.maxBlocksPerRowGroupRange, just fewer points sampled
     * along them.
     *
     * v3.4 matrix (superseded — kept for history, not re-enabled):
     * - 10-30 items step 10 → 1 column
     * - 40-60 items step 10 → 2 columns
     * - 70-90 items step 10 → 3 columns
     *
     * v2.4 matrix (original expansion, for reference only): QUARTER n=20-60
     * at BOTH 2 and 3 columns; HALF_LENGTH/HALF_CROSS n=70-120 with free
     * column choice (1-3 / 4-6 respectively). No longer valid at all.
     */
    // v3.6.1: added 30q/2col, 60q/3col, 90q/4col per explicit request — mid-
    // tier points (not just each tier's top edge) for each column count
    // above 1, so a column count gets exercised at more than one item total.
    // All three are real, reachable GenerateSheetActivity states (verified
    // against SheetTemplate.maxBlocksPerRowGroupRange/rowsPerBlockRange),
    // same "no test-only combos" rule as the rest of this list.
    private fun buildComboList(): List<Combo> = listOf(
        Combo(PaperLayout.QUARTER, 1, 10),
        Combo(PaperLayout.QUARTER, 1, 15),
        Combo(PaperLayout.QUARTER, 1, 25),
        Combo(PaperLayout.QUARTER, 2, 30),
        Combo(PaperLayout.QUARTER, 2, 50),
        Combo(PaperLayout.QUARTER, 3, 60),
        Combo(PaperLayout.QUARTER, 3, 75),
        Combo(PaperLayout.QUARTER, 4, 90),
        Combo(PaperLayout.QUARTER, 4, 100)
    )

    private fun rowsForBatch(questionCount: Int, columns: Int): Int =
        SheetTemplate.rowsPerBlockRange(questionCount, columns).first

    /** Genuinely randomized per variation (not the old fixed A,B,C,D
     *  cycle) — seeded deterministically from the combo + variation index
     *  so re-running the batch generator produces the same set of
     *  patterns rather than a different one every time, which matters for
     *  a testing tool you'd want to compare results against run to run. */
    private fun randomAnswerKey(questionCount: Int, seed: Long): List<Int> {
        val rng = kotlin.random.Random(seed)
        return List(questionCount) { rng.nextInt(CHOICE_COUNT) }
    }

    // v2.4: the deliberately-messy stress sheet concept — a mostly-clean
    // sheet with a random slice of questions given a realistic ambiguous
    // mark instead of a clean fill, so a teacher/tester can exercise the
    // scanner's ambiguity detection without hand-marking a sheet. About 1
    // in 6 questions gets a defect, split evenly across the four defect
    // types — frequent enough to be easy to find without making the sheet
    // mostly unreadable (which would defeat the point).
    //
    // v3.4: HALF_CROSS/HALF_LENGTH stress configs removed along with the
    // rest of PaperLayout's now-deleted values — QUARTER is the only
    // stress config generated. See v2.8 history in git for the removed
    // configs' prior tuning notes.
    //
    // v3.5: was a single fixed 60q/3col config — per explicit request, now
    // one stress config per milestone item count in buildComboList (same
    // 10/20/30/60/90 list, same column pairing), so every item-count
    // milestone gets both a clean multi-variant sheet AND a stress sheet
    // with realistic ambiguous marks, not just the 60-question one.
    //
    // v3.6: re-anchored to buildComboList's new 10/15/25/50/75/100
    // milestones (was 10/20/30/60/90) to match the new 4-column, 25-cap
    // tier system.
    // v3.6.1: added 30/2, 60/3, 90/4 alongside the existing tier-edge
    // configs — same reasoning as buildComboList above.
    private val STRESS_CONFIGS: List<Pair<Int, Int>> = listOf(
        10 to 1, 15 to 1, 25 to 1, 30 to 2, 50 to 2, 60 to 3, 75 to 3, 90 to 4, 100 to 4
    ) // questionCount to columns
    private const val STRESS_VARIATIONS_PER_CONFIG = 4

    private fun randomDefectPlan(questionCount: Int, seed: Long): List<SheetGenerator.MarkDefect> {
        val rng = kotlin.random.Random(seed)
        val defectTypes = listOf(
            SheetGenerator.MarkDefect.BLANK,
            SheetGenerator.MarkDefect.PARTIAL,
            SheetGenerator.MarkDefect.OVERFLOW,
            SheetGenerator.MarkDefect.MULTI
        )
        return List(questionCount) {
            if (rng.nextInt(6) == 0) defectTypes[rng.nextInt(defectTypes.size)] else SheetGenerator.MarkDefect.NONE
        }
    }

    /** Generates ONE physical sheet holding all [STRESS_VARIATIONS_PER_CONFIG]
     *  distinct stress variations for one layout/column/question-count
     *  combo — one variation per quadrant, each with its own answer key
     *  AND its own defect pattern (both seeded off the variation index, so
     *  re-running the batch reproduces the same set every time, same
     *  reasoning as randomAnswerKey's own seeding elsewhere in this file).
     *  v3.6.5: was 4 SEPARATE pages, each showing the same one pattern
     *  repeated across all 4 quadrants (redundant — 3 of every page's 4
     *  quadrants were identical copies). Packing the 4 DIFFERENT
     *  variations onto one page's 4 quadrants instead cuts this batch's
     *  paper cost 4x, the same way the combo answer-key list already
     *  packed multiple variants per page. Relies on STRESS_VARIATIONS_PER_
     *  CONFIG matching QUARTER's exact region count (4) — true today since
     *  QUARTER is the only PaperLayout left; would need revisiting if a
     *  second layout with a different region count ever returns. Returns
     *  the one file written, for the caller to fold into its zip/count. */
    private fun generateStressVariations(
        dir: File,
        layout: PaperLayout,
        columns: Int,
        questionCount: Int,
        baseSeed: Long,
        paperSize: PaperSize
    ): List<File> {
        val rows = rowsForBatch(questionCount, columns)
        val template = SheetTemplate(
            testName = "${layout.name}_STRESSTEST_${columns}col_${questionCount}q",
            questionCount = questionCount,
            choiceCount = CHOICE_COUNT,
            numbering = NumberingOrder.COLUMN_MAJOR,
            rowsPerBlock = rows,
            maxBlocksPerRowGroup = columns,
            // v3.6.3 fix: this was never set here, so it silently fell
            // back to SheetTemplate's own default parameter value
            // (BubbleSizePrefs.DEFAULT_MM, 2.2mm) instead of matching
            // what GenerateSheetActivity actually uses for a brand-new
            // real template (BubbleSizePrefs.FIXED_MM, 2.68mm) — a
            // smaller bubble, and since column/row spacing is derived
            // FROM the bubble diameter (see SheetTemplate's doc
            // comment), a tighter gap too. This was the actual root
            // cause of the reported gap mismatch — paper size (fixed
            // in an earlier release) was unrelated.
            bubbleSizeMm = BubbleSizePrefs.FIXED_MM
        )
        // Two independent seeds per variation (answer key vs. defect plan)
        // so a variation's defect PATTERN isn't just a shifted copy of its
        // answer-key pattern — distinct RNG streams.
        val answerKeys = (0 until STRESS_VARIATIONS_PER_CONFIG).map { variationIndex ->
            randomAnswerKey(questionCount, baseSeed + variationIndex * 2L)
        }
        val defects = (0 until STRESS_VARIATIONS_PER_CONFIG).map { variationIndex ->
            randomDefectPlan(questionCount, baseSeed + variationIndex * 2L + 1L)
        }
        val file = File(dir, "${template.testName}.pdf")
        SheetGenerator.generateMultiVariant(template, file, layout, answerKeys, paperSize, defects)
        return listOf(file)
    }

    // --- Sample Item Analysis (v2.8) ---
    // Reuses ItemAnalysis.compute() and ItemAnalysisXlsxExporter.buildXlsx()
    // directly rather than hand-writing P/D values and rubric labels a
    // second time — this guarantees the sample file's numbers and
    // recommendation text are produced by the EXACT same logic a real
    // report would use, not a hand-faked approximation that could drift
    // out of sync with the real rubric later.
    private const val SAMPLE_ITEM_ANALYSIS_QUESTIONS = 30
    private const val SAMPLE_ITEM_ANALYSIS_STUDENTS = 45

    /** Simple 1-parameter (Rasch-style) simulation: each student gets a
     *  random ability, each question a difficulty, P(correct) from the
     *  logistic of their difference — standard textbook approach for
     *  generating realistic-looking test response data. Difficulty is
     *  deliberately banded (not a smooth ramp) so the 30 items span a
     *  believable easy/medium/hard mix, and 3 items are forced to near-50%
     *  odds REGARDLESS of ability — i.e. genuinely poor discrimination —
     *  since a sample report with zero flagged items wouldn't show what
     *  the report is actually for. */
    private fun generateSampleItemAnalysisScans(seed: Long): List<ScannedSheetStorage.SavedScan> {
        val rng = kotlin.random.Random(seed)
        val questionDifficulty = List(SAMPLE_ITEM_ANALYSIS_QUESTIONS) { idx ->
            when {
                idx < 6 -> rng.nextDouble(-2.0, -0.8)  // easy
                idx < 12 -> rng.nextDouble(-0.8, 0.2)  // easy-medium
                idx < 22 -> rng.nextDouble(-0.2, 1.0)  // medium-hard
                idx < 27 -> rng.nextDouble(1.0, 2.2)   // hard
                else -> 0.0                             // last 3: see lowDiscriminationItems below
            }
        }
        val lowDiscriminationItems = setOf(27, 28, 29) // 0-based — the "needs review" demo items

        fun randomNormal(): Double {
            // Box-Muller, off the same seeded RNG as everything else in
            // this file, so the sample is reproducible run to run.
            val u1 = rng.nextDouble().coerceAtLeast(1e-9)
            val u2 = rng.nextDouble()
            return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        }
        fun logistic(x: Double) = 1.0 / (1.0 + kotlin.math.exp(-x))

        return (1..SAMPLE_ITEM_ANALYSIS_STUDENTS).map { studentIndex ->
            val ability = randomNormal()
            val perQuestion: List<Boolean?> = questionDifficulty.mapIndexed { qIdx, difficulty ->
                val pCorrect = if (qIdx in lowDiscriminationItems) 0.55 else logistic(ability - difficulty)
                rng.nextDouble() < pCorrect
            }
            ScannedSheetStorage.SavedScan(
                id = "sample_$studentIndex",
                testName = "Sample Item Analysis",
                folder = ScannedSheetStorage.MAIN_FOLDER,
                savedAtMillis = System.currentTimeMillis(),
                earnedPoints = perQuestion.count { it == true },
                totalPoints = SAMPLE_ITEM_ANALYSIS_QUESTIONS,
                perQuestion = perQuestion
            )
        }
    }

    /** Returns null (and generates nothing) only if ItemAnalysis.compute()
     *  itself declines — e.g. not enough usable scans — which shouldn't
     *  happen with a fixed 45-student sample, but the real compute()
     *  function returns nullable, so this stays honest about that instead
     *  of forcing a !! that would crash the whole batch if the underlying
     *  logic ever changes its minimum-N requirement. */
    private fun generateSampleItemAnalysisFile(dir: File): File? {
        val scans = generateSampleItemAnalysisScans(seed = 909090L)
        val result = ItemAnalysis.compute(scans, SAMPLE_ITEM_ANALYSIS_QUESTIONS) ?: return null
        val bytes = ItemAnalysisXlsxExporter.buildXlsx(
            "Sample Item Analysis (30Q, 45 students)", null, result
        )
        val file = File(dir, "Sample_ItemAnalysis_30q_45students.xlsx")
        file.writeBytes(bytes)
        return file
    }

    /** v2.9: was generateAndShare(), which always shared with no choice.
     *  Renamed and now offers Share or Download via FileExportPrompt.
     *  Also moved the working PDFs + zip from getExternalFilesDir's
     *  persistent "test_sheets" folder to cacheDir — same double-save
     *  reasoning as GenerateSheetActivity's PDF flow (see its doc
     *  comment). The dir was already wiped at the START of every run
     *  (deleteRecursively/mkdirs below, unchanged), but nothing ever
     *  cleaned it up AFTER a run, so a persistent copy sat in Android/data
     *  indefinitely alongside anything saved to Downloads. Now deleted
     *  after a successful Download; left alone after Share (cache is the
     *  right place for a FileProvider working file either way). */
    fun generateThenPrompt(activity: Activity) {
        Toast.makeText(activity, "Generating test sheets…", Toast.LENGTH_SHORT).show()
        // v3.6.2 fix: was always rendering these at a fixed Letter page
        // regardless of what paper size the person actually generates/
        // prints real sheets at — read once here, off the UI thread work
        // below, same source real sheet generation reads from.
        val paperSize = SettingsPrefs.getPaperSizePreference(activity)
        Thread {
            try {
                val dir = File(activity.cacheDir, "test_sheets").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val combos = buildComboList()
                combos.forEach { combo ->
                    val rows = rowsForBatch(combo.questionCount, combo.columns)
                    val template = SheetTemplate(
                        testName = "${combo.layout.name}_${combo.columns}col_${combo.questionCount}q",
                        questionCount = combo.questionCount,
                        choiceCount = CHOICE_COUNT,
                        numbering = NumberingOrder.COLUMN_MAJOR,
                        rowsPerBlock = rows,
                        maxBlocksPerRowGroup = combo.columns,
                        bubbleSizeMm = BubbleSizePrefs.FIXED_MM
                    )
                    val variations = variationCount(combo.layout)
                    val answerKeys = (0 until variations).map { variationIndex ->
                        val seed = combo.questionCount * 1000L + combo.columns * 97L + variationIndex
                        randomAnswerKey(combo.questionCount, seed)
                    }
                    val file = File(dir, "${template.testName}.pdf")
                    SheetGenerator.generateMultiVariant(template, file, combo.layout, answerKeys, paperSize)
                }
                STRESS_CONFIGS.forEachIndexed { index, (questionCount, columns) ->
                    // Distinct base seed per config (not just per variation
                    // within one config) so the 10q and 20q stress batches
                    // don't end up with identical relative defect placement.
                    generateStressVariations(dir, PaperLayout.QUARTER, columns, questionCount, 424242L + index * 10_000L, paperSize)
                }
                val sampleItemAnalysisFile = generateSampleItemAnalysisFile(dir)
                // v3.5: was `* 1` (single fixed stress config) — now one
                // stress batch per STRESS_CONFIGS entry.
                // v3.6.5: was STRESS_VARIATIONS_PER_CONFIG * STRESS_CONFIGS.size
                // (4 separate pages per config) — now 1 page per config,
                // since generateStressVariations packs all 4 variations onto
                // one page's quadrants instead.
                val totalSheets = combos.size + STRESS_CONFIGS.size

                val zipFile = File(dir, "mcq_test_sheets.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    dir.listFiles()?.forEach { pdf ->
                        if (pdf == zipFile) return@forEach
                        zos.putNextEntry(ZipEntry(pdf.name))
                        pdf.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                // Dropped `val zipBytes = zipFile.readBytes()` here — same
                // fix as BackupDataActivity/ScannedSheetExporter, see
                // their doc comments. Download now streams from zipFile.

                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "$totalSheets test sheets" + (if (sampleItemAnalysisFile != null) " + 1 sample Item Analysis" else "") + " generated",
                        Toast.LENGTH_SHORT
                    ).show()
                    FileExportPrompt.show(
                        activity = activity,
                        title = "Test sheets ready",
                        fileName = "mcq_test_sheets.zip",
                        mimeType = "application/zip",
                        workingFile = zipFile,
                        shareTitle = "Share test sheets",
                        onDownloadComplete = { dir.deleteRecursively() }
                    )
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Test sheet generation failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
