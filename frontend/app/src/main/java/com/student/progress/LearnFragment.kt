package com.example.superznalci

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LearnFragment : Fragment(R.layout.fragment_learn) {

    private val sl: Locale by lazy { Locale.Builder().setLanguage("sl").setRegion("SI").build() }

    private var screen: LearnScreen? = null
    private val claimed = mutableSetOf<String>()      // missions claimed on this screen
    private var errorDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.main)
        root.applySystemBarsPadding()
        BottomNavHelper.setSelected(view, R.id.navLearn)

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        setupNavigation(view)
        root.visibility = View.INVISIBLE   // shown once the data has arrived
        load(root)
    }

    private fun load(root: View) {
        ServiceLocator.learn.loadLearnData { result ->
            if (view == null) return@loadLearnData

            when (result) {
                is ApiResult.Success -> {
                    val s = LearnMapper.toScreen(result.data)
                    screen = s
                    bindTop(root, s)
                    bindStreak(root, s)
                    bindHomework(root, s)
                    bindChapter(root, s)
                    bindMissions(root, s)
                    bindActivity(root, s)
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

    // top bar + streak

    private fun bindTop(view: View, s: LearnScreen) {
        setText(view, R.id.tvTopStreak, "🔥 ${s.streakDays}")
        setText(view, R.id.tvTopXp, "⚡ ${s.xp}")
        view.findViewById<TextView>(R.id.tvAvatar).apply {
            text = s.userInitials
            background = oval(color(R.color.primary_dark))
            setOnClickListener { openProfile() }
        }
    }

    private fun bindStreak(view: View, s: LearnScreen) {
        setText(
            view, R.id.tvStreakTitle,
            resources.getQuantityString(R.plurals.learn_streak_days, s.streakDays, s.streakDays)
        )
        view.findViewById<TextView>(R.id.tvStreakRecord).apply {
            if (s.streakRecord > 0) {
                visibility = View.VISIBLE
                text = getString(R.string.learn_streak_record, s.streakRecord)
            } else {
                visibility = View.GONE
            }
        }
        view.findViewById<View>(R.id.streakIcon).background =
            rounded(ColorUtils.setAlphaComponent(color(R.color.secondary), 60), 14)

        val reached = s.dailyDone >= s.dailyGoal
        view.findViewById<TextView>(R.id.tvGoalChip).apply {
            visibility = if (reached) View.VISIBLE else View.GONE
            background = rounded(ColorUtils.setAlphaComponent(color(R.color.primary), 90), 16)
        }
        view.findViewById<View>(R.id.goalBox).background = rounded(color(R.color.quiz_surface_alt), 14)
        setText(view, R.id.tvGoalCount, getString(R.string.learn_goal_count, s.dailyDone.coerceAtMost(s.dailyGoal), s.dailyGoal))
        view.findViewById<ProgressBar>(R.id.progressGoal).progress =
            (s.dailyDone * 100 / s.dailyGoal).coerceIn(0, 100)
    }

    // homework

    private fun bindHomework(view: View, s: LearnScreen) {
        val card = view.findViewById<View>(R.id.homeworkCard)
        val hw = s.homework
        if (hw == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        setText(view, R.id.tvHwTitle, hw.lessonName)
        setText(
            view, R.id.tvHwMeta,
            if (hw.teacher.isNullOrBlank())
                getString(R.string.learn_hw_meta_no_teacher, hw.subjectTitle)
            else
                getString(R.string.learn_hw_meta, hw.teacher, hw.subjectTitle)
        )

        view.findViewById<TextView>(R.id.tvHwDue).apply {
            val due = hw.dueMillis
            if (due == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = if (DateUtils.isToday(due))
                    getString(R.string.learn_due_today, SimpleDateFormat("HH:mm", sl).format(Date(due)))
                else
                    getString(R.string.learn_due_date, SimpleDateFormat("d. M.", sl).format(Date(due)))
                background = rounded(ColorUtils.setAlphaComponent(color(R.color.quiz_wrong), 28), 12)
            }
        }
        view.findViewById<TextView>(R.id.btnHomework).apply {
            background = rounded(color(R.color.primary_dark), 14)
            setOnClickListener { startLesson(hw.subjectId, hw.subjectTitle, s.gradeNumber, hw.lessonId) }
        }
    }

    // chapter path

    private fun bindChapter(view: View, s: LearnScreen) {
        val chapter = s.chapter
        val container = view.findViewById<LinearLayout>(R.id.pathContainer)
        container.removeAllViews()

        view.findViewById<View>(R.id.btnMap).apply {
            background = rounded(color(R.color.quiz_surface_alt), 12)
            setOnClickListener { toast(R.string.profile_pending) }   // TODO: chapter map screen
        }

        if (chapter == null) {
            view.findViewById<View>(R.id.chapterHeader).visibility = View.GONE
            container.addView(TextView(requireContext()).apply {
                setText(R.string.learn_no_path)
                setTextColor(color(R.color.on_surface_muted))
                textSize = 13f
                setPadding(dp(16), dp(16), dp(16), dp(16))
            })
            return
        }
        view.findViewById<View>(R.id.chapterHeader).visibility = View.VISIBLE
        setText(view, R.id.tvChapterLabel, getString(R.string.learn_chapter_label, chapter.number))
        setText(view, R.id.tvChapterTitle, chapter.title)

        val inflater = LayoutInflater.from(requireContext())
        chapter.nodes.forEachIndexed { index, node ->
            val frac = NODE_POSITIONS[index % NODE_POSITIONS.size]
            if (index > 0) {
                val prev = chapter.nodes[index - 1]
                val prevFrac = NODE_POSITIONS[(index - 1) % NODE_POSITIONS.size]
                val solid = prev.state == NodeState.DONE
                container.addView(
                    PathConnectorView(
                        requireContext(), prevFrac, frac, dp(NODE_WIDTH_DP),
                        if (solid) color(R.color.primary) else color(R.color.quiz_surface_alt),
                        dashed = !solid
                    ),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
                )
            }
            container.addView(inflateNode(inflater, container, node, frac, chapter, s.gradeNumber))
        }
    }

    private fun inflateNode(
        inflater: LayoutInflater,
        parent: ViewGroup,
        node: PathNode,
        frac: Float,
        chapter: Chapter,
        grade: Int?
    ): View {
        val item = inflater.inflate(R.layout.item_path_node, parent, false)

        item.findViewById<View>(R.id.spacerStart).layoutParams =
            LinearLayout.LayoutParams(0, 1, frac * 100f)
        item.findViewById<View>(R.id.spacerEnd).layoutParams =
            LinearLayout.LayoutParams(0, 1, (1f - frac) * 100f)

        val tag = item.findViewById<TextView>(R.id.tvNodeTag)
        val circle = item.findViewById<View>(R.id.nodeCircle)
        val icon = item.findViewById<TextView>(R.id.tvNodeIcon)
        val title = item.findViewById<TextView>(R.id.tvNodeTitle)
        val sub = item.findViewById<TextView>(R.id.tvNodeSub)
        val card = item.findViewById<View>(R.id.nodeCard)

        val size = dp(if (node.state == NodeState.CURRENT) 88 else 72)
        circle.layoutParams = circle.layoutParams.apply { width = size; height = size }

        tag.visibility = View.GONE
        card.background = rounded(color(R.color.surface), 12)

        when (node.state) {
            NodeState.DONE -> {
                circle.background = oval(color(R.color.primary))
                icon.text = "✓"
                icon.setTextColor(color(R.color.surface))
                sub.text = "★".repeat(node.stars) + "☆".repeat(3 - node.stars)
                sub.setTextColor(color(R.color.secondary))
            }
            NodeState.CURRENT -> {
                circle.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color(R.color.primary))
                    setStroke(dp(8), ColorUtils.setAlphaComponent(color(R.color.primary), 90))
                }
                icon.text = if (node.isBoss) "🏆" else "▶"
                icon.setTextColor(color(R.color.surface))
                if (!node.isBoss) {
                    tag.visibility = View.VISIBLE
                    tag.text = getString(
                        R.string.learn_node_tag,
                        resources.getQuantityString(R.plurals.learn_questions, node.questionCount, node.questionCount)
                    )
                    tag.background = rounded(color(R.color.surface), 12)
                    sub.text = getString(R.string.learn_node_level, node.order, node.total)
                } else {
                    sub.text = getString(R.string.learn_boss_sub)
                }
                sub.setTextColor(color(R.color.on_surface_muted))
            }
            NodeState.LOCKED -> {
                circle.background = oval(color(R.color.quiz_surface_alt))
                icon.text = if (node.isBoss) "🏆" else "🔒"
                icon.setTextColor(color(R.color.on_surface_hint))
                sub.text = getString(R.string.learn_locked)
                sub.setTextColor(color(R.color.on_surface_muted))
                card.background = null
            }
        }

        title.text = if (node.isBoss) getString(R.string.learn_boss_title) else node.title
        if (node.state == NodeState.LOCKED) title.setTextColor(color(R.color.on_surface_muted))

        item.findViewById<View>(R.id.nodeContent).setOnClickListener {
            when {
                node.state == NodeState.LOCKED -> toast(R.string.learn_locked_toast)
                node.isBoss || node.lessonId == null -> toast(R.string.profile_pending)   // TODO: boss quiz
                else -> startLesson(chapter.subjectId, chapter.subjectTitle, grade, node.lessonId)
            }
        }
        return item
    }

    // missions

    private fun bindMissions(view: View, s: LearnScreen) {
        setText(view, R.id.tvReset, getString(R.string.learn_reset_in, s.hoursToReset))

        val container = view.findViewById<LinearLayout>(R.id.missionsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        s.missions.forEach { m ->
            val item = inflater.inflate(R.layout.item_mission, container, false)
            item.background = rounded(color(R.color.surface), 16)

            val isClaimed = m.state == MissionState.CLAIMED || m.id in claimed
            val state = if (isClaimed) MissionState.CLAIMED else m.state

            val (glyph, tint) = when (m.kind) {
                MissionKind.FLAWLESS_LESSON -> "✓" to R.color.primary
                MissionKind.EARN_XP -> "⚡" to R.color.tertiary
                MissionKind.DUEL -> "⚔" to R.color.on_surface_muted
                MissionKind.OTHER -> "★" to R.color.secondary
            }
            item.findViewById<TextView>(R.id.tvMissionIcon).apply {
                text = glyph
                setTextColor(color(tint))
                background = rounded(ColorUtils.setAlphaComponent(color(tint), 40), 14)
            }
            setText(item, R.id.tvMissionTitle, m.title)
            setText(
                item, R.id.tvMissionSub,
                when (m.kind) {
                    MissionKind.FLAWLESS_LESSON -> getString(R.string.learn_mission_flawless_sub, m.progress, m.target)
                    MissionKind.EARN_XP -> getString(R.string.learn_mission_xp_sub, m.progress, m.target)
                    MissionKind.DUEL -> getString(R.string.learn_mission_duel_sub, m.progress, m.target)
                    MissionKind.OTHER -> getString(R.string.learn_mission_generic_sub, m.progress, m.target)
                }
            )

            val bar = item.findViewById<ProgressBar>(R.id.progressMission)
            val button = item.findViewById<TextView>(R.id.btnMissionAction)
            val reward = item.findViewById<TextView>(R.id.tvMissionReward)
            bar.visibility = View.GONE
            button.visibility = View.GONE
            reward.visibility = View.GONE
            button.isEnabled = true
            button.alpha = 1f

            when (state) {
                MissionState.CLAIMABLE -> {
                    button.visibility = View.VISIBLE
                    button.text = getString(R.string.learn_reward_btn, m.rewardXp)
                    button.setTextColor(color(R.color.surface))
                    button.background = rounded(color(R.color.amber_dark), 14)
                    // TODO: POST /api/missions/{id}/claim
                    button.setOnClickListener {
                        claimed += m.id
                        toast(R.string.learn_reward_claimed)
                        bindMissions(view, s)
                    }
                }
                MissionState.CLAIMED -> {
                    button.visibility = View.VISIBLE
                    button.text = getString(R.string.learn_claimed)
                    button.setTextColor(color(R.color.on_surface_muted))
                    button.background = rounded(color(R.color.quiz_surface_alt), 14)
                    button.isEnabled = false
                    button.alpha = 0.7f
                }
                MissionState.PROGRESS -> {
                    bar.visibility = View.VISIBLE
                    bar.progress = m.percent
                    bar.progressTintList = android.content.res.ColorStateList.valueOf(color(tint))
                    reward.visibility = if (m.rewardXp > 0) View.VISIBLE else View.GONE
                    reward.text = getString(R.string.learn_reward_text, m.rewardXp)
                    reward.setTextColor(color(tint))
                }
                MissionState.ACTION -> {
                    button.visibility = View.VISIBLE
                    button.text = getString(R.string.learn_challenge_btn)
                    button.setTextColor(color(R.color.on_surface))
                    button.background = rounded(color(R.color.quiz_surface_alt), 14)
                    button.setOnClickListener { toast(R.string.profile_pending) }   // TODO: duels
                }
            }
            container.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
    }

    // class activity

    private fun bindActivity(view: View, s: LearnScreen) {
        val card = view.findViewById<View>(R.id.activityCard)
        val a = s.activity
        if (a == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        card.background = rounded(color(R.color.quiz_surface_alt), 16)
        card.setOnClickListener { openLeaderboard() }

        setText(view, R.id.tvActivityMessage, a.message)
        setText(view, R.id.tvActivityDetail, a.detail)

        val avatars = view.findViewById<LinearLayout>(R.id.activityAvatars)
        avatars.removeAllViews()
        val tints = listOf(R.color.primary_dark, R.color.tertiary, R.color.amber_dark)
        a.initials.forEachIndexed { i, initials ->
            avatars.addView(TextView(requireContext()).apply {
                text = initials
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(color(R.color.surface))
                background = oval(color(tints[i % tints.size]))
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { if (i > 0) marginStart = -dp(8) })
        }
    }

    // navigation

    private fun startLesson(subjectId: String, subjectTitle: String, grade: Int?, lessonId: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, QuizFragment.newInstance(subjectId, subjectTitle, grade, lessonId))
            .addToBackStack(null)
            .commit()
    }

    private fun setupNavigation(view: View) {
    view.findViewById<View>(R.id.navLearn)?.setOnClickListener {
    }

    view.findViewById<View>(R.id.navSubjects)?.setOnClickListener {
        replaceWith(SubjectsFragment())
    }

    view.findViewById<View>(R.id.navPractice)?.setOnClickListener {
        replaceWith(PracticeFragment())
    }

    view.findViewById<View>(R.id.navLeaderboard)?.setOnClickListener {
        openLeaderboard()
    }

    view.findViewById<View>(R.id.navProfile)?.setOnClickListener {
        openProfile()
    }
}

    private fun openProfile() = replaceWith(ProfileFragment())

    private fun openLeaderboard() = replaceWith(LeaderboardFragment())

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

    private fun setText(view: View, id: Int, text: CharSequence) {
        view.findViewById<TextView>(id).text = text
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private companion object {
        const val NODE_WIDTH_DP = 150
        val NODE_POSITIONS = floatArrayOf(0.20f, 0.65f, 0.25f, 0.70f, 0.40f)
    }
}