package com.jpagdi.mcqquickcheck

import android.content.Context
import android.os.Build

/**
 * Builds the plain-text report body attached to "Send Diagnostic Report"'s
 * email (v3.1.4 — previously uploaded via HTTP; see AboutActivity's
 * sendDiagnosticReportEmail doc comment for why that was replaced).
 * Deliberately thin — it assembles app/device info + ScanDiagnostics'
 * existing rolling log (already capped at 100 entries, newest-first, as of
 * this same v3.1 pass) + optional feedback, rather than maintaining any
 * diagnostic data of its own. This is the "reuse existing diagnostic
 * generation, don't duplicate" requirement in code: there is exactly one
 * place scan diagnostics get recorded (ScanDiagnostics.logAttempt) and
 * exactly one place they get read back out (ScanDiagnostics.logText, used
 * here — the old local Export Scan Diagnostics share/download path that
 * used to also read this file was removed in this same pass, since
 * replacing it with this upload flow was the point).
 *
 * Never touches student data — it has no path to the student-facing scan
 * results at all, only ScanDiagnostics' log (which itself only ever records
 * scan-technical fields — see ScanDiagnostics.logAttempt's parameters —
 * never student names, answers, or images) and whatever the user typed in
 * the optional feedback box.
 */
object DiagnosticReportGenerator {

    fun generate(context: Context, feedback: String?): String {
        val sb = StringBuilder()
        sb.append("Project A.S.S.E.S.S. — Diagnostic Report\n")
        sb.append("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")

        sb.append("==================================================\n")
        sb.append("APP / DEVICE INFO\n")
        sb.append("==================================================\n")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        sb.append("App version: ${packageInfo.versionName}\n")
        val buildVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") packageInfo.versionCode
        }
        sb.append("Build version: $buildVersion\n")
        sb.append("Device model: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Theme: ${ThemePrefs.getTheme(context)}\n")
        sb.append("\n")

        sb.append("==================================================\n")
        sb.append("SCANNER CONFIGURATION\n")
        sb.append("==================================================\n")
        sb.append("MIN_MATCH_SCORE: ${MarkerDetector.MIN_MATCH_SCORE}\n")
        sb.append("PaperSurroundCheck.WHITE_THRESHOLD: ${PaperSurroundCheck.WHITE_THRESHOLD}\n")
        sb.append("\n")

        sb.append("==================================================\n")
        sb.append("RECENT SCANNER ACTIVITY (up to last 100 scan attempts)\n")
        sb.append("==================================================\n")
        val log = ScanDiagnostics.logText(context)
        sb.append(if (log.isBlank()) "(no scan attempts logged yet)\n" else log)
        sb.append("\n")

        if (!feedback.isNullOrBlank()) {
            sb.append("==================================================\n")
            sb.append("USER FEEDBACK\n")
            sb.append("==================================================\n\n")
            sb.append("Comments:\n\n")
            sb.append(feedback.trim())
            sb.append("\n\n")
        }

        return sb.toString()
    }
}
