package com.example.superznalci

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

// MATCHING: for every item on the left the student picks its pair from the right column
class MatchingComponent(
    private val context: Context,
    parent: ViewGroup,
    private val question: QuizQuestion
) : QuestionComponent {

    private class Row(val left: String, val box: LinearLayout, val spinner: Spinner, val note: TextView)

    override val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    override var onAnswerChanged: (() -> Unit)? = null

    private val rows = mutableListOf<Row>()
    private val duplicateNote: TextView
    private var locked = false

    init {
        root.addView(TextView(context).apply {
            setText(R.string.quiz_match_hint)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.on_surface_muted))
            setPadding(0, 0, 0, QuizUi.dp(context, 10))
        })

        // first item is the "choose" placeholder
        val options = listOf(context.getString(R.string.quiz_match_choose)) + question.matchRight

        question.matchLeft.forEachIndexed { index, left ->
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = QuizUi.dp(context, 12)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = QuizUi.dp(context, 10) }
            }
            box.addView(TextView(context).apply {
                text = left
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(c(R.color.on_surface))
            })
            val spinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, R.layout.item_spinner, options).apply {
                    setDropDownViewResource(R.layout.item_spinner_dropdown)
                }
                setBackgroundResource(R.drawable.bg_spinner)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = QuizUi.dp(context, 8) }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) = changed()
                    override fun onNothingSelected(p: AdapterView<*>?) = changed()
                }
            }
            val note = TextView(context).apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
                setPadding(0, QuizUi.dp(context, 6), 0, 0)
            }
            box.addView(spinner)
            box.addView(note)
            root.addView(box)
            rows += Row(left, box, spinner, note)
            renderBox(box, null)
        }

        duplicateNote = TextView(context).apply {
            setText(R.string.quiz_match_duplicate)
            textSize = 13f
            setTextColor(c(R.color.quiz_wrong))
            visibility = View.GONE
            setPadding(0, QuizUi.dp(context, 10), 0, 0)
        }
        root.addView(duplicateNote)
    }

    // null = not chosen yet
    private fun chosen(row: Row): String? =
        row.spinner.selectedItemPosition.takeIf { it > 0 }?.let { question.matchRight[it - 1] }

    private fun hasDuplicates(): Boolean {
        val picked = rows.mapNotNull { chosen(it) }
        return picked.size != picked.toSet().size
    }

    private fun changed() {
        if (locked) return
        duplicateNote.visibility = if (hasDuplicates()) View.VISIBLE else View.GONE
        onAnswerChanged?.invoke()
    }

    // every item has a pair and no right option is used twice (the server rejects that)
    override fun hasAnswer() = rows.isNotEmpty() && rows.all { chosen(it) != null } && !hasDuplicates()

    override fun answer(): JsonElement = JsonArray().apply {
        rows.forEach { row ->
            add(JsonObject().apply {
                addProperty("left", row.left)
                addProperty("right", chosen(row))
            })
        }
    }

    override fun setLocked(locked: Boolean) {
        this.locked = locked
        rows.forEach { it.spinner.isEnabled = !locked }
    }

    override fun showResult(result: AnswerResult) {
        setLocked(true)
        rows.forEach { row ->
            val correctRight = result.correctPairs[row.left]
            val right = result.correct || (correctRight != null && correctRight == chosen(row))
            renderBox(row.box, right)
            if (!right && correctRight != null) {
                row.note.visibility = View.VISIBLE
                row.note.text = context.getString(R.string.quiz_match_correct, correctRight)
                row.note.setTextColor(c(R.color.primary_dark))
            }
        }
    }

    private fun renderBox(box: LinearLayout, right: Boolean?) {
        box.background = when (right) {
            null -> QuizUi.rounded(context, c(R.color.surface), c(R.color.quiz_stroke), 16)
            true -> QuizUi.rounded(context, c(R.color.quiz_correct_bg), c(R.color.primary), 16)
            false -> QuizUi.rounded(context, c(R.color.quiz_wrong_bg), c(R.color.quiz_wrong), 16)
        }
    }

    private fun c(res: Int) = QuizUi.color(context, res)
}
