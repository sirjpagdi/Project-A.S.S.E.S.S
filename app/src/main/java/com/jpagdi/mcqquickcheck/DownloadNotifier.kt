package com.jpagdi.mcqquickcheck

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * System notification shown whenever a file is actually saved into the
 * phone's Downloads folder (v2.9). Deliberately does NOT fire for the
 * pre-Android-10 "share instead" fallback path — that hands off to
 * another app immediately and never lands a file the user could look for
 * later, so a "saved" notification there would be misleading.
 *
 * Only POST_NOTIFICATIONS (Android 13 / API 33+) is a real runtime
 * permission concern here. This app deliberately does not request
 * WRITE_EXTERNAL_STORAGE for older devices — see
 * ItemAnalysisXlsxExporter's class doc comment for why (falls back to
 * share-sheet instead, same design carried forward here, not changed).
 */
object DownloadNotifier {

    private const val CHANNEL_ID = "downloads"
    private const val CHANNEL_NAME = "Downloads"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Confirms when a file has been saved to your phone's Downloads folder"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** True if a POST_NOTIFICATIONS request is actually needed right now
     *  (API 33+ and not already granted). Callers check this BEFORE
     *  calling notifyDownloaded() so they can request permission first
     *  instead of silently never showing the confirmation. */
    fun needsPermissionRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    /** Posts the "saved" notification. Silently does nothing if permission
     *  genuinely isn't granted (API 33+, user declined) rather than
     *  crashing — the file itself is already safely saved either way;
     *  the notification is a courtesy, not a requirement. */
    fun notifyDownloaded(context: Context, fileName: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Saved to Downloads")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Unique-ish notification id per filename so saving several
        // different files in a row shows separate notifications instead
        // of one replacing another.
        NotificationManagerCompat.from(context).notify(fileName.hashCode(), notification)
    }
}
