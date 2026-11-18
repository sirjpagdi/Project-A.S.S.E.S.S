package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * v1.2.0 — the per-class score sheet for ONE exam: every enrolled learner of
 * the class with their score (or "—" if not scanned yet), the class average,
 * the scans waiting under "Needs ID check", and an XLSX export.
 *
 * Opened from the exam's page (TestDetailActivity -> Classes). Viewing and
 * exporting always work for data that exists, even after a plan lapses;
 * resolving an unlinked scan or removing a score changes data, so those need
 * ClassesAccess.isActive().
 */
class ClassSheetActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_NAME = "test_name"
        const val EXTRA_CLASS_ID = "class_id"
    }

    private lateinit var testName: String
    private lateinit var classId: String
    private val dynamicViews = ArrayList<View>()

    /** How the learner list (and the exported spreadsheet) is ordered. Kept
     *  only while this screen is open (and across rotation) — deliberately
     *  not a saved Setting. Unscanned learners always sort after scanned
     *  ones in the two score orders. */
    private enum class SortMode(val label: String) {
        NAME_AZ("Name (A–Z)"),
        NAME_ZA("Name (Z–A)"),
        SCORE_HIGH("Score (high to low)"),
        SCORE_LOW("Score (low to high)"),
        ID("ID number"),
        GENDER("Gender (male first)")
    }
    private var sortMode = SortMode.NAME_AZ

    private val requestNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* result not needed — the download proceeds either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        sortMode = SortMode.values().getOrElse(savedInstanceState?.getInt("sortMode", 0) ?: 0) { SortMode.NAME_AZ }
        testName = intent.getStringExtra(EXTRA_TEST_NAME) ?: run { finish(); return }
        classId = intent.getStringExtra(EXTRA_CLASS_ID) ?: run { finish(); return }
        DrawerHelper.attach(this, findViewById(R.id.drawerLayout), findViewById(R.id.btnOpenDrawer))
        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("sortMode", sortMode.ordinal)
    }

    private fun sortedLearners(learners: List<Learner>, results: Map<String, LearnerResult>): List<Learner> {
        fun percent(l: Learner): Double? = results[l.learnerId]?.takeIf { it.totalPoints > 0 }
            ?.let { it.earnedPoints * 100.0 / it.totalPoints }
        val byName = compareBy<Learner> { it.name.lowercase() }
        return when (sortMode) {
            SortMode.NAME_AZ -> learners.sortedWith(byName)
            SortMode.NAME_ZA -> learners.sortedWith(byName.reversed())
            SortMode.SCORE_HIGH -> learners.sortedWith(compareBy<Learner> { percent(it) == null }.thenByDescending { percent(it) ?: 0.0 }.then(byName))
            SortMode.SCORE_LOW -> learners.sortedWith(compareBy<Learner> { percent(it) == null }.thenBy { percent(it) ?: 0.0 }.then(byName))
            SortMode.ID -> learners.sortedWith(compareBy<Learner> { it.learnerId.length }.thenBy { it.learnerId }.then(byName))
            SortMode.GENDER -> learners.sortedWith(compareBy<Learner> { when (it.gender) { "M" -> 0; "F" -> 1; else -> 2 } }.then(byName))
        }
    }

    private fun chooseSort() {
        val modes = SortMode.values()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Sort learners by")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), sortMode.ordinal) { d, which ->
                sortMode = modes[which]
                d.dismiss()
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        DialogColors.fix(this, dialog)
    }

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
        rebuild()
    }

    private fun rebuild() {
        val cls = ClassStorage.getClass(this, classId)
        if (cls == null) { finish(); return }
        val active = ClassesAccess.isActive(this)
        val results = LearnerResultStorage.listResults(this, testName).filter { it.classId == classId }.associateBy { it.learnerId }
        val pending = LearnerResultStorage.listPending(this, testName)

        val scored = cls.learners.mapNotNull { results[it.learnerId] }.filter { it.totalPoints > 0 }
        val summary = if (scored.isEmpty()) "No learners scanned yet"
        else "${scored.size} of ${cls.learners.size} scanned · average ${"%.1f".format(scored.map { it.earnedPoints * 100.0 / it.totalPoints }.average())}%"

        findViewById<TextView>(R.id.tvPlaceholderTitle).text = cls.name
        findViewById<TextView>(R.id.tvPlaceholderBody).text = "$testName\n$summary"

        val content = findViewById<LinearLayout>(R.id.placeholderContent)
        dynamicViews.forEach { content.removeView(it) }
        dynamicViews.clear()
        var insertAt = content.indexOfChild(findViewById(R.id.btnPlaceholderBack))
        fun add(v: View) { content.addView(v, insertAt++); dynamicViews.add(v) }

        add(ClassUi.actionButton(this, "SORT: ${sortMode.label.uppercase()}") { chooseSort() })
        val canExport = ClassesAccess.hasScoreExportAccess(this)
        add(ClassUi.actionButton(this, if (canExport) "EXPORT SPREADSHEET (.XLSX)" else "🔒 EXPORT SPREADSHEET (FULL YEAR)") { exportXlsx(cls, results) })

        if (pending.isNotEmpty()) {
            add(ClassUi.sectionLabel(this, "NEEDS ID CHECK (${pending.size})"))
            add(ClassUi.note(this, "These scans were graded but the ID couldn't be matched confidently. Tap one to choose the learner."))
            if (active) add(ClassUi.actionButton(this, "RE-CHECK IDS") { recheckIds() })
            pending.forEach { p ->
                val read = if (p.readDigits.isBlank()) "not read" else "read as ${p.readDigits}"
                add(ClassUi.card(
                    this, "Score ${p.earnedPoints} / ${p.totalPoints}", "ID $read",
                    onClick = if (active) ({ choosePendingLearner(p) }) else null
                ))
            }
        }

        add(ClassUi.sectionLabel(this, "LEARNERS"))
        if (cls.learners.isEmpty()) add(ClassUi.note(this, "This class has no learners."))
        sortedLearners(cls.learners, results).forEach { learner ->
            val r = results[learner.learnerId]
            val trailing = if (r == null) "—" else "${r.earnedPoints} / ${r.totalPoints}"
            val genderTag = ClassUi.genderLabel(learner.gender).let { if (it.isEmpty()) "" else " · $it" }
            val sub = if (r == null) "ID ${learner.learnerId}$genderTag · not scanned yet"
            else "ID ${learner.learnerId}$genderTag · ${ClassUi.percentText(r.earnedPoints, r.totalPoints)}"
            add(ClassUi.card(this, learner.name, sub, trailing, onClick = if (active && r != null) ({ confirmRemoveScore(learner, r) }) else null))
        }
        AccentColorPrefs.applyAccents(this)
    }

    // --- unlinked scans -------------------------------------------------------------

    /** Re-reads the saved sheet of every unmatched scan and links the ones that
     *  now match (see ClassLinker.recheckPending). Off the main thread. */
    private fun recheckIds() {
        val working = AlertDialog.Builder(this)
            .setTitle("Re-checking IDs…")
            .setMessage("Reading the saved sheets again. This takes a few seconds.")
            .setCancelable(false)
            .create()
        working.show()
        DialogColors.fix(this, working)
        val appContext = applicationContext
        val test = testName
        Thread {
            val summary = try { ClassLinker.recheckPending(appContext, test, rereadImages = true) } catch (e: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                working.dismiss()
                rebuild()
                val text = when {
                    summary == null -> "Couldn't re-check right now. Try again."
                    summary.linked == 0 && summary.remaining == 0 -> "Nothing left to check."
                    summary.linked == 0 -> "No more sheets could be matched confidently. ${summary.remaining} still need your tap." +
                        if (summary.noImage > 0) "\n\n${summary.noImage} of them have no ID read on file (they were scanned before this version)." else ""
                    else -> "Matched ${summary.linked} more sheet${if (summary.linked == 1) "" else "s"} to learners. ${summary.remaining} still need your tap."
                }
                val done = AlertDialog.Builder(this)
                    .setTitle("Re-check finished")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .show()
                DialogColors.fix(this, done)
            }
        }.start()
    }

    /** After the teacher confirms one sheet, the learner now counts as scored,
     *  which can settle other near-ties. Runs quietly; only speaks up if it
     *  linked something. */
    private fun quietRecheck() {
        val appContext = applicationContext
        val test = testName
        Thread {
            val summary = try { ClassLinker.recheckPending(appContext, test, rereadImages = false) } catch (e: Exception) { null }
            if (summary != null && summary.linked > 0) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    rebuild()
                    Toast.makeText(this, "Also matched ${summary.linked} more sheet${if (summary.linked == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun choosePendingLearner(p: PendingLink) {
        val test = TestStorage.loadTestOrNull(this, testName)
        val digits = test?.template?.idDigits ?: 0
        if (test == null || digits <= 0) return
        val classes = ClassStorage.getClasses(this, test.classIds)
        val candidates = p.candidateIds.mapNotNull { learnerId -> ClassStorage.findLearner(classes, learnerId) }
        val all = classes.flatMap { c -> c.learners.map { c to it } }.sortedBy { it.second.name.lowercase() }
        // The handwritten ID, cut from the saved sheet image (if there is one).
        val strip = p.scanId?.let { id ->
            ScannedSheetStorage.loadScan(this, id)?.let { bmp ->
                try { IdCompareDialog.makeStrip(bmp, digits) } finally { bmp.recycle() }
            }
        }
        IdCompareDialog.show(
            this, "Whose sheet is this? (${p.earnedPoints} / ${p.totalPoints})",
            strip, p.readDigits, digits, candidates, all
        ) { c, l -> assign(p, c, l) }
    }

    private fun assign(p: PendingLink, cls: LearnerClass, learner: Learner) {
        val existing = ClassLinker.resolvePending(this, p, cls.id, cls.name, learner.learnerId, replaceIfExists = false)
        if (existing == null) {
            Toast.makeText(this, "Saved to ${learner.name}", Toast.LENGTH_SHORT).show()
            rebuild()
            if (LearnerResultStorage.listPending(this, testName).isNotEmpty()) quietRecheck()
            return
        }
        ClassUi.confirm(
            this, "${learner.name} already has a score",
            "This exam already has ${existing.earnedPoints} / ${existing.totalPoints} for ${learner.name}. Replace it with ${p.earnedPoints} / ${p.totalPoints}?",
            "Replace"
        ) {
            ClassLinker.resolvePending(this, p, cls.id, cls.name, learner.learnerId, replaceIfExists = true)
            rebuild()
        }
    }

    private fun confirmRemoveScore(learner: Learner, r: LearnerResult) {
        ClassUi.confirm(
            this, "Remove ${learner.name}'s score?",
            "This removes ${r.earnedPoints} / ${r.totalPoints} for ${learner.name} on \"$testName\". The scanned sheet image is not deleted.",
            "Remove"
        ) {
            LearnerResultStorage.removeResult(this, testName, learner.learnerId)
            rebuild()
        }
    }

    // --- export ---------------------------------------------------------------------

    private fun exportXlsx(cls: LearnerClass, results: Map<String, LearnerResult>) {
        val rows = sortedLearners(cls.learners, results).map { l ->
            val r = results[l.learnerId]
            ClassSheetXlsxExporter.Row(l.learnerId, l.name, l.gender, r?.earnedPoints, r?.totalPoints)
        }
        val bytes = ClassSheetXlsxExporter.build(cls.name, testName, rows)
        val fileName = "Scores_${cls.name}_${testName}.xlsx".replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, fileName)
        file.writeBytes(bytes)
        FileExportPrompt.show(
            activity = this,
            title = "Class scores ready",
            fileName = fileName,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workingFile = file,
            shareTitle = "Share class scores",
            onDownloadComplete = { file.delete() }
        )
        if (DownloadNotifier.needsPermissionRequest(this)) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
