package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages a test's saved scanned sheets into a single zip for Share/
 * Download, from the export icon on TestDetailActivity's header bar (see
 * TestDetailActivity.exportScansThenPrompt). Reuses FileExportPrompt for
 * the actual "Preparing your files... Cancel / Share / Download" dialog -
 * same one dialog implementation every other export feature in this app
 * already shares (TestSheetBatchGenerator, ItemAnalysisActivity,
 * GenerateSheetActivity) - so the only genuinely new piece here is the
 * Main-vs-per-folder choice that has to happen BEFORE that dialog, since
 * it changes what actually goes into the zip.
 *
 * Scan images are stored as base64-encoded JPEG strings directly in
 * SharedPreferences (see ScannedSheetStorage's own doc comment for why),
 * not as files - so unlike TestSheetBatchGenerator (which already has real
 * PDF files on disk to zip), this has to decode each scan's base64 back
 * into raw JPEG bytes before it can become a zip entry. getRawEncoded()
 * hands back the base64 string as-is (no Bitmap round-trip needed), so
 * this only ever does ONE decode per scan - same "don't re-encode data we
 * already have in the right format" reasoning ScannedSheetStorage's own
 * getRawEncoded/putRawEncoded already use for BackupDataActivity.
 */
object ScannedSheetExporter {

    /**
     * Entry point from TestDetailActivity's export icon. Per spec: if the
     * test has folders beyond Main, ask whether to package everything
     * (Main + every subfolder, each as its own subfolder inside the zip)
     * or just one specific folder. If Main is the ONLY folder that exists,
     * there's nothing to choose between - skip straight to exporting
     * everything, matching how startScanningAllFolders() on this same
     * screen already skips its own folder prompt in the single-folder
     * case.
     */
    fun promptThenExport(activity: Activity, testName: String) {
        val hasScans = ScannedSheetStorage.listScansForTest(activity, testName).isNotEmpty()
        if (!hasScans) {
            Toast.makeText(activity, "No scanned sheets to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val folders = ScannedSheetStorage.listFoldersForTest(activity, testName)
        if (folders.size <= 1) {
            buildZipThenPrompt(activity, testName, folder = null)
            return
        }
        val labels = arrayOf("All folders") + folders.map {
            if (it == ScannedSheetStorage.MAIN_FOLDER) "Main only" else "$it only"
        }.toTypedArray()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Export Scanned Sheets")
            .setItems(labels) { _, which ->
                val folder = if (which == 0) null else folders[which - 1]
                buildZipThenPrompt(activity, testName, folder)
            }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(activity, dialog)
    }

    private fun buildZipThenPrompt(activity: Activity, testName: String, folder: String?) {
        Toast.makeText(activity, "Packaging scanned sheets...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val scans = ScannedSheetStorage.listScansForTest(activity, testName, folder)
                val dir = File(activity.cacheDir, "scan_exports").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val safeName = testName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                // v1.1.0: folder name added to the zip's own filename, not
                // just the entries inside it — per explicit request, so a
                // teacher who's exported several folders separately can
                // tell them apart from the Downloads list alone, without
                // opening each zip first.
                val folderLabel = when {
                    folder == null -> "All"
                    folder == ScannedSheetStorage.MAIN_FOLDER -> "Main"
                    else -> folder
                }
                val safeFolder = folderLabel.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val zipFile = File(dir, "${safeName}_${safeFolder}_scans.zip")
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault())

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // One subfolder per Android folder inside the zip (only
                    // relevant when folder == null, i.e. "all folders") so
                    // a teacher who exported the whole test still sees the
                    // same Main/subfolder organization once they unzip,
                    // rather than every scan dumped into one flat pile.
                    scans.forEach { scan ->
                        val encoded = ScannedSheetStorage.getRawEncoded(activity, scan.id) ?: return@forEach
                        val bytes = try {
                            Base64.decode(encoded, Base64.DEFAULT)
                        } catch (e: Exception) {
                            return@forEach // skip a corrupt entry rather than fail the whole export
                        }
                        val folderPrefix = if (folder == null) "${scan.folder}/" else ""
                        val stamp = dateFormat.format(java.util.Date(scan.savedAtMillis))
                        zos.putNextEntry(ZipEntry("$folderPrefix${safeName}_${stamp}_${scan.id}.jpg"))
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
                // FIX (matches BackupDataActivity's same-shape OOM crash —
                // see its doc comment): this used to also do `val zipBytes
                // = zipFile.readBytes()` here just to hand the whole zip
                // to FileExportPrompt as a ByteArray. A single test with
                // enough scanned sheets (or "All folders") could easily
                // produce a zip past this app's heap ceiling, on top of
                // the zip file itself already sitting on disk. Dropped —
                // FileExportPrompt.show() now streams the Download path
                // straight from zipFile instead of needing it in memory.
                activity.runOnUiThread {
                    FileExportPrompt.show(
                        activity = activity,
                        title = "Export Scanned Sheets",
                        fileName = zipFile.name,
                        mimeType = "application/zip",
                        workingFile = zipFile,
                        shareTitle = "Share scanned sheets",
                        onDownloadComplete = { dir.deleteRecursively() }
                    )
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Export failed - nothing was changed", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** Same fix used everywhere else in this app - AlertDialog buttons
     *  default to colorPrimary text, unreadable in Dark/Black themes. Uses
     *  ThemePrefs.getTheme() directly rather than theme.resolveAttribute(),
     *  per this app's known bug-class notes on why that's unreliable here. */
    private fun fixDialogButtonColors(activity: Activity, dialog: AlertDialog) {
        HapticUtils.confirm(dialog.window?.decorView)
        val color = if (ThemePrefs.getTheme(activity) == AppTheme.LIGHT) {
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
