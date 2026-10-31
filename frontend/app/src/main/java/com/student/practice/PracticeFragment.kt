package com.example.superznalci

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

// Vadnica: free practice for every student, not connected to the classroom
class PracticeFragment : Fragment(R.layout.fragment_practice) {

    private var gradeNumber: Int? = null
    private var subjects: List<PracticeSubjectDto> = emptyList()
    private var errorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gradeNumber = savedInstanceState?.getInt(KEY_GRADE)?.takeIf { it in GRADES }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        gradeNumber?.let { outState.putInt(KEY_GRADE, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.main).applySystemBarsPadding()
        BottomNavHelper.setSelected(view, R.id.navPractice)

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        setupNavigation(view)
        bindInfo(view)

        val grade = gradeNumber
        if (grade != null) {
            selectGrade(grade)
        } else {
            // this@PracticeFragment.view: the parameter "view" would always be non-null here
            ServiceLocator.practice.loadDefaultGrade { if (this@PracticeFragment.view != null) selectGrade(it) }
        }
    }

    override fun onDestroyView() {
        errorDialog?.dismiss()
        errorDialog = null
        super.onDestroyView()
    }

    private fun selectGrade(grade: Int) {
        gradeNumber = grade
        subjects = emptyList()
        bindGrades(requireView())
        bindSubjects(requireView(), loading = true)
        load(grade)
    }

    private fun load(grade: Int) {
        ServiceLocator.practice.loadSubjects(grade) { result ->
            // ignore late answers for a grade that is no longer selected
            if (view == null || grade != gradeNumber) return@loadSubjects

            when (result) {
                is ApiResult.Success -> {
                    subjects = result.data
                    bindSubjects(requireView(), loading = false)
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    401 -> {
                        SessionStore.clear()
                        goToLogin()
                    }
                    null -> showLoadError(grade, R.string.err_network)
                    else -> showLoadError(grade, R.string.err_server)
                }
            }
        }
    }

    private fun showLoadError(grade: Int, messageRes: Int) {
        errorDialog?.dismiss()
        errorDialog = AlertDialog.Builder(requireContext())
            .setMessage(messageRes)
            .setPositiveButton(R.string.profile_retry) { _, _ -> load(grade) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> bindSubjects(requireView(), loading = false) }
            .show()
    }

    // rendering

    private fun bindInfo(view: View) {
        val title = getString(R.string.practice_info_title)
        view.findViewById<TextView>(R.id.tvInfo).text = SpannableStringBuilder()
            .append(title, StyleSpan(Typeface.BOLD), 0)
            .append(" ")
            .append(getString(R.string.practice_info))
    }

    private fun bindGrades(view: View) {
        val selected = gradeNumber
        view.findViewById<TextView>(R.id.tvGradeActive).text =
            if (selected == null) "" else getString(R.string.practice_grade_active, selected)

        val container = view.findViewById<LinearLayout>(R.id.gradeChips)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        GRADES.forEach { grade ->
            val item = inflater.inflate(R.layout.item_practice_grade, container, false)
            val isSelected = grade == selected
            item.findViewById<TextView>(R.id.tvGradeNumber).apply {
                text = grade.toString()
                setTextColor(color(if (isSelected) R.color.sub_green_dark else R.color.sub_text))
            }
            if (isSelected) item.setBackgroundResource(R.drawable.sub_tile_selected)
            item.setOnClickListener { if (grade != gradeNumber) selectGrade(grade) }
            container.addView(item)
        }
    }

    private fun bindSubjects(view: View, loading: Boolean) {
        val grade = gradeNumber
        view.findViewById<TextView>(R.id.tvSubjectsTitle).text =
            if (grade == null) "" else getString(R.string.practice_subjects_title, grade)
        view.findViewById<TextView>(R.id.tvSubjectsCount).text =
            if (loading) "" else resources.getQuantityString(R.plurals.practice_subject_count, subjects.size, subjects.size)
        view.findViewById<View>(R.id.progressSubjects).visibility = if (loading) View.VISIBLE else View.GONE

        val container = view.findViewById<LinearLayout>(R.id.subjectsContainer)
        container.removeAllViews()
        if (loading) return

        if (subjects.isEmpty()) {
            addEmptyMessage(container, R.string.practice_empty)
            return
        }

        // two cards per row, like in the mockup
        val inflater = LayoutInflater.from(requireContext())
        subjects.chunked(2).forEachIndexed { rowIndex, rowSubjects ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = dp(12) }
            }
            rowSubjects.forEachIndexed { i, subject ->
                val card = inflater.inflate(R.layout.item_practice_subject, row, false)
                bindSubjectCard(card, subject, ACCENTS[(rowIndex * 2 + i) % ACCENTS.size])
                if (i == 1) (card.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
                row.addView(card)
            }
            // keep a single last card at half width
            if (rowSubjects.size == 1) {
                row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(12) })
            }
            container.addView(row)
        }
    }

    private fun bindSubjectCard(card: View, subject: PracticeSubjectDto, accent: SubjectAccent) {
        val bar = color(accent.bar)
        card.findViewById<ImageView>(R.id.ivSubjectIcon).apply {
            setImageResource(ProfileMapper.iconFor(subject.icon))
            imageTintList = ColorStateList.valueOf(bar)
            background.mutate().setTint(ColorUtils.setAlphaComponent(bar, 40))
        }
        card.findViewById<TextView>(R.id.tvSubjectName).text = subject.name
        card.findViewById<TextView>(R.id.tvSubjectMeta).text = getString(
            R.string.practice_subject_meta,
            resources.getQuantityString(R.plurals.subjects_topics, subject.topicCount, subject.topicCount),
            resources.getQuantityString(R.plurals.practice_questions, subject.questionCount, subject.questionCount)
        )
        // TODO (next task): open the topics and lessons of the subject
        card.setOnClickListener { toast(R.string.profile_pending) }
    }

    // navigation

    private fun setupNavigation(view: View) {
        view.findViewById<View>(R.id.navLearn)?.setOnClickListener { replaceWith(LearnFragment()) }
        view.findViewById<View>(R.id.navSubjects)?.setOnClickListener { replaceWith(SubjectsFragment()) }
        view.findViewById<View>(R.id.navLeaderboard)?.setOnClickListener { replaceWith(LeaderboardFragment()) }
        view.findViewById<View>(R.id.navProfile)?.setOnClickListener { replaceWith(ProfileFragment()) }
        view.findViewById<View>(R.id.ivAvatar)?.setOnClickListener { replaceWith(ProfileFragment()) }
    }

    private fun replaceWith(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun goToLogin() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
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

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_GRADE = "practice_grade"
        val GRADES = 1..9
        val ACCENTS = SubjectAccent.values()
    }
}
