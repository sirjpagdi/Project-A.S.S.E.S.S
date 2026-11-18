package com.jpagdi.mcqquickcheck

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var rgChoiceCount: RadioGroup
    private lateinit var etQuestionCount: EditText
    private lateinit var tvColumnsValue: TextView
    private lateinit var tvRowsValue: TextView
    private lateinit var switchAutosave: Switch
    private lateinit var switchCameraSound: Switch
    private lateinit var switchParentSignatureLine: Switch
    private lateinit var switchClassesEnabled: Switch
    private lateinit var tvIdDigitsValue: TextView
    private var idDigits = SheetTemplate.ID_DEFAULT_DIGITS
    private lateinit var rgStorageResolution: RadioGroup
    private lateinit var rgFontScale: RadioGroup

    private var columns = 3
    private var rows = 10

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        DrawerHelper.attach(
            this,
            findViewById(R.id.drawerLayout),
            findViewById(R.id.btnOpenDrawer)
        )
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        rgChoiceCount = findViewById(R.id.rgDefaultChoiceCount)
        etQuestionCount = findViewById(R.id.etDefaultQuestionCount)
        tvColumnsValue = findViewById(R.id.tvDefColumnsValue)
        tvRowsValue = findViewById(R.id.tvDefRowsValue)
        switchAutosave = findViewById(R.id.switchAutosaveScan)
        switchCameraSound = findViewById(R.id.switchCameraSound)
        switchParentSignatureLine = findViewById(R.id.switchParentSignatureLine)
        switchClassesEnabled = findViewById(R.id.switchClassesEnabled)
        tvIdDigitsValue = findViewById(R.id.tvIdDigitsValue)
        rgStorageResolution = findViewById(R.id.rgStorageResolution)
        rgFontScale = findViewById(R.id.rgFontScale)

        findViewById<Button>(R.id.btnAccentGroupA).setOnClickListener {
            AccentColorPickerDialog.show(
                activity = this,
                title = "Top Panel Accent",
                currentColor = AccentColorPrefs.getGroupA(this),
                defaultColor = AccentColorPrefs.DEFAULT_GROUP_A,
                onSave = { color -> AccentColorPrefs.setGroupA(this, color); AccentColorPrefs.applyAccents(this) },
                onRevert = { AccentColorPrefs.revertGroupA(this); AccentColorPrefs.applyAccents(this) }
            )
        }
        findViewById<Button>(R.id.btnAccentGroupB).setOnClickListener {
            AccentColorPickerDialog.show(
                activity = this,
                title = "Button Accent",
                currentColor = AccentColorPrefs.getGroupB(this),
                defaultColor = AccentColorPrefs.DEFAULT_GROUP_B,
                onSave = { color -> AccentColorPrefs.setGroupB(this, color); AccentColorPrefs.applyAccents(this) },
                onRevert = { AccentColorPrefs.revertGroupB(this); AccentColorPrefs.applyAccents(this) }
            )
        }

        val current = SettingsPrefs.getDefaults(this)
        rgChoiceCount.check(if (current.choiceCount == 5) R.id.rbDef5Choices else R.id.rbDef4Choices)
        etQuestionCount.setText(current.questionCount.toString())
        columns = current.columns
        rows = current.questionsPerColumn
        tvColumnsValue.text = columns.toString()
        tvRowsValue.text = rows.toString()
        switchAutosave.isChecked = current.autosaveScannedSheet
        switchCameraSound.isChecked = current.cameraSoundEnabled
        switchParentSignatureLine.isChecked = SettingsPrefs.isParentSignatureLineEnabled(this)

        // v1.2.0: Classes switch. Locked (Free / Quarter): shown off and
        // disabled, with the plan hint, and the whole row opens Subscription.
        // Unlocked: a normal switch saved by the Save button like the others.
        val classesRow = findViewById<android.view.View>(R.id.rowClassesEnabled)
        val classesHint = findViewById<TextView>(R.id.tvClassesEnabledHint)
        // ID-digits stepper (v1.2.0): same +/- pattern as the columns/rows
        // steppers; clamped 4..12. On a locked plan the whole stepper is
        // shown dimmed and inert.
        val btnIdMinus = findViewById<Button>(R.id.btnIdDigitsMinus)
        val btnIdPlus = findViewById<Button>(R.id.btnIdDigitsPlus)
        val idStepperCard = findViewById<android.view.View>(R.id.cardIdDigitsStepperSettings)
        idDigits = SettingsPrefs.getIdDigits(this)
        tvIdDigitsValue.text = idDigits.toString()
        if (ClassesAccess.hasAccess(this)) {
            switchClassesEnabled.isEnabled = true
            switchClassesEnabled.isChecked = SettingsPrefs.isClassesEnabled(this)
            btnIdMinus.setOnClickListener {
                idDigits = SheetTemplate.clampIdDigits(idDigits - 1)
                tvIdDigitsValue.text = idDigits.toString()
            }
            btnIdPlus.setOnClickListener {
                idDigits = SheetTemplate.clampIdDigits(idDigits + 1)
                tvIdDigitsValue.text = idDigits.toString()
            }
        }
        rgStorageResolution.check(
            when (SettingsPrefs.getStorageResolutionPx(this)) {
                1800 -> R.id.rbRes1800
                1600 -> R.id.rbRes1600
                1200 -> R.id.rbRes1200
                else -> R.id.rbRes2000
            }
        )

        rgFontScale.check(
            when (FontSizePrefs.getFontScale(this)) {
                FontScale.VERY_SMALL -> R.id.rbFontVerySmall
                FontScale.SMALL -> R.id.rbFontSmall
                FontScale.MEDIUM -> R.id.rbFontMedium
                FontScale.LARGE -> R.id.rbFontLarge
            }
        )
        // v3.9.9: applied the instant the person taps a size, not deferred
        // to Save — this is the one setting on this whole screen that's
        // meant to be felt immediately (that's the point of a font-size
        // preview), unlike every other field here which only takes effect
        // once Save is pressed. It's still persisted right away too
        // (setFontScale), so leaving via the back button without hitting
        // Save doesn't lose the choice, matching how the accent color
        // pickers on this same screen already behave (see
        // btnAccentGroupA/B's onSave above, which also apply + persist
        // immediately rather than waiting for Save).
        rgFontScale.setOnCheckedChangeListener { _, checkedId ->
            val scale = when (checkedId) {
                R.id.rbFontVerySmall -> FontScale.VERY_SMALL
                R.id.rbFontSmall -> FontScale.SMALL
                R.id.rbFontLarge -> FontScale.LARGE
                else -> FontScale.MEDIUM
            }
            FontSizePrefs.setFontScale(this, scale)
            FontScaleController.apply(this)
        }

        // Columns/rows steppers are plain +/- with a generous, simple clamp
        // (1..8 / 1..30) rather than SheetTemplate's context-sensitive range
        // functions — those need a specific question count and paper layout
        // to compute a valid range, which fits GenerateSheetActivity's live
        // preview but is more precision than a background "default" needs.
        // GenerateSheetActivity's own clampColumnsAndRows() will correct
        // these into a valid range for whatever the user actually picks
        // when they next create a sheet, so an occasionally-oversized
        // default here can't produce a broken template.
        // Question-count stepper (v2.5, item 2): same reasoning as the
        // columns/rows steppers above — no per-layout SheetTemplate range
        // exists here since a "default" isn't tied to one layout yet.
        // 90 is the app's real global ceiling (HALF_LENGTH/HALF_CROSS max,
        // per SheetTemplate.maxQuestionCountForLayout) — an assumption, not
        // a value read from SheetTemplate directly, since there's no single
        // correct ceiling without knowing which layout the user will pick
        // later. GenerateSheetActivity's own clamp will correct this default
        // down further for QUARTER/lower layouts when a sheet is actually
        // created, same safety net as columns/rows above.
        findViewById<Button>(R.id.btnDefQuestionCountMinus).setOnClickListener {
            val current = etQuestionCount.text.toString().toIntOrNull() ?: 1
            etQuestionCount.setText((current - 1).coerceIn(1, 90).toString())
        }
        findViewById<Button>(R.id.btnDefQuestionCountPlus).setOnClickListener {
            val current = etQuestionCount.text.toString().toIntOrNull() ?: 1
            etQuestionCount.setText((current + 1).coerceIn(1, 90).toString())
        }

        findViewById<Button>(R.id.btnDefColumnsMinus).setOnClickListener {
            columns = (columns - 1).coerceIn(1, 8)
            tvColumnsValue.text = columns.toString()
        }
        findViewById<Button>(R.id.btnDefColumnsPlus).setOnClickListener {
            columns = (columns + 1).coerceIn(1, 8)
            tvColumnsValue.text = columns.toString()
        }
        findViewById<Button>(R.id.btnDefRowsMinus).setOnClickListener {
            rows = (rows - 1).coerceIn(1, 30)
            tvRowsValue.text = rows.toString()
        }
        findViewById<Button>(R.id.btnDefRowsPlus).setOnClickListener {
            rows = (rows + 1).coerceIn(1, 30)
            tvRowsValue.text = rows.toString()
        }

        findViewById<android.view.View>(R.id.btnSettingsSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnPlaceholderBack).apply {
            BackButtonStyle.apply(this)
            setOnClickListener { finish() }
        }
    }

    private fun save() {
        val questionCount = etQuestionCount.text.toString().toIntOrNull()
        if (questionCount == null || questionCount <= 0) {
            Toast.makeText(this, "Enter a valid default question count", Toast.LENGTH_SHORT).show()
            return
        }
        // v3.4: QUARTER is now the only PaperLayout value — the
        // rgPaperLayout radio group this used to read from is gone.
        val paperLayout = PaperLayout.QUARTER
        val choiceCount = if (rgChoiceCount.checkedRadioButtonId == R.id.rbDef5Choices) 5 else 4

        SettingsPrefs.saveDefaults(
            this,
            SettingsPrefs.Defaults(
                paperLayout = paperLayout,
                choiceCount = choiceCount,
                questionCount = questionCount,
                columns = columns,
                questionsPerColumn = rows,
                autosaveScannedSheet = switchAutosave.isChecked,
                cameraSoundEnabled = switchCameraSound.isChecked
            )
        )
        val storageResolutionPx = when (rgStorageResolution.checkedRadioButtonId) {
            R.id.rbRes1800 -> 1800
            R.id.rbRes1600 -> 1600
            R.id.rbRes1200 -> 1200
            else -> 2000
        }
        SettingsPrefs.setStorageResolutionPx(this, storageResolutionPx)
        SettingsPrefs.setParentSignatureLineEnabled(this, switchParentSignatureLine.isChecked)
        // v1.2.0: only written on an entitled plan, so a locked device can
        // never persist "on" (the switch is disabled there anyway).
        if (ClassesAccess.hasAccess(this)) {
            SettingsPrefs.setClassesEnabled(this, switchClassesEnabled.isChecked)
            SettingsPrefs.setIdDigits(this, idDigits)
        }

        // v3.5: paper size is no longer set here — it moved to a popup
        // shown right at GENERATE PDF time (see GenerateSheetActivity's
        // showPaperSizePickerThenGenerate()), which still persists the
        // choice via SettingsPrefs so it's remembered as next time's
        // default without needing its own row on this screen.
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
