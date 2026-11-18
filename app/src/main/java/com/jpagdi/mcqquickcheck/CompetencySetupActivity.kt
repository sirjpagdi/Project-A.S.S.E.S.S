package com.jpagdi.mcqquickcheck

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Lets a teacher define named "competencies" for a test and check off
 * which item numbers belong to each — used by ItemAnalysis.compute() to
 * build the Most/Least Learned Competencies summaries (see that file and
 * ItemAnalysisXlsxExporter). Purely a UI over TestStorage's competencies
 * field — nothing here is persisted until "Save Competencies" is tapped;
 * everything before that is in-memory only (see [slots]).
 */
class CompetencySetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_NAME = "testName"
    }

    /** One slot's in-memory, not-yet-saved state. Index in [slots]
     *  determines its "Competency N" label — not stored, just display. */
    // v1.2.5 (Test sets): [itemNumbers] is the test's own placement (set 0);
    // [extra] holds the placement of sets 1..3 the teacher has edited. A set
    // with no entry here uses [itemNumbers] (same numbering as the first set).
    private data class Slot(
        var name: String,
        val itemNumbers: MutableSet<Int>,
        val extra: MutableMap<Int, MutableSet<Int>> = mutableMapOf()
    )

    /** Which set's item placement the screen is showing/editing (0 = first). */
    private var currentSet = 0
    private var setPickerView: TextView? = null

    private fun itemsOf(slot: Slot, set: Int): Set<Int> =
        if (set == 0) slot.itemNumbers else slot.extra[set] ?: slot.itemNumbers

    private fun setLabelOf(set: Int): String =
        savedTest.template.setLabels().getOrNull(set)?.toString() ?: "?"

    private lateinit var testName: String
    private lateinit var savedTest: SavedTest
    private val slots = mutableListOf<Slot>()

    override fun onResume() {
        super.onResume()
        FontScaleController.apply(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_competency_setup)

        testName = intent.getStringExtra(EXTRA_TEST_NAME) ?: run { finish(); return }
        val loaded = TestStorage.loadTestOrNull(this, testName)
        if (loaded == null) {
            finish()
            return
        }
        savedTest = loaded
        AccentColorPrefs.applyAccents(this)
        FontScaleController.apply(this)

        // Pre-fill from whatever's already saved (reopening this screen
        // shows exactly what was there before, per explicit request) —
        // a genuinely fresh test with nothing configured yet starts at
        // 3 empty slots instead, matching the requested initial layout.
        if (savedTest.competencies.isEmpty()) {
            repeat(3) { slots.add(Slot("", mutableSetOf())) }
        } else {
            savedTest.competencies.forEach { slots.add(Slot(it.name, it.itemNumbers.toMutableSet())) }
        }
        // v1.2.5: load each other set's stored placement onto the matching slots (by name).
        if (savedTest.template.hasSets) {
            for (set in 1 until savedTest.template.setCount) {
                val stored = SetCompetencyStore.get(this, testName, set).associateBy { it.name }
                slots.forEach { slot -> stored[slot.name]?.let { slot.extra[set] = it.itemNumbers.toMutableSet() } }
            }
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddCompetency).setOnClickListener {
            HapticUtils.confirm(it)
            slots.add(Slot("", mutableSetOf()))
            renderSlots()
        }
        findViewById<View>(R.id.btnSaveCompetencies).setOnClickListener { saveAndFinish() }

        addSetPickerIfNeeded()
        renderSlots()
    }

    /** For a test with sets: a tappable line above the competency list that
     *  switches which set's item numbers are being placed. Names stay shared. */
    private fun addSetPickerIfNeeded() {
        if (!savedTest.template.hasSets) return
        val container = findViewById<LinearLayout>(R.id.competencySlotContainer)
        val parent = container.parent as? LinearLayout ?: return
        val picker = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = android.view.Gravity.CENTER
            textSize = 15f
            setPadding(16, 16, 16, 16)
            setTextColor(AccentColorPrefs.getGroupA(this@CompetencySetupActivity))
            setOnClickListener { HapticUtils.confirm(it); showSetPickerDialog() }
        }
        parent.addView(picker, parent.indexOfChild(container))
        setPickerView = picker
    }

    private fun showSetPickerDialog() {
        val options = savedTest.template.setLabels().map { "Set $it" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Place items for")
            .setSingleChoiceItems(options, currentSet) { dialog, which ->
                currentSet = which
                dialog.dismiss()
                findViewById<View>(R.id.btnAddCompetency).visibility = if (currentSet == 0) View.VISIBLE else View.GONE
                renderSlots()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderSlots() {
        val container = findViewById<LinearLayout>(R.id.competencySlotContainer)
        container.removeAllViews()
        setPickerView?.text = if (currentSet == 0)
            "Item numbers for: Set ${setLabelOf(0)} ▾  (names are shared by every set)"
        else
            "Item numbers for: Set ${setLabelOf(currentSet)} ▾  (starts as a copy of Set ${setLabelOf(0)})"

        slots.forEachIndexed { index, slot ->
            val row = layoutInflater.inflate(R.layout.row_competency_slot, container, false)

            row.findViewById<TextView>(R.id.tvCompetencySlotLabel).text = "Competency ${index + 1}"

            val nameField = row.findViewById<EditText>(R.id.etCompetencyName)
            nameField.setText(slot.name)
            nameField.isEnabled = currentSet == 0
            // Plain TextWatcher rather than re-rendering on every keystroke
            // (that would rebuild the whole list — including this very
            // EditText — mid-type, losing focus/cursor position).
            nameField.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    slot.name = s?.toString().orEmpty()
                }
            })

            updateSlotSummary(row, slot)

            row.findViewById<View>(R.id.btnAssignTestItem).setOnClickListener {
                HapticUtils.confirm(it)
                openAssignItemsDialog(index)
            }
            row.findViewById<ImageView>(R.id.btnDeleteCompetencySlot).visibility = if (currentSet == 0) View.VISIBLE else View.GONE
            row.findViewById<ImageView>(R.id.btnDeleteCompetencySlot).setOnClickListener {
                HapticUtils.confirm(it)
                slots.removeAt(index)
                renderSlots()
            }

            container.addView(row)
        }
    }

    private fun updateSlotSummary(row: View, slot: Slot) {
        val tv = row.findViewById<TextView>(R.id.tvCompetencySlotSummary)
        val items = itemsOf(slot, currentSet)
        tv.text = if (items.isEmpty()) {
            "No items assigned yet"
        } else {
            "${items.size} item${if (items.size == 1) "" else "s"} assigned: " +
                items.sorted().joinToString(", ")
        }
    }

    /** Shows every item 1..questionCount as a checkbox. Items already
     *  claimed by a DIFFERENT slot are disabled and labeled with which
     *  competency has them — making an overlap impossible to create,
     *  rather than something caught after the fact, per explicit
     *  request. Items belonging to THIS slot are pre-checked. */
    private fun openAssignItemsDialog(slotIndex: Int) {
        val questionCount = savedTest.template.questionCount
        val thisSlot = slots[slotIndex]

        val claimedByOther = mutableMapOf<Int, String>()
        slots.forEachIndexed { otherIndex, other ->
            if (otherIndex == slotIndex) return@forEachIndexed
            val label = other.name.ifBlank { "Competency ${otherIndex + 1}" }
            itemsOf(other, currentSet).forEach { itemNo -> claimedByOther.putIfAbsent(itemNo, label) }
        }

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val checkboxes = mutableMapOf<Int, CheckBox>()
        for (itemNo in 1..questionCount) {
            val claimedLabel = claimedByOther[itemNo]
            val checkbox = CheckBox(this).apply {
                text = if (claimedLabel != null) "Item $itemNo — in \"$claimedLabel\"" else "Item $itemNo"
                isChecked = claimedLabel == null && itemNo in itemsOf(thisSlot, currentSet)
                isEnabled = claimedLabel == null
                alpha = if (claimedLabel == null) 1f else 0.45f
            }
            checkboxes[itemNo] = checkbox
            list.addView(checkbox)
        }
        scroll.addView(list)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Assign items — ${thisSlot.name.ifBlank { "Competency ${slotIndex + 1}" }}" +
                if (savedTest.template.hasSets) " (Set ${setLabelOf(currentSet)})" else "")
            .setView(scroll)
            .setPositiveButton("Assign & Save") { _, _ ->
                val newSet = checkboxes.filterValues { it.isEnabled && it.isChecked }.keys.toMutableSet()
                if (currentSet == 0) {
                    thisSlot.itemNumbers.clear()
                    thisSlot.itemNumbers.addAll(newSet)
                } else {
                    thisSlot.extra[currentSet] = newSet
                }
                renderSlots()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        fixDialogButtonColors(dialog)
    }

    /** Slots with a blank name AND no assigned items are silently
     *  dropped rather than saved as empty placeholders — lets a teacher
     *  leave unused "+"-added slots without needing to delete them
     *  first. A slot with a name but zero items, or items but no name,
     *  is still dropped too (either half alone isn't a usable
     *  competency for ItemAnalysis.compute() to group by). */
    private fun saveAndFinish() {
        val toSave = slots
            .filter { it.name.isNotBlank() && it.itemNumbers.isNotEmpty() }
            .map { Competency(it.name.trim(), it.itemNumbers.toSet()) }

        TestStorage.saveCompetencies(this, testName, toSave)
        // v1.2.5: each other set's edited placement, for the competencies being saved.
        if (savedTest.template.hasSets) {
            val saved = slots.filter { it.name.isNotBlank() && it.itemNumbers.isNotEmpty() }
            val perSet = linkedMapOf<Int, List<Competency>>()
            for (set in 1 until savedTest.template.setCount) {
                val list = saved.filter { it.extra.containsKey(set) }
                    .map { Competency(it.name.trim(), it.extra.getValue(set).toSet()) }
                if (list.isNotEmpty()) perSet[set] = list
            }
            SetCompetencyStore.replaceAll(this, testName, perSet)
        }
        Toast.makeText(this, "Competencies saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Same fix as other activities' fixDialogButtonColors — AlertDialog
     *  buttons default to colorPrimary as text color, unreadable in
     *  Dark/Black themes since those themes never set
     *  android:textColorPrimary. */
    private fun fixDialogButtonColors(dialog: AlertDialog) {
        val color = if (ThemePrefs.getTheme(this) == AppTheme.LIGHT) {
            android.graphics.Color.parseColor("#221111")
        } else {
            android.graphics.Color.WHITE
        }
        for (which in intArrayOf(
            android.content.DialogInterface.BUTTON_POSITIVE,
            android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL
        )) {
            dialog.getButton(which)?.setTextColor(color)
        }
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(color)
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(color)
    }
}
