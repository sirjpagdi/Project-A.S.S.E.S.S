package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Stores scanned sheet images (the final annotated result, same bitmap
 * shown in ScanActivity's preview) so they can be reviewed later. Only
 * active when SettingsPrefs.isAutosaveScannedSheetEnabled is true —
 * ScanActivity calls saveScan() itself right when a graded result is shown.
 *
 * Images are stored as base64-encoded JPEG strings directly in
 * SharedPreferences, matching the rest of this app's "SharedPreferences +
 * JSON only, no backend, no file-based DB" architecture rather than writing
 * to app-private file storage. This is heavier per-entry than TestStorage's
 * small JSON blobs.
 *
 * v3.3: there is deliberately NO cap on scans-per-test anymore. An earlier
 * version silently stopped saving past 60 scans (oldest-first eviction) to
 * bound SharedPreferences growth — but that meant a full class set (often
 * 100+ students) would quietly lose its earliest scans, which defeats the
 * entire point of a class-wide Test Summary / Item Analysis. Removing the
 * cap trades that safety margin for correctness: SharedPreferences has no
 * hard document-size limit on Android, but it does load and hold its whole
 * backing XML file in memory, so a test with hundreds of full-resolution
 * JPEGs autosaved will use more memory and take longer to read than a small
 * one. If this ever becomes a real problem in practice, the fix should be
 * moving scan images to app-private file storage (one file per scan) rather
 * than reintroducing a silent cap that hides data.
 *
 * Metadata (folder, score, timestamp) is stored as a small JSON object
 * under its own key ("meta_$id"), separate from the image blob ("scan_$id")
 * — a folder name can contain characters like underscores that would have
 * broken the old scheme of parsing fields out of the storage key itself, and
 * this also means reading metadata for a list screen never has to touch the
 * much larger base64 image payload.
 */
/** Sort options for the Scanned Sheets screen — persisted globally via
 *  SettingsPrefs.getScanSortOrder/setScanSortOrder, not applied inside
 *  listScansForTest itself (that function's own default date-descending
 *  sort stays as-is for every other caller, e.g. Test Summary/Item
 *  Analysis don't care about display order). */
enum class ScanSortOrder(val label: String) {
    DATE_DESC("Date (newest first)"),
    DATE_ASC("Date (oldest first)"),
    SCORE_DESC("Score (highest first)"),
    SCORE_ASC("Score (lowest first)")
}

object ScannedSheetStorage {
    private const val PREFS = "mcq_scanner_scans"

    /** v3.6.1: was a fixed 2000px constant — now a Settings-backed
     *  preference (SettingsPrefs.getStorageResolutionPx/setStorageResolutionPx),
     *  per explicit request. 2000 is still the shipped default (identical
     *  behavior to before for anyone who never opens the setting); 1800/1600
     *  are offered as smaller options for anyone who wants to trade a little
     *  legibility for less storage growth on a heavily-scanned class set.
     *  Deliberately only applied at save/export time, not to the in-memory
     *  bitmap ScanActivity shows immediately after a scan (that stays full
     *  source-capture resolution) — this only shrinks what gets written to
     *  SharedPreferences / the backup file. */

    /** Shrinks [source] to fit within the current storage-resolution
     *  preference on its long edge, preserving aspect ratio. Returns
     *  [source] itself unchanged if it's already at or under the cap —
     *  Bitmap.createScaledBitmap always allocates a new bitmap even when
     *  asked to "scale" to the same size, so skipping the call entirely
     *  when it isn't needed avoids a pointless extra full-size allocation
     *  on every save. Used by saveScan() below for new scans, AND by
     *  BackupDataActivity's exportBackup() (via the public
     *  downscaleForExport wrapper) to shrink OLD, already-stored
     *  full-resolution scans as they're streamed into a backup file — same
     *  cap, same logic, one shared implementation. v3.6.1: now reads the
     *  cap from SettingsPrefs instead of a fixed constant, so changing the
     *  preference affects both new saves AND anything backed up/exported
     *  afterward — it does NOT retroactively re-shrink scans already saved
     *  at a larger cap (they stay as they are until next exported). */
    private fun downscaleForStorage(context: Context, source: Bitmap): Bitmap {
        val maxDimension = SettingsPrefs.getStorageResolutionPx(context)
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxDimension) return source
        val scale = maxDimension.toFloat() / longEdge.toFloat()
        val newWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

    /** Public wrapper around downscaleForStorage for BackupDataActivity's
     *  streaming export — see that function's doc comment for why old,
     *  already-saved full-resolution scans are also downscaled at export
     *  time now, not just new saves. */
    fun downscaleForExport(context: Context, source: Bitmap): Bitmap = downscaleForStorage(context, source)

    /** Default folder every test starts with — always shown even when
     *  empty, per the folder feature request. Scans save here automatically
     *  when no other folder has been created for that test yet. */
    const val MAIN_FOLDER = "main"

    data class SavedScan(
        val id: String,
        val testName: String,
        val folder: String,
        val savedAtMillis: Long,
        val earnedPoints: Int? = null,
        val totalPoints: Int? = null,
        // Per-question correctness at save time: true = correct, false =
        // wrong/ambiguous, null = left blank. Added in v2.7 for Item
        // Analysis — only present on scans saved from v2.7 onward; older
        // scans have this as null (not an empty list) since the data was
        // never captured for them, and callers need to be able to tell
        // "no data exists" apart from "this test has zero questions."
        val perQuestion: List<Boolean?>? = null,
        // v3.3: 1-based question numbers that were ambiguous (multiple/faint
        // marks) at save time AND haven't been manually resolved yet by the
        // teacher. Deliberately kept as its OWN field rather than folded into
        // perQuestion — perQuestion's true/false/null meaning is already
        // relied on by Item Analysis and Test Summary exactly as-is (an
        // ambiguous question still counts as "false"/wrong there, same as
        // before, until it's resolved), and widening that type to a 4-state
        // value would have meant touching every consumer of perQuestion just
        // to support the strictly-smaller set of screens that care about
        // ambiguity specifically. Empty set (not null) once every ambiguous
        // question on a scan has been resolved; null only for scans saved
        // before this existed, where the data was never captured.
        val ambiguousQuestions: Set<Int>? = null,
        // v1.2.5 (Test sets): which set this sheet was graded as (0 = first set / no sets).
        val setIndex: Int = 0
    )

    /** Saves one scanned sheet bitmap under the given test name and folder.
     *  Returns the scan's id, or null if saving failed (e.g. image too
     *  large to encode) — callers should treat that as "couldn't autosave
     *  this one" rather than crash the scan flow over it. */
    fun saveScan(
        context: Context,
        testName: String,
        bitmap: Bitmap,
        folder: String = MAIN_FOLDER,
        earnedPoints: Int? = null,
        totalPoints: Int? = null,
        perQuestion: List<Boolean?>? = null,
        ambiguousQuestions: Set<Int>? = null,
        setIndex: Int = 0
    ): String? {
        return try {
            // v3.4: downscale to the storage-resolution cap before
            // compressing, per explicit request — this app previously
            // saved every scan at full source-capture resolution (often
            // 3000-4000px+ on the long edge), which is part of why
            // removing the old scan-count cap (see this object's doc
            // comment above) made a heavily-used test's total storage
            // grow faster than necessary. Only affects what's PERSISTED —
            // ScanActivity's in-memory preview bitmap is a separate object
            // this function never touches.
            val forStorage = downscaleForStorage(context, bitmap)
            val stream = ByteArrayOutputStream()
            // JPEG at 85% quality, not PNG — a full-resolution PNG of a
            // photo-sourced bitmap can be several MB once base64-encoded,
            // which risks a single SharedPreferences value exceeding what's
            // safe to read back reliably. JPEG at this quality is visually
            // fine for reviewing a bubble sheet and keeps entries small.
            forStorage.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val encoded = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)

            val id = "${testName}_${System.currentTimeMillis()}"
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val meta = JSONObject().apply {
                put("testName", testName)
                put("folder", folder)
                put("savedAtMillis", System.currentTimeMillis())
                if (earnedPoints != null) put("earnedPoints", earnedPoints)
                if (totalPoints != null) put("totalPoints", totalPoints)
                if (setIndex > 0) put("setIndex", setIndex)
                // Compact encoding: "1"=correct, "0"=wrong/ambiguous,
                // "-"=blank — one character per question rather than a
                // full JSONArray, since this is written to (and re-read
                // from) SharedPreferences per scan and questionCount can
                // run up to 90; a terse string keeps the per-scan entry
                // small without needing a nested array structure.
                if (perQuestion != null) {
                    val encoded2 = perQuestion.joinToString("") {
                        when (it) {
                            true -> "1"
                            false -> "0"
                            null -> "-"
                        }
                    }
                    put("perQuestion", encoded2)
                }
                // Comma-separated question numbers, e.g. "3,7,12" — only
                // written when non-empty, matching how earnedPoints/
                // totalPoints above are omitted rather than written as 0/
                // empty. readAllMeta() below treats a missing key as null
                // (pre-v3.3 scan) and an empty string as impossible to
                // produce from this write path, so there's no ambiguity.
                if (!ambiguousQuestions.isNullOrEmpty()) {
                    put("ambiguousQuestions", ambiguousQuestions.sorted().joinToString(","))
                }
            }
            prefs.edit()
                .putString("scan_$id", encoded)
                .putString("meta_$id", meta.toString())
                .apply()

            id
        } catch (e: Exception) {
            null
        }
    }

    /** v3.3: overwrites an existing scan's image and score metadata in
     *  place — used when a teacher resolves an ambiguous question from the
     *  Scanned Sheets folder, after the scan was already autosaved (see
     *  AmbiguousAnswerPanel). Preserves testName/folder/savedAtMillis from
     *  the existing entry; only the fields passed here change. Returns
     *  false if [id] doesn't already exist — this must never behave like
     *  saveScan() and silently create a new entry, since that would leave
     *  a duplicate (unresolved) copy of the same scan behind. */
    fun updateScanAfterResolution(
        context: Context,
        id: String,
        bitmap: Bitmap,
        perQuestion: List<Boolean?>,
        ambiguousQuestions: Set<Int>,
        earnedPoints: Int?,
        totalPoints: Int?
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existingRaw = prefs.getString("meta_$id", null) ?: return false
        return try {
            val meta = JSONObject(existingRaw) // keeps testName/folder/savedAtMillis as-is
            if (earnedPoints != null) meta.put("earnedPoints", earnedPoints) else meta.remove("earnedPoints")
            if (totalPoints != null) meta.put("totalPoints", totalPoints) else meta.remove("totalPoints")
            meta.put("perQuestion", perQuestion.joinToString("") {
                when (it) {
                    true -> "1"
                    false -> "0"
                    null -> "-"
                }
            })
            if (ambiguousQuestions.isEmpty()) {
                meta.remove("ambiguousQuestions")
            } else {
                meta.put("ambiguousQuestions", ambiguousQuestions.sorted().joinToString(","))
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val encoded = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)

            prefs.edit()
                .putString("scan_$id", encoded)
                .putString("meta_$id", meta.toString())
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** One-time migration for scans saved before v1.9's storage rewrite.
     *  v1.8 and earlier stored each bitmap under a single combined key,
     *  "scan_${testName}_${timestamp}" — with NO separate meta entry.
     *  v1.9's listScansForTest() only reads keys starting with "meta_", so
     *  those old entries went invisible (not deleted) the moment the app
     *  updated — this looked exactly like "my saved scans disappeared"
     *  even though nothing was removed. Call once at app startup (see
     *  MainActivity.onCreate) to synthesize the missing meta entry for any
     *  "scan_*" key that doesn't already have one, making old scans show up
     *  again under MAIN_FOLDER. Never deletes or overwrites anything —
     *  purely additive. Safe to call every launch: does nothing once every
     *  old entry already has a meta counterpart. */
    fun migrateLegacyScansIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var migratedCount = 0
        prefs.all.keys
            .filter { it.startsWith("scan_") }
            .forEach { scanKey ->
                val id = scanKey.removePrefix("scan_")
                if (prefs.contains("meta_$id")) return@forEach // already has metadata, not a legacy entry

                // Old id shape was "<testName>_<timestampMillis>". testName
                // itself can contain underscores, so only the LAST
                // underscore-separated segment is trusted as the timestamp
                // — anything before it, rejoined, is the test name.
                val lastUnderscore = id.lastIndexOf('_')
                if (lastUnderscore <= 0 || lastUnderscore == id.length - 1) return@forEach
                val testName = id.substring(0, lastUnderscore)
                val timestamp = id.substring(lastUnderscore + 1).toLongOrNull() ?: return@forEach

                val meta = JSONObject().apply {
                    put("testName", testName)
                    put("folder", MAIN_FOLDER)
                    put("savedAtMillis", timestamp)
                }
                editor.putString("meta_$id", meta.toString())
                migratedCount++
            }
        if (migratedCount > 0) editor.apply()
    }

    fun loadScan(context: Context, id: String): Bitmap? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = prefs.getString("scan_$id", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /** Crops just the Name / Section region from a saved scan, for
     *  use as a thumbnail on the saved-scans list screen. Uses the same
     *  fraction constants SheetTemplate already defines for that header
     *  area (GRID_LEFT..TABLE_MID_X_FRACTION horizontally, TABLE_TOP..
     *  TABLE_ROW2_BOTTOM vertically — the left column of the details
     *  table, excluding Date/Score on the right), so the crop region can
     *  never drift from wherever the header actually sits — it's derived
     *  from the same single source of truth SheetGenerator used to draw
     *  that header in the first place.
     *
     *  v3.4: HALF_CROSS removed, along with the special-cased crop width
     *  this function used to need for it (that layout's wide-landscape
     *  proportions made the normal fraction-of-full.width crop come out
     *  stretched edge-to-edge — QUARTER, the only layout remaining, was
     *  never affected by this and needs no special-casing). */
    fun loadNameSectionThumbnail(context: Context, id: String): Bitmap? {
        val full = loadScan(context, id) ?: return null
        return try {
            val left = (SheetTemplate.GRID_LEFT * full.width).toInt().coerceIn(0, full.width - 1)
            val top = (SheetTemplate.TABLE_TOP * full.height).toInt().coerceIn(0, full.height - 1)
            val bottom = (SheetTemplate.TABLE_ROW2_BOTTOM * full.height).toInt().coerceIn(top + 1, full.height)
            val right = (SheetTemplate.TABLE_MID_X_FRACTION * full.width).toInt().coerceIn(left + 1, full.width)
            Bitmap.createBitmap(full, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            null
        }
    }

    /** Moves a single saved scan to a different folder (or to Main). No-op
     *  if the scan's meta entry can't be found (shouldn't normally happen
     *  from the UI, since the id always comes from a scan already listed). */
    fun moveScan(context: Context, id: String, newFolder: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val metaKey = "meta_$id"
        val existing = prefs.getString(metaKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        existing.put("folder", newFolder)
        prefs.edit().putString(metaKey, existing.toString()).apply()
    }

    fun deleteScan(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove("scan_$id").remove("meta_$id").apply()
        // v1.2.0: a deleted scan takes its recorded learner score with it.
        LearnerResultStorage.removeByScanId(context, id)
    }

    /** Updates every scan's "testName" field (used by listScansForTest's
     *  filter) and moves the explicit-folders entry, so a test rename
     *  (TestStorage.renameTest) doesn't orphan its existing scans/folders.
     *  Scan IDs themselves are NOT changed — the id ("${testName}_
     *  ${timestamp}") is just an opaque storage key at this point, only the
     *  testName FIELD inside each meta JSON is what listScansForTest
     *  actually filters on. */
    fun renameTestReferences(context: Context, oldName: String, newName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        readAllMeta(context).filter { it.testName == oldName }.forEach { scan ->
            val metaKey = "meta_${scan.id}"
            val existing = prefs.getString(metaKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (existing != null) {
                existing.put("testName", newName)
                editor.putString(metaKey, existing.toString())
            }
        }
        val oldFoldersKey = FOLDERS_KEY_PREFIX + oldName
        prefs.getString(oldFoldersKey, null)?.let { foldersJson ->
            editor.putString(FOLDERS_KEY_PREFIX + newName, foldersJson)
            editor.remove(oldFoldersKey)
        }
        editor.apply()
    }

    /** v1.1.0: wipes every scan, every test's explicit-folders entry, and
     *  every raw scan_/meta_ key in one shot — used by RestoreDataActivity's
     *  "Clear Data and Continue" prompt. deleteAllScansForTest() only ever
     *  removes scan_/meta_ keys for ONE test and (per its own scope) never
     *  touched FOLDERS_KEY_PREFIX entries either — fine for that per-test
     *  use, but restoring a whole new dataset needs everything gone,
     *  including stray folder_ keys, so this is a plain full-prefs clear()
     *  rather than looping the narrower per-test delete over every test. */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun deleteAllScansForTest(context: Context, testName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        listScansForTest(context, testName).forEach {
            editor.remove("scan_${it.id}").remove("meta_${it.id}")
        }
        editor.apply()
    }

    /** All scans for one test, optionally filtered to a single folder.
     *  Pass folder = null to get every scan for the test regardless of
     *  folder (used by deleteAllScansForTest and the backup export). */
    fun listScansForTest(context: Context, testName: String, folder: String? = null): List<SavedScan> {
        return readAllMeta(context)
            .filter { it.testName == testName && (folder == null || it.folder == folder) }
            .sortedByDescending { it.savedAtMillis }
    }

    /** All saved scans across every test, used by BackupDataActivity's export. */
    fun listAllScans(context: Context): List<SavedScan> = readAllMeta(context)

    private fun readAllMeta(context: Context): List<SavedScan> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries
            .filter { it.key.startsWith("meta_") }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix("meta_")
                val json = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
                SavedScan(
                    id = id,
                    testName = json.optString("testName"),
                    folder = json.optString("folder", MAIN_FOLDER).ifBlank { MAIN_FOLDER },
                    savedAtMillis = json.optLong("savedAtMillis"),
                    earnedPoints = if (json.has("earnedPoints")) json.optInt("earnedPoints") else null,
                    totalPoints = if (json.has("totalPoints")) json.optInt("totalPoints") else null,
                    perQuestion = if (json.has("perQuestion")) {
                        json.optString("perQuestion").map {
                            when (it) {
                                '1' -> true
                                '0' -> false
                                else -> null // '-', or any unexpected char — treat as blank rather than crash
                            }
                        }
                    } else {
                        null // pre-v2.7 scan — this data was never captured, not an empty result
                    },
                    setIndex = json.optInt("setIndex", 0),
                    ambiguousQuestions = if (json.has("ambiguousQuestions")) {
                        json.optString("ambiguousQuestions")
                            .split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .toSet()
                    } else {
                        // Empty set for scans saved on/after v3.3 with
                        // nothing ambiguous (write side omits the key when
                        // empty — see saveScan), null for pre-v3.3 scans
                        // where this was never captured at all. Both read
                        // as "no key present" here, so this can't actually
                        // distinguish the two cases; callers that need to
                        // (e.g. hiding the resolve UI entirely on very old
                        // scans vs. just having nothing to resolve) fall
                        // back to treating a missing key as "nothing to
                        // resolve," which is the safe default either way.
                        null
                    }
                )
            }
    }

    /** Every folder name that currently has at least one scan for [testName],
     *  PLUS the always-present MAIN_FOLDER. Used to decide whether the
     *  folder-picker prompt should appear before scanning (only prompts if
     *  more than just MAIN_FOLDER exists) and to populate the folder list
     *  screen. */
    fun listFoldersForTest(context: Context, testName: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fromScans = readAllMeta(context).filter { it.testName == testName }.map { it.folder }.toSet()
        val explicitlyCreated = readExplicitFolders(context, testName)
        return (setOf(MAIN_FOLDER) + fromScans + explicitlyCreated).sortedWith(
            compareBy({ it != MAIN_FOLDER }, { it })
        )
    }

    /** Folders can be created empty (before any scan is saved into them),
     *  so folder existence can't be derived purely from scan metadata —
     *  this tracks explicitly-created folder names separately, per test. */
    private const val FOLDERS_KEY_PREFIX = "folders_"

    fun addFolder(context: Context, testName: String, folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isBlank() || trimmed == MAIN_FOLDER) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = readExplicitFolders(context, testName).toMutableSet()
        current.add(trimmed)
        prefs.edit().putString(FOLDERS_KEY_PREFIX + testName, JSONObject().apply {
            put("names", org.json.JSONArray(current.toList()))
        }.toString()).apply()
    }

    /** Removing a folder does NOT delete its scans — they fall back to
     *  being shown under MAIN_FOLDER instead, since silently deleting a
     *  teacher's saved scans as a side effect of a folder-organization
     *  action would be a surprising, risky default. */
    fun removeFolder(context: Context, testName: String, folderName: String) {
        if (folderName == MAIN_FOLDER) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = readExplicitFolders(context, testName).toMutableSet()
        current.remove(folderName)
        prefs.edit().putString(FOLDERS_KEY_PREFIX + testName, JSONObject().apply {
            put("names", org.json.JSONArray(current.toList()))
        }.toString()).apply()

        // Re-home any scans that were in the removed folder back to main,
        // rather than leaving them tagged with a folder name that no
        // longer exists anywhere in the UI.
        val prefsEditor = prefs.edit()
        listScansForTest(context, testName, folder = folderName).forEach { scan ->
            val metaKey = "meta_${scan.id}"
            val existing = prefs.getString(metaKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (existing != null) {
                existing.put("folder", MAIN_FOLDER)
                prefsEditor.putString(metaKey, existing.toString())
            }
        }
        prefsEditor.apply()
    }

    /** v3.7.5: renames a folder — both its entry in the explicit-folders
     *  set AND the "folder" tag on every scan currently filed under it,
     *  so scans stay associated with the (renamed) folder instead of
     *  falling back to MAIN_FOLDER the way removeFolder deliberately
     *  does. MAIN_FOLDER itself can't be renamed (it's not a real stored
     *  folder — it's the always-present default), and renaming onto an
     *  existing folder's name isn't allowed here; the caller
     *  (promptRenameFolder in SavedScansListActivity) is expected to
     *  check listFoldersForTest for a collision before calling this, the
     *  same way test-rename collisions are checked in MainActivity.
     */
    fun renameFolder(context: Context, testName: String, oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == MAIN_FOLDER) return
        if (oldName == MAIN_FOLDER || oldName == trimmed) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val current = readExplicitFolders(context, testName).toMutableSet()
        current.remove(oldName)
        current.add(trimmed)
        prefs.edit().putString(FOLDERS_KEY_PREFIX + testName, JSONObject().apply {
            put("names", org.json.JSONArray(current.toList()))
        }.toString()).apply()

        val prefsEditor = prefs.edit()
        listScansForTest(context, testName, folder = oldName).forEach { scan ->
            val metaKey = "meta_${scan.id}"
            val existing = prefs.getString(metaKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (existing != null) {
                existing.put("folder", trimmed)
                prefsEditor.putString(metaKey, existing.toString())
            }
        }
        prefsEditor.apply()
    }

    private fun readExplicitFolders(context: Context, testName: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(FOLDERS_KEY_PREFIX + testName, null) ?: return emptySet()
        return runCatching {
            val arr = JSONObject(raw).getJSONArray("names")
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    /** Raw encoded string for a scan's image — used by BackupDataActivity to
     *  copy scan data into a backup file without decoding/re-encoding it. */
    fun getRawEncoded(context: Context, id: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("scan_$id", null)
    }

    /** Used by RestoreDataActivity to write a scan's image back exactly as
     *  it was exported, without re-encoding through a Bitmap round-trip. */
    fun putRawEncoded(context: Context, id: String, encoded: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("scan_$id", encoded).apply()
    }

    /** Raw metadata JSON string for a scan — used by BackupDataActivity. */
    fun getRawMeta(context: Context, id: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("meta_$id", null)
    }

    /** Used by RestoreDataActivity to write a scan's metadata back exactly
     *  as it was exported. */
    fun putRawMeta(context: Context, id: String, metaJson: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("meta_$id", metaJson).apply()
    }

}
