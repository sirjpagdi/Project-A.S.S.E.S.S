package com.jpagdi.mcqquickcheck

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Single shared entry point for the "haptic feedback on every popup prompt
 * and confirmation" behavior (v2.9). Deliberately just one small object
 * instead of touching every dialog-building call site individually — each
 * of this app's 8 existing fixDialogButtonColors() copies (Main, About,
 * GenerateSheet, ItemAnalysis, RestoreData, SavedScansList, TestDetail,
 * TestSummaryDialog) already runs exactly once per dialog shown, right
 * after .show(), so that's the one place per file this gets called from —
 * no need to hunt down every individual AlertDialog.Builder call site.
 *
 * HapticFeedbackConstants.CONFIRM is the "positive outcome" pattern but
 * only exists from API 30 (Android 11) onward; this app's minSdk is 24,
 * so anything below 30 falls back to VIRTUAL_KEY, the older generic
 * "acknowledged" click pulse. FLAG_IGNORE_VIEW_SETTING is required here
 * because these dialog root views never opted into haptic feedback via
 * View.isHapticFeedbackEnabled themselves (that flag defaults true for
 * things like buttons, but not for an arbitrary decorView) — without it
 * performHapticFeedback silently no-ops on most views.
 *
 * performHapticFeedback already respects the device's system-wide haptics
 * toggle on its own, so no separate settings check is needed here.
 */
object HapticUtils {

    fun confirm(view: View?) {
        view ?: return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    /**
     * A light, frequent pulse for repeated navigation gestures (e.g.
     * swiping between scans) — deliberately NOT confirm()'s "positive
     * outcome" pattern, which is meant for a single completed action, not
     * something that can fire many times in a row. CLOCK_TICK exists from
     * API 21, well below this app's minSdk 24, so unlike confirm() there's
     * no version fallback needed here.
     */
    fun tick(view: View?) {
        view ?: return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }
}
