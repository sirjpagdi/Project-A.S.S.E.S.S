package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.content.DialogInterface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * v1.2.0 — shared copy of the dialog-colour fix MainActivity and the other
 * screens each carry privately: AlertDialog text defaults to colorPrimary /
 * unset text colours, unreadable in Dark/Black (buttons) and Light (message).
 * Uses ThemePrefs.getTheme() directly, never theme.resolveAttribute() (see the
 * engineering notes). Call right after dialog.show().
 */
object DialogColors {
    fun fix(activity: Activity, dialog: AlertDialog) {
        HapticUtils.confirm(dialog.window?.decorView)
        val color = if (ThemePrefs.getTheme(activity) == AppTheme.LIGHT) {
            android.graphics.Color.parseColor("#221111")
        } else {
            android.graphics.Color.WHITE
        }
        for (which in intArrayOf(
            DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL
        )) {
            dialog.getButton(which)?.setTextColor(color)
        }
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
