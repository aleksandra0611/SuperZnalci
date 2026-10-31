package com.example.superznalci

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import com.google.gson.JsonArray
import com.google.gson.JsonElement

class MultiSelectComponent(
    private val context: Context,
    parent: ViewGroup,
    private val question: QuizQuestion
) : QuestionComponent {

    private enum class State { IDLE, SELECTED, CORRECT, MISSED, WRONG, DISABLED }

    private class Look(
        @ColorInt val rowFill: Int,
        @ColorInt val rowStroke: Int,
        @ColorInt val letterFill: Int,
        @ColorInt val letterText: Int,
        val mark: String
    )

    override val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    override var onAnswerChanged: (() -> Unit)? = null

    private val rows = mutableListOf<View>()
    private val selected = mutableSetOf<Int>()
    private var locked = false

    init {
        root.addView(TextView(context).apply {
            setText(R.string.quiz_multi_hint)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.on_surface_muted))
            setPadding(0, 0, 0, QuizUi.dp(context, 10))
        })

        val inflater = LayoutInflater.from(context)
        question.answers.forEachIndexed { index, value ->
            val row = inflater.inflate(R.layout.item_quiz_option, root, false)
            row.findViewById<TextView>(R.id.tvOptionLetter).text = ('A' + index).toString()
            row.findViewById<TextView>(R.id.tvOptionText).text = value
            row.setOnClickListener { toggle(index) }
            root.addView(row)
            rows += row
            render(index, State.IDLE)
        }
    }

    override fun hasAnswer() = selected.isNotEmpty()

    override fun answer(): JsonElement = JsonArray().apply {
        selected.sorted().forEach { add(question.answers[it]) }
    }

    override fun setLocked(locked: Boolean) {
        this.locked = locked
    }

    override fun showResult(result: AnswerResult) {
        locked = true

        val right = question.answers.indices
            .filter { question.answers[it] in result.correctAnswers }
            .toSet()

        rows.indices.forEach { i ->
            val isRight = i in right
            val picked = i in selected
            render(
                i,
                when {
                    isRight && picked -> State.CORRECT
                    isRight -> State.MISSED
                    picked -> State.WRONG
                    else -> State.DISABLED
                }
            )
        }
    }

    private fun toggle(index: Int) {
        if (locked) return
        if (!selected.add(index)) selected.remove(index)
        render(index, if (index in selected) State.SELECTED else State.IDLE)
        onAnswerChanged?.invoke()
    }

    private fun render(index: Int, state: State) {
        val row = rows[index]
        val look = when (state) {
            State.IDLE -> Look(c(R.color.surface), c(R.color.quiz_stroke), c(R.color.quiz_surface_alt), c(R.color.on_surface), "☐")
            State.SELECTED -> Look(c(R.color.quiz_correct_bg), c(R.color.primary), c(R.color.primary_dark), c(R.color.surface), "☑")
            State.CORRECT -> Look(c(R.color.quiz_correct_bg), c(R.color.primary), c(R.color.primary_dark), c(R.color.surface), "✓")
            State.MISSED -> Look(c(R.color.surface), c(R.color.primary), c(R.color.primary_dark), c(R.color.surface), "✓")
            State.WRONG -> Look(c(R.color.quiz_wrong_bg), c(R.color.quiz_wrong), c(R.color.quiz_wrong), c(R.color.surface), "✕")
            State.DISABLED -> Look(c(R.color.surface), c(R.color.quiz_stroke), c(R.color.quiz_surface_alt), c(R.color.on_surface_muted), "")
        }

        row.background = QuizUi.rounded(context, look.rowFill, look.rowStroke, 16)
        row.alpha = if (state == State.DISABLED) 0.6f else 1f

        row.findViewById<TextView>(R.id.tvOptionLetter).apply {
            background = QuizUi.rounded(context, look.letterFill, null, 10)
            setTextColor(look.letterText)
        }
        row.findViewById<TextView>(R.id.tvOptionMark).apply {
            text = look.mark
            setTextColor(if (state == State.WRONG) c(R.color.quiz_wrong) else c(R.color.primary_dark))
        }
    }

    private fun c(@ColorRes res: Int) = QuizUi.color(context, res)
}