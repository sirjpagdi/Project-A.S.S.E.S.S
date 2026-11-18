package com.jpagdi.mcqquickcheck

import android.net.Uri
import android.os.Bundle
import android.util.JsonReader
import android.util.JsonToken
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * Reads a backup JSON file produced by BackupDataActivity and writes its
 * tests/scans back into TestStorage / ScannedSheetStorage. Uses
 * ACTION_OPEN_DOCUMENT (system file picker) rather than requiring the file
 * to sit in a specific app folder — the teacher may have moved it to Drive,
 * Downloads, wherever, after exporting on a different day or device.
 *
 * v1.1.0 FIX — real-device crash importing over already-saved data (same
 * OOM shape as BackupDataActivity/ScannedSheetExporter's own fixes — see
 * their doc comments): the OLD restoreFrom() did
 * `contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }`
 * then `JSONObject(raw)` — a large backup meant holding the ENTIRE file as
 * one String, THEN a full org.json parse tree with every scan's base64
 * image string resident simultaneously on top of that, before a single
 * test or scan was actually restored. This now reads with android.util.
 * JsonReader instead, token by token, converting only ONE test or ONE
 * scan object into a small org.json JSONObject at a time (see
 * readJsonValue below) — the exact same per-test/per-scan parsing logic
 * as before runs on that one small object, then it's discarded before the
 * next one is read, so peak memory is roughly one scan's worth, not the
 * whole file, regardless of backup size.
 *
 * Also replaces the old per-test-name "Overwrite existing tests?" prompt
 * with an unconditional "Clear Data and Continue?" prompt shown whenever
 * ANY test or scan already exists locally — restoring used to try to
 * merge onto whatever was already saved, which is the other half of what
 * was crashing/misbehaving on "import over already-saved data": stale
 * folder entries, partially-overwritten tests, etc. Confirming now fully
 * clears TestStorage + ScannedSheetStorage + this app's own scratch cache
 * first, THEN imports, so every restore starts from a guaranteed-clean
 * slate instead of a merge.
 */
class RestoreDataActivity : AppCompatActivity() {

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) validateThenPrompt(uri)
    }

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

        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "Restore Data"
        findViewById<TextView>(R.id.tvPlaceholderBody).text =
            "Choose a backup file exported from Backup Data. If you already have saved tests or scans, you'll be asked to clear them first so the restore starts clean."

        // v3.7.6: same maroon-to-gold switch and manual re-tint as
        // BackupDataActivity's Export Backup button — see that file's
        // comment for the full reasoning (shared XML default is
        // accent_maroon, About still needs maroon on the same layout).
        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            text = "Choose Backup File"
            tag = "accent_gold"
            val accent = AccentColorPrefs.getGroupB(this@RestoreDataActivity)
            val contrastColor = AccentColorPrefs.textColorFor(accent)
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
            setTextColor(contrastColor)
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@RestoreDataActivity, R.drawable.ic_cloud_download),
                contrastColor
            )
            setOnClickListener { pickFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
        findViewById<Button>(R.id.btnPlaceholderSecondary).apply {
            visibility = android.view.View.VISIBLE
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
    }

    /** Cheap first pass — confirms the file actually has a "tests" key
     *  before anything local gets cleared. Streams through every other
     *  top-level value with skipValue() rather than parsing them, so this
     *  never holds more than one token's worth of the file in memory even
     *  though it does read through the whole stream (including "scans")
     *  to get there — same bounded-memory shape as the real import pass
     *  below, just discarding everything instead of storing it. */
    private fun validateThenPrompt(uri: Uri) {
        val looksLikeBackup = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var found = false
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "tests") found = true
                        reader.skipValue()
                    }
                    reader.endObject()
                    found
                }
            } ?: false
        } catch (e: Exception) {
            false
        }

        if (!looksLikeBackup) {
            Toast.makeText(this, "That doesn't look like a valid backup file", Toast.LENGTH_SHORT).show()
            return
        }

        val hasExistingData = TestStorage.listTestNames(this).isNotEmpty() ||
            ScannedSheetStorage.listAllScans(this).isNotEmpty() ||
            ClassStorage.listClasses(this).isNotEmpty()

        if (!hasExistingData) {
            applyRestore(uri)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear Data and Continue?")
            .setMessage(
                "This will clear currently saved data to avoid conflicts during import — every saved test, scanned sheet and class roster on this device will be removed first. This can't be undone."
            )
            .setPositiveButton("Clear Data and Continue") { _, _ ->
                TestStorage.clearAll(this)
                ScannedSheetStorage.clearAll(this)
                ClassStorage.clearAll(this)
                LearnerResultStorage.clearAll(this)
                CacheSweeper.sweepNow(this)
                applyRestore(uri)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { it.show(); fixDialogButtonColors(it) }
    }

    /** Converts ONE JSON value (object, array, or primitive) under the
     *  reader's current position into the equivalent org.json type —
     *  JSONObject/JSONArray/String/Long/Double/Boolean/JSONObject.NULL.
     *  Only ever called on a single test object, a single scan object, or
     *  one of the small top-level blocks (settings/accentColors) — never
     *  on the whole file — which is what keeps this streaming rewrite's
     *  memory bounded (see this file's class-level doc comment). Letting
     *  each caller get back a familiar JSONObject means the existing
     *  per-test/per-scan/settings parsing logic below is otherwise
     *  unchanged from before this rewrite — it has no idea whether its
     *  `obj` came from a full parse tree or one token-stream read. */
    private fun readJsonValue(reader: JsonReader): Any {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    obj.put(reader.nextName(), readJsonValue(reader))
                }
                reader.endObject()
                obj
            }
            JsonToken.BEGIN_ARRAY -> {
                val arr = JSONArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    arr.put(readJsonValue(reader))
                }
                reader.endArray()
                arr
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                // nextString() on a NUMBER token returns its literal text
                // (documented JsonReader behavior) — parsed by hand here
                // instead of nextDouble() so an integer like savedAtMillis
                // round-trips as a Long, not a Double, same as it would
                // have via org.json's own number parsing on the old path.
                val text = reader.nextString()
                if (text.contains('.') || text.contains('e') || text.contains('E')) {
                    text.toDouble()
                } else {
                    text.toLongOrNull() ?: text.toDouble()
                }
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> { reader.skipValue(); JSONObject.NULL }
        }
    }

    private fun applyRestore(uri: Uri) {
        var testsRestored = 0
        // v3.4: counts tests skipped because their backed-up paperLayout
        // was HALF_CROSS/HALF_LENGTH (removed) — see the skip site below.
        var testsSkippedLegacyLayout = 0
        var scansRestored = 0

        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "tests" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val obj = readJsonValue(reader) as? JSONObject ?: continue
                                    try {
                                        val template = SheetTemplate(
                                            testName = obj.getString("testName"),
                                            questionCount = obj.getInt("questionCount"),
                                            choiceCount = obj.getInt("choiceCount"),
                                            numbering = NumberingOrder.valueOf(obj.getString("numbering")),
                                            rowsPerBlock = obj.optInt("rowsPerBlock", 10),
                                            maxBlocksPerRowGroup = obj.optInt("maxBlocksPerRowGroup", 3),
                                            bubbleSizeMm = obj.optDouble("bubbleSizeMm", BubbleSizePrefs.DEFAULT_MM.toDouble()).toFloat(),
                                            idDigits = obj.optInt("idDigits", 0).let { if (it == 0) 0 else SheetTemplate.clampIdDigits(it) },
                                            setCount = SheetTemplate.clampSetCount(obj.optInt("setCount", 1))
                                        )
                                        val keyArray = obj.getJSONArray("answerKey")
                                        val key = (0 until keyArray.length()).map { keyArray.getInt(it) }
                                        val points = if (obj.has("points")) {
                                            val pointsArray = obj.getJSONArray("points")
                                            (0 until pointsArray.length()).map { pointsArray.getInt(it) }
                                        } else {
                                            List(key.size) { 1 }
                                        }
                                        // v3.4: skip a test whose backed-up paperLayout is one of
                                        // PaperLayout.LEGACY_LAYOUT_NAMES (HALF_CROSS/HALF_LENGTH,
                                        // removed) rather than letting fromStorageName's fallback
                                        // silently create it as a QUARTER test — see TestStorage.
                                        // LegacyLayoutException's doc comment for the LOAD-side
                                        // equivalent of this same guard.
                                        val storedLayoutName = obj.optString("paperLayout", null)
                                        if (storedLayoutName != null && storedLayoutName in PaperLayout.LEGACY_LAYOUT_NAMES) {
                                            testsSkippedLegacyLayout++
                                            continue
                                        }
                                        val layout = PaperLayout.fromStorageName(obj.optString("paperLayout"))
                                        // v1.1.1: same .has()-guarded optional pattern as
                                        // "points" above — absent on any backup made before
                                        // this feature existed, or any test that never used it.
                                        val competencies = if (obj.has("competencies")) {
                                            val compArray = obj.getJSONArray("competencies")
                                            (0 until compArray.length()).map { ci ->
                                                val compObj = compArray.getJSONObject(ci)
                                                val itemsArray = compObj.getJSONArray("items")
                                                Competency(
                                                    name = compObj.getString("name"),
                                                    itemNumbers = (0 until itemsArray.length()).map { itemsArray.getInt(it) }.toSet()
                                                )
                                            }
                                        } else {
                                            emptyList()
                                        }
                                        // v1.2.0: assigned class ids — optional, absent before Classes.
                                        val classIds = if (obj.has("classIds")) {
                                            val idArray = obj.getJSONArray("classIds")
                                            (0 until idArray.length()).map { idArray.getString(it) }
                                        } else {
                                            emptyList()
                                        }
                                        TestStorage.saveTest(this, SavedTest(template, key, layout, points, competencies, classIds))
                                        // v1.2.5: keys of sets 2..4 (absent on older backups).
                                        val restoredSetKeys = linkedMapOf<Int, List<Int>>()
                                        obj.optJSONObject("setKeys")?.let { sk ->
                                            sk.keys().forEach { idxStr ->
                                                val idx = idxStr.toIntOrNull() ?: return@forEach
                                                val arr = sk.optJSONArray(idxStr) ?: return@forEach
                                                restoredSetKeys[idx] = (0 until arr.length()).map { arr.getInt(it) }
                                            }
                                        }
                                        SetKeyStore.replaceAll(this, template.testName, restoredSetKeys)
                                        val restoredSetComps = linkedMapOf<Int, List<Competency>>()
                                        obj.optJSONObject("setCompetencies")?.let { sc ->
                                            sc.keys().forEach { idxStr ->
                                                val idx = idxStr.toIntOrNull() ?: return@forEach
                                                val arr = sc.optJSONArray(idxStr) ?: return@forEach
                                                restoredSetComps[idx] = SetCompetencyStore.decode(arr.toString())
                                            }
                                        }
                                        SetCompetencyStore.replaceAll(this, template.testName, restoredSetComps)
                                        testsRestored++
                                        // v3.2.1: re-create any folders this test had, so a folder
                                        // that was created but never scanned into yet (no scan
                                        // metadata to imply its existence) still comes back.
                                        if (obj.has("folders")) {
                                            val foldersArray = obj.getJSONArray("folders")
                                            for (fi in 0 until foldersArray.length()) {
                                                ScannedSheetStorage.addFolder(this, template.testName, foldersArray.getString(fi))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Skip a malformed single entry rather than aborting the
                                        // whole restore over one bad test.
                                    }
                                }
                                reader.endArray()
                            }
                            "classes" -> {
                                try {
                                    (readJsonValue(reader) as? JSONArray)?.let { ClassStorage.importAll(this, it) }
                                } catch (e: Exception) {
                                    // Skip a malformed classes block rather than abort the restore.
                                }
                            }
                            "learnerResults" -> {
                                try {
                                    (readJsonValue(reader) as? JSONObject)?.let { LearnerResultStorage.importAll(this, it) }
                                } catch (e: Exception) {
                                    // Same policy.
                                }
                            }
                            "scans" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val obj = readJsonValue(reader) as? JSONObject ?: continue
                                    try {
                                        val imageBase64 = obj.getString("imageBase64")
                                        if (obj.has("meta")) {
                                            // v2 format: id + a full meta JSON object.
                                            val id = obj.getString("id")
                                            ScannedSheetStorage.putRawEncoded(this, id, imageBase64)
                                            ScannedSheetStorage.putRawMeta(this, id, obj.getJSONObject("meta").toString())
                                        } else {
                                            // v1 format (backupFormatVersion 1): flat "key" +
                                            // "testName" + "savedAtMillis", no folder/score
                                            // fields at all. Synthesize an equivalent meta JSON
                                            // so an old backup file still restores correctly.
                                            val id = obj.getString("key")
                                            val meta = JSONObject().apply {
                                                put("testName", obj.optString("testName"))
                                                put("folder", ScannedSheetStorage.MAIN_FOLDER)
                                                put("savedAtMillis", obj.optLong("savedAtMillis"))
                                            }
                                            ScannedSheetStorage.putRawEncoded(this, id, imageBase64)
                                            ScannedSheetStorage.putRawMeta(this, id, meta.toString())
                                        }
                                        scansRestored++
                                    } catch (e: Exception) {
                                        // Same as above — skip one bad scan entry, keep going.
                                    }
                                }
                                reader.endArray()
                            }
                            "settings" -> {
                                try {
                                    val s = readJsonValue(reader) as JSONObject
                                    val defaults = SettingsPrefs.Defaults(
                                        paperLayout = PaperLayout.fromStorageName(s.optString("paperLayout")),
                                        choiceCount = s.optInt("choiceCount", 4),
                                        questionCount = s.optInt("questionCount", 25),
                                        columns = s.optInt("columns", 3),
                                        questionsPerColumn = s.optInt("questionsPerColumn", 10),
                                        autosaveScannedSheet = s.optBoolean("autosaveScan", false),
                                        cameraSoundEnabled = s.optBoolean("cameraSoundEnabled", true)
                                    )
                                    SettingsPrefs.saveDefaults(this, defaults)
                                    if (s.has("paperSize")) {
                                        SettingsPrefs.setPaperSizePreference(this, PaperSize.fromNameOrDefault(s.optString("paperSize")))
                                    }
                                    if (s.has("welcomeAnimationEnabled")) {
                                        SettingsPrefs.setWelcomeAnimationEnabled(this, s.getBoolean("welcomeAnimationEnabled"))
                                    }
                                    if (s.has("storageResolutionPx")) {
                                        SettingsPrefs.setStorageResolutionPx(this, s.getInt("storageResolutionPx"))
                                    }
                                    if (s.has("fontScale")) {
                                        val scale = FontScale.values().firstOrNull { it.name == s.optString("fontScale") }
                                        if (scale != null) FontSizePrefs.setFontScale(this, scale)
                                    }
                                    // v1.1.0: parent/guardian signature line toggle —
                                    // guarded with .has() the same way as every other
                                    // setting added after this block first shipped, so
                                    // an older backup that predates it just leaves
                                    // whatever's currently set untouched.
                                    if (s.has("parentSignatureLineEnabled")) {
                                        SettingsPrefs.setParentSignatureLineEnabled(this, s.getBoolean("parentSignatureLineEnabled"))
                                    }
                                    // v1.2.0: Classes switch + ID digit count (guarded like the rest).
                                    if (s.has("classesEnabled")) {
                                        SettingsPrefs.setClassesEnabled(this, s.getBoolean("classesEnabled"))
                                    }
                                    if (s.has("idDigits")) {
                                        SettingsPrefs.setIdDigits(this, s.getInt("idDigits"))
                                    }
                                } catch (e: Exception) {
                                    // Skip settings restore rather than aborting the whole
                                    // restore over one malformed block.
                                }
                            }
                            "theme" -> {
                                try {
                                    val themeName = reader.nextString()
                                    val theme = AppTheme.values().firstOrNull { it.name == themeName }
                                    if (theme != null) ThemePrefs.setTheme(this, theme)
                                } catch (e: Exception) {
                                    reader.skipValue()
                                }
                            }
                            "accentColors" -> {
                                try {
                                    val a = readJsonValue(reader) as JSONObject
                                    if (a.has("groupA")) AccentColorPrefs.setGroupA(this, a.getInt("groupA"))
                                    if (a.has("groupB")) AccentColorPrefs.setGroupB(this, a.getInt("groupB"))
                                } catch (e: Exception) {
                                    // Same policy — skip rather than abort.
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            } ?: run {
                Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val legacyNote = if (testsSkippedLegacyLayout > 0) {
            " ($testsSkippedLegacyLayout test(s) skipped — old paper layout no longer supported)"
        } else ""
        Toast.makeText(this, "Restored $testsRestored test(s) and $scansRestored scanned sheet(s)$legacyNote", Toast.LENGTH_LONG).show()
        finish()
    }

    /** Same fix as MainActivity.fixDialogButtonColors — AlertDialog buttons
     *  default to colorPrimary as text color, unreadable in Dark/Black
     *  themes since those themes never set android:textColorPrimary. */
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
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
