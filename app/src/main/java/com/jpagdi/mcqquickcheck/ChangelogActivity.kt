package com.jpagdi.mcqquickcheck

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ChangelogActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)

        DrawerHelper.attach(
            this,
            findViewById(R.id.drawerLayout),
            findViewById(R.id.btnOpenDrawer)
        )
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        val body = findViewById<android.widget.TextView>(R.id.tvChangelogBody)
        body.text = buildString {
            Changelog.entries.forEach { entry ->
                append("v${entry.version}  •  ${entry.date}\n")
                entry.notes.forEach { note -> append("   — $note\n") }
                append("\n")
            }
            // v1.0.0 reset: synthetic bridge entry, not a real
            // ChangelogEntry — just reads like one. Sits at the oldest
            // (bottom) position, which is chronologically where it
            // belongs. "Show more" below expands the real historical log.
            append("v0.0.0  •  Historical development\n")
            append("   — This version represents months of iteration, testing, and fixes that led to v1.0.0.\n")
        }
        // justificationMode only exists from API 26 onward — guarded so
        // this can't crash on the API 24-25 devices this app still
        // supports (minSdk 24), same approach as AboutActivity.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            body.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }

        // v1.0.0 reset: "Show more" expands Changelog.legacyEntries inline,
        // right below the v0.0.0 bridge entry above. Built once, lazily,
        // the first time it's expanded — most people opening this screen
        // will never tap this, so there's no reason to build a 75-entry
        // string on every screen open.
        val showMoreToggle = findViewById<android.widget.TextView>(R.id.tvChangelogShowMore)
        val legacyBody = findViewById<android.widget.TextView>(R.id.tvChangelogLegacyBody)
        var legacyBuilt = false
        var legacyExpanded = false
        showMoreToggle.setOnClickListener {
            legacyExpanded = !legacyExpanded
            if (legacyExpanded && !legacyBuilt) {
                legacyBody.text = buildString {
                    Changelog.legacyEntries.forEach { entry ->
                        append("v${entry.version}  •  ${entry.date}\n")
                        entry.notes.forEach { note -> append("   — $note\n") }
                        append("\n")
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    legacyBody.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                }
                legacyBuilt = true
            }
            legacyBody.visibility = if (legacyExpanded) android.view.View.VISIBLE else android.view.View.GONE
            showMoreToggle.text = if (legacyExpanded) "Show less" else "Show more"
        }

        // Welcome-animation toggle. v1.0.0 reset: sits below the (now
        // collapsible) historical log — see its own doc comment in
        // activity_changelog.xml for why it's positioned there rather than
        // pinned above the expandable content.
        val welcomeToggle = findViewById<android.widget.Switch>(R.id.welcomeAnimToggle)
        welcomeToggle.isChecked = SettingsPrefs.isWelcomeAnimationEnabled(this)
        welcomeToggle.setOnCheckedChangeListener { _, isChecked ->
            SettingsPrefs.setWelcomeAnimationEnabled(this, isChecked)
            android.widget.Toast.makeText(
                this,
                if (isChecked) "Welcome animation: on" else "Welcome animation: off",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
