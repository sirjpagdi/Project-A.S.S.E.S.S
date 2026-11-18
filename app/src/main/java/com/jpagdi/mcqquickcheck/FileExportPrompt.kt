package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shared "Share or Download?" prompt (v2.9), used after a single GENERATE
 * PDF / EXPORT / etc. button generates a file, per the instruction to
 * merge the old separate Share and Download buttons into one action that
 * asks afterward. Reused by GenerateSheetActivity (PDF), ItemAnalysisActivity
 * (xlsx), AboutActivity (diagnostics log + prefilled test sheets zip) —
 * one dialog implementation instead of four copies.
 *
 * Dialog button text color uses ThemePrefs.getTheme() directly, same as
 * every other dialog in this app (see the project's dialog-button-color
 * bug class — theme.resolveAttribute() is unreliable here, this is the
 * proven workaround), and fires the same confirm haptic as every other
 * popup.
 */
object FileExportPrompt {

    /**
     * @param workingFile The file already written to a SCRATCH location
     *   (this app uses context.cacheDir subfolders for this — see each
     *   caller) — used for BOTH the Share path (FileProvider needs a real
     *   File) and the Download path (streamed straight from disk).
     *
     *   FIX (OOM crashes on large backups/exports — see BackupDataActivity
     *   and ScannedSheetExporter's own doc comments for the specific
     *   crash traces this was written for): this used to also take a
     *   `bytes: ByteArray` — the same content as workingFile, but already
     *   fully loaded into memory — solely so the Download path had
     *   something to write. Every caller was doing `file.readBytes()`
     *   (or equivalent) right before calling show(), which is exactly the
     *   "hold the whole exported file in one contiguous allocation"
     *   pattern that was blowing through the ~256MB heap ceiling on large
     *   backups/zips. Download now streams directly from workingFile in
     *   small chunks (see downloadThenNotify) instead, so no caller needs
     *   to materialize the file's full contents in memory just to hand it
     *   to this dialog — same fix shape as the Share path, which already
     *   never needed `bytes` since FileProvider always worked from a File.
     * @param onDownloadComplete Called after a successful Download so the
     *   caller can delete its own scratch file — kept as a callback
     *   instead of this object deleting workingFile itself, since only
     *   the caller knows whether that file is safe to remove yet.
     */
    fun show(
        activity: Activity,
        title: String,
        fileName: String,
        mimeType: String,
        workingFile: File,
        shareTitle: String,
        // v1.1.0: optional override for the dialog body — defaults to the
        // existing "$fileName is ready." shared by every other caller
        // (Backup Data, Export Scans, Item Analysis, etc.) unchanged.
        // Added so GenerateSheetActivity can show the human-readable exam
        // title instead of the sanitized filename, and fold in a
        // Letter-size caveat sentence when relevant, without touching
        // this dialog's wording for every other export.
        message: String? = null,
        onDownloadComplete: () -> Unit = {}
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message ?: "$fileName is ready.")
            .setPositiveButton("Download") { _, _ ->
                downloadThenNotify(activity, fileName, mimeType, workingFile, shareTitle, onDownloadComplete)
            }
            .setNegativeButton("Share") { _, _ ->
                shareWorkingFile(activity, workingFile, mimeType, shareTitle)
            }
            .setNeutralButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(activity, dialog)
    }

    private fun downloadThenNotify(
        activity: Activity,
        fileName: String,
        mimeType: String,
        workingFile: File,
        shareTitle: String,
        onDownloadComplete: () -> Unit
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Streams workingFile -> MediaStore in small chunks (see the
            // File-based saveToDownloadsViaMediaStore overload) instead of
            // needing the whole thing in one ByteArray first.
            ItemAnalysisXlsxExporter.saveToDownloadsViaMediaStore(activity, fileName, workingFile, mimeType)
            if (DownloadNotifier.needsPermissionRequest(activity)) {
                // Permission prompt is handled by each calling Activity's
                // own registered launcher (see requestNotificationPermissionIfNeeded
                // below) — file is already safely saved to Downloads either
                // way, the notification is just a courtesy on top.
            } else {
                DownloadNotifier.notifyDownloaded(activity, fileName)
            }
        } else {
            // Pre-Android-10: no Downloads write without the
            // WRITE_EXTERNAL_STORAGE dangerous permission, which this app
            // deliberately doesn't request (see ItemAnalysisXlsxExporter
            // doc comment) — falls back to share instead, same as before.
            // FIX: this used to copy `bytes` into a second scratch file via
            // ItemAnalysisXlsxExporter.shareViaFileProvider (another full
            // in-memory copy on top of whatever built `bytes` in the first
            // place). workingFile is already a real file FileProvider can
            // share directly, so this just reuses the same Share path the
            // Share button itself uses — no extra copy, no bytes needed.
            shareWorkingFile(activity, workingFile, mimeType, shareTitle)
        }
        onDownloadComplete()
    }

    private fun shareWorkingFile(activity: Activity, file: File, mimeType: String, shareTitle: String) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, shareTitle))
    }

    /** Same pattern as every other dialog in this app — see e.g.
     *  MainActivity.fixDialogButtonColors doc comment for why this isn't
     *  done via theme.resolveAttribute(). Kept as its own copy rather than
     *  reusing one of the 8 existing per-Activity copies, consistent with
     *  how this codebase already does it (each caller owns its own). */
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
