package com.jpagdi.mcqquickcheck

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/**
 * v3.7.4: swipe-left-to-delete for the app's plain-LinearLayout list rows
 * (home screen's saved-tests list, scanned-sheet list). The app has no
 * RecyclerView anywhere — every list is a vertical LinearLayout of
 * manually-built rows — so this can't use the standard
 * androidx.recyclerview ItemTouchHelper. Instead this is a per-ROW
 * OnTouchListener that translates just that row horizontally; it
 * deliberately does NOT touch the container's own touch handling, so
 * normal vertical list scrolling is completely unaffected (the
 * documented ACTION_DOWN-interception trap that broke scrolling
 * elsewhere in this app doesn't apply here, since nothing at the
 * container level is intercepting anything).
 *
 * Usage: wrap an already-built row content view with wrapRowWithSwipeDelete(),
 * which returns a FrameLayout to add to the list container in the row's
 * place. The red delete layer sits underneath the row content and is
 * revealed as the content is dragged left; releasing past the reveal
 * threshold snaps open (tap the revealed icon to confirm-delete), releasing
 * short of it snaps back closed. A second swipe-open row auto-closes when
 * another row is opened, so at most one delete affordance is visible at a
 * time — this avoids a stray open row being accidentally tapped later.
 */
object SwipeToDeleteHelper {

    private var currentlyOpenRow: View? = null

    /**
     * Wraps [rowContent] (an already fully-built row — click listeners,
     * background, etc. all already set by the caller) in a FrameLayout
     * with a red delete-reveal layer behind it. [onConfirmedDelete] fires
     * only after the user taps the revealed delete icon AND confirms the
     * dialog — never on swipe alone, matching every other delete flow in
     * this app.
     */
    fun wrapRowWithSwipeDelete(
        context: Context,
        rowContent: View,
        deleteLabel: String,
        fixDialogButtonColors: (androidx.appcompat.app.AlertDialog) -> Unit,
        onConfirmedDelete: () -> Unit
    ): FrameLayout {
        val dp = context.resources.displayMetrics.density
        val revealWidth = (72 * dp)

        val deleteLayer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#C62828")) // consistent red across all themes — this is a delete-danger color, not a themed surface, so it deliberately does NOT go through AccentColorPrefs/bg_card_rounded
            // v3.7.4: fixed width matching revealWidth, right-aligned
            // within the wrapper via FrameLayout.LayoutParams gravity —
            // was MATCH_PARENT width with center gravity, which centered
            // the delete icon across the entire row's width (including
            // the still-covered portion) instead of within just the
            // sliver actually revealed by the swipe.
            layoutParams = FrameLayout.LayoutParams(
                revealWidth.toInt(), FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.END }
        }
        val deleteIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_delete))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp.times(24).toInt(), dp.times(24).toInt())
            contentDescription = deleteLabel
        }
        deleteLayer.addView(deleteIcon)

        val wrapper = FrameLayout(context).apply {
            layoutParams = rowContent.layoutParams
        }
        // rowContent's own layoutParams already carry the list's row
        // margins/sizing (set by the caller before this function runs) —
        // reuse them on the wrapper, then give rowContent fresh MATCH_PARENT
        // params scoped to the wrapper itself.
        rowContent.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )

        wrapper.addView(deleteLayer)
        wrapper.addView(rowContent)

        deleteLayer.setOnClickListener {
            confirmAndDelete(context, deleteLabel, rowContent, fixDialogButtonColors, onConfirmedDelete)
        }

        var downX = 0f
        var downTranslation = 0f
        var isDragging = false
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

        rowContent.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downTranslation = view.translationX
                    isDragging = false
                    // Never consume DOWN — this is what preserves normal
                    // click/long-click on the row and, critically, lets an
                    // ancestor ScrollView still claim the gesture for
                    // vertical scroll if the user's first movement is
                    // vertical rather than horizontal.
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    if (!isDragging && kotlin.math.abs(dx) > touchSlop) {
                        isDragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (isDragging) {
                        val newTranslation = (downTranslation + dx).coerceIn(-revealWidth, 0f)
                        view.translationX = newTranslation
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        val openThreshold = -revealWidth / 2
                        if (view.translationX < openThreshold) {
                            snapOpen(view, revealWidth, wrapper)
                        } else {
                            snapClosed(view)
                        }
                        isDragging = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        return wrapper
    }

    private fun snapOpen(rowContent: View, revealWidth: Float, wrapper: FrameLayout) {
        // Close whichever other row was previously left open — at most one
        // delete affordance visible at a time, so a stale open row can't be
        // mistakenly tapped later after the list has scrolled.
        currentlyOpenRow?.let { if (it !== rowContent) snapClosed(it) }
        currentlyOpenRow = rowContent
        rowContent.animate().translationX(-revealWidth).setDuration(150).start()
    }

    private fun snapClosed(rowContent: View) {
        rowContent.animate().translationX(0f).setDuration(150).start()
        if (currentlyOpenRow === rowContent) currentlyOpenRow = null
    }

    /** Call when rebuilding the whole list (e.g. rebuildRows()) so a stale
     *  reference from a since-removed row isn't held onto. */
    fun resetOpenState() {
        currentlyOpenRow = null
    }

    private fun confirmAndDelete(
        context: Context,
        deleteLabel: String,
        rowContent: View,
        fixDialogButtonColors: (androidx.appcompat.app.AlertDialog) -> Unit,
        onConfirmedDelete: () -> Unit
    ) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Delete $deleteLabel?")
            .setMessage("This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                onConfirmedDelete()
            }
            .setNegativeButton("Cancel") { _, _ ->
                snapClosed(rowContent)
            }
            .setOnCancelListener { snapClosed(rowContent) }
            .show()
        // Reuses the CALLING activity's own fixDialogButtonColors (passed
        // in) rather than duplicating that logic here — this app's
        // Dark/Black dialog-contrast fix already exists per-activity
        // (MainActivity.fixDialogButtonColors, etc); re-implementing a
        // second, slightly-different copy inside this shared helper is
        // exactly the kind of drift that caused the recurring dialog-
        // contrast bug class in the first place.
        fixDialogButtonColors(dialog)
    }
}
