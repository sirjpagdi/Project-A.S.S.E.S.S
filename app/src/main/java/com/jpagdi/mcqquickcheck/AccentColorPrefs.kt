package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

/**
 * Two independently-recolorable accent groups:
 *  - GROUP_A ("Top Panel Accent") replaces every currently-maroon surface
 *    (screen header bars, the drawer header) and the status bar.
 *  - GROUP_B ("Button Accent") replaces every currently-gold surface
 *    (the app's ?attr/colorAccent-tinted buttons/icons).
 *
 * WHY TAGS INSTEAD OF findViewById PER SCREEN: Android can't swap a theme's
 * ?attr/colorAccent at runtime, and hunting down every affected view by ID
 * across 11 screens (see repo-wide grep before this file was added) would
 * mean re-doing that list by hand every time a screen adds a new maroon/gold
 * element, with no way to catch a missed one at compile time. Instead, any
 * XML view that should follow an accent group gets android:tag="accent_maroon"
 * / "accent_gold" (or the _text variants for auto-contrast text), and
 * applyAccents() below walks the view tree once per screen and retints
 * whatever it finds tagged. Adding a new accent-colored view later is then
 * a one-line tag in XML, not a code change here.
 *
 * PICKING THE RIGHT TAG FOR A CHILD ICON/TEXT (v3.7):
 *  - Child sits on a NEUTRAL (theme-only) background and should itself be
 *    accent-colored -> accent_maroon_icon / accent_maroon_text / accent_gold_icon
 *    / accent_gold_text. These tint straight to the group color.
 *  - Child sits ON TOP OF a view that is ITSELF tagged accent_maroon /
 *    accent_gold (its parent card's own background is being tinted to that
 *    same group in this same pass) -> use the _on suffix instead:
 *    accent_maroon_icon_on / accent_maroon_text_on / accent_gold_icon_on /
 *    accent_gold_text_on. These compute readable contrast against the group
 *    color rather than matching it, so the icon/text doesn't disappear
 *    against its own freshly-tinted background.
 */
object AccentColorPrefs {
    private const val PREFS = "mcq_scanner_prefs"
    private const val KEY_GROUP_A = "accent_group_a"
    private const val KEY_GROUP_B = "accent_group_b"

    // Defaults match the original hardcoded tnhs_maroon / tnhs_gold values —
    // "Revert to Default" restores exactly these, not a re-guess.
    const val DEFAULT_GROUP_A = 0xFF6B0F14.toInt() // tnhs_maroon
    const val DEFAULT_GROUP_B = 0xFFE8B923.toInt() // tnhs_gold

    /** A fixed palette for the swatch grid — not a continuous picker.
     *  Deliberately includes the two current defaults as the first entries
     *  of their respective natural family so "Default" is always one tap
     *  away from the grid itself, in addition to the explicit Revert button. */
    val PRESET_SWATCHES = listOf(
        0xFF6B0F14.toInt(), 0xFFB0293A.toInt(), 0xFF7A1F3D.toInt(), 0xFF4A0709.toInt(),
        0xFFE8B923.toInt(), 0xFFD4A017.toInt(), 0xFFC77B2E.toInt(), 0xFF8A6D3B.toInt(),
        0xFF1F5C4A.toInt(), 0xFF2E7D32.toInt(), 0xFF1565A8.toInt(), 0xFF2A3F8F.toInt(),
        0xFF5C2D91.toInt(), 0xFF8E44AD.toInt(), 0xFF37474F.toInt(), 0xFF212121.toInt()
    )

    fun getGroupA(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_GROUP_A, DEFAULT_GROUP_A)

    fun getGroupB(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_GROUP_B, DEFAULT_GROUP_B)

    fun setGroupA(context: Context, color: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_GROUP_A, color).apply()
    }

    fun setGroupB(context: Context, color: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_GROUP_B, color).apply()
    }

    fun revertGroupA(context: Context) = setGroupA(context, DEFAULT_GROUP_A)
    fun revertGroupB(context: Context) = setGroupB(context, DEFAULT_GROUP_B)

    /** Relative luminance (WCAG-style, sRGB), used to decide readable text
     *  color on an arbitrary chosen background rather than a manual picker. */
    fun luminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel(Color.red(color))
        val g = channel(Color.green(color))
        val b = channel(Color.blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Simple, decisive threshold rather than full WCAG contrast-ratio math
     *  against both candidates — for a single swatch background this reduces
     *  to "is it closer to white or black," which luminance alone answers. */
    fun textColorFor(backgroundColor: Int): Int =
        if (luminance(backgroundColor) > 0.42) Color.BLACK else Color.WHITE

    /** Lightens (positive) or darkens (negative) a color in HSV space.
     *  amount is -1f..1f. Used by the picker's horizontal slider. */
    fun adjustLightness(color: Int, amount: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + amount).coerceIn(0.08f, 1f) // never fully black or blown-out white
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    /** Call after setContentView() in every drawer-enabled screen. Walks the
     *  whole content view tree once; cheap for this app's screen sizes (a
     *  few dozen views max) and avoids maintaining a manual view list. */
    fun applyAccents(activity: Activity) {
        val groupA = getGroupA(activity)
        val groupB = getGroupB(activity)
        val root = activity.findViewById<View>(android.R.id.content)
        walk(root, groupA, groupB)

        // Status bar follows Group A directly, per spec — no separate
        // "dark" variant computed for it (unlike the original fixed
        // tnhs_maroon_dark), so a very light chosen color will show as a
        // light status bar. That's an intentional tradeoff of a single
        // user-chosen color rather than reintroducing a second color the
        // teacher would also have to manage.
        activity.window.statusBarColor = groupA
    }

    private fun walk(view: View?, groupA: Int, groupB: Int) {
        if (view == null) return
        when (view.tag as? String) {
            "accent_maroon" -> tintSurface(view, groupA)
            "accent_maroon_text" -> (view as? TextView)?.setTextColor(textColorFor(groupA))
            // v3.0: for an icon-only ImageView with no real background of
            // its own (the hamburger menu glyph, ic_menu) — tintSurface's
            // existing ImageView branch sets backgroundTintList, which
            // does nothing visible here since there's no background
            // drawable to tint. imageTintList tints the actual glyph
            // pixels instead, same idea as _text for TextViews.
            "accent_maroon_icon" -> (view as? ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(groupA)
            // v3.7 fix: for an icon/text that sits ON TOP of a surface this
            // SAME walk() call is about to tint with groupA/groupB (e.g. the
            // Scan Filled Answer Sheets card's own icon+label, tagged
            // accent_gold on the parent AND accent_gold_icon/_text on the
            // children) — tinting the child to the identical group color as
            // its own background makes it invisible (gold icon on a gold
            // card, maroon icon on a maroon header bar). These "_on" tags
            // compute readable contrast against that same group color
            // instead, the same textColorFor() logic already used for
            // pill-toggle checked-state text and generic Button text above.
            // Use accent_maroon_icon/_text (not _on) only when the icon/text
            // sits on a NEUTRAL theme background and should itself read as
            // maroon-colored — e.g. folderNavRow's chevrons in Scanned
            // Sheets, which have no accent-tinted parent.
            "accent_maroon_icon_on" -> (view as? ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(textColorFor(groupA))
            "accent_maroon_text_on" -> (view as? TextView)?.setTextColor(textColorFor(groupA))
            "accent_gold" -> tintSurface(view, groupB)
            "accent_gold_text" -> (view as? TextView)?.setTextColor(textColorFor(groupB))
            "accent_gold_icon" -> (view as? ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(groupB)
            "accent_gold_icon_on" -> (view as? ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(textColorFor(groupB))
            "accent_gold_text_on" -> (view as? TextView)?.setTextColor(textColorFor(groupB))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), groupA, groupB)
        }
    }

    private fun tintSurface(view: View, color: Int) {
        when (view) {
            // RadioButton and Switch/SwitchCompat both extend Button in
            // Android's view hierarchy (RadioButton -> CompoundButton ->
            // Button -> TextView), so these two branches MUST come before
            // the generic "is Button" branch below, or Kotlin's `when`
            // would match Button first and tint the wrong property —
            // backgroundTintList affects a RadioButton/Switch's invisible
            // rectangular background, not the circular marker or thumb/
            // track people actually see.
            is android.widget.CheckBox -> {
                // Same hierarchy note as RadioButton above (CheckBox also
                // extends CompoundButton -> Button -> TextView) — must be
                // its own branch before the generic "is Button" case below.
                view.buttonTintList = android.content.res.ColorStateList.valueOf(color)
            }
            is android.widget.RadioButton -> {
                if (view.buttonDrawable == null) {
                    // v3.8.4: pill-toggle style (android:button="@null",
                    // used by A–D/A–E and the storage-resolution toggle)
                    // has no marker to tint — the view's own background
                    // (bg_pill_toggle_option.xml, a checked/unchecked
                    // state selector) IS what's visible. backgroundTintList
                    // with the default SRC_IN mode respects each state's
                    // alpha, so the unchecked state's fully-transparent
                    // fill stays invisible after tinting while the checked
                    // state's opaque fill becomes the chosen accent color
                    // — no extra per-state logic needed.
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                    // Text also needs to react: the XML's static
                    // text_pill_toggle.xml color selector assumed the
                    // checked pill would always be light gold (hence
                    // hardcoded black), which breaks if the chosen accent
                    // is dark. Replaces it with a real ColorStateList
                    // computed from the actual chosen color's luminance
                    // for the checked state; unchecked state falls back to
                    // this app's plain Light/Dark/Black text color exactly
                    // as text_pill_toggle.xml did (reads ThemePrefs
                    // directly rather than theme.resolveAttribute(), for
                    // the same reliability reason as the dialog-button and
                    // folder-tab fixes elsewhere in this app).
                    val normalTextColor =
                        if (ThemePrefs.getTheme(view.context) == AppTheme.LIGHT)
                            Color.parseColor("#221111")
                        else Color.WHITE
                    view.setTextColor(
                        android.content.res.ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                            intArrayOf(textColorFor(color), normalTextColor)
                        )
                    )
                } else {
                    // Flat color for both checked/unchecked states, matching
                    // this app's existing (pre-accent-system) visual behavior
                    // where the theme's colorAccent tinted the ring in BOTH
                    // states, not just the filled dot — so this isn't a new
                    // look, just the same look now following the user's choice.
                    view.buttonTintList = android.content.res.ColorStateList.valueOf(color)
                }
            }
            is android.widget.Switch -> {
                val tintList = onOffColorStateList(color)
                view.thumbTintList = tintList
                view.trackTintList = tintList
            }
            is Button -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                view.setTextColor(textColorFor(color))
            }
            // v3.1 FIX: EditText (and TextInputEditText, which extends it)
            // used to fall into the generic `else` branch below, which only
            // sets backgroundTintList — that tints the underline but leaves
            // the blinking cursor and the drag "handle" (teardrop selection
            // locator) on the theme's fixed default gold, since those are
            // two separate drawables/attributes neither one touches. The
            // Kotlin-built dialog EditTexts (rename, Send Diagnostic
            // Report) already set all three explicitly; this brings every
            // XML-tagged EditText (Generate/Edit Template, Settings) up to
            // the same standard instead of just the underline.
            is EditText -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                tintCursorDrawable(view, color)
                tintTextSelectHandles(view, color)
            }
            is ImageView -> view.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            // v2.8 fold-in fix: this used to be an unconditional
            // view.setBackgroundColor(color) for anything that wasn't a
            // Button/ImageView/RadioButton/Switch — which REPLACES the
            // view's entire background with a flat rectangular
            // ColorDrawable, discarding whatever shape drawable was there
            // before. That's correct for a view whose background genuinely
            // IS just a flat color (e.g. the maroon header bars elsewhere
            // in this app), but it silently squared off the round FAB
            // (fab_circle_background.xml, an oval shape drawable) into a
            // rectangle, since the FAB is a plain TextView here, not a
            // Button. backgroundTintList tints whatever drawable is
            // already there — including a flat ColorDrawable, which
            // renders identically to setBackgroundColor for that case — so
            // preferring it whenever a background drawable already exists
            // fixes the FAB without changing anything about the flat-color
            // cases.
            else -> {
                if (view.background != null) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                } else {
                    view.setBackgroundColor(color)
                }
            }
        }
    }

    /** v3.1.3 FIX: tints an EditText's blinking cursor drawable. Replaces
     *  four scattered direct `.setTint()` calls (rename-test dialog,
     *  save-flow rename dialog — which was missing the cursor tint
     *  entirely — the XML-tagged-field walk below, and the diagnostic
     *  feedback box) that all had the same two gaps:
     *  1) No `.mutate()` before `.setTint()` — same shared-ConstantState
     *     risk documented on tintTextSelectHandles below, which already
     *     mutates for exactly this reason.
     *  2) Called immediately at view-creation time, before the EditText is
     *     attached to a window or has gained focus. The Editor owning the
     *     cursor drawable can re-derive/reset it from the theme's default
     *     textCursorDrawable resource when focus is first gained (e.g. the
     *     field auto-focusing when a dialog appears, or the user tapping
     *     in) — discarding a tint applied before that point. Deferring via
     *     `.post { }` runs after attach/layout/focus settle, so the tint
     *     sticks. Unverified on a real device — this addresses two
     *     concrete, confirmed gaps in the code, but the exact Editor
     *     timing can only be confirmed by testing the rename dialog live.
     *  Requires API 29 (Q) — getTextCursorDrawable()/setTextCursorDrawable()
     *  don't exist below that; a no-op there leaves the theme's default
     *  cursor color, same accepted limitation as tintTextSelectHandles'
     *  P floor below. */
    fun tintCursorDrawable(editText: EditText, color: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            fun apply() {
                val mutable = editText.textCursorDrawable?.mutate() ?: return
                mutable.setTint(color)
                editText.setTextCursorDrawable(mutable)
            }
            apply()
            editText.post { apply() }
            editText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) apply() }
        }
    }

    /**
     * v3.1.5 FIX: tintCursorDrawable() above tints the child EditText's
     * cursor drawable directly — that works for a PLAIN EditText, but for
     * a field wrapped in Material's TextInputLayout (the outlined-box
     * style used by the Rename Test dialog and the Send Diagnostic Report
     * feedback field), TextInputLayout runs its OWN internal cursor-color
     * sync tied to the box's stroke-color state, and that sync overrides
     * anything set directly on the child EditText — no matter when it's
     * (re)applied. That's why v3.1.4's focus-listener reapply still didn't
     * stick for either of those two fields specifically: both are
     * TextInputLayout, not a raw EditText. TextInputLayout has its own
     * dedicated API for exactly this case (setCursorColor) — use that
     * instead of continuing to fight its internal sync from outside.
     */
    fun tintTextInputLayoutCursor(inputLayout: com.google.android.material.textfield.TextInputLayout, color: Int) {
        inputLayout.setCursorColor(android.content.res.ColorStateList.valueOf(color))
    }

    /** v3.1 (item 3): tints an EditText's drag "handle" (the teardrop
     *  selection locator) to the given accent color. This is a SEPARATE
     *  drawable/attribute from textCursorDrawable (the blinking cursor bar,
     *  already fixed at each rename-dialog call site) — textSelectHandle /
     *  textSelectHandleLeft / textSelectHandleRight otherwise keep following
     *  the theme's static colorAccent regardless of the user's chosen accent.
     *  The three setTextSelectHandle*() setters require API 28+
     *  (Build.VERSION_CODES.P); below that this is a no-op and the handle
     *  stays the theme's default gold — an accepted, documented limitation
     *  rather than a fragile reflection-based workaround on older API levels. */
    fun tintTextSelectHandles(editText: EditText, color: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // .mutate() before .setTint() so this doesn't retint a drawable
            // instance shared with (and thus also visually changing) other
            // views using the same default handle drawable.
            fun tinted(drawable: android.graphics.drawable.Drawable?): android.graphics.drawable.Drawable? {
                val mutable = drawable?.mutate() ?: return null
                mutable.setTint(color)
                return mutable
            }
            // BUILD FIX: Kotlin exposes getTextSelectHandle()/setTextSelectHandle()
            // as a property ONLY when getter/setter line up exactly — here the
            // setter requires a non-null Drawable while the getter can return
            // null, so Kotlin only synthesizes a read-only `val`, not a `var`.
            // `editText.textSelectHandle = ...` therefore fails to compile
            // ("Val cannot be reassigned") even though it looks like valid
            // property syntax. Calling the Java setter methods directly side-
            // steps this — and the ?.let also fixes a real null-safety gap the
            // property syntax was masking, since these setters reject null.
            tinted(editText.textSelectHandle)?.let { editText.setTextSelectHandle(it) }
            tinted(editText.textSelectHandleLeft)?.let { editText.setTextSelectHandleLeft(it) }
            tinted(editText.textSelectHandleRight)?.let { editText.setTextSelectHandleRight(it) }
        }
    }

    /** Unlike a RadioButton (which this app already showed in one flat
     *  color for both states before the accent system existed), a Switch's
     *  whole point is the on/off color contrast — making both states the
     *  chosen accent color would make it impossible to tell at a glance
     *  whether a toggle is on. Keeps the off state a neutral gray instead
     *  of also being the accent color. */
    private fun onOffColorStateList(activeColor: Int): android.content.res.ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(activeColor, Color.argb(150, 128, 128, 128))
        return android.content.res.ColorStateList(states, colors)
    }
}
