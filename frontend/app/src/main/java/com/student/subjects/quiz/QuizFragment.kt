package com.example.superznalci

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import android.widget.ImageView
import android.view.HapticFeedbackConstants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Solves one lesson through the attempt API: the server checks every answer and calculates the result
class QuizFragment : Fragment(R.layout.fragment_quiz) {

    companion object {
        private const val ARG_SUBJECT_ID = "subject_id"
        private const val ARG_SUBJECT_NAME = "subject_name"
        private const val ARG_GRADE = "grade_number"
        private const val ARG_LESSON = "lesson_id"

        private const val STATE_OUTCOMES = "state_outcomes"
        private const val STATE_HEARTS = "state_hearts"
        private const val STATE_COMBO = "state_combo"
        private const val STATE_XP = "state_xp"
        private const val STATE_ATTEMPT = "state_attempt"
        private const val STATE_QUESTIONS = "state_questions"
        private const val STATE_RESULT = "state_result"

        private val gson = Gson()

        fun newInstance(
                subjectId: String,
                subjectName: String,
                gradeNumber: Int?,
                lessonId: String
            ) = QuizFragment().apply {
                arguments = bundleOf(
                    ARG_SUBJECT_ID to subjectId,
                    ARG_SUBJECT_NAME to subjectName,
                    ARG_GRADE to (gradeNumber ?: 0),
                    ARG_LESSON to lessonId
                )
            }
    }

    private val subjectName: String get() = arguments?.getString(ARG_SUBJECT_NAME).orEmpty()
    private val lessonId: String? get() = arguments?.getString(ARG_LESSON)

    private val repo: QuizRepository get() = ServiceLocator.quiz

    // the attempt and its questions are kept on rotation, so no new attempt is started
    private var attemptId: String? = null
    private var questions: List<QuizQuestion> = emptyList()
    private var finishResult: FinishResultDto? = null

    private val outcomes = mutableListOf<AnswerOutcome>()
    private var current = 0
    private var hearts = QuizConfig.START_HEARTS
    private var combo = 0
    private var xp = 0
    private var checked = false
    private var busy = false   // an answer or the finish is being sent to the server

    private var component: QuestionComponent? = null
    private var dialog: AlertDialog? = null

    private lateinit var scroll: ScrollView
    private lateinit var loading: View
    private lateinit var tvEmpty: TextView
    private lateinit var btnCheck: TextView

    private lateinit var tvProgressLabel: TextView
    private lateinit var tvPercent: TextView
    private lateinit var progressQuiz: ProgressBar
    private lateinit var tvHearts: TextView
    private lateinit var tvCombo: TextView

    private lateinit var tvTag: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvHintText: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var questionContainer: LinearLayout
    private lateinit var ivQuizImage: ImageView

    private lateinit var feedbackCard: View
    private lateinit var tvFeedbackTitle: TextView
    private lateinit var tvFeedbackXp: TextView
    private lateinit var tvFeedbackExplanation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            it.getIntArray(STATE_OUTCOMES)?.forEach { o -> outcomes += AnswerOutcome.values()[o] }
            hearts = it.getInt(STATE_HEARTS, QuizConfig.START_HEARTS)
            combo = it.getInt(STATE_COMBO)
            xp = it.getInt(STATE_XP)
            attemptId = it.getString(STATE_ATTEMPT)
            it.getString(STATE_QUESTIONS)?.let { json ->
                questions = gson.fromJson(json, object : TypeToken<List<QuizQuestion>>() {}.type)
            }
            it.getString(STATE_RESULT)?.let { json -> finishResult = gson.fromJson(json, FinishResultDto::class.java) }
            current = outcomes.size
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_OUTCOMES, outcomes.map { it.ordinal }.toIntArray())
        outState.putInt(STATE_HEARTS, hearts)
        outState.putInt(STATE_COMBO, combo)
        outState.putInt(STATE_XP, xp)
        outState.putString(STATE_ATTEMPT, attemptId)
        if (questions.isNotEmpty()) outState.putString(STATE_QUESTIONS, gson.toJson(questions))
        finishResult?.let { outState.putString(STATE_RESULT, gson.toJson(it)) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        scroll = view.findViewById(R.id.quizScroll)
        loading = view.findViewById(R.id.quizLoading)
        tvEmpty = view.findViewById(R.id.tvQuizEmpty)
        btnCheck = view.findViewById(R.id.btnCheck)

        tvProgressLabel = view.findViewById(R.id.tvQuizProgressLabel)
        tvPercent = view.findViewById(R.id.tvQuizPercent)
        progressQuiz = view.findViewById(R.id.progressQuiz)
        tvHearts = view.findViewById(R.id.tvQuizHearts)
        tvCombo = view.findViewById(R.id.tvQuizCombo)

        tvTag = view.findViewById(R.id.tvQuizTag)
        tvHint = view.findViewById(R.id.tvQuizHint)
        tvHintText = view.findViewById(R.id.tvQuizHintText)
        tvQuestion = view.findViewById(R.id.tvQuizQuestion)
        questionContainer = view.findViewById(R.id.questionContainer)
        ivQuizImage = view.findViewById(R.id.ivQuizImage)

        feedbackCard = view.findViewById(R.id.feedbackCard)
        tvFeedbackTitle = view.findViewById(R.id.tvFeedbackTitle)
        tvFeedbackXp = view.findViewById(R.id.tvFeedbackXp)
        tvFeedbackExplanation = view.findViewById(R.id.tvFeedbackExplanation)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = confirmExit()
            }
        )
        view.findViewById<View>(R.id.tvQuizBack).setOnClickListener { confirmExit() }
        view.findViewById<View>(R.id.tvQuizClose).setOnClickListener { confirmExit() }

        tvHint.setOnClickListener {
            tvHintText.visibility = if (tvHintText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        btnCheck.setOnClickListener { if (checked) next() else checkAnswer() }

        busy = false
        load()
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        component = null
        super.onDestroyView()
    }

    // loading

    private fun load() {
        scroll.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        btnCheck.visibility = View.GONE

        if (attemptId != null) {
            loading.visibility = View.GONE
            start()
            return
        }

        val lesson = lessonId
        if (lesson == null) {
            loading.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            return
        }

        loading.visibility = View.VISIBLE
        repo.startLesson(lesson) { result ->
            if (view == null) return@startLesson
            loading.visibility = View.GONE

            when (result) {
                is ApiResult.Success -> {
                    attemptId = result.data.attemptId
                    questions = result.data.questions
                    start()
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    401 -> {
                        SessionStore.clear()
                        goToLogin()
                    }
                    403, 404 -> showError(R.string.quiz_lesson_unavailable, retry = null)
                    null -> showError(R.string.err_network) { load() }
                    else -> showError(R.string.err_server) { load() }
                }
            }
        }
    }

    private fun showError(messageRes: Int, retry: (() -> Unit)?) {
        dialog?.dismiss()
        val builder = AlertDialog.Builder(requireContext())
            .setMessage(messageRes)
            .setCancelable(false)
            .setNegativeButton(R.string.quiz_load_back) { _, _ -> leave() }
        if (retry != null) builder.setPositiveButton(R.string.profile_retry) { _, _ -> retry() }
        dialog = builder.show()
    }

    private fun start() {
        if (questions.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }
        scroll.visibility = View.VISIBLE
        btnCheck.visibility = View.VISIBLE

        if (current >= questions.size) finishQuiz() else showQuestion()
    }

    // one question

    private fun showQuestion() {
        val q = questions[current]
        checked = false

        btnCheck.background = QuizUi.rounded(requireContext(), QuizUi.color(requireContext(), R.color.primary), null, 16)
        btnCheck.setTextColor(QuizUi.color(requireContext(), R.color.surface))

        tvTag.text = if (q.topicName.isBlank()) subjectName
        else getString(R.string.quiz_tag, subjectName, q.topicName)
        tvQuestion.text = q.text
        ImageQuiz.bind(ivQuizImage, q.image)

        val percent = (current + 1) * 100 / questions.size
        tvProgressLabel.text = getString(R.string.quiz_progress_label, current + 1, questions.size)
        tvPercent.text = getString(R.string.quiz_percent, percent)
        progressQuiz.progress = percent
        bindStatus()

        tvHintText.visibility = View.GONE
        if (q.hint.isNullOrBlank()) {
            tvHint.visibility = View.GONE
        } else {
            tvHint.visibility = View.VISIBLE
            tvHintText.text = q.hint
        }

        feedbackCard.visibility = View.GONE

        questionContainer.removeAllViews()
        component = QuestionComponents.create(requireContext(), questionContainer, q).also { c ->
            c.onAnswerChanged = { updateButton() }
            questionContainer.addView(c.root)
        }

        updateButton()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun bindStatus() {
        tvHearts.text = getString(R.string.quiz_hearts, hearts)
        tvCombo.text = getString(R.string.quiz_combo, combo)
    }

    private fun updateButton() {
        val enabled: Boolean
        when {
            busy -> {
                btnCheck.setText(if (checked || current >= questions.size) R.string.quiz_btn_finishing else R.string.quiz_btn_checking)
                enabled = false
            }
            !checked -> {
                btnCheck.setText(R.string.quiz_btn_check)
                enabled = component?.hasAnswer() == true
            }
            else -> {
                val last = current >= questions.lastIndex
                btnCheck.setText(if (last) R.string.quiz_btn_finish else R.string.quiz_btn_next)
                enabled = true
            }
        }
        btnCheck.isEnabled = enabled
        btnCheck.alpha = if (enabled) 1f else 0.5f
    }

    private fun checkAnswer() {
        val c = component ?: return
        val attempt = attemptId ?: return
        if (busy || !c.hasAnswer()) return
        val q = questions[current]

        // broken question data: nothing to send, the server would reject it
        if (q.type == QuestionType.UNKNOWN) {
            record(q, AnswerOutcome.SKIPPED, null)
            return
        }

        busy = true
        c.setLocked(true)
        updateButton()

        repo.submitAnswer(attempt, q.id, c.answer()) { result ->
            if (view == null) return@submitAnswer
            busy = false

            when (result) {
                is ApiResult.Success -> {
                    c.showResult(result.data)
                    record(q, if (result.data.correct) AnswerOutcome.CORRECT else AnswerOutcome.WRONG, result.data)
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    401 -> {
                        SessionStore.clear()
                        goToLogin()
                    }
                    // already answered (e.g. the screen was recreated while sending): the server keeps the first answer
                    409 -> record(q, AnswerOutcome.SKIPPED, null)
                    else -> {
                        c.setLocked(false)
                        toast(if (result.httpCode == null) R.string.err_network else R.string.quiz_answer_failed)
                        updateButton()
                    }
                }
            }
        }
    }

    private fun record(q: QuizQuestion, outcome: AnswerOutcome, result: AnswerResult?) {
        checked = true
        outcomes += outcome

        when (outcome) {
            AnswerOutcome.CORRECT -> {
                combo++
                xp += result?.points ?: 0
            }
            AnswerOutcome.WRONG -> {
                combo = 0
                hearts = (hearts - 1).coerceAtLeast(0)
            }
            AnswerOutcome.SKIPPED -> Unit
        }

        bindStatus()
        showFeedback(q, outcome, result)
        updateButton()
    }

    private fun showFeedback(q: QuizQuestion, outcome: AnswerOutcome, result: AnswerResult?) {
        val ctx = requireContext()
        val green = QuizUi.color(ctx, R.color.primary)
        val red = QuizUi.color(ctx, R.color.quiz_wrong)
        val grey = QuizUi.color(ctx, R.color.quiz_surface_alt)
        val white = QuizUi.color(ctx, R.color.surface)
        val dark = QuizUi.color(ctx, R.color.on_surface)

        val background = when (outcome) {
            AnswerOutcome.CORRECT -> green
            AnswerOutcome.WRONG -> red
            AnswerOutcome.SKIPPED -> grey
        }
        val textColor = if (outcome == AnswerOutcome.SKIPPED) dark else white

        feedbackCard.background = QuizUi.rounded(ctx, background, null, 20)

        // Title
        tvFeedbackTitle.setText(
            when (outcome) {
                AnswerOutcome.CORRECT -> R.string.quiz_feedback_correct
                AnswerOutcome.WRONG -> R.string.quiz_feedback_wrong
                AnswerOutcome.SKIPPED -> R.string.quiz_feedback_skipped
            }
        )
        tvFeedbackTitle.setTextColor(textColor)

        // XP badge
        if (outcome == AnswerOutcome.CORRECT) {
            tvFeedbackXp.visibility = View.VISIBLE
            tvFeedbackXp.text = getString(R.string.quiz_feedback_xp, result?.points ?: 0)
            tvFeedbackXp.setTextColor(textColor)
            tvFeedbackXp.background =
                QuizUi.rounded(ctx, ColorUtils.setAlphaComponent(Color.WHITE, 70), null, 20)
        } else {
            tvFeedbackXp.visibility = View.GONE
        }

        // Body: correct answer from the server (only when wrong) + combo / heart info + explanation
        val lines = listOfNotNull(
            if (outcome == AnswerOutcome.WRONG && result != null) correctAnswerLine(q, result) else null,
            if (outcome == AnswerOutcome.CORRECT && combo >= 3)
                getString(R.string.quiz_feedback_combo, combo) else null,
            if (outcome == AnswerOutcome.WRONG)
                getString(R.string.quiz_feedback_heart_lost, hearts) else null,
            result?.explanation
        )
        tvFeedbackExplanation.text = lines.joinToString("\n")
        tvFeedbackExplanation.setTextColor(textColor)
        tvFeedbackExplanation.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE

        // Button matches the result
        if (outcome != AnswerOutcome.SKIPPED) {
            btnCheck.background = QuizUi.rounded(ctx, background, null, 16)
            btnCheck.setTextColor(white)
        }

        // Show with a slide-up animation
        feedbackCard.visibility = View.VISIBLE
        feedbackCard.alpha = 0f
        feedbackCard.translationY = QuizUi.dp(ctx, 24).toFloat()
        feedbackCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .start()

        // Haptic + accessibility
        when (outcome) {
            AnswerOutcome.CORRECT ->
                feedbackCard.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            AnswerOutcome.WRONG ->
                feedbackCard.performHapticFeedback(HapticFeedbackConstants.REJECT)
            AnswerOutcome.SKIPPED -> Unit
        }
        feedbackCard.announceForAccessibility(
            "${tvFeedbackTitle.text}. ${tvFeedbackExplanation.text}"
        )

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun correctAnswerLine(q: QuizQuestion, result: AnswerResult): String? {
        val text = if (q.type == QuestionType.MATCHING) {
            result.correctPairs.entries.joinToString("; ") { "${it.key} → ${it.value}" }
        } else {
            result.correctAnswers.joinToString(", ") { QuizUi.answerLabel(requireContext(), q.type, it) }
        }
        return if (text.isBlank()) null else getString(R.string.quiz_correct_answer, text)
    }

    private fun next() {
        current++
        if (current >= questions.size) finishQuiz() else showQuestion()
    }

    // finish: the server calculates points, stars, XP and streak

    private fun finishQuiz() {
        finishResult?.let {
            showSummary(it)
            return
        }
        val attempt = attemptId ?: return
        if (busy) return

        busy = true
        updateButton()

        repo.finish(attempt) { result ->
            if (view == null) return@finish
            busy = false
            updateButton()

            when (result) {
                is ApiResult.Success -> {
                    finishResult = result.data
                    showSummary(result.data)
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    401 -> {
                        SessionStore.clear()
                        goToLogin()
                    }
                    // finished already (the screen was recreated while finishing): the result is saved
                    409 -> showError(R.string.quiz_already_finished, retry = null)
                    null -> showError(R.string.err_network) { finishQuiz() }
                    else -> showError(R.string.quiz_finish_failed) { finishQuiz() }
                }
            }
        }
    }

    private fun showSummary(r: FinishResultDto) {
        val stars = r.stars.coerceIn(0, 3)
        val lines = mutableListOf(
            getString(R.string.quiz_done_stars, "★".repeat(stars) + "☆".repeat(3 - stars)),
            getString(R.string.quiz_done_correct, r.correctCount, r.totalQuestions),
            getString(R.string.quiz_done_points, r.points, r.maxPoints),
            if (r.xpAlreadyAwarded || r.xpEarned == 0) getString(R.string.quiz_done_no_xp)
            else getString(R.string.quiz_done_xp, r.xpEarned),
            getString(R.string.quiz_done_streak, r.streak)
        )
        val badges = r.newBadges.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
        if (badges.isNotEmpty()) lines += getString(R.string.quiz_done_badges, badges.joinToString(", "))

        dialog?.dismiss()
        dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.quiz_done_title)
            .setMessage(lines.joinToString("\n"))
            .setCancelable(false)
            .setPositiveButton(R.string.quiz_done_ok) { _, _ -> leave() }
            .show()
    }

    // navigation

    private fun confirmExit() {
        // the result is already saved on the server, nothing can be lost
        if (finishResult != null) {
            leave()
            return
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.quiz_exit_title)
            .setMessage(R.string.quiz_exit_message)
            .setNegativeButton(R.string.quiz_exit_cancel, null)
            .setPositiveButton(R.string.quiz_exit_confirm) { _, _ -> leave() }
            .show()
    }

    private fun leave() {
        parentFragmentManager.popBackStack()
    }

    private fun goToLogin() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }
}
