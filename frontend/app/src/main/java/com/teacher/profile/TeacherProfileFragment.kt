package com.example.superznalci

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class TeacherProfileFragment : Fragment(R.layout.fragment_teacher_profile) {

    private val repo: TeacherRepository get() = ServiceLocator.teacher

    private lateinit var progress: ProgressBar
    private lateinit var switchEmail: SwitchCompat
    private lateinit var switchWeekly: SwitchCompat

    private var savedSettings = TeacherSettingsDto()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        progress = view.findViewById(R.id.progress)
        switchEmail = view.findViewById(R.id.switchEmail)
        switchWeekly = view.findViewById(R.id.switchWeekly)

        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        listOf(
            R.id.btnEditProfile, R.id.btnCreateClass, R.id.btnEditSubjects, R.id.rowSecurity,
            R.id.navLearn, R.id.navSubjects, R.id.navClassroom, R.id.navLeaderboard
        ).forEach { view.findViewById<View>(it).setOnClickListener(comingSoon) }

        view.findViewById<View>(R.id.btnLogout).setOnClickListener { logout() }

        SessionStore.user?.let { showUserHeader(view, it) }

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        repo.loadProfile { result -> onProfileResult(result) }
    }

    private fun onProfileResult(result: ApiResult<TeacherProfileData>) {
        val root = view ?: return
        progress.visibility = View.GONE
        when (result) {
            is ApiResult.Success -> render(root, result.data)
            is ApiResult.Failure -> when (result.httpCode) {
                401 -> logout()
                null -> toast(R.string.err_network)
                else -> toast(R.string.err_server)
            }
        }
    }


    private fun render(root: View, data: TeacherProfileData) {
        showUserHeader(root, data.user)

        root.findViewById<TextView>(R.id.tvSchool).text = data.schoolName
        root.findViewById<View>(R.id.schoolRow).visibility =
            if (data.schoolName.isBlank()) View.GONE else View.VISIBLE

        val verified = if (data.extras.isVerified) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.tvVerified).visibility = verified
        root.findViewById<View>(R.id.verifiedDot).visibility = verified

        renderStats(root, data)
        renderClasses(root, data)
        renderSubjects(root, data)
        bindSettings(data.settings)
    }

    private fun showUserHeader(root: View, user: UserDto) {
        root.findViewById<TextView>(R.id.tvStreak).text = user.streak.toString()
        root.findViewById<TextView>(R.id.tvXp).text = user.totalXp.toString()
        root.findViewById<TextView>(R.id.tvTeacherName).text =
            getString(R.string.teacher_name_fmt, user.name)
    }

    private fun renderStats(root: View, data: TeacherProfileData) {
        val row1 = root.findViewById<LinearLayout>(R.id.statsRow1)
        val row2 = root.findViewById<LinearLayout>(R.id.statsRow2)
        row1.removeAllViews()
        row2.removeAllViews()

        val classNames = data.classes
            .map { ClassroomNaming.parse(it.classroom.name).first.replace(".", ". ") }
            .joinToString(", ")

        addStat(
            row1, first = true,
            icon = R.drawable.ic_t_group, tile = R.color.t_orange_soft, iconColor = R.color.t_orange_text,
            badge = getString(R.string.teacher_stat_classes_badge),
            badgeBg = R.color.t_orange_soft, badgeText = R.color.t_orange_text,
            value = data.classes.size.toString(),
            caption = getString(R.string.teacher_stat_classes_caption, classNames)
        )
        addStat(
            row1, first = false,
            icon = R.drawable.ic_t_group, tile = R.color.t_mint, iconColor = R.color.t_green,
            badge = getString(R.string.teacher_stat_students_badge),
            badgeBg = R.color.t_green_soft, badgeText = R.color.t_green,
            value = data.totalStudents.toString(),
            caption = getString(R.string.teacher_stat_students_caption)
        )
        addStat(
            row2, first = true,
            icon = R.drawable.ic_t_quiz, tile = R.color.t_indigo_soft, iconColor = R.color.t_indigo,
            badge = getString(R.string.teacher_stat_quizzes_badge),
            badgeBg = R.color.t_indigo_soft, badgeText = R.color.t_indigo,
            value = data.extras.quizzesCreated.toString(),
            caption = getString(R.string.teacher_stat_quizzes_caption)
        )
        addStat(
            row2, first = false,
            icon = R.drawable.ic_t_trending_up, tile = R.color.t_green, iconColor = android.R.color.white,
            badge = getString(R.string.teacher_accuracy_delta, data.extras.accuracyDelta),
            badgeBg = R.color.t_green_soft, badgeText = R.color.t_green,
            value = getString(R.string.teacher_accuracy_value, data.extras.averageAccuracy.toInt()),
            caption = getString(R.string.teacher_stat_accuracy_caption)
        )
    }

    private fun addStat(
        parent: LinearLayout, first: Boolean,
        icon: Int, tile: Int, iconColor: Int,
        badge: String, badgeBg: Int, badgeText: Int,
        value: String, caption: String
    ) {
        val card = layoutInflater.inflate(R.layout.item_t_stat, parent, false)
        val gap = dp(4)
        (card.layoutParams as LinearLayout.LayoutParams).apply {
            if (first) marginEnd = gap else marginStart = gap
        }
        card.findViewById<View>(R.id.statIconBg).backgroundTintList = tint(tile)
        card.findViewById<ImageView>(R.id.statIcon).apply {
            setImageResource(icon)
            imageTintList = tint(iconColor)
        }
        card.findViewById<TextView>(R.id.statBadge).apply {
            text = badge
            backgroundTintList = tint(badgeBg)
            setTextColor(color(badgeText))
        }
        card.findViewById<TextView>(R.id.statValue).text = value
        card.findViewById<TextView>(R.id.statCaption).text = caption
        parent.addView(card)
    }

    private fun renderClasses(root: View, data: TeacherProfileData) {
        root.findViewById<TextView>(R.id.tvClassesCount).text =
            getString(R.string.teacher_classes_count, data.classes.size)

        val container = root.findViewById<LinearLayout>(R.id.classesContainer)
        container.removeAllViews()

        data.classes.forEachIndexed { index, item ->
            val card = layoutInflater.inflate(R.layout.item_t_class, container, false)
            bindClassCard(card, item, accentIndigo = index % 2 == 1)
            container.addView(card)
        }
    }

    private fun bindClassCard(card: View, item: TeacherClassUi, accentIndigo: Boolean) {
        val c = item.classroom
        val label = ClassroomNaming.parse(c.name).first 
        val pretty = label.replace(Regex("^(\\d+)\\.?\\s*"), "$1. ") 

        val accent = if (accentIndigo) R.color.t_indigo else R.color.t_green
        val shareBg = if (accentIndigo) R.color.t_indigo_soft else R.color.t_mint

        card.findViewById<TextView>(R.id.classBadge).apply {
            text = label.replace(".", "").replace(" ", "").take(3)
            backgroundTintList = tint(accent)
        }
        card.findViewById<TextView>(R.id.classTitle).text = getString(R.string.teacher_class_title, pretty)
        card.findViewById<ImageView>(R.id.classStudentsIcon).imageTintList = tint(R.color.t_indigo)

        val students = getString(R.string.teacher_class_students, item.studentCount)
        val subjects = item.subjectNames.joinToString(" & ") { it.trim().take(3) }
        card.findViewById<TextView>(R.id.classMeta).text =
            if (subjects.isEmpty()) students else "$students • $subjects"

        val shiftView = card.findViewById<TextView>(R.id.classShift)
        when (item.shift) {
            Shifts.MORNING -> shiftView.setText(R.string.teacher_shift_morning)
            Shifts.AFTERNOON -> shiftView.setText(R.string.teacher_shift_afternoon)
            else -> shiftView.visibility = View.GONE
        }

        card.findViewById<TextView>(R.id.classCode).apply {
            text = c.joinCode
            setTextColor(color(accent))
        }
        card.findViewById<View>(R.id.btnCopy).setOnClickListener { copyCode(c.joinCode) }
        card.findViewById<View>(R.id.btnShare).apply {
            backgroundTintList = tint(shareBg)
            (this as ImageView).imageTintList = tint(accent)
            setOnClickListener { shareCode(pretty, c.joinCode) }
        }
    }

    private fun renderSubjects(root: View, data: TeacherProfileData) {
        val subtitle = root.findViewById<TextView>(R.id.tvSubjectsSubtitle)
        subtitle.text = data.gradeNumber
            ?.let { getString(R.string.teacher_subjects_subtitle, it) }
            ?: getString(R.string.teacher_subjects_subtitle_empty)

        val flow = root.findViewById<ViewGroup>(R.id.subjectsFlow)
        flow.removeAllViews()

        val dots = listOf(R.color.t_green, R.color.t_indigo, R.color.t_orange_text)
        data.subjectChips.forEachIndexed { i, chip ->
            val v = layoutInflater.inflate(R.layout.item_t_subject_chip, flow, false)
            v.findViewById<View>(R.id.chipDot).backgroundTintList = tint(dots[i % dots.size])
            v.findViewById<TextView>(R.id.chipName).text = chip.name
            v.findViewById<TextView>(R.id.chipModules).text =
                getString(R.string.teacher_subject_modules, chip.moduleCount)
            flow.addView(v)
        }

        val add = TextView(requireContext()).apply {
            text = getString(R.string.teacher_add_subject)
            setTextColor(color(R.color.t_text))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_t_pill)
            backgroundTintList = tint(R.color.t_chip_bg)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            compoundDrawablePadding = dp(4)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_t_add, 0, 0, 0)
            compoundDrawableTintList = tint(R.color.t_text)
            setOnClickListener { toast(R.string.teacher_coming_soon) }
        }
        flow.addView(add)
    }

    private fun bindSettings(settings: TeacherSettingsDto) {
        savedSettings = settings
        switchEmail.setOnCheckedChangeListener(null)
        switchWeekly.setOnCheckedChangeListener(null)
        switchEmail.isChecked = settings.emailNotifications
        switchWeekly.isChecked = settings.weeklyReport
        switchEmail.setOnCheckedChangeListener { _, _ -> saveSettings() }
        switchWeekly.setOnCheckedChangeListener { _, _ -> saveSettings() }
    }

    private fun saveSettings() {
        val wanted = TeacherSettingsDto(switchEmail.isChecked, switchWeekly.isChecked)
        repo.updateSettings(wanted) { result ->
            if (view == null) return@updateSettings
            when (result) {
                is ApiResult.Success -> savedSettings = result.data
                is ApiResult.Failure -> {
                    bindSettings(savedSettings)
                    if (result.httpCode == 401) logout() else toast(R.string.teacher_settings_save_failed)
                }
            }
        }
    }

    // ----------------------------------------------------------------- actions

    private fun copyCode(code: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("joinCode", code))
        toast(R.string.teacher_code_copied)
    }

    private fun shareCode(className: String, code: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.teacher_share_text, className, code))
        }
        startActivity(Intent.createChooser(send, getString(R.string.teacher_share_chooser)))
    }

    private fun logout() {
        SessionStore.clear()
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    // ----------------------------------------------------------------- helpers

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun tint(res: Int) = ColorStateList.valueOf(color(res))
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
