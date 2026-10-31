package com.example.superznalci

import android.graphics.drawable.GradientDrawable
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
import java.text.NumberFormat
import java.util.Locale

class LeaderboardFragment : Fragment(R.layout.fragment_leaderboard) {

    private val numberFormat: NumberFormat by lazy {
        NumberFormat.getIntegerInstance(Locale.Builder().setLanguage("sl").setRegion("SI").build())
    }

    private var errorDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.main)
        root.applySystemBarsPadding()
        BottomNavHelper.setSelected(view, R.id.navLeaderboard)

        if (SessionStore.user == null) {
            goToLogin()
            return
        }

        setupNavigation(view)
        root.visibility = View.INVISIBLE   // shown once the data has arrived
        load(root)
    }

    private fun load(root: View) {
        ServiceLocator.leaderboard.loadLeaderboard { result ->
            if (view == null) return@loadLeaderboard

            when (result) {
                is ApiResult.Success -> {
                    val s = LeaderboardMapper.toScreen(result.data)
                    bindTop(root, s)
                    bindResult(root, s)
                    bindBoard(root, s)
                    bindMe(root, s)
                    bindCheer(root, s)
                    bindBadges(root, s)
                    bindButtons(root, s)
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

    // binding

    private fun bindTop(view: View, s: LeaderboardScreen) {
        setText(view, R.id.tvTopStreak, getString(R.string.profile_top_streak, s.streakDays))
        setText(view, R.id.tvTopStars, getString(R.string.profile_top_stars, s.stars))
    }

    private fun bindResult(view: View, s: LeaderboardScreen) {
        val card = view.findViewById<View>(R.id.resultCard)
        val r = s.result
        if (r == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        setText(view, R.id.tvResultChip, r.chip)
        setText(view, R.id.tvResultSubtitle, getString(R.string.lb_result_subtitle, r.lessonName))
        setText(view, R.id.tvStatXp, getString(R.string.lb_stat_xp, r.xp))
        setText(view, R.id.tvStatAccuracy, getString(R.string.lb_stat_accuracy, r.accuracy))
        setText(view, R.id.tvStatTime, r.timeLabel)

        val tile = rounded(color(R.color.quiz_surface_alt), 16)
        listOf(R.id.tileXp, R.id.tileAccuracy, R.id.tileTime).forEach {
            view.findViewById<View>(it).background = rounded(color(R.color.quiz_surface_alt), 16)
        }
        view.findViewById<View>(R.id.privacyNote).background = tile
    }

    private fun bindBoard(view: View, s: LeaderboardScreen) {
        setText(view, R.id.tvBoardClass, getString(R.string.lb_board_class, s.className))

        val row = view.findViewById<LinearLayout>(R.id.podiumRow)
        row.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        // visual order: 2nd - 1st - 3rd
        listOf(2, 1, 3).forEach { rank ->
            val item = s.podium.getOrNull(rank - 1)
            if (item == null) {
                row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 0, 1f))
            } else {
                row.addView(inflatePodium(inflater, row, item))
            }
        }
    }

    private fun inflatePodium(inflater: LayoutInflater, parent: LinearLayout, p: PodiumItem): View {
        val item = inflater.inflate(R.layout.item_podium, parent, false)
        val first = p.rank == 1

        val ringColor = when (p.rank) {
            1 -> color(R.color.secondary)
            2 -> color(R.color.on_surface_hint)
            else -> color(R.color.amber_dark)
        }

        item.findViewById<View>(R.id.tvCrown).visibility = if (first) View.VISIBLE else View.INVISIBLE

        item.findViewById<TextView>(R.id.tvPodiumAvatar).apply {
            text = p.initials
            val size = dp(if (first) 76 else 60)
            layoutParams = layoutParams.apply { width = size; height = size }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(ringColor, 40))
                setStroke(dp(3), ringColor)
            }
            setTextColor(color(R.color.on_surface))
        }

        item.findViewById<TextView>(R.id.tvPodiumRank).apply {
            text = p.rank.toString()
            setTextColor(color(R.color.surface))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ringColor)
            }
        }

        setText(item, R.id.tvPodiumName, p.name)
        item.findViewById<TextView>(R.id.tvPodiumXp).apply {
            text = getString(R.string.lb_xp, xp(p.xp))
            setTextColor(color(R.color.amber_dark))
        }

        item.findViewById<TextView>(R.id.tvPodiumBlock).apply {
            text = if (first) getString(R.string.lb_place_first) else getString(R.string.lb_place_n, p.rank)
            layoutParams = layoutParams.apply {
                height = dp(
                    when (p.rank) {
                        1 -> 128
                        2 -> 96
                        else -> 80
                    }
                )
            }
            val fill = if (first) ColorUtils.setAlphaComponent(color(R.color.secondary), 70)
            else color(R.color.quiz_surface_alt)
            background = rounded(fill, 12, topOnly = true)
            setTextColor(if (first) color(R.color.amber_dark) else color(R.color.on_surface_muted))
        }
        return item
    }

    private fun bindMe(view: View, s: LeaderboardScreen) {
        val card = view.findViewById<View>(R.id.meCard)
        val me = s.me
        if (me == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        card.background = rounded(ColorUtils.setAlphaComponent(color(R.color.primary), 35), 16)

        view.findViewById<TextView>(R.id.tvMeRank).apply {
            text = me.rank.toString()
            setTextColor(color(R.color.surface))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(R.color.primary_dark))
            }
        }
        setText(view, R.id.tvMeName, me.name)
        view.findViewById<TextView>(R.id.tvMeTag).background = rounded(color(R.color.primary), 6)
        setText(
            view, R.id.tvMeSub,
            if (me.xpToNext != null && me.nextRank != null)
                getString(R.string.lb_me_gap, me.xpToNext, me.nextRank)
            else
                getString(R.string.lb_me_top)
        )
        setText(view, R.id.tvMeXp, getString(R.string.lb_xp, xp(me.xp)))
        view.findViewById<TextView>(R.id.tvMeDelta).apply {
            visibility = if (me.gainedXp > 0) View.VISIBLE else View.GONE
            text = getString(R.string.lb_me_delta, me.gainedXp)
        }
        view.findViewById<ProgressBar>(R.id.progressMe).progress = me.progress
    }

    private fun bindCheer(view: View, s: LeaderboardScreen) {
        val card = view.findViewById<View>(R.id.cheerCard)
        val cheer = s.cheer
        if (cheer == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        card.background = rounded(ColorUtils.setAlphaComponent(color(R.color.tertiary), 25), 16)

        setText(view, R.id.tvCheerTitle, getString(R.string.lb_cheer_title, cheer.name, cheer.rank))
        setText(view, R.id.tvCheerSub, getString(R.string.lb_cheer_sub, cheer.rank))

        view.findViewById<TextView>(R.id.btnCheer).apply {
            isEnabled = true
            alpha = 1f
            background = rounded(color(R.color.surface), 12)
            setText(R.string.lb_cheer_btn)
            // TODO: POST /api/cheers { toUserId = cheer.userId } when the backend has it
            setOnClickListener {
                isEnabled = false
                alpha = 0.5f
                setText(R.string.lb_cheer_sent)
                toast(R.string.lb_cheer_sent)
            }
        }
    }

    private fun bindBadges(view: View, s: LeaderboardScreen) {
        val title = view.findViewById<View>(R.id.tvBadgesTitle)
        val row = view.findViewById<LinearLayout>(R.id.badgesRow)
        row.removeAllViews()

        if (s.badges.isEmpty()) {
            title.visibility = View.GONE
            row.visibility = View.GONE
            return
        }
        title.visibility = View.VISIBLE
        row.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(requireContext())
        s.badges.take(3).forEachIndexed { index, badge ->
            val item = inflater.inflate(R.layout.item_lb_badge, row, false)
            item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (index > 0) marginStart = dp(10)
            }
            item.findViewById<TextView>(R.id.tvLbBadgeTitle).text = badge.title
            item.findViewById<TextView>(R.id.tvLbBadgeDesc).text = badge.description

            val circle = item.findViewById<View>(R.id.lbBadgeCircle)
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(color(badge.colorRes), 50))
            }
            val glyph = item.findViewById<TextView>(R.id.tvLbBadgeGlyph)
            val icon = item.findViewById<ImageView>(R.id.ivLbBadgeIcon)
            if (badge.glyph != null) {
                glyph.text = badge.glyph
                glyph.visibility = View.VISIBLE
            } else if (badge.iconRes != null) {
                icon.setImageResource(badge.iconRes)
                icon.imageTintList = android.content.res.ColorStateList.valueOf(color(badge.colorRes))
                icon.visibility = View.VISIBLE
            }
            row.addView(item)
        }
    }

    private fun bindButtons(view: View, s: LeaderboardScreen) {
        view.findViewById<TextView>(R.id.btnContinue).apply {
            background = rounded(color(R.color.primary), 16)
            // TODO: open the next lesson of the subject
            setOnClickListener { openSubjects() }
        }

        val wrong = s.result?.wrongCount ?: 0
        view.findViewById<TextView>(R.id.btnReviewWrong).apply {
            if (s.result == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = getString(R.string.lb_btn_review, wrong)
                background = rounded(color(R.color.quiz_surface_alt), 16)
                // TODO: screen with the wrong answers of the last attempt
                setOnClickListener { toast(R.string.profile_pending) }
            }
        }
    }

    // navigation

    private fun setupNavigation(view: View) {
        view.findViewById<View>(R.id.navSubjects)?.setOnClickListener { openSubjects() }
        view.findViewById<View>(R.id.navPractice)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PracticeFragment())
                .commit()
        }
        view.findViewById<View>(R.id.navProfile)?.setOnClickListener { openProfile() }
        view.findViewById<View>(R.id.navLearn)?.setOnClickListener { toast(R.string.profile_pending) }
        view.findViewById<View>(R.id.navLearn)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LearnFragment())
                .commit()
        }
    }

    private fun openSubjects() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SubjectsFragment())
            .commit()
    }

    private fun openProfile() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProfileFragment())
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

    private fun xp(value: Int): String = numberFormat.format(value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radiusDp: Int, topOnly: Boolean = false) = GradientDrawable().apply {
        setColor(fill)
        val r = dp(radiusDp).toFloat()
        cornerRadii = if (topOnly) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else FloatArray(8) { r }
    }
}