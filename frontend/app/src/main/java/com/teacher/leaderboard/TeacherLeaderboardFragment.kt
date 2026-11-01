package com.example.superznalci

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TeacherLeaderboardFragment : Fragment(R.layout.fragment_teacher_leaderboard) {

    private enum class Filter { ALL, TOP, HELP }

    private val repo: TeacherLeaderboardRepository get() = ServiceLocator.teacherLeaderboard
    private val classesRepo: TeacherSubjectsRepository get() = ServiceLocator.teacherSubjects

    private var tabs: List<TeacherClassTab> = emptyList()
    private var classIndex = 0
    private var subjectId: String? = null
    private var filter = Filter.ALL
    private var query = ""
    private var selectedId: String? = null

    private val subjectsCache = HashMap<String, List<TlSubjectUi>>()
    private val boards = HashMap<String, TlBoard>()
    private val details = HashMap<String, TeacherStudentDetailDto>()

    private lateinit var progress: ProgressBar
    private lateinit var scroll: ScrollView
    private lateinit var content: View

    private val selectedTab: TeacherClassTab? get() = tabs.getOrNull(classIndex)
    private fun boardKey(tab: TeacherClassTab) = "${tab.classroom.id}|${subjectId.orEmpty()}"
    private val board: TlBoard? get() = selectedTab?.let { boards[boardKey(it)] }
    private val selectedStudent: TlStudentUi? get() = board?.students?.firstOrNull { it.id == selectedId }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        progress = view.findViewById(R.id.progress)
        scroll = view.findViewById(R.id.scroll)
        content = view.findViewById(R.id.content)

        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        listOf(
            R.id.btnAllBadges, R.id.btnAssignExtra, R.id.btnReport, R.id.navLearn
        ).forEach { view.findViewById<View>(it).setOnClickListener(comingSoon) }

        TeacherNav.bind(view, R.id.navLeaderboard, this)

        view.findViewById<View>(R.id.btnPraise).setOnClickListener { askPraise() }
        view.findViewById<View>(R.id.btnRefreshBoard).setOnClickListener { refresh() }

        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnClear = view.findViewById<View>(R.id.btnClearSearch)
        btnClear.setOnClickListener { etSearch.text.clear() }
        etSearch.doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            if (board != null) {
                renderFilters()
                renderBoard()
            }
        }

        SessionStore.user?.let {
            view.findViewById<TextView>(R.id.tvStreak).text = "🔥 ${it.streak}"
            view.findViewById<TextView>(R.id.tvXp).text = "⭐ ${compact(it.totalXp)}"
            view.findViewById<TextView>(R.id.tvAvatar).text = initialsOf(it.name)
        }
        view.findViewById<TextView>(R.id.tvSchoolYear).text = getString(R.string.tl_school_year, schoolYear())

        content.visibility = View.INVISIBLE
        loadClasses()
    }

    // loading

    private fun loadClasses() {
        progress.visibility = View.VISIBLE
        classesRepo.loadClasses { result ->
            if (view == null) return@loadClasses
            when (result) {
                is ApiResult.Success -> {
                    tabs = result.data
                    if (tabs.isEmpty()) {
                        progress.visibility = View.GONE
                        content.visibility = View.VISIBLE
                        view?.findViewById<TextView>(R.id.tvBoardEmpty)?.apply {
                            setText(R.string.tl_empty_classes)
                            visibility = View.VISIBLE
                        }
                    } else {
                        selectClass(0)
                    }
                }
                is ApiResult.Failure -> onFailure(result.httpCode)
            }
        }
    }

    private fun selectClass(index: Int) {
        classIndex = index
        subjectId = null
        filter = Filter.ALL
        selectedId = null
        renderClassTabs()
        renderHeader()
        val tab = selectedTab ?: return
        loadSubjects(tab)
        loadBoard(tab)
    }

    private fun loadSubjects(tab: TeacherClassTab) {
        if (subjectsCache.containsKey(tab.classroom.id)) {
            renderSubjectChips()
            return
        }
        renderSubjectChips()
        repo.loadSubjects(tab.classroom) { result ->
            if (view == null) return@loadSubjects
            when (result) {
                is ApiResult.Success -> {
                    subjectsCache[tab.classroom.id] = result.data
                    if (selectedTab?.classroom?.id == tab.classroom.id) renderSubjectChips()
                }
                is ApiResult.Failure -> if (result.httpCode == 401) logout()
            }
        }
    }

    private fun loadBoard(tab: TeacherClassTab) {
        val key = boardKey(tab)
        if (boards.containsKey(key)) {
            renderAll()
            return
        }
        val requestedSubject = subjectId
        progress.visibility = View.VISIBLE
        repo.loadBoard(tab.classroom, requestedSubject) { result ->
            if (view == null) return@loadBoard
            progress.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    boards[key] = result.data
                    if (selectedTab?.classroom?.id == tab.classroom.id && subjectId == requestedSubject) renderAll()
                }
                is ApiResult.Failure -> onFailure(result.httpCode)
            }
        }
    }

    private fun loadDetail(studentId: String) {
        val tab = selectedTab ?: return
        if (details.containsKey(studentId)) {
            renderDetail()
            return
        }
        renderDetail()
        repo.loadDetail(tab.classroom.id, studentId) { result ->
            if (view == null) return@loadDetail
            when (result) {
                is ApiResult.Success -> {
                    details[studentId] = result.data
                    if (selectedId == studentId) renderDetail()
                }
                is ApiResult.Failure -> if (result.httpCode == 401) logout()
            }
        }
    }

    private fun refresh() {
        val tab = selectedTab ?: return
        boards.remove(boardKey(tab))
        details.clear()
        loadBoard(tab)
    }

    private fun onFailure(code: Int?) {
        progress.visibility = View.GONE
        when (code) {
            401 -> logout()
            null -> toast(R.string.err_network)
            else -> toast(R.string.err_server)
        }
    }

    // rendering

    private fun renderAll() {
        val b = board ?: return
        if (selectedId == null || b.students.none { it.id == selectedId }) {
            selectedId = b.students.firstOrNull()?.id
        }
        renderFilters()
        renderSelected()
        renderBoard()
        selectedId?.let { loadDetail(it) }
    }

    private fun renderHeader() {
        val root = view ?: return
        val tab = selectedTab ?: return
        content.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.tvBrand).text = getString(R.string.tl_brand, tab.classroom.gradeNumber)
    }

    private fun renderClassTabs() {
        val root = view ?: return
        val row = root.findViewById<LinearLayout>(R.id.classTabs)
        row.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            val selected = i == classIndex
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundResource(R.drawable.bg_tc_card)
                backgroundTintList = tint(if (selected) android.R.color.white else R.color.t_chip_bg)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                setOnClickListener { if (i != classIndex) selectClass(i) }
            }
            box.addView(TextView(requireContext()).apply {
                text = getString(R.string.tl_class_tab, prettyClass(tab))
                textSize = 15f
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(color(if (selected) R.color.t_text else R.color.ts_muted))
            })
            box.addView(TextView(requireContext()).apply {
                text = getString(R.string.tc_students_count, tab.studentCount)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_t_pill)
                backgroundTintList = tint(if (selected) R.color.t_green_soft else R.color.t_chip_bg)
                setTextColor(color(if (selected) R.color.t_green else R.color.ts_muted))
                setPadding(dp(10), dp(3), dp(10), dp(3))
            })
            row.addView(box, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    private fun renderSubjectChips() {
        val root = view ?: return
        val tab = selectedTab ?: return
        val row = root.findViewById<LinearLayout>(R.id.subjectChips)
        row.removeAllViews()

        val items = listOf<Pair<String?, String>>(null to getString(R.string.tl_all_subjects)) +
            subjectsCache[tab.classroom.id].orEmpty().map { it.id to it.name }

        items.forEach { (id, name) ->
            val selected = id == subjectId
            row.addView(chip(name, selected) {
                if (id != subjectId) {
                    subjectId = id
                    selectedId = null
                    renderSubjectChips()
                    loadBoard(tab)
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    private fun renderFilters() {
        val root = view ?: return
        val b = board ?: return
        val row = root.findViewById<LinearLayout>(R.id.filterChips)
        row.removeAllViews()

        val helpCount = b.students.count { it.needsHelp }
        listOf(
            Filter.ALL to getString(R.string.tl_filter_all, b.students.size),
            Filter.TOP to getString(R.string.tl_filter_top),
            Filter.HELP to getString(R.string.tl_filter_help, helpCount)
        ).forEach { (f, label) ->
            row.addView(chip(label, f == filter) {
                filter = f
                renderFilters()
                renderBoard()
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundResource(R.drawable.bg_t_pill)
        backgroundTintList = tint(if (selected) R.color.t_green else android.R.color.white)
        setTextColor(color(if (selected) android.R.color.white else R.color.t_text))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
    }

    private fun renderSelected() {
        val root = view ?: return
        val card = root.findViewById<View>(R.id.selectedCard)
        val s = selectedStudent
        if (s == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val tab = selectedTab ?: return

        root.findViewById<TextView>(R.id.tvSelAvatar).text = s.initials
        root.findViewById<TextView>(R.id.tvSelName).text = s.name
        root.findViewById<TextView>(R.id.tvSelClass).text = prettyClass(tab)
        root.findViewById<TextView>(R.id.tvSelRank).text = getString(R.string.tl_rank_place, s.rank)
        root.findViewById<TextView>(R.id.tvSelSub).text =
            if (s.title.isNullOrBlank()) getString(R.string.tl_level, s.level)
            else getString(R.string.tl_level_title, s.level, s.title)
        root.findViewById<TextView>(R.id.tvSelXp).text = String.format(Locale.US, "%,d", s.xp).replace(',', '.')
        root.findViewById<TextView>(R.id.tvSelAcc).text =
            s.accuracy?.let { getString(R.string.tl_pct, it) } ?: "–"
        root.findViewById<TextView>(R.id.tvSelStreak).text = getString(R.string.tl_streak_days, s.streak)
    }

    private fun renderDetail() {
        val root = view ?: return
        val badges = root.findViewById<LinearLayout>(R.id.badgesContainer)
        val quizzes = root.findViewById<LinearLayout>(R.id.quizzesContainer)
        badges.removeAllViews()
        quizzes.removeAllViews()

        val d = selectedId?.let { details[it] }
        root.findViewById<TextView>(R.id.tvAssigned).text =
            if (d == null) "–" else getString(R.string.tl_assigned_value, d.assignedDone, d.assignedTotal)
        root.findViewById<TextView>(R.id.tvBadgesTitle).text =
            getString(R.string.tl_badges_title, d?.badgeTotal ?: 0)
        if (d == null) return

        val icons = listOf("🏅", "🏆", "🔥")
        val tones = listOf(R.color.t_mint, R.color.t_indigo_soft, R.color.t_orange_soft)
        d.badges.orEmpty().take(3).forEachIndexed { i, b ->
            val v = layoutInflater.inflate(R.layout.item_tl_badge, badges, false)
            v.findViewById<TextView>(R.id.tlBadgeIcon).apply {
                text = icons[i % icons.size]
                backgroundTintList = tint(tones[i % tones.size])
            }
            v.findViewById<TextView>(R.id.tlBadgeName).text = b.name
            v.findViewById<TextView>(R.id.tlBadgeSub).text = b.subtitle.orEmpty()
            badges.addView(v)
        }

        d.recentQuizzes.orEmpty().forEach { q ->
            val v = layoutInflater.inflate(R.layout.item_tl_quiz, quizzes, false)
            val perfect = q.mistakeCount == 0
            v.findViewById<TextView>(R.id.tlQIcon).apply {
                text = if (perfect) "Σ" else "△"
                backgroundTintList = tint(if (perfect) R.color.t_mint else R.color.t_orange_soft)
                setTextColor(color(if (perfect) R.color.t_green else R.color.t_orange_text))
            }
            v.findViewById<TextView>(R.id.tlQTitle).text = q.title
            v.findViewById<TextView>(R.id.tlQDetail).apply {
                if (perfect) {
                    text = getString(R.string.tl_quiz_perfect, q.xpGained)
                    setTextColor(color(R.color.t_green))
                } else {
                    val base = resources.getQuantityString(R.plurals.tl_quiz_mistakes, q.mistakeCount, q.mistakeCount)
                    text = if (q.mistakeNote.isNullOrBlank()) base else "$base (${q.mistakeNote})"
                    setTextColor(color(R.color.tc_red))
                }
            }
            v.findViewById<TextView>(R.id.tlQScore).text = getString(R.string.tl_quiz_score, q.score, q.total, q.accuracy)
            v.findViewById<TextView>(R.id.tlQWhen).text = whenLabel(q.finishedAt)
            quizzes.addView(v)
        }
    }

    private fun visibleStudents(): List<TlStudentUi> {
        val b = board ?: return emptyList()
        return b.students
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.TOP -> it.rank <= 3
                    Filter.HELP -> it.needsHelp
                }
            }
    }

    private fun renderBoard() {
        val root = view ?: return
        val b = board ?: return
        val tab = selectedTab ?: return
        val container = root.findViewById<LinearLayout>(R.id.boardContainer)
        val empty = root.findViewById<TextView>(R.id.tvBoardEmpty)
        container.removeAllViews()

        root.findViewById<TextView>(R.id.tvBoardTitle).text = getString(R.string.tl_board_title, prettyClass(tab))
        val minutes = ((System.currentTimeMillis() - b.loadedAt) / 60_000).toInt()
        root.findViewById<TextView>(R.id.tvUpdated).text =
            if (minutes <= 0) getString(R.string.tl_updated_now) else getString(R.string.tl_updated_min, minutes)

        val rows = visibleStudents()
        empty.setText(R.string.tl_empty_board)
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        val avatarTones = listOf(R.color.t_mint, R.color.t_indigo_soft, R.color.t_orange_soft)
        rows.forEach { s ->
            val row = layoutInflater.inflate(R.layout.item_tl_row, container, false)
            val selected = s.id == selectedId

            row.backgroundTintList = tint(
                when {
                    selected -> R.color.t_mint
                    s.needsHelp -> R.color.tc_red_soft
                    else -> android.R.color.white
                }
            )

            row.findViewById<TextView>(R.id.tlRank).apply {
                text = when (s.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${s.rank}." }
                backgroundTintList = tint(
                    when {
                        s.rank <= 3 -> R.color.ts_amber_bg
                        s.needsHelp -> R.color.tc_red_soft
                        else -> R.color.t_chip_bg
                    }
                )
                setTextColor(color(if (s.needsHelp && s.rank > 3) R.color.tc_red else R.color.ts_muted))
            }
            row.findViewById<TextView>(R.id.tlAvatar).apply {
                text = s.initials
                backgroundTintList = tint(avatarTones[s.rank % avatarTones.size])
            }
            row.findViewById<TextView>(R.id.tlName).text = s.name
            row.findViewById<View>(R.id.tlSelectedTag).visibility = if (selected) View.VISIBLE else View.GONE
            row.findViewById<TextView>(R.id.tlMeta).apply {
                text = if (s.needsHelp && s.helpTopic != null)
                    getString(R.string.tl_row_help_meta, s.accuracy ?: 0, s.helpTopic)
                else
                    s.accuracy?.let { getString(R.string.tl_row_meta, it, s.tasksDone) }
                        ?: getString(R.string.tl_row_no_data)
                setTextColor(color(if (s.needsHelp) R.color.tc_red else R.color.ts_muted))
            }
            row.findViewById<TextView>(R.id.tlXp).text =
                getString(R.string.tl_xp, String.format(Locale.US, "%,d", s.xp).replace(',', '.'))
            row.findViewById<TextView>(R.id.tlRankLabel).apply {
                text = when {
                    s.needsHelp -> getString(R.string.tl_needs_support)
                    s.rank <= 3 -> getString(R.string.tl_rank_place, s.rank)
                    else -> getString(R.string.tl_level, s.level)
                }
                setTextColor(color(if (s.needsHelp) R.color.tc_red else R.color.ts_muted))
            }
            row.findViewById<View>(R.id.tlHelp).apply {
                visibility = if (s.needsHelp) View.VISIBLE else View.GONE
                setOnClickListener { toast(R.string.teacher_coming_soon) }
            }
            row.findViewById<TextView>(R.id.tlAction).apply {
                visibility = if (s.needsHelp) View.GONE else View.VISIBLE
                text = if (selected) "👁" else "›"
            }
            row.setOnClickListener { selectStudent(s.id) }
            container.addView(row)
        }

        root.findViewById<TextView>(R.id.tvAvgTitle).text = getString(R.string.tl_avg_title, prettyClass(tab))
        root.findViewById<TextView>(R.id.tvAvgDesc).text = getString(
            R.string.tl_avg_desc,
            String.format(Locale.US, "%,d", b.averageXp).replace(',', '.'),
            b.averageAccuracy
        )
    }

    private fun selectStudent(id: String) {
        if (id != selectedId) {
            selectedId = id
            renderSelected()
            renderBoard()
            loadDetail(id)
        }
        view?.findViewById<View>(R.id.selectedCard)?.let { scroll.smoothScrollTo(0, it.top) }
    }

    // actions

    private fun askPraise() {
        val s = selectedStudent ?: return
        val tab = selectedTab ?: return
        val input = EditText(requireContext()).apply {
            setText(getString(R.string.tl_praise_default, s.name.substringBefore(' ')))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.tl_praise_title, s.name))
            .setView(input)
            .setPositiveButton(R.string.tl_praise_send) { _, _ ->
                val msg = input.text.toString().trim()
                if (msg.isNotEmpty()) sendPraise(tab.classroom.id, s.id, msg)
            }
            .setNegativeButton(R.string.tc_cancel, null)
            .show()
    }

    private fun sendPraise(classId: String, studentId: String, message: String) {
        progress.visibility = View.VISIBLE
        repo.sendPraise(classId, studentId, message) { result ->
            if (view == null) return@sendPraise
            progress.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> toast(R.string.tl_praise_sent)
                is ApiResult.Failure -> if (result.httpCode == 401) logout() else toast(R.string.tl_praise_failed)
            }
        }
    }

    private fun logout() {
        SessionStore.clear()
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    // helpers

    private fun whenLabel(iso: String?): String {
        val millis = IsoDate.parse(iso) ?: return ""
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun same(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            same(then, now) -> getString(R.string.tl_today, SimpleDateFormat("HH:mm", Locale.US).format(Date(millis)))
            same(then, yesterday) -> getString(R.string.tl_yesterday)
            else -> SimpleDateFormat("d. MMM", Locale.Builder().setLanguage("sl").setRegion("SI").build())
                .format(Date(millis))
        }
    }

    private fun schoolYear(): String {
        val now = Calendar.getInstance()
        val start = if (now.get(Calendar.MONTH) >= Calendar.SEPTEMBER) now.get(Calendar.YEAR)
        else now.get(Calendar.YEAR) - 1
        return "$start/${(start + 1) % 100}"
    }

    private fun prettyClass(tab: TeacherClassTab): String =
        ClassroomNaming.parse(tab.classroom.name).first.replace(Regex("^(\\d+)\\.?\\s*"), "$1. ")

    private fun initialsOf(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }

    private fun compact(xp: Int): String =
        if (xp >= 1000) String.format(Locale.US, "%.1fk", xp / 1000.0) else xp.toString()

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun tint(res: Int) = ColorStateList.valueOf(color(res))
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}