package com.example.superznalci

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class FillBlankComponent(
    private val context: Context,
    parent: ViewGroup,
    private val question: QuizQuestion
) : QuestionComponent {

    private enum class State { IDLE, FOCUSED, CORRECT, WRONG }

    override val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    override var onAnswerChanged: (() -> Unit)? = null

    private val fields = mutableListOf<EditText>()
    private var locked = false

    init {
        val count = question.blankCount

        repeat(count) { index ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = QuizUi.dp(context, 10) }
            }

            val label = TextView(context).apply {
                text = "${index + 1}."
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(QuizUi.color(context, R.color.on_surface_muted))
                minWidth = QuizUi.dp(context, 28)
            }

            val field = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = true
                imeOptions = if (index == count - 1) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
                textSize = 16f
                setTextColor(QuizUi.color(context, R.color.on_surface))
                val h = QuizUi.dp(context, 14)
                val v = QuizUi.dp(context, 12)
                setPadding(h, v, h, v)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (!locked) onAnswerChanged?.invoke()
                    }
                })
                setOnFocusChangeListener { _, focused ->
                    if (!locked) render(this, if (focused) State.FOCUSED else State.IDLE)
                }
            }
            render(field, State.IDLE)

            row.addView(label)
            row.addView(field)
            root.addView(row)
            fields += field
        }
    }

    override fun hasAnswer() = fields.isNotEmpty() && fields.all { it.text.isNotBlank() }

    // FILL_IN_BLANK: one value per blank; IMAGE_QUESTION: a single value
    override fun answer(): JsonElement =
        if (question.type == QuestionType.IMAGE_QUESTION) {
            JsonPrimitive(fields.first().text.toString().trim())
        } else {
            JsonArray().apply { fields.forEach { add(it.text.toString().trim()) } }
        }

    override fun setLocked(locked: Boolean) {
        this.locked = locked
        fields.forEach { it.isEnabled = !locked }
        if (locked) hideKeyboard()
    }

    override fun showResult(result: AnswerResult) {
        locked = true
        hideKeyboard()

        fields.forEachIndexed { i, field ->
            // the server decides; per blank we only colour the fields that match its solution
            val right = result.correct ||
                (question.type == QuestionType.FILL_BLANK &&
                    i < result.correctAnswers.size &&
                    normalize(field.text.toString()) == normalize(result.correctAnswers[i]))

            field.isEnabled = true
            field.clearFocus()
            field.isFocusable = false
            field.isFocusableInTouchMode = false
            field.isCursorVisible = false
            field.keyListener = null
            render(field, if (right) State.CORRECT else State.WRONG)
        }
    }

    private fun render(field: EditText, state: State) {
        val fill: Int
        val stroke: Int
        when (state) {
            State.IDLE -> {
                fill = c(R.color.surface); stroke = c(R.color.quiz_stroke)
            }
            State.FOCUSED -> {
                fill = c(R.color.surface); stroke = c(R.color.primary)
            }
            State.CORRECT -> {
                fill = c(R.color.quiz_correct_bg); stroke = c(R.color.primary)
            }
            State.WRONG -> {
                fill = c(R.color.quiz_wrong_bg); stroke = c(R.color.quiz_wrong)
            }
        }
        field.background = QuizUi.rounded(context, fill, stroke, 14)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase()
            .replace('\u2212', '-')   // minus sign
            .replace('\u2013', '-')   // en dash
            .replace('\u2014', '-')   // em dash
            .replace(Regex("\\s+"), "")
            .replace(',', '.')

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun c(res: Int) = QuizUi.color(context, res)
}