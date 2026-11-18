package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/**
 * New launcher activity (see AndroidManifest.xml — this now holds the
 * MAIN/LAUNCHER intent-filter that MainActivity used to have). Plays the
 * welcome animation (res/raw/welcome_animation.mp4) full-screen, then hands
 * off to MainActivity. Gated by the hidden dev toggle in ChangelogActivity
 * (SettingsPrefs.isWelcomeAnimationEnabled) — when off, this skips straight
 * to MainActivity with no video and no delay, so turning the toggle off is
 * a real bypass, not just a "shorter" animation.
 *
 * Crop-to-fill, not aspect-fit: the reference clip is exactly 1080x1920
 * (9:16) with its animated content composed inward from all four edges —
 * sampled frames showed nothing depends on the very edge of the frame, so
 * cropping slightly on devices with a taller aspect ratio than 9:16 (most
 * current phones run ~19.5:9-20:9) should be safe. Revertible to aspect-fit
 * by swapping CropFillVideoView for a plain VideoView if a real device
 * check ever shows clipping that matters.
 *
 * Fade window: originally implemented as 4.30s-4.55s decimal, tuned for
 * the first reference clip's actual runtime (~4.552s), then retimed to
 * 4.23s-4.48s for the v2.2 clip (~4.506s). v2.3 replaces the clip again
 * with a much shorter ~2.25s one (confirmed via ffprobe) — retimed per
 * spec to 2.10s-2.25s, i.e. the fade now runs right up to the clip's
 * actual end with essentially no safety margin (unlike v2.2's ~26ms
 * buffer) and a shorter 150ms fade duration (was 250ms) to fit the
 * much-shorter available window. Worth watching on-device: less margin
 * means a slightly higher chance of a one-frame freeze flash right at
 * the cut to goToMain() if playback timing drifts at all — flag this if
 * it's visible in testing, since 150ms leaves less room to fix than the
 * old 250ms did.
 */
class WelcomeActivity : AppCompatActivity() {

    companion object {
        /** v2.8 fold-in (item 4): tap-to-replay from the home screen/sidebar
         *  logo passes this so the video always plays regardless of
         *  SettingsPrefs.isWelcomeAnimationEnabled — that toggle exists to
         *  skip the AUTOMATIC cold-start playback, not to block a deliberate
         *  "show me the animation" tap. */
        const val EXTRA_FORCE_PLAY = "force_play"
    }

    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // v3.9.0: fired unconditionally, before the animation/skip branch
        // below, so it runs on every real app launch regardless of which
        // path this activity takes (animation, instant skip, or forced
        // replay from the sidebar logo). Cheap no-op most of the time —
        // UpdateChecker.autoCheckIfDue() only actually hits the network
        // once the internal 24h throttle has elapsed. Entirely silent:
        // any update found is just cached for AboutActivity to resurface
        // later, nothing shown here.
        UpdateChecker.autoCheckIfDue(this)
        // v3.9.2: cleans up any downloaded update APK left over from a
        // previous session — see UpdateInstaller.purgeStaleDownloads's
        // doc comment for why this runs here (launch time) rather than
        // right after handing off to the installer.
        UpdateInstaller.purgeStaleDownloads(this)

        val forcePlay = intent.getBooleanExtra(EXTRA_FORCE_PLAY, false)
        if (!forcePlay && !SettingsPrefs.isWelcomeAnimationEnabled(this)) {
            goToMain()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val videoView = CropFillVideoView(this)
        root.addView(
            videoView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        setContentView(root)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.welcome_animation}")
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            videoView.setVideoSize(mp.videoWidth, mp.videoHeight)
            mp.isLooping = false
            mp.start()

            val fadeStartMs = 2100L
            val fadeDurationMs = 150L
            root.postDelayed({
                root.animate().alpha(0f).setDuration(fadeDurationMs).withEndAction { goToMain() }.start()
            }, fadeStartMs)
        }
        // Safety nets — never strand the user on a black/broken screen if
        // the video is shorter than expected, fails to decode, or the
        // scheduled fade above somehow doesn't fire first.
        videoView.setOnCompletionListener { goToMain() }
        videoView.setOnErrorListener { _, _, _ -> goToMain(); true }
    }

    private fun goToMain() {
        if (handedOff || isFinishing) return
        handedOff = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the activity is destroyed some other way (e.g. the user backs
        // out) before goToMain() ran, don't leave the app with no visible
        // screen — this is a launcher activity, there's nothing to "go
        // back" to underneath it.
        if (!handedOff && !isFinishing) goToMain()
    }
}

/** VideoView subclass that scales up (never down) to fill its parent's
 *  bounds entirely, cropping whichever dimension overflows — the parent
 *  FrameLayout clips to its own bounds by default, so this is the whole
 *  crop-to-fill mechanism, no manual Canvas clipping needed. Falls back to
 *  normal VideoView sizing until setVideoSize() is called from
 *  onPreparedListener (before that, the real video dimensions aren't known
 *  yet). */
private class CropFillVideoView(context: android.content.Context) : VideoView(context) {
    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val viewWidth = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = android.view.View.MeasureSpec.getSize(heightMeasureSpec)
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

        val finalWidth: Int
        val finalHeight: Int
        if (videoRatio > viewRatio) {
            // Video is relatively wider than the view — match the view's
            // height exactly, let width overflow (crop left/right).
            finalHeight = viewHeight
            finalWidth = (viewHeight * videoRatio).toInt()
        } else {
            // Video is relatively taller than the view — match the view's
            // width exactly, let height overflow (crop top/bottom).
            finalWidth = viewWidth
            finalHeight = (viewWidth / videoRatio).toInt()
        }
        setMeasuredDimension(finalWidth, finalHeight)
    }
}
