package com.jpagdi.mcqquickcheck

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap

/**
 * v1.2.5 (tablet landscape): the app's screens were laid out for a phone-width
 * column. This keeps them that way on wide screens instead of stretching every
 * row across a 1200 dp tablet: on screens wider than [MAX_CONTENT_DP] the
 * activity's content is centred with equal side margins, and the activity's own
 * background fills the sides. Phones and narrow windows are untouched.
 *
 * Screens handle rotation themselves (see configChanges in the manifest), so
 * nothing is recreated or lost when a tablet turns; this re-fits on every size
 * change. ScanActivity is portrait-only and is left alone.
 */
class McqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(WideScreen)
    }
}

object WideScreen : Application.ActivityLifecycleCallbacks {
    private const val MAX_CONTENT_DP = 720

    private val listeners = WeakHashMap<ViewGroup, View.OnLayoutChangeListener>()

    private fun fit(activity: Activity, content: ViewGroup) {
        val density = activity.resources.displayMetrics.density
        val maxPx = (MAX_CONTENT_DP * density).toInt()
        val width = if (content.width > 0) content.width else activity.resources.displayMetrics.widthPixels
        val side = ((width - maxPx) / 2).coerceAtLeast(0)
        if (content.paddingLeft != side || content.paddingRight != side) {
            if (side > 0 && content.paddingLeft == 0) {
                // Let the page background show behind the side margins too.
                content.getChildAt(0)?.background?.constantState?.newDrawable()?.let {
                    activity.window.setBackgroundDrawable(it)
                }
            }
            content.setPadding(side, content.paddingTop, side, content.paddingBottom)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is ScanActivity) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        fit(activity, content)
        if (listeners.containsKey(content)) return
        val l = View.OnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) v.post { fit(activity, content) }
        }
        listeners[content] = l
        content.addOnLayoutChangeListener(l)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
