package com.example.superznalci

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
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
import java.text.NumberFormat
import java.util.Locale
import android.widget.ScrollView
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Calendar

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    // Slovenian formatting: 2450 -> "2.450"
    private val numberFormat: NumberFormat by lazy {
        NumberFormat.getIntegerInstance(Locale.Builder().setLanguage("sl").setRegion("SI").build())
    }

    private var errorDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.main)
        root.applySystemBarsPadding()
        BottomNavHelper.setSelected(view, R.id.navProfile)

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        root.visibility = View.INVISIBLE   // shown once the data has arrived
        loadProfile(root)
    }

    private fun loadProfile(root: View) {
        ServiceLocator.profile.loadProfileData { result ->
            if (view == null) return@loadProfileData   // screen was left while waiting

            when (result) {
                is ApiResult.Success -> {
                    val profile = ProfileMapper.toStudentProfile(result.data)
                    bindHeader(root, profile)
                    bindIdentity(root, profile)
                    bindStreak(root, profile)
                    bindStats(root, profile)
                    bindSubjects(root, profile)
                    bindBadges(root, profile)
                    bindResults(root, profile)
                    setupActions(root, profile)
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

    // Without data there is nothing to show, so offer a retry instead of a blank screen
    private fun showLoadError(root: View, messageRes: Int) {
        errorDialog?.dismiss()
        errorDialog = AlertDialog.Builder(requireContext())
            .setMessage(messageRes)
            .setCancelable(false)
            .setPositiveButton(R.string.profile_retry) { _, _ -> loadProfile(root) }
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

    // binding

    private fun bindHeader(view: View, p: StudentProfile) {
        setText(view, R.id.tvTopStreak, getString(R.string.profile_top_streak, p.streakDays))
        setText(view, R.id.tvTopStars, getString(R.string.profile_top_stars, p.stars))
        setText(view, R.id.tvName, p.name)
        setText(view, R.id.tvClassSchool, getString(R.string.profile_class_school, p.className, p.school))
        setText(view, R.id.tvClassCode, getString(R.string.profile_code, p.classCode))

        setText(view, R.id.tvLevelNumber, p.level.toString())
        setText(view, R.id.tvLevelTitle, getString(R.string.profile_level_title, p.level, p.levelTitle))
        setText(
            view, R.id.tvLevelNext,
            getString(R.string.profile_level_next, p.level + 1, xp(p.xpGoal - p.xp))
        )

        val xpText = SpannableString(getString(R.string.profile_xp_value, xp(p.xp)))
        xpText.setSpan(
            RelativeSizeSpan(0.55f), xpText.length - 3, xpText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        setText(view, R.id.tvXp, xpText)

        val range = (p.xpGoal - p.xpLevelStart).coerceAtLeast(1)
        view.findViewById<ProgressBar>(R.id.progressLevel).progress =
            ((p.xp - p.xpLevelStart) * 100 / range).coerceIn(0, 100)

        setText(view, R.id.tvXpStart, getString(R.string.profile_xp_start, xp(p.xp), p.level))   // current XP
        setText(view, R.id.tvXpGoal, getString(R.string.profile_xp_goal, xp(p.xpGoal), p.level + 1))
    }

    private fun bindIdentity(view: View, p: StudentProfile) {
        setText(view, R.id.tvEmail, p.email)
        setText(
            view, R.id.tvRoleBadge,
            if (p.role.equals(Roles.TEACHER, ignoreCase = true))
                getString(R.string.profile_role_teacher)
            else
                getString(R.string.profile_role_student)
        )
        setText(view, R.id.tvRegisteredAt, p.registeredAt)
        setText(view, R.id.tvLastActiveAt, p.lastActiveAt)
    }

    private fun bindStreak(view: View, p: StudentProfile) {
        setText(
            view, R.id.tvStreakTitle,
            resources.getQuantityString(R.plurals.profile_streak_title, p.streakDays, p.streakDays)
        )

        val row = view.findViewById<LinearLayout>(R.id.weekRow)
        row.removeAllViews()
        val letters = resources.getStringArray(R.array.profile_week_days)
        val inflater = LayoutInflater.from(requireContext())

        p.week.forEachIndexed { index, state ->
            val day = inflater.inflate(R.layout.item_week_day, row, false)
            day.findViewById<TextView>(R.id.tvDayLetter).text = letters.getOrElse(index) { "" }

            val circle = day.findViewById<View>(R.id.dayCircle)
            val icon = day.findViewById<ImageView>(R.id.ivDayIcon)
            val emoji = day.findViewById<TextView>(R.id.tvDayEmoji)

            when (state) {
                DayState.DONE -> {
                    circle.setBackgroundResource(R.drawable.bg_day_done)
                    icon.setImageResource(R.drawable.ic_check_white)
                    icon.imageTintList = ColorStateList.valueOf(color(R.color.surface))
                }
                DayState.TODAY -> {
                    circle.setBackgroundResource(R.drawable.bg_day_today)
                    icon.visibility = View.GONE
                    emoji.visibility = View.VISIBLE
                }
                DayState.LOCKED -> {
                    circle.setBackgroundResource(R.drawable.bg_day_locked)
                    icon.setImageResource(R.drawable.ic_lock)
                    icon.imageTintList = ColorStateList.valueOf(color(R.color.on_surface_hint))
                }
                DayState.MISSED -> {        // a past day without activity: empty grey circle
                    circle.setBackgroundResource(R.drawable.bg_day_locked)
                    icon.visibility = View.GONE
                }
            }
            row.addView(day)
        }
    }

    private fun bindStats(view: View, p: StudentProfile) {
        setText(view, R.id.tvSolved, p.solvedTasks.toString())
        setText(view, R.id.tvAccuracy, getString(R.string.profile_accuracy_value, p.accuracy))
    }

    private fun bindSubjects(view: View, p: StudentProfile) {
        val container = view.findViewById<LinearLayout>(R.id.subjectsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        p.subjects.forEach { subject ->
            val item = inflater.inflate(R.layout.item_subject, container, false)
            val barColor = color(subject.accent.bar)
            val percent =
                if (subject.lessonsTotal == 0) 0 else subject.lessonsDone * 100 / subject.lessonsTotal

            item.findViewById<TextView>(R.id.tvSubjectTitle).text = subject.title
            item.findViewById<TextView>(R.id.tvSubjectLessons).text = getString(
                R.string.profile_subject_lessons, subject.lessonsDone, subject.lessonsTotal
            )
            item.findViewById<TextView>(R.id.tvSubjectStars).text = subject.stars.toString()

            item.findViewById<ImageView>(R.id.ivSubjectIcon).apply {
                setImageResource(subject.iconRes)
                imageTintList = ColorStateList.valueOf(barColor)
                background.mutate().setTint(ColorUtils.setAlphaComponent(barColor, 40))
            }

            item.findViewById<ProgressBar>(R.id.progressSubject).apply {
                progress = percent
                progressTintList = ColorStateList.valueOf(barColor)
            }

            item.findViewById<TextView>(R.id.tvSubjectPercent).apply {
                text = getString(R.string.profile_subject_percent, percent)
                setTextColor(color(subject.accent.text))
            }

            item.findViewById<TextView>(R.id.btnSubject).apply {
                if (subject.isActive) {
                    setText(R.string.profile_btn_continue)
                    setBackgroundResource(R.drawable.bg_button_primary)
                    setTextColor(color(R.color.surface))
                } else {
                    setText(R.string.profile_btn_review)
                    setBackgroundResource(R.drawable.bg_copy_btn)
                    setTextColor(color(R.color.on_surface))
                }
                setOnClickListener { openSubjects(subject.id) }
            }

            container.addView(item)
        }
    }

    private fun bindBadges(view: View, p: StudentProfile) {
        setText(view, R.id.tvBadgeCount, "${p.badgesEarned}/${p.badgesTotal}")

        val grid = view.findViewById<LinearLayout>(R.id.badgesContainer)
        grid.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        if (p.badges.isEmpty()) addEmptyMessage(grid, R.string.profile_no_badges)

        // Two badges per row
        p.badges.chunked(2).forEach { pair ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            pair.forEachIndexed { index, badge ->
                val item = inflater.inflate(R.layout.item_badge, row, false)
                item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply {
                        if (index == 0) marginEnd = dp(6) else marginStart = dp(6)
                    }
                bindBadge(item, badge)
                row.addView(item)
            }
            if (pair.size == 1) {   // keep the single badge at half width
                row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 0, 1f))
            }
            grid.addView(row)
        }

        val lockedCard = view.findViewById<View>(R.id.lockedBadgeCard)
        val locked = p.lockedBadge
        if (locked == null) {
            lockedCard.visibility = View.GONE
        } else {
            lockedCard.visibility = View.VISIBLE
            setText(view, R.id.tvLockedTitle, getString(R.string.profile_locked_title, locked.title))
            setText(view, R.id.tvLockedDesc, locked.condition)
        }
    }

    private fun bindBadge(item: View, badge: BadgeItem) {
        item.findViewById<TextView>(R.id.tvBadgeTitle).text = badge.title
        item.findViewById<TextView>(R.id.tvBadgeDesc).text = badge.description
        item.findViewById<View>(R.id.badgeCircle).background.mutate().setTint(color(badge.colorRes))

        val glyph = item.findViewById<TextView>(R.id.tvBadgeGlyph)
        val icon = item.findViewById<ImageView>(R.id.ivBadgeIcon)
        if (badge.glyph != null) {
            glyph.text = badge.glyph
            glyph.visibility = View.VISIBLE
        } else if (badge.iconRes != null) {
            icon.setImageResource(badge.iconRes)
            icon.visibility = View.VISIBLE
        }
    }

    private fun bindResults(view: View, p: StudentProfile) {
        val container = view.findViewById<LinearLayout>(R.id.resultsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        if (p.recentResults.isEmpty()) addEmptyMessage(container, R.string.profile_no_results)

        p.recentResults.forEach { result ->
            val item = inflater.inflate(R.layout.item_result, container, false)
            val accent = color(result.accent.bar)

            item.findViewById<ImageView>(R.id.ivResultIcon).apply {
                setImageResource(result.iconRes)
                imageTintList = ColorStateList.valueOf(accent)
                background.mutate().setTint(ColorUtils.setAlphaComponent(accent, 40))
            }
            item.findViewById<TextView>(R.id.tvResultTitle).text = result.title
            item.findViewById<TextView>(R.id.tvResultSub).text = result.subtitle
            item.findViewById<TextView>(R.id.tvResultScore).apply {
                text = getString(R.string.profile_result_score, result.score, result.maxScore)
                setTextColor(color(result.accent.text))
            }
            item.findViewById<TextView>(R.id.tvResultXp).text =
                getString(R.string.profile_result_xp, result.xp)

            container.addView(item)
        }
    }

    private fun addEmptyMessage(container: LinearLayout, textRes: Int) {
        container.addView(TextView(requireContext()).apply {
            setText(textRes)
            setTextColor(color(R.color.on_surface_muted))
            textSize = 13f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })
    }

    // actions

    private fun setupActions(view: View, p: StudentProfile) {
        val pending = View.OnClickListener { toast(R.string.profile_pending) }

        // TODO: connect each of these to a real screen.
        listOf(
            R.id.btnEditAvatar, R.id.rowAvatar, R.id.rowReminders,
            R.id.tvAllSubjects, R.id.tvAllBadges
        ).forEach { view.findViewById<View>(it).setOnClickListener(pending) }

        view.findViewById<View>(R.id.navLeaderboard).setOnClickListener { openLeaderboard() }
        view.findViewById<View>(R.id.btnCopyCode).setOnClickListener { copyClassCode(p.classCode) }
        view.findViewById<View>(R.id.rowLogout).setOnClickListener { confirmLogout() }
        view.findViewById<View>(R.id.navSubjects).setOnClickListener { openSubjects() }
        view.findViewById<View>(R.id.navPractice).setOnClickListener { openPractice() }
        view.findViewById<View>(R.id.navLearn)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LearnFragment())
                .commit()
        }
        view.findViewById<View>(R.id.tvAllBadges).setOnClickListener { showAllBadges(p) }
        view.findViewById<View>(R.id.tvAllSubjects).setOnClickListener { openSubjects(null) }
        view.findViewById<View>(R.id.rowReminders).setOnClickListener { onReminderClicked() }
    }

    private fun copyClassCode(code: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("class code", code))
        toast(R.string.reg_code_copied)
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.profile_logout_title)
            .setMessage(R.string.profile_logout_message)
            .setNegativeButton(R.string.profile_logout_cancel, null)
            .setPositiveButton(R.string.profile_logout_confirm) { _, _ ->
                SessionStore.clear()
                goToLogin()
            }
            .show()
    }

    private fun goToLogin() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    private fun openSubjects(subjectId: String? = null) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SubjectsFragment.newInstance(subjectId))
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

    private fun showAllBadges(p: StudentProfile) {
        val ctx = requireContext()
        val inflater = LayoutInflater.from(ctx)
        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        p.allBadges.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }
            pair.forEachIndexed { index, entry ->
                val item = inflater.inflate(R.layout.item_badge, row, false)
                item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (index == 0) marginEnd = dp(6) else marginStart = dp(6) }
                bindBadge(item, entry.badge)
                item.alpha = if (entry.earned) 1f else 0.35f   // locked = faded
                row.addView(item)
            }
            if (pair.size == 1) row.addView(View(ctx), LinearLayout.LayoutParams(0, 0, 1f))
            grid.addView(row)
        }
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.profile_all_badges_title, p.badgesEarned, p.badgesTotal))
            .setView(ScrollView(ctx).apply { addView(grid) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private val notificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showReminderPicker() else toast(R.string.reminder_permission_denied)
    }

    private fun onReminderClicked() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else showReminderPicker()
    }

    private fun showReminderPicker() {
        val saved = ReminderManager.savedTime(requireContext())
        val now = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                ReminderManager.set(requireContext(), hour, minute)
                ReminderManager.showNotification(requireContext()) 
                val time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                Toast.makeText(requireContext(), getString(R.string.reminder_set, time), Toast.LENGTH_SHORT).show()
            },
            saved?.first ?: now.get(Calendar.HOUR_OF_DAY),
            saved?.second ?: now.get(Calendar.MINUTE),
            true
        ).show()
    }

    // helpers

    private fun setText(view: View, id: Int, text: CharSequence) {
        view.findViewById<TextView>(id).text = text
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun xp(value: Int): String = numberFormat.format(value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}