package com.jpagdi.mcqquickcheck

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Wires the standard nav drawer (Backup, Restore, Theme, Settings, About,
 * version footer) the same way on every screen, instead of duplicating
 * the same ~20-line block in each Activity. Originally only MainActivity
 * had a drawer; every other screen used a plain back/close icon instead.
 * Now every screen gets full sidebar access via a hamburger icon,
 * top-left, per explicit request — this helper is what makes that
 * consistent instead of six-plus copies that could drift apart.
 *
 * Requires the screen's own layout to: wrap its content in a
 * DrawerLayout, `<include layout="@layout/drawer_content" .../>` as the
 * drawer panel, and have some hamburger-icon View to open it. See
 * TestDetailActivity/SavedScansListActivity for the reference pattern.
 */
object DrawerHelper {

    fun attach(activity: AppCompatActivity, drawerLayout: DrawerLayout, hamburger: View) {
        hamburger.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // v3.6.10: Press Start 2P for the sidebar's "MCQ" / "Quick Check"
        // title, set here (not in each Activity) so every one of the 12
        // screens that call attach() gets it — previously only
        // MainActivity set this itself, so the sidebar rendered in the
        // theme's Montserrat everywhere else. Not set via
        // android:fontFamily in drawer_content.xml — see the comment
        // above tvDrawerTitleLine1 in that file for why that doesn't
        // work on this app's minSdk.
        // A single-line/autosize version was tried and reverted — it
        // changed "MCQ" (never asked for) and merged both lines into one
        // when only "Quick Check" was ever clipping. Back to two
        // TextViews, matching activity_main.xml's home-screen header
        // sizes (18sp / 9sp) exactly, which were already confirmed
        // working and never needed to change.
        // requestLayout() after the swap: each TextView's wrap_content
        // height was already measured once during inflation, before this
        // code runs, using whatever font was active then (Montserrat).
        // Press Start 2P's glyphs are taller per line at the same sp, so
        // without forcing a re-measure here the swapped-in font paints
        // outside the box sized for the old one — line 1 ("MCQ") visibly
        // overlapping into line 2 ("Quick Check") below it. This alone
        // wasn't enough last time specifically because "Quick Check" was
        // also clipping horizontally at the old 300dp width, which
        // muddied whether the vertical fix was working — with the drawer
        // now widened below, there's no forced wrap/clip fighting the
        // requestLayout() fix at the same time.
        val pressStart2P = ResourcesCompat.getFont(activity, R.font.press_start_2p)
        val drawerLine1 = drawerLayout.findViewById<TextView>(R.id.tvDrawerTitleLine1)
        val drawerLine2 = drawerLayout.findViewById<TextView>(R.id.tvDrawerTitleLine2)
        drawerLine1?.typeface = pressStart2P
        drawerLine1?.requestLayout()
        drawerLine2?.typeface = pressStart2P
        drawerLine2?.requestLayout()

        // v3.6.10: drawer widened to roughly half the screen, per explicit
        // request ("up to the center of the screen"). Set here in code
        // rather than a fixed dp in drawer_content.xml, since a fixed dp
        // is a guess that's already been wrong twice this release — this
        // computes the actual half-width of whatever device it's running
        // on, using the drawer root's explicit ID (drawerRootPanel) so
        // there's no ambiguity about which view is being resized.
        val screenWidthPx = activity.resources.displayMetrics.widthPixels
        drawerLayout.findViewById<LinearLayout>(R.id.drawerRootPanel)?.let {
            val params = it.layoutParams
            // v1.2.5: half the screen, but never wider than 360 dp (a tablet in
            // landscape would otherwise get a 600 dp drawer).
            params.width = minOf(screenWidthPx / 2, (360 * activity.resources.displayMetrics.density).toInt())
            it.layoutParams = params
        }

        // v2.8 fold-in fix (item 7): setTheme() only takes effect for the
        // Activity instance it was called on, at its own onCreate() — it
        // can't be re-applied from onResume() for the SAME instance
        // (must run before super.onCreate()/setContentView()). So if the
        // theme changes while THIS screen is sitting underneath another
        // one in the back stack, this instance is still carrying whatever
        // theme was active when it was created, and nothing normally
        // tells it otherwise when the user navigates back to it — Android
        // doesn't re-run onCreate() just from resuming an existing
        // Activity. Recording the theme active at attach()-time (this
        // runs from onCreate(), same timing setTheme() needed) and
        // comparing it against the CURRENT persisted value every time this
        // screen resumes is what actually catches the mismatch; recreate()
        // then re-runs onCreate() with the up-to-date theme. Centralized
        // here instead of copy-pasted into 11 onResume() overrides.
        val themeAtCreate = ThemePrefs.getTheme(activity)
        var groupAAtCreate = AccentColorPrefs.getGroupA(activity)
        var groupBAtCreate = AccentColorPrefs.getGroupB(activity)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (ThemePrefs.getTheme(activity) != themeAtCreate) {
                    activity.recreate()
                    return
                }
                refreshClassesRow(activity, drawerLayout)
                // v2.8 fold-in fix (item 6): accent color Save previously
                // only ever became visible on OTHER already-open screens
                // as an accidental side effect of a theme-triggered
                // recreate() — there was no direct mechanism of its own.
                // Same staleness class as the theme check above, so
                // extended the same observer rather than building a
                // second parallel one. applyAccents() alone (no full
                // recreate) is enough here — unlike a theme change, an
                // accent color doesn't need setTheme()/onCreate() to
                // re-run, just the tagged-view walk to re-run with the
                // new color.
                val currentA = AccentColorPrefs.getGroupA(activity)
                val currentB = AccentColorPrefs.getGroupB(activity)
                if (currentA != groupAAtCreate || currentB != groupBAtCreate) {
                    groupAAtCreate = currentA
                    groupBAtCreate = currentB
                    AccentColorPrefs.applyAccents(activity)
                }
            }
        })

        val versionName = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        drawerLayout.findViewById<TextView>(R.id.tvDrawerVersion)?.text = "v$versionName"
        drawerLayout.findViewById<TextView>(R.id.drawerThemeLabel)?.text =
            "Theme: ${ThemePrefs.getTheme(activity).label}"

        drawerLayout.findViewById<LinearLayout>(R.id.drawerFooter)?.setOnClickListener {
            activity.startActivity(Intent(activity, ChangelogActivity::class.java))
        }
        // v1.2.0: Classes entry (under DATA, after Backup/Restore). Active
        // (plan + Settings switch on): normal entry. Plan without access:
        // shown with a lock; opens the (read-only) class list if the teacher
        // already has classes stored, else Subscription. Entitled but
        // switched off in Settings: hidden. Re-evaluated on every onResume
        // (observer below), because the plan, the switch and the stored
        // classes can all change while another screen is in front.
        refreshClassesRow(activity, drawerLayout)
        drawerLayout.findViewById<LinearLayout>(R.id.drawerBackupData)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activity.startActivity(Intent(activity, BackupDataActivity::class.java))
        }
        drawerLayout.findViewById<LinearLayout>(R.id.drawerRestoreData)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activity.startActivity(Intent(activity, RestoreDataActivity::class.java))
        }
        drawerLayout.findViewById<LinearLayout>(R.id.drawerTheme)?.setOnClickListener {
            ThemePrefs.cycleTheme(activity)
            activity.recreate()
        }
        drawerLayout.findViewById<LinearLayout>(R.id.drawerSettings)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        drawerLayout.findViewById<LinearLayout>(R.id.drawerHelp)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activity.startActivity(Intent(activity, HelpActivity::class.java))
        }
        drawerLayout.findViewById<LinearLayout>(R.id.drawerAbout)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }
        drawerLayout.findViewById<android.widget.ImageView>(R.id.drawerLogo)?.setOnClickListener {
            replayWelcomeAnimation(activity)
        }
    }

    private fun refreshClassesRow(activity: AppCompatActivity, drawerLayout: DrawerLayout) {
        val classesRow = drawerLayout.findViewById<LinearLayout>(R.id.drawerClasses) ?: return
        val hasAccess = ClassesAccess.hasAccess(activity)
        val classesActive = ClassesAccess.isActive(activity)
        val hasStoredClasses = ClassStorage.listClasses(activity).isNotEmpty()
        classesRow.visibility = if (hasAccess && !classesActive) View.GONE else View.VISIBLE
        drawerLayout.findViewById<TextView>(R.id.drawerClassesLabel)?.text = when {
            classesActive -> "Classes"
            hasStoredClasses -> "Classes 🔒 view only"
            else -> "Classes 🔒"
        }
        classesRow.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            if (classesActive || hasStoredClasses) {
                activity.startActivity(Intent(activity, ClassesActivity::class.java))
            } else {
                android.widget.Toast.makeText(activity, "${ClassesAccess.LOCKED_HINT}.", android.widget.Toast.LENGTH_LONG).show()
                activity.startActivity(Intent(activity, ClassesActivity::class.java))
            }
        }
    }

    /** v2.8 fold-in (item 4): tapping the logo (home screen or sidebar,
     *  both wired to this) replays the welcome animation. FLAG_ACTIVITY_
     *  CLEAR_TASK + FLAG_ACTIVITY_NEW_TASK clears whatever's currently in
     *  the back stack (this could be triggered from any of the 11 drawer
     *  screens, not just Home) so WelcomeActivity's own goToMain() ends up
     *  with a clean single-MainActivity stack afterward, same shape as a
     *  cold start — without these flags, replaying from a non-Home screen
     *  would leave a duplicate MainActivity instance stacked underneath
     *  the new one goToMain() creates. */
    fun replayWelcomeAnimation(activity: AppCompatActivity) {
        val intent = Intent(activity, WelcomeActivity::class.java).apply {
            putExtra(WelcomeActivity.EXTRA_FORCE_PLAY, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }
}
