package com.example.superznalci

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class SubjectsFragment : Fragment(R.layout.fragment_subjects) {

    private var data: SubjectsData? = null
    private var screen: SubjectsScreen? = null
    private var selectedSubjectId: String? = null

    private val expandedTopics = mutableSetOf<String>()
    private val seenTopics = mutableSetOf<String>()

    private var errorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedSubjectId = savedInstanceState?.getString(KEY_SELECTED)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED, selectedSubjectId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.main)
        root.applySystemBarsPadding()
        BottomNavHelper.setSelected(view, R.id.navSubjects)

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        setupNavigation(view)
        root.visibility = View.INVISIBLE
        load(root)
    }

    private fun load(root: View) {
        ServiceLocator.subjects.loadSubjectsData { result ->
            if (view == null) return@loadSubjectsData

            when (result) {
                is ApiResult.Success -> {
                    data = result.data
                    render()
                    root.visibility = View.VISIBLE
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    401 -> {
                        SessionStore.clear()
                        goToLogin()
                    }
                    null -> showLoadError(root, R.string.err_network)
                    else -> showLoadError(root, R.string.err_server)
                }
            }
        }
    }

    private fun showLoadError(root: View, messageRes: Int) {
        errorDialog?.dismiss()
        errorDialog = AlertDialog.Builder(requireContext())
            .setMessage(messageRes)
            .setCancelable(false)
            .setPositiveButton(R.string.profile_retry) { _, _ -> load(root) }
            .setNegativeButton(R.string.profile_logout_confirm) { _, _ ->
                SessionStore.clear()
                goToLogin()
            }
            .show()
    }

    override fun onDestroyView() {
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroyView()
    }

    // rendering

    private fun render() {
        val d = data ?: return
        val view = requireView()
        val s = SubjectsMapper.toScreen(d, selectedSubjectId)
        screen = s
        selectedSubjectId = s.active?.id

        bindTop(view, s)
        bindClass(view, s)
        bindTabs(view, s)
        bindActive(view, s)
        bindTopics(view, s)
    }

    private fun bindTop(view: View, s: SubjectsScreen) {
        setText(view, R.id.tvTopStreak, getString(R.string.profile_top_streak, s.streakDays))
        setText(view, R.id.tvTopStars, getString(R.string.profile_top_stars, s.stars))
    }

    private fun bindClass(view: View, s: SubjectsScreen) {
        val chip = view.findViewById<TextView>(R.id.tvGradeChip)
        if (s.gradeNumber == null) {
            chip.visibility = View.GONE
        } else {
            chip.visibility = View.VISIBLE
            chip.text = getString(R.string.subjects_grade_chip, s.gradeNumber)
        }
        setText(
            view, R.id.tvStudents,
            resources.getQuantityString(R.plurals.subjects_students, s.studentCount, s.studentCount)
        )
        setText(view, R.id.tvClassName, getString(R.string.subjects_class_name, s.className))
        setText(view, R.id.tvSchool, s.school)
        view.findViewById<View>(R.id.tvSchool).visibility =
            if (s.school.isBlank()) View.GONE else View.VISIBLE
        setText(view, R.id.tvClassCode, s.classCode)
        view.findViewById<View>(R.id.btnCopyCode).setOnClickListener { copyClassCode(s.classCode) }

        setText(view, R.id.tvChallenge, getString(R.string.subjects_challenge, s.challengeGoal))
        setText(
            view, R.id.tvChallengeProgress,
            getString(R.string.subjects_fraction, s.challengeDone, s.challengeGoal)
        )
    }

    private fun bindTabs(view: View, s: SubjectsScreen) {
        setText(view, R.id.tvSubjectsTitle, getString(R.string.subjects_your_subjects))
        setText(
            view, R.id.tvSubjectsCount,
            resources.getQuantityString(R.plurals.subjects_active_count, s.tabs.size, s.tabs.size)
        )
        view.findViewById<View>(R.id.tvSwitch).setOnClickListener {
            val index = s.tabs.indexOfFirst { it.id == s.active?.id }
            if (s.tabs.isNotEmpty()) selectSubject(s.tabs[(index + 1) % s.tabs.size].id)
        }

        val container = view.findViewById<LinearLayout>(R.id.subjectTabs)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        s.tabs.forEach { tab ->
            val item = inflater.inflate(R.layout.item_subject_tab, container, false)
            val selected = tab.id == s.active?.id
            val bar = color(tab.accent.bar)

            item.findViewById<TextView>(R.id.tvTabName).text = tab.name
            item.findViewById<TextView>(R.id.tvTabTopics).text =
                resources.getQuantityString(R.plurals.subjects_topics, tab.topicCount, tab.topicCount)
            item.findViewById<TextView>(R.id.tvTabPercent).apply {
                text = getString(R.string.subjects_percent, tab.percent)
                setTextColor(color(tab.accent.text))
            }
            item.findViewById<ProgressBar>(R.id.progressTab).apply {
                progress = tab.percent
                progressTintList = ColorStateList.valueOf(bar)
            }
            item.findViewById<ImageView>(R.id.ivTabIcon).apply {
                setImageResource(tab.iconRes)
                if (selected) {
                    imageTintList = ColorStateList.valueOf(color(R.color.sub_white))
                    background.mutate().setTint(bar)
                } else {
                    imageTintList = ColorStateList.valueOf(bar)
                    background.mutate().setTint(ColorUtils.setAlphaComponent(bar, 40))
                }
            }
            if (selected) item.setBackgroundResource(R.drawable.sub_tile_selected)

            item.setOnClickListener { selectSubject(tab.id) }
            container.addView(item)
        }
    }

    private fun selectSubject(id: String) {
        if (id == selectedSubjectId) return
        selectedSubjectId = id
        render()
    }

    private fun bindActive(view: View, s: SubjectsScreen) {
        val card = view.findViewById<View>(R.id.activeCard)
        val a = s.active
        if (a == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        setText(view, R.id.tvActiveTitle, a.title)
        view.findViewById<TextView>(R.id.tvTeacher).apply {
            if (a.teacher.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = getString(R.string.subjects_teacher, a.teacher)
            }
        }
        setText(view, R.id.tvActiveXp, getString(R.string.subjects_xp, a.xp))
        setText(view, R.id.tvActiveLevel, getString(R.string.subjects_level, a.level))
        setText(view, R.id.tvStatPercent, getString(R.string.subjects_percent, a.percent))
        setText(
            view, R.id.tvStatLessons,
            getString(R.string.subjects_fraction, a.lessonsDone, a.lessonsTotal)
        )
        setText(view, R.id.tvStatStars, a.stars.toString())
    }

    private fun bindTopics(view: View, s: SubjectsScreen) {
        val container = view.findViewById<LinearLayout>(R.id.topicsContainer)
        container.removeAllViews()
        val a = s.active

        if (a == null) {
            addEmptyMessage(container, R.string.subjects_empty)
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        a.topics.forEach { topic ->
            if (seenTopics.add(topic.id) && topic.expandedByDefault) expandedTopics += topic.id

            val item = inflater.inflate(R.layout.item_topic, container, false)
            val lessonsHost = item.findViewById<LinearLayout>(R.id.lessonsContainer)
            val lessonsWrap = item.findViewById<View>(R.id.lessonsWrap)
            val chevron = item.findViewById<ImageView>(R.id.ivChevron)
            val number = item.findViewById<TextView>(R.id.tvTopicNumber)

            setText(item, R.id.tvTopicLabel, getString(R.string.subjects_topic_label, topic.number))
            setText(item, R.id.tvTopicName, topic.name)
            number.text = topic.number.toString()
            item.findViewById<TextView>(R.id.tvTopicProgress).text =
                if (topic.lessonsTotal > 0 && topic.lessonsDone == topic.lessonsTotal)
                    getString(R.string.subjects_percent, 100)
                else
                    getString(R.string.subjects_topic_lessons, topic.lessonsDone, topic.lessonsTotal)

            if (topic.lessons.isEmpty()) addEmptyMessage(lessonsHost, R.string.subjects_no_lessons)
            topic.lessons.forEach { lesson -> lessonsHost.addView(inflateLesson(inflater, lessonsHost, lesson)) }

            fun apply() {
                val open = topic.id in expandedTopics
                lessonsWrap.visibility = if (open) View.VISIBLE else View.GONE
                chevron.rotation = if (open) 180f else 0f
                number.setBackgroundResource(if (open) R.drawable.sub_circle_dark else R.drawable.sub_circle_grey)
                number.setTextColor(color(if (open) R.color.sub_white else R.color.sub_text))
            }
            apply()
            item.findViewById<View>(R.id.topicHeader).setOnClickListener {
                if (!expandedTopics.add(topic.id)) expandedTopics.remove(topic.id)
                apply()
            }

            container.addView(item)
        }
    }

    // lessons

    private fun inflateLesson(inflater: LayoutInflater, parent: ViewGroup, lesson: LessonItem): View =
        when (lesson.state) {
            LessonState.DONE -> inflater.inflate(R.layout.item_lesson_done, parent, false).also { bindDone(it, lesson) }
            LessonState.CURRENT -> inflater.inflate(R.layout.item_lesson_current, parent, false).also { bindCurrent(it, lesson) }
            LessonState.LOCKED -> inflater.inflate(R.layout.item_lesson_locked, parent, false).also { bindLocked(it, lesson) }
        }

    private fun bindDone(item: View, l: LessonItem) {
        setText(item, R.id.tvLessonHeader, getString(R.string.subjects_lesson_done_header, l.number))
        setText(item, R.id.tvLessonStars, "★".repeat(l.stars) + "☆".repeat(3 - l.stars))
        setText(item, R.id.tvLessonTitle, l.title)
        setText(
            item, R.id.tvLessonDesc,
            if (l.total > 0 && l.correct == l.total)
                getString(R.string.subjects_lesson_perfect, l.correct, l.total)
            else
                getString(R.string.subjects_lesson_score, l.correct, l.total)
        )
        item.findViewById<View>(R.id.tvRepeat).setOnClickListener { startLesson(l) }
    }

    private fun bindCurrent(item: View, l: LessonItem) {
        setText(item, R.id.tvLessonXp, getString(R.string.subjects_lesson_xp, l.xp))
        setText(item, R.id.tvLessonTitle, getString(R.string.subjects_lesson_title, l.number, l.title))
        item.findViewById<TextView>(R.id.tvLessonDesc).apply {
            if (l.description.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = l.description
            }
        }
        item.findViewById<TextView>(R.id.btnStart).apply {
            text = resources.getQuantityString(R.plurals.subjects_start, l.questionCount, l.questionCount)
            setOnClickListener { startLesson(l) }
        }
        val meta = listOfNotNull(
            getString(R.string.subjects_minutes, l.minutes),
            l.dueLabel?.let { getString(R.string.subjects_due, it) }
        )
        setText(item, R.id.tvLessonMeta, meta.joinToString(" • "))
    }

    private fun bindLocked(item: View, l: LessonItem) {
        setText(item, R.id.tvLessonHeader, getString(R.string.subjects_lesson_locked_header, l.number))
        setText(item, R.id.tvLessonScore, getString(R.string.subjects_fraction, 0, l.questionCount))
        setText(item, R.id.tvLessonTitle, l.title)
        setText(
            item, R.id.tvLessonDesc,
            if (l.unlockAfter != null) getString(R.string.subjects_unlock, l.unlockAfter) else ""
        )
    }

    private fun startLesson(lesson: LessonItem) {
        val s = screen ?: return
        val subject = s.active ?: return
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container,
                QuizFragment.newInstance(subject.id, subject.title, s.gradeNumber, lesson.id))
            .addToBackStack(null)
            .commit()
    }

    // navigation

    private fun setupNavigation(view: View) {
        val pending = View.OnClickListener { toast(R.string.profile_pending) }
        view.findViewById<View>(R.id.navLeaderboard).setOnClickListener { openLeaderboard() }
        view.findViewById<View>(R.id.navPractice).setOnClickListener { openPractice() }
        view.findViewById<View>(R.id.navProfile).setOnClickListener { openProfile() }
        view.findViewById<View>(R.id.ivAvatar).setOnClickListener { openProfile() }
        view.findViewById<View>(R.id.navLearn)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LearnFragment())
                .commit()
        }
    }

    private fun openProfile() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProfileFragment())
            .commit()
    }

    private fun openLeaderboard() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LeaderboardFragment())
            .commit()
    }

    private fun openPractice() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PracticeFragment())
            .commit()
    }

    private fun goToLogin() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    private fun copyClassCode(code: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("class code", code))
        toast(R.string.reg_code_copied)
    }

    // helpers

    private fun addEmptyMessage(container: LinearLayout, textRes: Int) {
        container.addView(TextView(requireContext()).apply {
            setText(textRes)
            setTextColor(color(R.color.sub_text_muted))
            textSize = 13f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })
    }

    private fun setText(view: View, id: Int, text: CharSequence) {
        view.findViewById<TextView>(id).text = text
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_SELECTED = "selected_subject"
    }
}
