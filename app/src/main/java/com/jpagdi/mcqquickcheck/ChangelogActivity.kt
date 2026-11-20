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
        }
        // justificationMode only exists from API 26 onward — guarded so
        // this can't crash on the API 24-25 devices this app still
        // supports (minSdk 24), same approach as AboutActivity.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            body.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }

        // Project A.S.S.E.S.S.: only this project's own log is shown. The
        // earlier MCQ Quick Check history stays in the source and the repo's
        // CHANGELOG.md, not in the app.
        findViewById<android.view.View>(R.id.tvChangelogShowMore).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.tvChangelogLegacyBody).visibility = android.view.View.GONE

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
