package com.example.superznalci

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

// One question on the screen. The component does not know the correct answer:
// it gives the student's answer in the API format and then shows what the server said.
interface QuestionComponent {
    val root: View

    var onAnswerChanged: (() -> Unit)?

    fun hasAnswer(): Boolean

    // givenAnswer for POST /api/attempts/{attemptId}/answers
    fun answer(): JsonElement

    // locked while the answer is being checked and after it was checked
    fun setLocked(locked: Boolean)

    fun showResult(result: AnswerResult)
}

object QuestionComponents {
    fun create(context: Context, parent: ViewGroup, question: QuizQuestion): QuestionComponent =
        when (question.type) {
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE -> ChoiceComponent(context, parent, question)
            QuestionType.MULTIPLE_SELECT -> MultiSelectComponent(context, parent, question)
            QuestionType.FILL_BLANK -> FillBlankComponent(context, parent, question)
            QuestionType.IMAGE_QUESTION -> FillBlankComponent(context, parent, question)
            QuestionType.MATCHING -> MatchingComponent(context, parent, question)
            else -> UnsupportedComponent(context, parent)
        }
}


object QuizUi {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun color(context: Context, @ColorRes res: Int): Int = ContextCompat.getColor(context, res)

    fun rounded(
        context: Context,
        @ColorInt fill: Int,
        @ColorInt stroke: Int? = null,
        radiusDp: Int = 16
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(fill)
        if (stroke != null) setStroke(dp(context, 2), stroke)
    }

    // TRUE_FALSE answers come from the server as "Drži" / "Ne drži"; older data may use true / false
    fun answerLabel(context: Context, type: QuestionType, value: String): String =
        when {
            type == QuestionType.TRUE_FALSE && value.equals("true", ignoreCase = true) -> context.getString(R.string.quiz_true)
            type == QuestionType.TRUE_FALSE && value.equals("false", ignoreCase = true) -> context.getString(R.string.quiz_false)
            else -> value
        }
}

class ChoiceComponent(
    private val context: Context,
    parent: ViewGroup,
    private val question: QuizQuestion
) : QuestionComponent {

    private enum class State { IDLE, SELECTED, CORRECT, WRONG, DISABLED }

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
    private var selected = -1
    private var locked = false

    init {
        val inflater = LayoutInflater.from(context)
        question.answers.forEachIndexed { index, value ->
            val row = inflater.inflate(R.layout.item_quiz_option, root, false)
            row.findViewById<TextView>(R.id.tvOptionLetter).text = ('A' + index).toString()
            row.findViewById<TextView>(R.id.tvOptionText).text =
                QuizUi.answerLabel(context, question.type, value)
            row.setOnClickListener { select(index) }
            root.addView(row)
            rows += row
            render(index, State.IDLE)
        }
    }

    override fun hasAnswer() = selected >= 0

    override fun answer(): JsonElement = JsonPrimitive(question.answers[selected])

    override fun setLocked(locked: Boolean) {
        this.locked = locked
    }

    override fun showResult(result: AnswerResult) {
        locked = true
        rows.indices.forEach { i ->
            val isRight = question.answers[i] in result.correctAnswers
            render(
                i,
                when {
                    isRight -> State.CORRECT
                    i == selected -> State.WRONG
                    else -> State.DISABLED
                }
            )
        }
    }

    private fun select(index: Int) {
        if (locked) return
        if (selected >= 0 && selected != index) render(selected, State.IDLE)
        selected = index
        render(index, State.SELECTED)
        onAnswerChanged?.invoke()
    }

    private fun render(index: Int, state: State) {
        val row = rows[index]
        val look = when (state) {
            State.IDLE -> Look(c(R.color.surface), c(R.color.quiz_stroke), c(R.color.quiz_surface_alt), c(R.color.on_surface), "○")
            State.SELECTED -> Look(c(R.color.quiz_correct_bg), c(R.color.primary), c(R.color.primary_dark), c(R.color.surface), "●")
            State.CORRECT -> Look(c(R.color.quiz_correct_bg), c(R.color.primary), c(R.color.primary_dark), c(R.color.surface), "✓")
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

// Only for broken data (unknown type, missing options). The quiz skips it without asking the server.
class UnsupportedComponent(context: Context, parent: ViewGroup) : QuestionComponent {

    override val root: View = TextView(context).apply {
        setText(R.string.quiz_unsupported)
        setTextColor(QuizUi.color(context, R.color.on_surface_muted))
        textSize = 14f
        val pad = QuizUi.dp(context, 16)
        setPadding(pad, pad, pad, pad)
        background = QuizUi.rounded(context, QuizUi.color(context, R.color.quiz_surface_alt), null, 16)
    }
    override var onAnswerChanged: (() -> Unit)? = null

    override fun hasAnswer() = true

    override fun answer(): JsonElement = JsonPrimitive("")

    override fun setLocked(locked: Boolean) = Unit

    override fun showResult(result: AnswerResult) = Unit
}
