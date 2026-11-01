package com.example.superznalci

import androidx.appcompat.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max

class TeacherLearnFragment : Fragment(R.layout.fragment_teacher_learn) {

    private val repo: TeacherLearnRepository get() = ServiceLocator.teacherLearn

    private class Deadline(val labelRes: Int, val days: Int)

    private val deadlines = listOf(
        Deadline(R.string.tl_deadline_tomorrow, 1),
        Deadline(R.string.tl_deadline_3_days, 3),
        Deadline(R.string.tl_deadline_week, 7)
    )

    private var tabs: List<TeacherClassTab> = emptyList()
    private var classIndex = 0
    private val cache = HashMap<String, TeacherLearnData>()
    private var homeworkLessons: List<HomeworkLessonUi> = emptyList()

    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var content: View
    private lateinit var scroll: ScrollView
    private lateinit var spinnerLesson: Spinner
    private lateinit var spinnerDeadline: Spinner
    private lateinit var cbRetry: CheckBox
    private lateinit var btnSend: TextView

    private val selectedTab: TeacherClassTab? get() = tabs.getOrNull(classIndex)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        content = view.findViewById(R.id.content)
        scroll = view.findViewById(R.id.scroll)
        spinnerLesson = view.findViewById(R.id.spinnerLesson)
        spinnerDeadline = view.findViewById(R.id.spinnerDeadline)
        cbRetry = view.findViewById(R.id.cbRetry)
        btnSend = view.findViewById(R.id.btnSendHomework)

        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        listOf(R.id.btnAddClass, R.id.btnPathFilter).forEach {
            view.findViewById<View>(it).setOnClickListener(comingSoon)
        }

        SessionStore.user?.let {
            view.findViewById<TextView>(R.id.tvStreak).text = "🔥 ${it.streak}"
            view.findViewById<TextView>(R.id.tvXp).text = "⭐ ${compact(it.totalXp)}"
        }

        spinnerDeadline.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            deadlines.map { getString(it.labelRes) }
        )
        btnSend.setOnClickListener { sendHomework() }

        TeacherNav.bind(view, R.id.navLearn, this)

        content.visibility = View.INVISIBLE
        loadClasses()
    }

    // loading

    private fun loadClasses() {
        progress.visibility = View.VISIBLE
        repo.loadClasses { result ->
            if (view == null) return@loadClasses
            when (result) {
                is ApiResult.Success -> {
                    tabs = result.data
                    if (tabs.isEmpty()) {
                        progress.visibility = View.GONE
                        showEmpty()
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
        renderTabs()
        val tab = selectedTab ?: return
        if (cache.containsKey(tab.classroom.id)) {
            progress.visibility = View.GONE
            render()
        } else loadClass(tab)
    }

    private fun loadClass(tab: TeacherClassTab) {
        progress.visibility = View.VISIBLE
        repo.loadClass(tab) { result ->
            if (view == null) return@loadClass
            progress.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    cache[tab.classroom.id] = result.data
                    if (selectedTab?.classroom?.id == tab.classroom.id) render()
                }
                is ApiResult.Failure -> onFailure(result.httpCode)
            }
        }
    }

    private fun reloadCurrent() {
        val tab = selectedTab ?: return
        cache.remove(tab.classroom.id)
        loadClass(tab)
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

    private fun showEmpty() {
        content.visibility = View.VISIBLE
        tvEmpty.visibility = View.VISIBLE
        listOf(R.id.overviewCard, R.id.pathSection, R.id.homeworkCard, R.id.strugglersCard).forEach {
            view?.findViewById<View>(it)?.visibility = View.GONE
        }
    }

    private fun render() {
        val root = view ?: return
        val tab = selectedTab ?: return
        val data = cache[tab.classroom.id] ?: return

        content.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        root.findViewById<View>(R.id.overviewCard).visibility = View.VISIBLE
        root.findViewById<View>(R.id.pathSection).visibility = View.VISIBLE

        renderOverview(root, tab, data)
        renderPath(root, tab, data)
        renderHomework(root, tab, data)
        renderStruggling(root, tab, data)
    }

    private fun renderTabs() {
        val root = view ?: return
        val row = root.findViewById<LinearLayout>(R.id.classTabs)
        row.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            val selected = i == classIndex
            val label = gradeLabel(tab)
            row.addView(TextView(requireContext()).apply {
                text = "$label\n${resources.getQuantityString(R.plurals.tl_students, tab.studentCount, tab.studentCount)}"
                textSize = 14f
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setBackgroundResource(R.drawable.bg_t_pill)
                backgroundTintList = tint(if (selected) R.color.t_green else R.color.t_chip_bg)
                setTextColor(color(if (selected) android.R.color.white else R.color.t_text))
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { if (i != classIndex) selectClass(i) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    private fun renderOverview(root: View, tab: TeacherClassTab, d: TeacherLearnData) {
        val o = d.overview
        root.tv(R.id.tvOverviewTitle).text = getString(R.string.tl_overview_title, gradeLabel(tab))
        root.findViewById<View>(R.id.tvWeekBadge).visibility = if (o.weekActive) View.VISIBLE else View.GONE

        val current = d.modules.firstOrNull { it.state == PathState.OPEN }
        root.tv(R.id.tvTileModule).text = current?.name ?: "—"
        root.tv(R.id.tvTileModuleSub).text = current?.subjectName.orEmpty()

        val percent = if (o.totalStudents > 0) o.completedCount * 100 / o.totalStudents else 0
        root.tv(R.id.tvTileSubmit).text = getString(R.string.tl_percent, percent)
        root.tv(R.id.tvTileSubmitSub).text =
            getString(R.string.tl_submitted_of, o.completedCount, o.totalStudents)

        if (o.needHelpCount > 0) {
            root.tv(R.id.tvTileHelp).text =
                resources.getQuantityString(R.plurals.tl_students, o.needHelpCount, o.needHelpCount)
            root.tv(R.id.tvTileHelpSub).setText(R.string.tl_help_sub)
        } else {
            root.tv(R.id.tvTileHelp).setText(R.string.tl_help_none)
            root.tv(R.id.tvTileHelpSub).text = ""
        }

        root.tv(R.id.tvGoalCount).text = getString(R.string.tl_goal_count, o.completedCount, o.totalStudents)
        root.findViewById<ProgressBar>(R.id.progressGoal).apply {
            max = 100
            this.progress = percent
        }
    }

    private fun renderPath(root: View, tab: TeacherClassTab, d: TeacherLearnData) {
        val container = root.findViewById<LinearLayout>(R.id.modulesContainer)
        container.removeAllViews()
        d.modules.forEach { container.addView(buildModule(container, tab, it)) }
    }

    private fun buildModule(parent: LinearLayout, tab: TeacherClassTab, m: PathModuleUi): View {
        val v = layoutInflater.inflate(R.layout.item_tl_module, parent, false)
        val open = m.state == PathState.OPEN
        val locked = m.state == PathState.LOCKED
        val draft = m.state == PathState.DRAFT

        v.tv(R.id.tvModuleLabel).text = getString(R.string.ts_module_label, m.number)
        v.tv(R.id.tvModuleTitle).text = m.name

        v.findViewById<View>(R.id.accent).backgroundTintList =
            tint(if (open) R.color.t_green else R.color.tl_locked_accent)
        v.findViewById<View>(R.id.moduleCard).backgroundTintList =
            if (draft) tint(R.color.tl_draft_bg) else tint(R.color.tl_card)
        v.findViewById<View>(R.id.accent).visibility = if (draft) View.GONE else View.VISIBLE

        v.tv(R.id.tvModuleIcon).apply {
            text = when (m.state) { PathState.OPEN -> "Σ"; PathState.LOCKED -> "📐"; PathState.DRAFT -> "✏️" }
            backgroundTintList = tint(if (open) R.color.t_mint else R.color.t_chip_bg)
        }

        v.tv(R.id.tvModuleState).apply {
            text = getString(
                when (m.state) {
                    PathState.OPEN -> R.string.tl_state_open
                    PathState.LOCKED -> R.string.tl_state_locked
                    PathState.DRAFT -> R.string.tl_state_draft
                }
            )
            setTextColor(color(if (open) R.color.t_green else R.color.ts_muted))
        }
        v.findViewById<View>(R.id.tvModuleBadge).visibility = if (open) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.tvChevron).visibility = if (draft) View.VISIBLE else View.GONE

        val meta = v.tv(R.id.tvModuleMeta)
        val lessons = resources.getQuantityString(R.plurals.tl_lessons, m.lessonCount, m.lessonCount)
        when {
            open -> meta.text = m.averageAccuracy
                ?.let { getString(R.string.tl_meta_accuracy, lessons, it) } ?: lessons
            locked -> meta.text = m.unlockAtMillis
                ?.let { getString(R.string.tl_planned_for, slovenian("EEEE, d. MMM").format(Date(it))) }
                ?: lessons
        }
        meta.visibility = if (draft) View.GONE else View.VISIBLE

        val rowOpen = v.findViewById<View>(R.id.rowOpen)
        rowOpen.visibility = if (open) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.btnAssign).setOnClickListener { focusHomework(m) }
        v.findViewById<View>(R.id.btnEditQuestions).setOnClickListener { toast(R.string.teacher_coming_soon) }

        val rowLocked = v.findViewById<View>(R.id.rowLocked)
        rowLocked.visibility = if (locked) View.VISIBLE else View.GONE
        if (locked) {
            val days = m.unlockAtMillis
                ?.let { max(1, ceil((it - System.currentTimeMillis()) / LEARN_DAY_MS.toDouble()).toInt()) }
            v.tv(R.id.tvLockedInfo).text = days
                ?.let {
                    getString(
                        R.string.tl_auto_unlock,
                        resources.getQuantityString(R.plurals.tl_in_days, it, it)
                    )
                }.orEmpty()
            v.findViewById<View>(R.id.btnUnlock).setOnClickListener { unlock(tab, m) }
        }

        // Klik na kartico modula odpre njegove lekcije.
        // Izbrana lekcija usmeri na obrazec za domačo nalogo.
        v.findViewById<View>(R.id.moduleCard).setOnClickListener {
            openModule(m)
        }
        if (draft) v.setOnClickListener { openModule(m) }
        return v
    }

    private fun renderHomework(root: View, tab: TeacherClassTab, d: TeacherLearnData) {
        homeworkLessons = d.homeworkLessons
        val card = root.findViewById<View>(R.id.homeworkCard)
        card.visibility = if (homeworkLessons.isEmpty()) View.GONE else View.VISIBLE
        spinnerLesson.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            homeworkLessons.map { it.label }
        )
        btnSend.text = getString(R.string.tl_send_homework, gradeLabel(tab))
        btnSend.isEnabled = true
    }

    private fun renderStruggling(root: View, tab: TeacherClassTab, d: TeacherLearnData) {
        val card = root.findViewById<View>(R.id.strugglersCard)
        card.visibility = if (d.struggling.isEmpty()) View.GONE else View.VISIBLE
        root.tv(R.id.tvStrugglingCount).text =
            resources.getQuantityString(R.plurals.tl_urgent, d.struggling.size, d.struggling.size)

        val container = root.findViewById<LinearLayout>(R.id.strugglersContainer)
        container.removeAllViews()
        d.struggling.forEachIndexed { i, s -> container.addView(buildStruggler(container, tab, s, i)) }
    }

    private fun buildStruggler(parent: LinearLayout, tab: TeacherClassTab, s: StrugglingStudentDto, index: Int): View {
        val v = layoutInflater.inflate(R.layout.item_tl_struggler, parent, false)

        v.tv(R.id.tvAvatar).apply {
            text = initials(s.name)
            backgroundTintList = tint(if (index % 2 == 0) R.color.t_orange_soft else R.color.t_chip_bg)
        }
        v.tv(R.id.tvStudentName).text = s.name

        v.tv(R.id.tvStudentTag).apply {
            when (s.reason) {
                StruggleReason.MISTAKES -> {
                    text = resources.getQuantityString(R.plurals.tl_mistakes, s.mistakeCount, s.mistakeCount)
                    backgroundTintList = tint(R.color.tl_red_bg)
                    setTextColor(color(R.color.tl_red_text))
                }
                StruggleReason.NOT_STARTED -> {
                    setText(R.string.tl_tag_not_started)
                    backgroundTintList = tint(R.color.t_chip_bg)
                    setTextColor(color(R.color.ts_muted))
                }
                else -> {
                    setText(R.string.tl_tag_inactive)
                    backgroundTintList = tint(R.color.t_chip_bg)
                    setTextColor(color(R.color.ts_muted))
                }
            }
        }

        v.tv(R.id.tvStudentDetail).text =
            if (s.reason == StruggleReason.MISTAKES && !s.lessonName.isNullOrBlank()) s.lessonName
            else lastActiveText(s.lastActiveAt)

        val btn = v.tv(R.id.btnRemind)
        btn.setOnClickListener {
            btn.isEnabled = false
            repo.sendReminder(tab.classroom.id, s.studentId) { result ->
                if (view == null) return@sendReminder
                when (result) {
                    is ApiResult.Success -> {
                        btn.setText(R.string.tl_reminder_sent)
                        toast(R.string.tl_reminder_sent_toast)
                    }
                    is ApiResult.Failure -> {
                        btn.isEnabled = true
                        if (result.httpCode == 401) logout() else toast(R.string.err_server)
                    }
                }
            }
        }
        return v
    }

    // actions

    private fun openModule(module: PathModuleUi) {
        val choices = homeworkLessons.filter { it.moduleTopicId == module.topicId }
        if (choices.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(module.name)
                .setMessage("Ta modul še nima lekcij, ki jih je mogoče dodeliti.")
                .setPositiveButton("V redu", null)
                .show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(module.name)
            .setItems(choices.map { it.label }.toTypedArray()) { _, position ->
                val item = choices[position]
                val index = homeworkLessons.indexOfFirst { it.lessonId == item.lessonId }
                if (index >= 0) {
                    spinnerLesson.setSelection(index)
                    val card = view?.findViewById<View>(R.id.homeworkCard)
                    if (card != null) scroll.post { scroll.smoothScrollTo(0, card.top) }
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun focusHomework(m: PathModuleUi) {
        val root = view ?: return
        val index = homeworkLessons.indexOfFirst { it.moduleTopicId == m.topicId }
        if (index >= 0) spinnerLesson.setSelection(index)
        val card = root.findViewById<View>(R.id.homeworkCard)
        scroll.post { scroll.smoothScrollTo(0, card.top) }
    }

    private fun sendHomework() {
        val tab = selectedTab ?: return
        val lesson = homeworkLessons.getOrNull(spinnerLesson.selectedItemPosition) ?: return
        val deadline = deadlines.getOrNull(spinnerDeadline.selectedItemPosition) ?: return

        val due = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, deadline.days)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        btnSend.isEnabled = false
        progress.visibility = View.VISIBLE
        repo.assignHomework(tab.classroom.id, lesson.lessonId, isoUtc(due.time), cbRetry.isChecked) { result ->
            if (view == null) return@assignHomework
            when (result) {
                is ApiResult.Success -> {
                    toast(R.string.tl_homework_sent)
                    reloadCurrent()
                }
                is ApiResult.Failure -> {
                    progress.visibility = View.GONE
                    btnSend.isEnabled = true
                    if (result.httpCode == 401) logout() else toast(R.string.tl_homework_failed)
                }
            }
        }
    }

    private fun unlock(tab: TeacherClassTab, m: PathModuleUi) {
        progress.visibility = View.VISIBLE
        repo.unlockModule(tab.classroom.id, m) { result ->
            if (view == null) return@unlockModule
            when (result) {
                is ApiResult.Success -> reloadCurrent()
                is ApiResult.Failure -> {
                    progress.visibility = View.GONE
                    if (result.httpCode == 401) logout() else toast(R.string.ts_publish_failed)
                }
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

    private fun View.tv(id: Int) = findViewById<TextView>(id)

    private fun gradeLabel(tab: TeacherClassTab) =
        ClassroomNaming.parse(tab.classroom.name).first.replace(Regex("^(\\d+)\\.?\\s*"), "$1. ")

    private fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }

    private fun lastActiveText(iso: String?): String {
        val millis = IsoDate.parse(iso) ?: return getString(R.string.tl_never_active)
        val days = max(0, ((System.currentTimeMillis() - millis) / LEARN_DAY_MS).toInt())
        return getString(
            R.string.tl_last_active,
            resources.getQuantityString(R.plurals.tl_days_ago, days, days)
        )
    }

    private fun slovenian(pattern: String) =
        SimpleDateFormat(pattern, Locale.Builder().setLanguage("sl").setRegion("SI").build())

    private fun isoUtc(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)

    private fun compact(xp: Int): String =
        if (xp >= 1000) String.format(Locale.US, "%.1fk", xp / 1000.0) else xp.toString()

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun tint(res: Int) = ColorStateList.valueOf(color(res))
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val LEARN_DAY_MS = 86_400_000L
    }
}