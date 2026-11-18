package com.jpagdi.mcqquickcheck

import android.content.Context
import android.os.Bundle

/**
 * Defense-in-depth cache cleanup (v3.0). The main fix for "saving so much
 * cache" was in ScanActivity.processPhoto() — every capture attempt was
 * writing a full-resolution JPEG into cacheDir/images/ with nothing ever
 * deleting it afterward, on ANY outcome (accepted, rejected, error). A
 * teacher doing a dozen+ retakes in one session (confirmed from a real
 * diagnostics log) was accumulating dozens of full-res photos with no
 * cleanup — that's fixed at the source now (finally block).
 *
 * This sweeper catches what that source fix doesn't: PDF/xlsx/zip scratch
 * files from the v2.9 export flows (GenerateSheetActivity, ItemAnalysisActivity,
 * TestSheetBatchGenerator) that get cleaned up automatically after a
 * successful Download, but are deliberately LEFT in cache after a Share
 * (FileProvider needs a real file to hand to the receiving app, and there's
 * no reliable "the other app is done reading it now" callback) — or after
 * the app is killed mid-flow before either cleanup path runs.
 *
 * WHY A COLD-START SWEEP INSTEAD OF EXACT-MIDNIGHT SCHEDULING: an exact
 * daily alarm needs AlarmManager.setExactAndAllowWhileIdle or WorkManager,
 * both of which fight Android's Doze/battery-optimization restrictions on
 * a personal-use app that's often not running at midnight anyway — the
 * scheduling would be unreliable in practice for the one thing it's
 * supposed to guarantee. Sweeping cache on every fresh app open instead:
 * doesn't need a new permission, doesn't need a background service kept
 * alive, and in practice clears out whatever accumulated during the
 * previous session before the NEXT one starts — functionally close to
 * "on exit" (Android doesn't guarantee onDestroy() runs on a swiped-away
 * or system-killed task, so a cold-start sweep is the reliable half of
 * that pair, not the unreliable one).
 */
object CacheSweeper {

    /** Call from MainActivity.onCreate(). Only runs once per real cold
     *  start (savedInstanceState == null) — a config change (rotation)
     *  recreating MainActivity shouldn't wipe cache files another
     *  in-flight screen might still be using. */
    fun sweepOnColdStart(context: Context, savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        sweepNow(context)
    }

    /** v1.1.0: same sweep as sweepOnColdStart, minus the cold-start-only
     *  guard — used by RestoreDataActivity's "Clear Data and Continue"
     *  prompt, which needs to sweep on demand mid-session, not just on the
     *  next app launch. Safe to call any time: only ever touches this
     *  app's own known scratch subfolders (see KNOWN_SCRATCH_DIRS), never
     *  a screen's in-flight file, since every export flow already deletes
     *  its own scratch file immediately after a successful Download (the
     *  only files left behind here are post-Share leftovers or ones from
     *  a killed session — exactly what this sweep exists to catch). */
    fun sweepNow(context: Context) {
        val cache = context.cacheDir ?: return
        cache.listFiles()?.forEach { entry ->
            // Only sweep the scratch subfolders this app itself creates
            // (images/pdfs/exports/test_sheets) — never touch anything
            // else that might be in cacheDir (e.g. WebView's or another
            // library's own cache subfolder), since this is a shared
            // directory, not exclusively ours.
            if (entry.isDirectory && entry.name in KNOWN_SCRATCH_DIRS) {
                entry.deleteRecursively()
            }
        }
    }

    private val KNOWN_SCRATCH_DIRS = setOf("images", "pdfs", "exports", "test_sheets")
}
