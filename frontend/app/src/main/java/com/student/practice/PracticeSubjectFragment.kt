package com.example.superznalci

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

// Vadnica: topics of one subject with their lessons and the student's best result
class PracticeSubjectFragment : Fragment(R.layout.fragment_practice_subject) {

    companion object {
        private const val ARG_SUBJECT_ID = "subject_id"
        private const val ARG_SUBJECT_NAME = "subject_name"
        private const val ARG_GRADE = "grade_number"

        fun newInstance(subjectId: String, subjectName: String, gradeNumber: Int) =
            PracticeSubjectFragment().apply {
                arguments = bundleOf(
                    ARG_SUBJECT_ID to subjectId,
                    ARG_SUBJECT_NAME to subjectName,
                    ARG_GRADE to gradeNumber
                )
            }
    }

    private val subjectId: String get() = arguments?.getString(ARG_SUBJECT_ID).orEmpty()
    private val subjectName: String get() = arguments?.getString(ARG_SUBJECT_NAME).orEmpty()
    private val gradeNumber: Int get() = arguments?.getInt(ARG_GRADE) ?: PracticeConfig.DEFAULT_GRADE

    private var topics: List<PracticeTopicDetailDto> = emptyList()

    // null until the first load, then kept while the student goes to a quiz and back
    private var expandedTopics: MutableSet<String>? = null
    private var errorDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.main).applySystemBarsPadding()
        BottomNavHelper.setSelected(view, R.id.navPractice)

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        setupPracticeNavigation(this, view)
        view.findViewById<View>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<TextView>(R.id.tvSubjectName).text = subjectName

        // reloads also after a quiz, so the new stars are shown
        load()
    }

    override fun onDestroyView() {
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroyView()
    }

    private fun load() {
        requireView().findViewById<View>(R.id.progressTopics).visibility = View.VISIBLE

        ServiceLocator.practice.loadSubject(subjectId, gradeNumber) { result ->
            if (view == null) return@loadSubject
            requireView().findViewById<View>(R.id.progressTopics).visibility = View.GONE

            when (result) {
                is ApiResult.Success -> {
                    topics = result.data.topics.orEmpty()
                    if (expandedTopics == null) {
                        // open the first topic that is not finished yet, like in the mockup
                        val first = topics.firstOrNull { it.completedCount < it.lessonCount } ?: topics.firstOrNull()
                        expandedTopics = listOfNotNull(first?.id).toMutableSet()
                    }
                    render()
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    401 -> {
                        SessionStore.clear()
                        goToLogin()
                    }
                    null -> showLoadError(R.string.err_network)
                    else -> showLoadError(R.string.err_server)
                }
            }
        }
    }

    private fun showLoadError(messageRes: Int) {
        errorDialog?.dismiss()
        errorDialog = AlertDialog.Builder(requireContext())
            .setMessage(messageRes)
            .setCancelable(false)
            .setPositiveButton(R.string.profile_retry) { _, _ -> load() }
            .setNegativeButton(R.string.practice_back) { _, _ -> parentFragmentManager.popBackStack() }
            .show()
    }

    // rendering

    private fun render() {
        val view = requireView()
        view.findViewById<TextView>(R.id.tvSubjectInfo).text = getString(
            R.string.practice_subject_info,
            gradeNumber,
            resources.getQuantityString(R.plurals.subjects_topics, topics.size, topics.size)
        )
        bindToggleAll(view)

        val container = view.findViewById<LinearLayout>(R.id.topicsContainer)
        container.removeAllViews()

        if (topics.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                setText(R.string.practice_subject_empty)
                setTextColor(color(R.color.sub_text_muted))
                textSize = 13f
                setPadding(dp(16), dp(24), dp(16), dp(16))
            })
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        topics.forEachIndexed { index, topic ->
            container.addView(inflateTopic(inflater, container, index + 1, topic))
        }
    }

    private fun bindToggleAll(view: View) {
        val expanded = expandedTopics ?: return
        val allOpen = topics.isNotEmpty() && topics.all { it.id in expanded }
        view.findViewById<TextView>(R.id.btnToggleAll).apply {
            visibility = if (topics.isEmpty()) View.GONE else View.VISIBLE
            setText(if (allOpen) R.string.practice_close_all else R.string.practice_open_all)
            setOnClickListener {
                if (allOpen) expanded.clear() else expanded.addAll(topics.map { it.id })
                render()
            }
        }
    }

    private fun inflateTopic(
        inflater: LayoutInflater,
        parent: LinearLayout,
        number: Int,
        topic: PracticeTopicDetailDto
    ): View {
        val item = inflater.inflate(R.layout.item_practice_topic, parent, false)
        val open = expandedTopics?.contains(topic.id) == true

        item.findViewById<TextView>(R.id.tvTopicNumber).apply {
            text = number.toString()
            setBackgroundResource(if (open) R.drawable.sub_circle_dark else R.drawable.sub_circle_grey)
            setTextColor(color(if (open) R.color.sub_white else R.color.sub_text))
        }
        item.findViewById<TextView>(R.id.tvTopicName).text = topic.name
        item.findViewById<TextView>(R.id.tvTopicMeta).apply {
            text = topic.description.orEmpty()
            visibility = if (topic.description.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        item.findViewById<TextView>(R.id.tvTopicProgress).text =
            getString(R.string.practice_topic_solved, topic.completedCount, topic.lessonCount)
        item.findViewById<View>(R.id.ivChevron).rotation = if (open) 180f else 0f

        val lessonsHost = item.findViewById<LinearLayout>(R.id.lessonsContainer)
        lessonsHost.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            topic.lessons.orEmpty().forEach { lesson ->
                lessonsHost.addView(inflateLesson(inflater, lessonsHost, lesson))
            }
        }

        item.findViewById<View>(R.id.topicHeader).setOnClickListener {
            val expanded = expandedTopics ?: return@setOnClickListener
            if (!expanded.add(topic.id)) expanded.remove(topic.id)
            render()
        }
        return item
    }

    private fun inflateLesson(inflater: LayoutInflater, parent: LinearLayout, lesson: PracticeLessonDto): View {
        val item = inflater.inflate(R.layout.item_practice_lesson, parent, false)
        val stars = lesson.stars.coerceIn(0, 3)

        item.findViewById<View>(R.id.vLessonDot).backgroundTintList =
            ColorStateList.valueOf(color(if (lesson.completed) R.color.sub_green else R.color.sub_indigo))
        item.findViewById<TextView>(R.id.tvLessonTitle).text = lesson.name
        item.findViewById<View>(R.id.tvLessonNew).visibility = if (lesson.completed) View.GONE else View.VISIBLE
        item.findViewById<TextView>(R.id.tvLessonMeta).text =
            resources.getQuantityString(R.plurals.practice_questions, lesson.questionCount, lesson.questionCount)
        item.findViewById<TextView>(R.id.tvLessonStars).text = "★".repeat(stars) + "☆".repeat(3 - stars)
        item.findViewById<TextView>(R.id.tvLessonResult).apply {
            when {
                !lesson.completed -> {
                    setText(R.string.practice_lesson_new)
                    setTextColor(color(R.color.sub_text_muted))
                }
                stars == 3 -> {
                    setText(R.string.practice_lesson_perfect)
                    setTextColor(color(R.color.sub_amber_text))
                }
                else -> {
                    text = getString(R.string.practice_lesson_stars, stars)
                    setTextColor(color(R.color.sub_text_muted))
                }
            }
        }

        item.findViewById<TextView>(R.id.btnPractise).apply {
            val textColor = color(if (lesson.completed) R.color.sub_text else R.color.sub_white)
            setText(if (lesson.completed) R.string.practice_repeat else R.string.practice_start)
            setBackgroundResource(if (lesson.completed) R.drawable.bg_practice_btn_outline else R.drawable.bg_practice_btn)
            setTextColor(textColor)
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, if (lesson.completed) R.drawable.ic_sub_replay else R.drawable.ic_sub_play, 0
            )
            compoundDrawableTintList = ColorStateList.valueOf(textColor)
            setOnClickListener { startLesson(lesson) }
        }
        return item
    }

    private fun startLesson(lesson: PracticeLessonDto) {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                QuizFragment.newInstance(subjectId, subjectName, gradeNumber, lesson.id)
            )
            .addToBackStack(null)
            .commit()
    }

    private fun goToLogin() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    // helpers

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
