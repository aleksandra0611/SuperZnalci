package com.example.superznalci

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

class MistakesDto(
    val attemptId: String,
    val lessonName: String,
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val mistakes: List<MistakeDto>? = null
)

class MistakeDto(
    val questionId: String,
    val type: String,
    val text: String,
    val answers: JsonElement? = null,
    val givenAnswer: JsonElement? = null,
    val correctAnswers: JsonElement? = null,
    val explanation: String? = null
)

class MistakesFragment : Fragment() {

    private lateinit var content: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        return ScrollView(ctx).apply {
            setBackgroundColor(color(R.color.surface))
            addView(content)
            applySystemBarsPadding()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (SessionStore.user == null) { goToLogin(); return }
        load()
    }

    private fun load() {
        val id = requireArguments().getString(ARG_ATTEMPT_ID) ?: return
        ApiClient.service.attemptMistakes(id).go(
            onFail = { code ->
                if (view == null) return@go
                when (code) {
                    401 -> { SessionStore.clear(); goToLogin() }
                    null -> toast(R.string.err_network)
                    else -> toast(R.string.err_server)
                }
            }
        ) { dto ->
            if (view == null) return@go
            render(dto)
        }
    }

    private fun render(d: MistakesDto) {
        content.removeAllViews()
        content.addView(text(getString(R.string.mistakes_title), 22f, R.color.on_surface, bold = true))
        content.addView(
            text(getString(R.string.mistakes_summary, d.lessonName, d.correctCount, d.totalQuestions),
                14f, R.color.on_surface_muted).apply { setPadding(0, dp(4), 0, dp(16)) }
        )

        val list = d.mistakes.orEmpty()
        if (list.isEmpty()) {
            content.addView(text(getString(R.string.mistakes_none), 16f, R.color.on_surface))
            return
        }

        list.forEachIndexed { i, m ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    setColor(color(R.color.quiz_surface_alt)); cornerRadius = dp(16).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }
            card.addView(text("${i + 1}. ${m.text}", 16f, R.color.on_surface, bold = true))
            card.addView(
                text(getString(R.string.mistakes_your, show(m.givenAnswer, m)), 14f, 0).apply {
                    setTextColor(Color.parseColor("#D32F2F")); setPadding(0, dp(10), 0, 0)
                }
            )
            card.addView(
                text(getString(R.string.mistakes_correct, show(m.correctAnswers, m)), 14f, R.color.primary_dark)
                    .apply { setPadding(0, dp(4), 0, 0) }
            )
            if (!m.explanation.isNullOrBlank()) {
                card.addView(text(m.explanation, 13f, R.color.on_surface_muted).apply { setPadding(0, dp(8), 0, 0) })
            }
            content.addView(card)
        }
    }

    private fun show(el: JsonElement?, m: MistakeDto): String {
        if (el == null || el.isJsonNull) return getString(R.string.mistakes_empty_answer)
        if (m.type == "TRUE_FALSE" && el is JsonPrimitive && el.isBoolean)
            return getString(if (el.asBoolean) R.string.mistakes_true else R.string.mistakes_false)
        if (el is JsonArray) return el.joinToString(", ") { show(it, m) }
        if (el is JsonObject) return el.get("text")?.asString ?: el.toString()
        val raw = el.asString
        val options = (m.answers as? JsonArray)?.map { opt ->
            if (opt is JsonObject) opt.get("text")?.asString ?: opt.toString() else opt.asString
        }
        if (options != null && raw !in options) {
            raw.toIntOrNull()?.let { idx -> options.getOrNull(idx)?.let { return it } }
        }
        return raw
    }

    private fun text(s: String, size: Float, colorRes: Int, bold: Boolean = false) =
        TextView(requireContext()).apply {
            text = s; textSize = size
            if (colorRes != 0) setTextColor(color(colorRes))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun goToLogin() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment()).commit()
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_ATTEMPT_ID = "attempt_id"
        fun newInstance(attemptId: String) = MistakesFragment().apply {
            arguments = Bundle().apply { putString(ARG_ATTEMPT_ID, attemptId) }
        }
    }
}