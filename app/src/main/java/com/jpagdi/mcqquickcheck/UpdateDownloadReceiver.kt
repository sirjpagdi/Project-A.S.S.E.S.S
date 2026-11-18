package com.jpagdi.mcqquickcheck

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v3.9.1: manifest-declared (static) receiver for DownloadManager's
 * ACTION_DOWNLOAD_COMPLETE — see UpdateInstaller's class doc comment for
 * the full story of why this replaced a runtime-registered receiver that
 * didn't reliably survive the app being backgrounded during a download.
 *
 * Declared in AndroidManifest.xml with an intent-filter for
 * android.intent.action.DOWNLOAD_COMPLETE — Android starts a fresh
 * process just to deliver this broadcast here if the app's process had
 * been killed, which is the entire point of using a static receiver
 * instead of a dynamic one for this.
 *
 * Has no instance memory of which download this app is actually waiting
 * on (a static receiver can't hold state from whoever called
 * downloadManager.enqueue() — that call may have happened in a since-
 * killed process entirely), so the pending download's id and filename are
 * read back from UpdateInstaller's persisted SharedPreferences instead,
 * and matched against the completed id here. This also guards against
 * reacting to some OTHER app's completed download — ACTION_DOWNLOAD_COMPLETE
 * is a shared system broadcast, not scoped to this app alone.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val pendingId = UpdateInstaller.getPendingDownloadId(context)
        if (completedId == -1L || pendingId == -1L || completedId != pendingId) return

        val fileName = UpdateInstaller.getPendingFileName(context) ?: return
        UpdateInstaller.clearPendingDownload(context)
        UpdateInstaller.promptInstall(context, fileName)
    }
}
