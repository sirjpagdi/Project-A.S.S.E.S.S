package com.jpagdi.mcqquickcheck

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Exports everything TestStorage and ScannedSheetStorage know about into one
 * JSON file, then hands it to FileExportPrompt (v3.2 — previously went
 * straight to a Share-only intent with no Download option) — same
 * Share/Download choice every other export flow in this app already gives.
 *
 * Format is deliberately simple and versioned ("backupFormatVersion") so
 * RestoreDataActivity can tell an old backup file from a new one if this
 * format ever needs to change later.
 */
class BackupDataActivity : AppCompatActivity() {

    // v3.2: see ItemAnalysisActivity/GenerateSheetActivity's identical
    // property for why this is a no-op result handler.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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

        val testCount = TestStorage.listTestNames(this).size
        val scanCount = ScannedSheetStorage.listAllScans(this).size

        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Backup Data"
        // v1.2.0: enrolled classes (learner names, IDs, genders) and per-exam
        // learner scores travel in the backup too — worth saying out loud,
        // since the file then contains real learner information.
        val classCount = ClassStorage.listClasses(this).size
        val classNote = if (classCount > 0) {
            " It also includes your $classCount enrolled class${if (classCount == 1) "" else "es"} with learner names, IDs and scores, so keep the file private."
        } else ""
        findViewById<TextView>(R.id.tvPlaceholderBody).text =
            "Exports all saved tests ($testCount) and scanned sheets ($scanCount) into one file you can save to Drive, email, or keep as a copy.$classNote"

        // Reuses the placeholder layout's single button as "Export" — this
        // screen is otherwise the same shape (title + body + one action) as
        // the other drawer destinations, so no need for a dedicated layout.
        // v3.7.6: switched from maroon to gold group per explicit request.
        // The shared activity_placeholder.xml tags this button
        // accent_maroon by default (About still wants maroon for its own
        // buttons on the same shared layout), so a plain XML tag change
        // isn't possible here — this re-tags and re-tints it in Kotlin
        // instead, AFTER AccentColorPrefs.applyAccents() already ran
        // (which tinted it maroon per the XML default). Manually
        // replicates AccentColorPrefs.tintSurface()'s exact Button
        // branch (backgroundTintList + textColorFor for contrast) rather
        // than calling applyAccents() a second time, which would re-walk
        // the entire screen for one button. The icon's tint is computed
        // the same way — centerIconAndText's earlier calls all hardcoded
        // Color.WHITE, which only happened to be correct because maroon
        // is always dark; gold can be light, so the icon needs the same
        // textColorFor() contrast logic as the text, not a fixed color.
        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            text = "Export Backup"
            tag = "accent_gold"
            val accent = AccentColorPrefs.getGroupB(this@BackupDataActivity)
            val contrastColor = AccentColorPrefs.textColorFor(accent)
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
            setTextColor(contrastColor)
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@BackupDataActivity, R.drawable.ic_cloud_upload),
                contrastColor
            )
            setOnClickListener { exportBackup() }
        }
        findViewById<Button>(R.id.btnPlaceholderSecondary).apply {
            visibility = android.view.View.VISIBLE
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
    }

    /**
     * v3.4 FIX — real device crash (OutOfMemoryError, growth limit
     * 268435456 = 256MB), stack trace confirmed the OOM happened inside
     * JSONObject.toString() at the OLD line "val bytes = root.toString()...".
     * That call needed a SINGLE contiguous String holding every scan's
     * base64-encoded image simultaneously — JSONStringer builds this via a
     * StringBuilder that DOUBLES its internal char array as it grows, and
     * right before the final Arrays.copyOf, the old and new buffers briefly
     * coexist, so peak memory during that copy can spike to roughly 2x the
     * final string's size — on top of every scan's own already-decoded
     * base64 String still being held by the scansArray JSONArray it came
     * from. This wasn't purely a "too many scans" problem — it's what
     * removing the old 60-scan cap (a prior version) made newly reachable,
     * since nothing before this fix bounded how much of the backup could
     * be resident in memory at once.
     *
     * Fix: write the JSON incrementally to a BufferedWriter instead of
     * building one JSONObject tree and serializing it in one shot. Peak
     * memory during writing now only ever holds ONE scan's data at a time
     * (source bitmap decode -> downscale -> re-encode -> write -> discard),
     * never the whole backup. org.json has no streaming writer, so this is
     * written by hand — JSONObject.quote(String) (a public static method
     * already in the same org.json library this file imports) is used for
     * every string value instead of hand-rolled escaping, so the output
     * stays byte-compatible with what JSONObject.toString() would have
     * produced (RestoreDataActivity's org.json.JSONObject(raw) parse is
     * completely unchanged by this — it has no idea whether the file it's
     * reading was built by JSONObject.toString() or written by hand, only
     * that it's valid JSON in the same shape).
     *
     * Per explicit request: scan images are downscaled to
     * ScannedSheetStorage-equivalent 2000px here too, not just on new
     * saves — an old scan saved before the downscale-on-save fix existed
     * is still full-resolution in storage, and re-encoding it down while
     * streaming keeps peak per-scan memory bounded AND shrinks the backup
     * file/restore memory footprint, instead of carrying old scans through
     * export at their original (often 3000-4000px+) resolution forever.
     */
    private fun exportBackup() {
        val testNames = TestStorage.listTestNames(this)
        // v1.2.0: enrolled classes are worth backing up even before the first
        // exam exists (roster first, exam second is the intended order).
        if (testNames.isEmpty() && ClassStorage.listClasses(this).isEmpty()) {
            Toast.makeText(this, "No saved tests to back up yet", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val fileName = "mcq_quick_check_backup_$timestamp.json"
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)

        try {
            file.bufferedWriter().use { w ->
                w.write("{")
                w.write("\"backupFormatVersion\":4,")
                w.write("\"exportedAtMillis\":${System.currentTimeMillis()},")

                val defaults = SettingsPrefs.getDefaults(this)
                w.write("\"settings\":{")
                w.write("\"paperLayout\":${JSONObject.quote(defaults.paperLayout.name)},")
                w.write("\"choiceCount\":${defaults.choiceCount},")
                w.write("\"questionCount\":${defaults.questionCount},")
                w.write("\"columns\":${defaults.columns},")
                w.write("\"questionsPerColumn\":${defaults.questionsPerColumn},")
                w.write("\"autosaveScan\":${defaults.autosaveScannedSheet},")
                w.write("\"cameraSoundEnabled\":${defaults.cameraSoundEnabled},")
                w.write("\"paperSize\":${JSONObject.quote(SettingsPrefs.getPaperSizePreference(this).name)},")
                w.write("\"welcomeAnimationEnabled\":${SettingsPrefs.isWelcomeAnimationEnabled(this)},")
                // v1.0.0: two settings that existed for several versions
                // but were never actually included here — storage
                // resolution (added v3.6.1) and font size (added v3.9.9)
                // both live in their own standalone getter/setter pairs
                // outside SettingsPrefs.Defaults (see their own doc
                // comments), which is exactly why they were missed: this
                // block was only ever updated in step with Defaults'
                // fields, not with every standalone setting elsewhere in
                // the app. Same guarded-restore pattern as paperSize/
                // welcomeAnimationEnabled below in RestoreDataActivity.
                w.write("\"storageResolutionPx\":${SettingsPrefs.getStorageResolutionPx(this)},")
                w.write("\"fontScale\":${JSONObject.quote(FontSizePrefs.getFontScale(this).name)},")
                // v1.1.0: same standalone-setting gap as storageResolutionPx/
                // fontScale above — RestoreDataActivity already guards this
                // with .has() for backups exported before this line existed.
                w.write("\"parentSignatureLineEnabled\":${SettingsPrefs.isParentSignatureLineEnabled(this)},")
                // v1.2.0: Classes feature switch + ID digit count. Optional on
                // restore (.has()-guarded), like every setting added after this
                // block first shipped.
                w.write("\"classesEnabled\":${SettingsPrefs.isClassesEnabled(this)},")
                w.write("\"idDigits\":${SettingsPrefs.getIdDigits(this)}")
                w.write("},")
                w.write("\"theme\":${JSONObject.quote(ThemePrefs.getTheme(this).name)},")

                w.write("\"accentColors\":{")
                w.write("\"groupA\":${AccentColorPrefs.getGroupA(this)},")
                w.write("\"groupB\":${AccentColorPrefs.getGroupB(this)}")
                w.write("},")

                w.write("\"tests\":[")
                testNames.forEachIndexed { i, name ->
                    val test = TestStorage.loadTestOrNull(this, name) ?: return@forEachIndexed
                    if (i > 0) w.write(",")
                    val t = test.template
                    w.write("{")
                    w.write("\"testName\":${JSONObject.quote(t.testName)},")
                    w.write("\"questionCount\":${t.questionCount},")
                    w.write("\"choiceCount\":${t.choiceCount},")
                    w.write("\"numbering\":${JSONObject.quote(t.numbering.name)},")
                    w.write("\"rowsPerBlock\":${t.rowsPerBlock},")
                    w.write("\"maxBlocksPerRowGroup\":${t.maxBlocksPerRowGroup},")
                    w.write("\"bubbleSizeMm\":${t.bubbleSizeMm.toDouble()},")
                    w.write("\"paperLayout\":${JSONObject.quote(test.paperLayout.name)},")
                    w.write("\"answerKey\":[${test.answerKey.joinToString(",")}],")
                    w.write("\"points\":[${test.points.joinToString(",")}],")
                    // v1.1.1: only written when configured — same
                    // optional-field pattern as TestStorage's own
                    // toJson(), so a restore on an older RestoreDataActivity
                    // build (pre-competencies) just ignores this key via
                    // its own JSONObject-tree tolerance of unknown fields.
                    if (test.competencies.isNotEmpty()) {
                        val compJson = test.competencies.joinToString(",") { comp ->
                            "{\"name\":${JSONObject.quote(comp.name)},\"items\":[${comp.itemNumbers.sorted().joinToString(",")}]}"
                        }
                        w.write("\"competencies\":[$compJson],")
                    }
                    // v1.2.0 (Classes): ID-row digit count and assigned class ids,
                    // each written only when set — absent on every older test.
                    if (t.idDigits > 0) w.write("\"idDigits\":${t.idDigits},")
                    // v1.2.5 (Test sets): set count and the keys of sets 2..4,
                    // each written only when the test uses sets.
                    if (t.setCount > 1) {
                        w.write("\"setCount\":${t.setCount},")
                        val setKeysJson = SetKeyStore.all(this, t.testName).entries.joinToString(",") { (idx, k) ->
                            "\"$idx\":[${k.joinToString(",")}]"
                        }
                        w.write("\"setKeys\":{$setKeysJson},")
                        val setCompsJson = SetCompetencyStore.all(this, t.testName).entries.joinToString(",") { (idx, list) ->
                            "\"$idx\":${SetCompetencyStore.encode(list)}"
                        }
                        w.write("\"setCompetencies\":{$setCompsJson},")
                    }
                    if (test.classIds.isNotEmpty()) {
                        w.write("\"classIds\":[${test.classIds.joinToString(",") { JSONObject.quote(it) }}],")
                    }
                    val folderNames = ScannedSheetStorage.listFoldersForTest(this, t.testName)
                        .filter { it != ScannedSheetStorage.MAIN_FOLDER }
                    w.write("\"folders\":[${folderNames.joinToString(",") { JSONObject.quote(it) }}]")
                    w.write("}")
                }
                w.write("],")

                // v1.2.0 (Classes): rosters (learner names/IDs/gender) and the
                // per-exam learner scores. Local file only — this is the one
                // place roster data ever leaves the app's private storage, and
                // only because the teacher chose to export a backup.
                w.write("\"classes\":${ClassStorage.exportAll(this)},")
                w.write("\"learnerResults\":${LearnerResultStorage.exportAll(this)},")

                w.write("\"scans\":[")
                val allScans = ScannedSheetStorage.listAllScans(this)
                allScans.forEachIndexed { i, scan ->
                    val rawEncoded = ScannedSheetStorage.getRawEncoded(this, scan.id)
                    val metaJson = ScannedSheetStorage.getRawMeta(this, scan.id)
                    if (rawEncoded == null || metaJson == null) return@forEachIndexed
                    if (i > 0) w.write(",")

                    // Decode -> downscale -> re-encode ONE scan at a time,
                    // discarding the bitmap immediately after — this is the
                    // part that keeps peak memory bounded regardless of how
                    // many hundreds of scans a test has. Falls back to the
                    // original encoded string if decode/recompress fails
                    // for any reason (e.g. a corrupt entry) rather than
                    // dropping the scan from the backup entirely.
                    val exportEncoded = try {
                        val bytes = Base64.decode(rawEncoded, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val downscaled = ScannedSheetStorage.downscaleForExport(this@BackupDataActivity, bitmap)
                            val stream = ByteArrayOutputStream()
                            downscaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
                        } else {
                            rawEncoded
                        }
                    } catch (e: Exception) {
                        rawEncoded
                    }

                    w.write("{")
                    w.write("\"id\":${JSONObject.quote(scan.id)},")
                    w.write("\"meta\":$metaJson,")
                    w.write("\"imageBase64\":${JSONObject.quote(exportEncoded)}")
                    w.write("}")
                }
                w.write("]")
                w.write("}")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Backup export failed: ${e.message}", Toast.LENGTH_LONG).show()
            file.delete()
            return
        }

        // v3.9.x FIX — second real-device OOM crash on the SAME feature,
        // this time past the v3.4 streaming-write fix above: the crash
        // trace pointed here, at what used to be `val bytes =
        // file.readBytes()` right before FileExportPrompt.show(). That
        // line needed the ENTIRE finished backup file in one contiguous
        // ByteArray just to hand it to the Download button — undoing the
        // whole point of streaming the write above once the file was
        // large enough (many hundreds of scans, even downscaled). Removed
        // entirely: FileExportPrompt.show() no longer takes a `bytes`
        // param at all — its Download path now streams straight from
        // `file` on disk (see FileExportPrompt's own doc comment), so
        // peak memory here is back to whatever the streaming write above
        // already used, not file-size-dependent on top of that.
        FileExportPrompt.show(
            activity = this,
            title = "Backup ready",
            fileName = fileName,
            mimeType = "application/json",
            workingFile = file,
            shareTitle = "Share backup file",
            onDownloadComplete = { file.delete() }
        )
        if (DownloadNotifier.needsPermissionRequest(this)) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
