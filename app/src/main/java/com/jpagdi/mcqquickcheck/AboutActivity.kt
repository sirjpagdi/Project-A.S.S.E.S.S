package com.jpagdi.mcqquickcheck

import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Unlike the other new drawer destinations, this one is fully built now
 * rather than a placeholder — it only needed to read data (version,
 * WhatsNew.items) that already existed, no new storage/settings plumbing
 * required.
 *
 * Uses the curated WhatsNew list (see Changelog.kt) instead of the raw
 * changelog, and the shared placeholder layout is wrapped in a ScrollView
 * so this stays readable as WhatsNew.items grows over time, instead of
 * being clipped on a shorter screen.
 */
class AboutActivity : AppCompatActivity() {

    // v2.9: see GenerateSheetActivity's identical property for why this
    // is a no-op result handler.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // v3.9.0: launches Settings so the teacher can grant "install unknown
    // apps" for this app specifically (no direct runtime permission dialog
    // exists for this on API 26+ — see UpdateInstaller.canInstall's doc
    // comment). No-op result handler on purpose: UpdateInstaller.canInstall()
    // simply reflects the new state next time the teacher taps Download
    // again, so there's nothing to branch on here.
    private val requestInstallPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

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

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.tvPlaceholderTitle).text = "About"

        // Hidden entry point for the downloadable prefilled test-sheet
        // batch generator — long-press the title. One-off testing tool,
        // not something a teacher needs day to day, so it's not a normal
        // visible button. See TestSheetBatchGenerator's own doc comment.
        findViewById<TextView>(R.id.tvPlaceholderTitle).setOnLongClickListener {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Generate Test Sheets")
                // v3.6.1: was "~30 ... every paper layout × item count from
                // 10 to 120" — stale on both counts since v3.4 removed every
                // layout but QUARTER, and v3.6 moved the real ceiling to
                // 100. Sheet count bumped from ~30 to ~45 to match
                // TestSheetBatchGenerator's now-9-combo matrix (was 6).
                // v3.6.5: was ~45 — the 9 stress configs used to each
                // produce 4 separate pages (one per variation); now each
                // packs all 4 variations onto one page's quadrants, so
                // 9 combo + 9 stress-config pages = 18.
                .setMessage("This creates ~18 prefilled PDFs (QUARTER layout, item counts from 10 to 100) for testing the scanner. May take a moment. Continue?")
                .setPositiveButton("Generate") { _, _ ->
                    TestSheetBatchGenerator.generateThenPrompt(this)
                    if (DownloadNotifier.needsPermissionRequest(this)) {
                        requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            fixDialogButtonColors(dialog)
            true
        }

        val whatsNew = WhatsNew.items.joinToString("\n") { "• $it" }

        val bodyView = findViewById<TextView>(R.id.tvPlaceholderBody)
        // v3.7.4: switched from a plain String to a SpannableStringBuilder
        // so specific lines (app title, version, tagline, and the two
        // section headings) can be bolded while the description/What's
        // New list/license text stay regular weight — a single String
        // can't carry per-range styling, so this couldn't be done with
        // android:textStyle="bold" on the TextView as a whole (that would
        // bold everything, including the long description and license
        // paragraphs, which weren't asked for).
        val spannable = android.text.SpannableStringBuilder()

        fun appendBold(text: String) {
            val start = spannable.length
            spannable.append(text)
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start, spannable.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        appendBold("Project A.S.S.E.S.S.\n")
        val versionLineStart = spannable.length
        appendBold("Version $versionName\n")
        // Tagline, per request — placement/styling wasn't specified, so
        // this goes directly under the name/version header, before the
        // description, as a short standalone line. Bolded along with the
        // title/version above per later request.
        appendBold("SCAN CHECK REVIEW\n\n")
        spannable.append(getString(R.string.about_description))
        spannable.append("\n\n")
        appendBold("What's New:\n")
        spannable.append(whatsNew)
        // Appended into the same scrolling body rather than a new button/
        // screen — About was already made scrollable for What's New, so
        // this rides along on that instead of adding a new touch target
        // to the shared placeholder layout (which Backup/Restore also use).
        spannable.append("\n\n")
        appendBold("Legal & Licenses:\n")
        spannable.append(getString(R.string.about_license))

        bodyView.text = spannable

        // Long-press the "Version x.y.z" line to open the changelog. The
        // touch position is remembered so only a press on that line counts,
        // not a long-press anywhere else in the About text.
        var lastTouchX = 0f
        var lastTouchY = 0f
        bodyView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }
        bodyView.setOnLongClickListener { tv ->
            val textView = tv as TextView
            val layout = textView.layout ?: return@setOnLongClickListener false
            val y = (lastTouchY - textView.totalPaddingTop + textView.scrollY).toInt()
            val pressedLine = layout.getLineForVertical(y)
            if (pressedLine == layout.getLineForOffset(versionLineStart)) {
                startActivity(android.content.Intent(this, ChangelogActivity::class.java))
                true
            } else {
                false
            }
        }
        // Justified body text, per request. justificationMode only exists
        // from API 26 onward — guarded so this can't crash on the API
        // 24-25 devices this app still supports (minSdk 24). Below API 26
        // it just falls back to normal left-aligned text, which is a safe,
        // unremarkable degradation rather than a crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bodyView.justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
        }

        // v3.7.3: swapped which shared-layout slot each action uses —
        // Send Diagnostic Report (the button a teacher taps regularly
        // during testing) now sits on TOP in btnPlaceholderBack's slot,
        // with Back below it in btnPlaceholderSecondary's slot. Only the
        // slot assignment moved; the XML already tags both buttons
        // accent_maroon and AccentColorPrefs.applyAccents() (called
        // above) retints whichever button ends up in either slot, so no
        // manual color juggling is needed here — just re-point each
        // slot's text/icon/click-listener at its new role. Backup/Restore
        // aren't touched — this swap lives entirely inside AboutActivity's
        // own code, not the shared XML both screens also use.
        findViewById<Button>(R.id.btnPlaceholderSecondary).apply {
            BackButtonStyle.apply(this)
            text = "Back"
            visibility = android.view.View.VISIBLE
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@AboutActivity, R.drawable.ic_arrow_back),
                android.graphics.Color.WHITE
            )
            setOnClickListener { finish() }
        }

        // v3.7.5: Check for Updates — placed between Send Diagnostic
        // Report and Back per explicit request.
        // v3.7.6: switched to gold group per explicit request — same
        // manual re-tag/re-tint as BackupDataActivity's Export Backup
        // (btnPlaceholderTertiary defaults to accent_maroon in the shared
        // XML, same as the other placeholder slots; About's own Send
        // Diagnostic Report/Back stay maroon, unaffected by this).
        // v3.9.0: wired to the real UpdateChecker — was a "Coming soon!"
        // placeholder. Always forces a fresh network call regardless of
        // the 24h throttle used by the silent launch-time auto-check (see
        // WelcomeActivity), per spec: a manual tap should always give real
        // feedback, not a cached answer.
        findViewById<Button>(R.id.btnPlaceholderTertiary).apply {
            text = "Check for Updates"
            visibility = android.view.View.VISIBLE
            tag = "accent_gold"
            val accent = AccentColorPrefs.getGroupB(this@AboutActivity)
            val contrastColor = AccentColorPrefs.textColorFor(accent)
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
            setTextColor(contrastColor)
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@AboutActivity, R.drawable.ic_cloud_download),
                contrastColor
            )
            setOnClickListener {
                android.widget.Toast.makeText(this@AboutActivity, "Checking for updates…", android.widget.Toast.LENGTH_SHORT).show()
                UpdateChecker.checkNow(this@AboutActivity) { result -> handleUpdateCheckResult(result) }
            }
        }

        // v2.4 diagnosis pass: a visible (not hidden — this one the user
        // needs regularly during testing, unlike the batch generator above)
        // way to pull the scan-diagnostics log off the phone. Reuses
        // btnPlaceholderBack, since v3.7.3 moved this action into that top
        // slot (see swap note above) — was btnPlaceholderSecondary.
        // v3.1: replaced the local Export Scan Diagnostics flow with Send
        // Diagnostic Report. v3.1.4: this now composes an email instead of
        // uploading over HTTP — see showSendDiagnosticReportDialog /
        // sendDiagnosticReportEmail below. Long-press (clear log) is
        // unchanged: still directly useful on its own regardless of how
        // the report ultimately leaves the device.
        // v3.7.3: icon+text now centered as a unit via centerIconAndText
        // (was setCompoundDrawablesWithIntrinsicBounds, which only
        // centers the text within the leftover space next to a
        // fixed-inset icon — same fix already applied to Back buttons
        // and other rebuilt buttons across the app).
        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            text = "Send Diagnostic Report"
            visibility = android.view.View.VISIBLE
            centerIconAndText(
                this,
                androidx.core.content.ContextCompat.getDrawable(this@AboutActivity, R.drawable.ic_mail),
                android.graphics.Color.WHITE
            )
            setOnClickListener {
                if (ScanDiagnostics.hasLog(this@AboutActivity)) {
                    showSendDiagnosticReportDialog()
                } else {
                    android.widget.Toast.makeText(
                        this@AboutActivity,
                        "No scans logged yet — scan a few sheets first.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            // Long-press to start a fresh log before a new testing session,
            // so an old session's entries don't pad out the next report.
            setOnLongClickListener {
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@AboutActivity)
                    .setTitle("Clear Diagnostics Log")
                    .setMessage("Start a fresh scan diagnostics log? This can't be undone.")
                    .setPositiveButton("Clear") { _, _ ->
                        ScanDiagnostics.clearLog(this@AboutActivity)
                        android.widget.Toast.makeText(this@AboutActivity, "Diagnostics log cleared", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                fixDialogButtonColors(dialog)
                true
            }
        }

        // v3.9.0: resurfaces an update the silent launch-time auto-check
        // already found and cached (see WelcomeActivity/UpdateChecker).
        // Cache-only read, no network call here — shown as a dialog only
        // when About is actually opened, never on launch itself, matching
        // "no Home screen banner" from spec. Skipped entirely if that
        // cached versionCode was already dismissed via "Skip This
        // Version" in showUpdateAvailableDialog below.
        UpdateChecker.getCachedPendingUpdate(this)?.let { cached ->
            showUpdateAvailableDialog(cached)
        }
    }

    /** Routes a manual Check for Updates tap's result to the right dialog.
     *  UpdateAvailable reuses the same dialog the silent auto-check's
     *  resurface path shows (see end of onCreate above) — one code path
     *  for both, rather than two dialogs that could drift out of sync. */
    private fun handleUpdateCheckResult(result: UpdateChecker.Result) {
        when (result) {
            is UpdateChecker.Result.UpdateAvailable -> showUpdateAvailableDialog(result)
            is UpdateChecker.Result.UpToDate -> showSimpleUpdateDialog(
                "You're up to date (v${packageManager.getPackageInfo(packageName, 0).versionName})."
            )
            is UpdateChecker.Result.NoReleasesFound -> showSimpleUpdateDialog("No releases found yet.")
            is UpdateChecker.Result.Failure -> showSimpleUpdateDialog(result.message)
        }
    }

    private fun showSimpleUpdateDialog(message: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Check for Updates")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    /** Shown either right after a manual check finds something newer, or
     *  automatically when About opens and a silent background check
     *  already found and cached one (see onCreate above) — same dialog
     *  either way. Strips the release notes' own "versionCode: NN" marker
     *  line before displaying — that line is for UpdateChecker's own
     *  comparison logic, not something a teacher needs to see. Three
     *  buttons rather than two: "Not Now" just closes (resurfaces again
     *  next time, e.g. next launch or next About open — nothing was
     *  dismissed), "Skip This Version" is the deliberate, separate
     *  dismissal action (won't nag again for THIS versionCode, but will
     *  still resurface for anything newer than it). */
    private fun showUpdateAvailableDialog(update: UpdateChecker.Result.UpdateAvailable) {
        val currentVersionName = packageManager.getPackageInfo(packageName, 0).versionName
        val cleanedNotes = update.notes.lines()
            .filterNot { it.contains(Regex("(?i)versionCode\\s*[:=]")) }
            .joinToString("\n")
            .trim()
        val message = buildString {
            append("Version ${update.versionName} is available (you have v$currentVersionName).")
            if (cleanedNotes.isNotEmpty()) {
                append("\n\n")
                append(cleanedNotes)
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Download") { _, _ -> startUpdateDownload(update) }
            .setNeutralButton("Skip This Version") { _, _ ->
                UpdateChecker.dismissUpdate(this, update.versionCode)
            }
            .setNegativeButton("Not Now", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    /** Gates on UpdateInstaller.canInstall() first — if the teacher hasn't
     *  granted "install unknown apps" for this app yet, explains why and
     *  sends them to the one Settings screen that can grant it, rather
     *  than silently failing when the platform installer later refuses to
     *  run. Nothing here re-checks the permission automatically on
     *  return — the teacher just taps Download again from the same
     *  Update Available dialog once they're back, which re-evaluates
     *  canInstall() fresh at that point. */
    private fun startUpdateDownload(update: UpdateChecker.Result.UpdateAvailable) {
        if (!UpdateInstaller.canInstall(this)) {
            val permDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Needed")
                .setMessage("To install updates, Project A.S.S.E.S.S. needs permission to install unknown apps. You'll be taken to Settings — enable it there, then come back and tap Download again.")
                .setPositiveButton("Open Settings") { _, _ ->
                    requestInstallPermission.launch(UpdateInstaller.installPermissionSettingsIntent(this))
                }
                .setNegativeButton("Cancel", null)
                .show()
            fixDialogButtonColors(permDialog)
            return
        }
        UpdateInstaller.downloadAndInstall(this, update.downloadUrl, update.versionName) {
            android.widget.Toast.makeText(
                this,
                "Downloading update — you'll be prompted to install once it's done.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * v3.1.4: confirmation dialog + optional feedback field, then hands off
     * to the device's own email app via ACTION_SEND — replaces the earlier
     * DiagnosticUploadService HTTP-to-Apps-Script-to-Drive pipeline, which
     * turned out too fragile to keep hosting/debugging (redirect/response-
     * delivery failures that never fully resolved across several deploy
     * attempts — see CHANGELOG history for v3.1–v3.1.3). This has no server
     * to run, no deployment to break, and no network failure mode beyond
     * "does this device have an email app" — checked before launching.
     *
     * Because composing an email is just building an Intent (no network
     * call from this app's own process), there's no async success/failure
     * state to manage here — the positive button can use the normal
     * setPositiveButton listener and the dialog can dismiss immediately.
     * TextInputLayout for the feedback field reuses the exact outlined-box
     * pattern from MainActivity's rename dialogs (same reason: a plain
     * EditText falls back to a stray platform underline with no inset) —
     * accent-tinted the same way (cursor + drag handle).
     */
    private fun showSendDiagnosticReportDialog() {
        val density = resources.displayMetrics.density
        // FIX: this used to follow the accent color (Group A / maroon),
        // which wasn't visibly updating when the accent changed. Rather
        // than chase why, per instruction this is now plain theme-adaptive
        // color instead — black in Light, white in Dark/Black — same as
        // ordinary body text, not accent-linked at all. Uses
        // ThemePrefs.getTheme(context) directly (this codebase's
        // established reliable approach for theme-dependent color, not
        // theme.resolveAttribute()) rather than a hardcoded value, so it
        // still correctly flips with the active theme.
        val themeTextColor = when (ThemePrefs.getTheme(this)) {
            AppTheme.LIGHT -> androidx.core.content.ContextCompat.getColor(this, R.color.tnhs_ink)
            else -> androidx.core.content.ContextCompat.getColor(this, R.color.dark_theme_text_primary)
        }
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox)
        ).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
            hint = "Tell us what happened (Optional)"
            // FIX: this used to also be set as the EditText's own `hint`
            // below — TextInputLayout renders its floating label AND the
            // child EditText's hint at the same time when the field is
            // empty, so the two texts were stacking directly on top of each
            // other. placeholderText is TextInputLayout's own mechanism for
            // a second, more descriptive line distinct from the floating
            // label — same visual slot the plain hint would have used, but
            // coordinated with the label instead of overlapping it.
            placeholderText = "Describe any issue you encountered or suggestions to improve the scanner."
            boxStrokeColor = themeTextColor
            // Explicit setter call, not `hintTextColor = ...` property
            // syntax — TextInputLayout's getHintTextColor()/
            // setHintTextColor() pair may have the same getter/setter
            // nullability mismatch that made `textSelectHandle = ...`
            // silently fail to compile earlier this version (Kotlin only
            // synthesizes a settable property when getter and setter
            // nullability line up exactly). Calling the setter directly
            // sidesteps that regardless of whether it would apply here.
            setHintTextColor(android.content.res.ColorStateList.valueOf(themeTextColor))
            setPlaceholderTextColor(android.content.res.ColorStateList.valueOf(themeTextColor))
        }
        val feedbackInput = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        inputLayout.addView(feedbackInput)

        // v3.1.5 FIX: this field is a TextInputLayout, not a raw EditText —
        // tintCursorDrawable() can't win against TextInputLayout's own
        // internal cursor-color sync. See tintTextInputLayoutCursor's doc
        // comment (same root cause as the Rename Test dialog's cursor).
        AccentColorPrefs.tintTextInputLayoutCursor(inputLayout, themeTextColor)
        AccentColorPrefs.tintTextSelectHandles(feedbackInput, themeTextColor)

        val message = "This opens your email app with a diagnostic report ready to send to the developer.\n\n" +
            "The report includes:\n" +
            "• Scan diagnostics log\n" +
            "• App version\n" +
            "• Device model\n" +
            "• Android version\n" +
            "• Your optional note below\n\n" +
            "The report DOES NOT include:\n" +
            "• Student names\n" +
            "• Answer sheets\n" +
            "• Photos\n" +
            "• PDFs\n" +
            "• Exam content\n" +
            "• Personal files\n\n" +
            "You'll see the email before it sends, so you can review or cancel it there."

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Send Diagnostic Report")
            .setMessage(message)
            .setView(inputLayout)
            .setPositiveButton("Send Report") { _, _ ->
                val feedback = feedbackInput.text?.toString()?.trim().orEmpty()
                sendDiagnosticReportEmail(feedback)
            }
            .setNegativeButton("Cancel", null)
            .show()
        fixDialogButtonColors(dialog)
    }

    /** Builds the diagnostic report email and hands off to whatever email
     *  app is installed via ACTION_SEND — this app never touches the
     *  network itself. Report CONTENT reuses DiagnosticReportGenerator
     *  (app/device info, scanner config constants, the capped scan log,
     *  and this feedback) — the same generator the old upload flow used —
     *  rather than re-deriving a thinner version of the same thing here.
     *  That generator returns a String, not a File, so it's written to a
     *  fresh timestamped file under the already-registered "diagnostics"
     *  external-files folder before being handed to FileProvider — this
     *  is a NEW report file each send (unlike ScanDiagnostics' own rolling
     *  log file), so it doesn't collide with or get overwritten by it.
     *  `type = "message/rfc822"` (rather than a generic "text/plain") is
     *  the conventional way to restrict Android's chooser to actual email
     *  apps specifically, since EXTRA_EMAIL alone doesn't filter which
     *  apps can respond to the intent. resolveActivity() is checked first
     *  so a device with no email app at all gets a clear message instead
     *  of a silently empty/failing chooser. */
    private fun sendDiagnosticReportEmail(feedback: String) {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val reportText = DiagnosticReportGenerator.generate(this, feedback.ifEmpty { null })

        val diagnosticsDir = java.io.File(getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val reportFile = java.io.File(diagnosticsDir, "diagnostic_report_$timestamp.txt")
        reportFile.writeText(reportText)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", reportFile
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("sirjpagdi+project.assess@gmail.com"))
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Project A.S.S.E.S.S. Diagnostic Report - v$versionName")
            putExtra(android.content.Intent.EXTRA_TEXT, "Diagnostic report attached (diagnostic_report_$timestamp.txt).")
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            android.widget.Toast.makeText(
                this,
                "No email app found on this device — install one to send a diagnostic report.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Same fix as other activities' fixDialogButtonColors — AlertDialog
     *  buttons default to colorPrimary text color, unreadable in
     *  Dark/Black themes since those themes never set
     *  android:textColorPrimary. theme.resolveAttribute() wasn't reliable
     *  for this (tried and reverted elsewhere); using ThemePrefs.getTheme()
     *  directly instead. */
    private fun fixDialogButtonColors(dialog: androidx.appcompat.app.AlertDialog) {
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
        // v2.8 fold-in fix: the loop above only ever recolored the BUTTONS.
        // The dialog's title/message text still fell back to the theme's
        // own textColorPrimary/textColorSecondary, which is unset/wrong in
        // this app's themes the same way button text was (see this
        // function's own doc comment) — invisible in Light theme
        // specifically for message text, and unreliable in Dark/Black too.
        // android.R.id.message is part of the core Android alert dialog
        // layout AppCompat reuses, so it's a stable id to reach into here.
        // androidx.appcompat.R.id.alertTitle is the equivalent stable,
        // public id for the title in AppCompat's alert layout — not a
        // private/internal resource, safe to reference directly.
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
