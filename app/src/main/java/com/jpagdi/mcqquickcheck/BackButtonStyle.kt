package com.jpagdi.mcqquickcheck

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.widget.Button

/**
 * Rounded-card background whose LEFT edge comes to a point (like a pencil
 * nib / arrow), instead of bg_card_rounded's symmetric rounded corners on
 * all four corners. Used only on "Back" buttons across the app, per
 * explicit request, so a Back action reads differently at a glance from
 * every other rounded-card button.
 *
 * A plain XML <shape> can't express this — <corners> only ever ROUNDS a
 * corner (via radius), it can never bring one to a point — so this is a
 * real Drawable subclass that rebuilds its own Path from the view's
 * actual bounds in onBoundsChange(), meaning it scales correctly with
 * the button's real width/height instead of being a fixed-size asset
 * that would stretch or tile badly across different button widths.
 *
 * Manually implements setTintList()/onStateChange() because a fully
 * custom Drawable subclass does NOT get automatic backgroundTintList
 * support the way GradientDrawable (bg_card_rounded, bg_stepper_circle,
 * etc.) does out of the box — AccentColorPrefs relies on
 * view.backgroundTintList for every other maroon/gold button in this
 * app, so this has to honor that same mechanism or Back buttons would
 * silently stop following the Top Panel accent color.
 */
class PointedBackButtonBackground : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.DKGRAY
    }
    private val path = Path()
    private var tintList: ColorStateList? = null

    private fun rebuildPath(bounds: Rect) {
        path.reset()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val r = (h / 2.6f).coerceAtMost(w / 3f) // right-corner radius
        val pointInset = (h * 0.30f).coerceAtMost(w / 3f) // how far the point's shoulders sit in from the left edge

        path.moveTo(pointInset, 0f)
        path.lineTo(w - r, 0f)
        path.quadTo(w, 0f, w, r)
        path.lineTo(w, h - r)
        path.quadTo(w, h, w - r, h)
        path.lineTo(pointInset, h)
        path.lineTo(0f, h / 2f) // the point itself
        path.close()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath(bounds)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java, still required override for API < 29")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun isStateful(): Boolean = tintList?.isStateful == true

    override fun setTintList(tint: ColorStateList?) {
        tintList = tint
        applyTint()
    }

    override fun onStateChange(state: IntArray): Boolean = applyTint()

    private fun applyTint(): Boolean {
        val list = tintList ?: return false
        val resolved = list.getColorForState(state, list.defaultColor)
        if (paint.color != resolved) {
            paint.color = resolved
            invalidateSelf()
            return true
        }
        return false
    }
}

/** Applies the app's standard look to any button that plays a "Back"
 *  role, plus its back-arrow icon — call this instead of hand-repeating
 *  the same background/icon setup at every one of this app's several
 *  "Back" buttons (Backup/Restore/Help/About's shared placeholder,
 *  Settings' own copy).
 *
 *  v3.7 fold-in: was PointedBackButtonBackground (a custom Drawable with
 *  a pointed left edge, see the class above), reverted per explicit
 *  request back to the same bg_card_rounded shape every other button in
 *  the app uses. bg_card_rounded is a plain GradientDrawable-backed
 *  <shape>, so unlike the custom Drawable it doesn't need any
 *  hand-rolled setTintList()/onStateChange() forwarding —
 *  AccentColorPrefs' Button-branch backgroundTintList assignment
 *  (already tag="accent_maroon" in each screen's XML) works on it
 *  natively.
 *
 *  v3.7 fix: icon+text weren't centering as a unit — a plain Button's
 *  compound drawable sits at a fixed edge inset with the text merely
 *  centered in what's left over, not truly centered together. Switched
 *  to centerIconAndText() below (an inline ImageSpan baked into the
 *  button's own text, rather than a compound drawable) so gravity=center
 *  centers the icon+text pair as a whole, same fix family as the
 *  LinearLayout conversions used elsewhere for the same reason — just
 *  done without changing these buttons' type, since BackButtonStyle is
 *  shared across five call sites that all declare a plain Button. */
object BackButtonStyle {
    fun apply(button: Button) {
        button.background = androidx.core.content.ContextCompat.getDrawable(
            button.context, R.drawable.bg_card_rounded
        )
        centerIconAndText(
            button,
            androidx.core.content.ContextCompat.getDrawable(button.context, R.drawable.ic_arrow_back),
            Color.WHITE
        )
    }
}

/** Bakes [icon] into [button]'s own text as a leading ImageSpan (instead
 *  of a compound drawable) and sets gravity=center, so the icon and the
 *  label center together as one unit. Used by BackButtonStyle above, and
 *  directly by BackupDataActivity/RestoreDataActivity for their own
 *  "Export Backup" / "Choose Backup File" buttons, which have the same
 *  centering problem as a plain Button with drawableStart.
 *
 *  Must be called AFTER button.text is already set to its final label —
 *  it reads button.text to build the spannable, so calling it before the
 *  real label is set would bake in stale/empty text.
 *
 *  v3.7.6: also sets textStyle bold, matching the LinearLayout-card
 *  button pattern used elsewhere (Capture Photo, Scan Next/Done, Use
 *  This as Answer Key, etc.), which already had bold set directly in
 *  XML — this was the one gap where plain-Button icon+text pairings
 *  weren't bold while the newer card-style ones were.
 *
 *  v3.7.7: also uppercases the label, for the same reason as bold above
 *  — every LinearLayout-card button uses android:textAllCaps="true" in
 *  XML, and a plain Button's android:textAllCaps would normally do the
 *  same, but that attribute has no effect here since the label is baked
 *  into a SpannableString rather than left as the Button's real text
 *  property. Uppercasing the string before building the spannable is
 *  the equivalent fix for this code path. Button.text is left as the
 *  ORIGINAL (non-uppercased) string wherever it's read elsewhere — this
 *  only affects what's rendered on screen, matching how textAllCaps
 *  itself works (display-only transform, doesn't change the real text
 *  value other code might compare against). */
fun centerIconAndText(
    button: Button,
    icon: android.graphics.drawable.Drawable?,
    tint: Int,
    iconSizeDp: Int = 18
) {
    button.gravity = android.view.Gravity.CENTER
    button.setTypeface(button.typeface, android.graphics.Typeface.BOLD)
    button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
    val sizePx = (iconSizeDp * button.resources.displayMetrics.density).toInt()
    val sizedIcon = icon?.mutate()?.apply {
        setTint(tint)
        setBounds(0, 0, sizePx, sizePx)
    }
    val label = button.text.toString().uppercase()
    val spannable = android.text.SpannableString("  $label")
    if (sizedIcon != null) {
        spannable.setSpan(
            android.text.style.ImageSpan(sizedIcon, android.text.style.ImageSpan.ALIGN_CENTER),
            0, 1,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    button.text = spannable
}
