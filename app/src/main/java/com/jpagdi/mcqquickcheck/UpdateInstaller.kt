package com.jpagdi.mcqquickcheck

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * v3.9.0: downloads an update APK via the system DownloadManager and hands
 * it to the platform installer once the download completes.
 *
 * v3.9.1 FIX: the completion listener used to be a runtime-registered
 * BroadcastReceiver tied to the app's process (registered right here in
 * downloadAndInstall). Confirmed broken on a real device: backgrounding
 * the app while a several-second download was still running (e.g. to go
 * check the system Downloads app, wondering why nothing had happened yet)
 * let Android kill that registration along with the rest of the
 * background process. The OS's own download notification still appeared
 * — that part is entirely system-level, independent of this app — but
 * this app's own automatic install prompt never fired, since nothing was
 * left alive to receive the completion broadcast. Tapping the entry in
 * the system Downloads app afterward just reopened this app instead of
 * installing anything, since a plain file sitting in this app's PRIVATE
 * external-files directory isn't something the Downloads app's own
 * generic open-intent can hand off correctly on its own.
 *
 * Fixed by moving to a manifest-declared (static) receiver —
 * UpdateDownloadReceiver, registered in AndroidManifest.xml — which
 * Android will start a fresh process for just to deliver the broadcast,
 * surviving process death. ACTION_DOWNLOAD_COMPLETE is one of the
 * broadcasts explicitly exempted from the API 26+ implicit-broadcast
 * delivery restrictions, specifically so apps can rely on exactly this
 * kind of download-then-act flow. Since a static receiver has no instance
 * state from whoever called enqueue(), the pending download's id and
 * filename are persisted here to SharedPreferences at enqueue time and
 * read back by UpdateDownloadReceiver when the broadcast arrives.
 *
 * Two separate permission concerns, both checked before anything is
 * enqueued:
 *  - REQUEST_INSTALL_PACKAGES (manifest-declared). On API < 26 that alone
 *    is sufficient — canInstall() short-circuits to true there, since no
 *    runtime gate existed yet on those versions. On API 26+, the manifest
 *    permission only makes the REQUEST possible; the OS additionally
 *    requires canRequestPackageInstalls() to be true for this specific
 *    app, which the teacher grants per-app via Settings — there's no
 *    direct runtime permission dialog for this the way CAMERA/
 *    POST_NOTIFICATIONS have; ACTION_MANAGE_UNKNOWN_APP_SOURCES is the
 *    only path.
 *  - The download itself needs no runtime permission at all —
 *    DownloadManager writing into this app's OWN external-files dir
 *    (getExternalFilesDir) has never required WRITE_EXTERNAL_STORAGE on
 *    any API level this app supports (minSdk 24), unlike writing to the
 *    shared public Downloads folder elsewhere in this app.
 *
 * Reuses the app's existing FileProvider authority
 * (com.jpagdi.mcqquickcheck.fileprovider) — "updates" is a registered root
 * in file_paths.xml, same pattern already used for pdfs/backups/
 * test_sheets/diagnostics/exports.
 */
object UpdateInstaller {

    private const val PREFS = "update_installer_prefs"
    private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
    private const val KEY_PENDING_FILE_NAME = "pending_file_name"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True if this app can install packages right now without first being
     *  sent to Settings. Always true below API 26 — see class doc comment. */
    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** The Settings screen where THIS app can be granted "install unknown
     *  apps." Returned as an Intent (not launched directly) so the caller
     *  can start it via registerForActivityResult and simply re-check
     *  canInstall() when the teacher comes back — see AboutActivity. */
    fun installPermissionSettingsIntent(context: Context): Intent {
        return Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    /** Enqueues the download via DownloadManager and persists which
     *  download id/filename this app is now waiting on, for
     *  UpdateDownloadReceiver to pick up when the completion broadcast
     *  arrives — see this object's class doc comment for why that's a
     *  static receiver reading persisted state, not a live callback here.
     *  [onEnqueued] fires immediately so the caller can show
     *  "Downloading..." feedback right away; there's no further callback
     *  in this function for the download actually finishing. */
    fun downloadAndInstall(context: Context, downloadUrl: String, versionName: String, onEnqueued: () -> Unit) {
        val appContext = context.applicationContext
        val fileName = "project_assess_v$versionName.apk"

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Project A.S.S.E.S.S. Update")
            setDescription("Downloading v$versionName")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(appContext, "updates", fileName)
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        prefs(appContext).edit()
            .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
            .putString(KEY_PENDING_FILE_NAME, fileName)
            .apply()

        onEnqueued()
    }

    /** Read by UpdateDownloadReceiver to check whether a just-completed
     *  download is the one this app enqueued — DownloadManager's
     *  ACTION_DOWNLOAD_COMPLETE is a shared system broadcast, so this also
     *  guards against reacting to some OTHER app's completed download
     *  that happens to fire the same action. Returns -1 if nothing's
     *  pending (already handled, or nothing was ever enqueued). */
    fun getPendingDownloadId(context: Context): Long = prefs(context).getLong(KEY_PENDING_DOWNLOAD_ID, -1L)

    fun getPendingFileName(context: Context): String? = prefs(context).getString(KEY_PENDING_FILE_NAME, null)

    /** Called by UpdateDownloadReceiver once it's matched and handled a
     *  completion — prevents acting twice on the same download if the
     *  system ever redelivers the broadcast. */
    fun clearPendingDownload(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_DOWNLOAD_ID)
            .remove(KEY_PENDING_FILE_NAME)
            .apply()
    }

    /** Builds the FileProvider Uri for the downloaded APK and launches the
     *  platform installer. Public (not private) so UpdateDownloadReceiver
     *  can call this directly once it confirms a completion broadcast
     *  matches the pending download, instead of duplicating this same
     *  FileProvider/install-intent logic in two places. FLAG_ACTIVITY_NEW_TASK
     *  is required here since this can run from a receiver with no
     *  foreground Activity of its own (e.g. after the app's process was
     *  restarted purely to deliver the broadcast). */
    fun promptInstall(context: Context, fileName: String) {
        val file = File(context.getExternalFilesDir("updates"), fileName)
        if (!file.exists()) {
            Toast.makeText(context, "Download finished but the file couldn't be found.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    /** v3.9.2: deletes every file left in the private "updates" folder —
     *  called on every app launch (see WelcomeActivity.onCreate). A
     *  downloaded update APK has no further use once the platform
     *  installer has been handed the file: either the install succeeded
     *  (this app is now the new version — the old APK is dead weight) or
     *  the teacher canceled/backed out of it (a fresh download next time
     *  they check is simpler and safer than trying to detect and resume
     *  a half-finished install).
     *
     *  Deliberately NOT a scheduled delete fired shortly after
     *  promptInstall() hands off to the installer — this app's process
     *  can be (and, per v3.9.1's fix, demonstrably was) killed while the
     *  system installer UI is doing its own thing, which would silently
     *  drop any postDelayed callback the same way the old runtime
     *  download-complete receiver got silently dropped. A launch-time
     *  sweep doesn't depend on any process surviving in between — it just
     *  checks fresh, unconditionally, the next time this app actually
     *  runs again, whether that's seconds or days later.
     *
     *  Skips the file matching a still-genuinely-pending download (i.e.
     *  getPendingDownloadId hasn't been cleared yet) — DownloadManager
     *  downloads keep running via the system service even if this app's
     *  process is force-closed, so a relaunch mid-download, or a relaunch
     *  that races UpdateDownloadReceiver before it's finished handling a
     *  just-completed one, shouldn't delete the file out from under
     *  either of those. */
    fun purgeStaleDownloads(context: Context) {
        val dir = context.getExternalFilesDir("updates") ?: return
        val pendingFileName = if (getPendingDownloadId(context) != -1L) getPendingFileName(context) else null
        dir.listFiles()?.forEach { file ->
            if (file.name != pendingFileName) file.delete()
        }
    }
}
