package com.example.superznalci

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TeacherSubjectsFragment : Fragment(R.layout.fragment_teacher_subjects) {

    private val repo: TeacherSubjectsRepository get() = ServiceLocator.teacherSubjects

    private var tabs: List<TeacherClassTab> = emptyList()
    private var classIndex = 0
    private var subjectIndex = 0
    private val cache = HashMap<String, List<SubjectUi>>()

    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var content: View

    private val selectedTab: TeacherClassTab? get() = tabs.getOrNull(classIndex)
    private val subjects: List<SubjectUi>
        get() = selectedTab?.let { cache[it.classroom.id] }.orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        content = view.findViewById(R.id.content)

        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        listOf(R.id.btnNewSubject, R.id.btnEditPlan, R.id.btnAddTopic, R.id.btnCatalogImport, R.id.btnCatalogBrowse)
            .forEach { view.findViewById<View>(it).setOnClickListener(comingSoon) }

        SessionStore.user?.let {
            view.findViewById<TextView>(R.id.tvStreak).text = "🔥 ${it.streak}"
            view.findViewById<TextView>(R.id.tvXp).text = "⭐ ${compact(it.totalXp)}"
        }

        TeacherNav.bind(view, R.id.navSubjects, this)

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
                        showEmpty(R.string.ts_empty_classes)
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
        subjectIndex = 0
        renderTabs()
        val tab = selectedTab ?: return
        if (cache.containsKey(tab.classroom.id)) render() else loadSubjects(tab)
    }

    private fun loadSubjects(tab: TeacherClassTab) {
        progress.visibility = View.VISIBLE
        repo.loadClassSubjects(tab.classroom) { result ->
            if (view == null) return@loadClassSubjects
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
        loadSubjects(tab)
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

    private fun render() {
        val root = view ?: return
        val tab = selectedTab ?: return
        content.visibility = View.VISIBLE

        root.findViewById<TextView>(R.id.tvBrand).text =
            getString(R.string.ts_brand, tab.classroom.gradeNumber)

        val list = subjects
        val chips = root.findViewById<LinearLayout>(R.id.subjectChips)
        val card = root.findViewById<View>(R.id.subjectCard)
        val modules = root.findViewById<LinearLayout>(R.id.modulesContainer)
        chips.removeAllViews()
        modules.removeAllViews()

        if (list.isEmpty()) {
            card.visibility = View.GONE
            root.findViewById<View>(R.id.catalogCard).visibility = View.GONE
            showEmpty(R.string.ts_empty)
            return
        }
        tvEmpty.visibility = View.GONE
        card.visibility = View.VISIBLE
        root.findViewById<View>(R.id.catalogCard).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.tvCatalogDesc).text =
            getString(R.string.ts_catalog_desc, tab.classroom.gradeNumber)

        val dots = listOf(R.color.t_green, R.color.t_indigo, R.color.t_orange_text)
        list.forEachIndexed { i, subject ->
            val chip = layoutInflater.inflate(R.layout.item_t_subject_chip, chips, false)
            chip.findViewById<View>(R.id.chipDot).backgroundTintList = tint(dots[i % dots.size])
            chip.findViewById<TextView>(R.id.chipName).text = subject.title
            chip.findViewById<TextView>(R.id.chipModules).text =
                getString(R.string.ts_topics_count, subject.modules.size)
            chip.alpha = if (i == subjectIndex) 1f else 0.55f
            chip.setOnClickListener {
                subjectIndex = i
                render()
            }
            chips.addView(chip)
        }

        val subject = list[subjectIndex.coerceIn(0, list.lastIndex)]
        bindSubjectCard(root, subject)
        subject.modules.forEach { modules.addView(buildModule(modules, tab, subject, it)) }
    }

    private fun showEmpty(textRes: Int) {
        content.visibility = View.VISIBLE
        tvEmpty.setText(textRes)
        tvEmpty.visibility = View.VISIBLE
    }

    private fun renderTabs() {
        val root = view ?: return
        val row = root.findViewById<LinearLayout>(R.id.classTabs)
        row.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            val label = ClassroomNaming.parse(tab.classroom.name).first
                .replace(Regex("^(\\d+)\\.?\\s*"), "$1. ")
            val selected = i == classIndex
            row.addView(TextView(requireContext()).apply {
                text = "$label (${tab.studentCount})"
                textSize = 14f
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setBackgroundResource(R.drawable.bg_t_pill)
                backgroundTintList = tint(if (selected) R.color.t_green_soft else R.color.t_chip_bg)
                setTextColor(color(if (selected) R.color.t_green else R.color.t_text))
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { if (i != classIndex) selectClass(i) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) })
        }
    }

    private fun bindSubjectCard(root: View, s: SubjectUi) {
        root.findViewById<TextView>(R.id.tvSubjectTitle).text = s.title
        root.findViewById<View>(R.id.tvSubjectActive).visibility =
            if (s.isActive) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.tvSubjectSub).text =
            getString(R.string.ts_curriculum, s.gradeNumber, schoolYear())
        root.findViewById<TextView>(R.id.tvStatModules).text = s.modules.size.toString()
        root.findViewById<TextView>(R.id.tvStatLessons).text = s.lessonCount.toString()
        root.findViewById<TextView>(R.id.tvStatQuestions).text = s.questionCount.toString()
    }

    private fun buildModule(
        parent: LinearLayout, tab: TeacherClassTab, subject: SubjectUi, m: ModuleUi
    ): View {
        val v = layoutInflater.inflate(R.layout.item_ts_module, parent, false)
        v.findViewById<TextView>(R.id.tvModuleLabel).text = getString(R.string.ts_module_label, m.number)
        v.findViewById<TextView>(R.id.tvModuleTitle).text = m.name
        v.findViewById<TextView>(R.id.tvModuleStatus).apply {
            if (m.isActive) {
                setText(R.string.ts_module_active)
                backgroundTintList = tint(R.color.t_green_soft)
                setTextColor(color(R.color.t_green))
            } else {
                setText(R.string.ts_module_preparing)
                backgroundTintList = tint(R.color.ts_amber_bg)
                setTextColor(color(R.color.ts_amber_text))
            }
        }
        v.findViewById<View>(R.id.btnModuleMore).setOnClickListener { toast(R.string.teacher_coming_soon) }
        v.findViewById<View>(R.id.btnAddLesson).setOnClickListener { toast(R.string.teacher_coming_soon) }

        val visibilityRow = v.findViewById<View>(R.id.visibilityRow)
        val switch = v.findViewById<SwitchCompat>(R.id.switchVisible)
        if (m.isActive) {
            visibilityRow.visibility = View.GONE
        } else {
            visibilityRow.visibility = View.VISIBLE
            switch.isChecked = m.visibleToStudents
            switch.setOnCheckedChangeListener { _, checked ->
                repo.setTopicVisible(tab.classroom.id, m.topicId, checked) { result ->
                    if (view == null) return@setTopicVisible
                    if (result is ApiResult.Failure) {
                        switch.setOnCheckedChangeListener(null)
                        switch.isChecked = !checked
                        if (result.httpCode == 401) logout() else toast(R.string.err_server)
                    } else {
                        cache[tab.classroom.id] = cache[tab.classroom.id].orEmpty().map { s ->
                            if (s.id != subject.id) s
                            else s.copy(modules = s.modules.map {
                                if (it.topicId == m.topicId) it.copy(visibleToStudents = checked) else it
                            })
                        }
                    }
                }
            }
        }

        val lessons = v.findViewById<LinearLayout>(R.id.lessonsContainer)
        m.lessons.forEach { lessons.addView(buildLesson(lessons, tab, it)) }
        return v
    }

    private fun buildLesson(parent: LinearLayout, tab: TeacherClassTab, l: LessonRowUi): View {
        val v = layoutInflater.inflate(R.layout.item_ts_lesson, parent, false)
        val published = l.status == LessonStatus.PUBLISHED

        v.findViewById<TextView>(R.id.tvLessonTitle).text = l.name

        val number = v.findViewById<TextView>(R.id.tvNumber)
        val lock = v.findViewById<View>(R.id.ivLock)
        if (l.status == LessonStatus.SCHEDULED) {
            number.visibility = View.GONE
            lock.visibility = View.VISIBLE
        } else {
            number.text = l.number.toString()
            number.backgroundTintList = tint(if (published) R.color.t_green_soft else R.color.t_chip_bg)
            number.setTextColor(color(if (published) R.color.t_green else R.color.ts_muted))
        }

        val meta = StringBuilder()
        when (l.status) {
            LessonStatus.PUBLISHED -> {
                meta.append(
                    if (l.completionPercent != null)
                        getString(R.string.ts_lesson_meta_completion, l.questionCount, l.completionPercent)
                    else getString(R.string.ts_lesson_meta_draft, l.questionCount)
                )
                l.averageAccuracy?.let {
                    meta.append('\n').append(getString(R.string.ts_lesson_meta_accuracy, it))
                }
            }
            LessonStatus.SCHEDULED -> meta.append(
                getString(R.string.ts_lesson_meta_locked, l.questionCount, shortDate(l.availableFrom))
            )
            LessonStatus.DRAFT -> meta.append(getString(R.string.ts_lesson_meta_draft, l.questionCount))
        }
        v.findViewById<TextView>(R.id.tvLessonMeta).text = meta

        v.findViewById<TextView>(R.id.tvLessonStatus).apply {
            if (published) {
                setText(R.string.ts_published)
                backgroundTintList = tint(R.color.t_green)
                setTextColor(color(android.R.color.white))
            } else {
                setText(R.string.ts_draft)
                backgroundTintList = tint(R.color.ts_amber_bg)
                setTextColor(color(R.color.ts_amber_text))
            }
        }

        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        val iconsRow = v.findViewById<View>(R.id.rowIcons)
        val draftRow = v.findViewById<View>(R.id.rowDraft)
        if (published) {
            iconsRow.visibility = View.VISIBLE
            draftRow.visibility = View.GONE
            listOf(R.id.btnEdit, R.id.btnTune, R.id.btnDelete).forEach {
                v.findViewById<View>(it).setOnClickListener(comingSoon)
            }
        } else {
            iconsRow.visibility = View.GONE
            draftRow.visibility = View.VISIBLE
            v.findViewById<View>(R.id.btnEditDraft).setOnClickListener(comingSoon)
            v.findViewById<TextView>(R.id.btnChangeDate).apply {
                setText(if (l.status == LessonStatus.SCHEDULED) R.string.ts_change_date else R.string.ts_set_date)
                setOnClickListener { pickDate(tab, l) }
            }
            v.findViewById<View>(R.id.btnPublishNow).setOnClickListener { setAvailability(tab, l, null) }
        }
        return v
    }

    // actions

    private fun pickDate(tab: TeacherClassTab, l: LessonRowUi) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply { clear(); set(y, m, d) }
                setAvailability(tab, l, isoUtc(picked.time))
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    private fun setAvailability(tab: TeacherClassTab, l: LessonRowUi, iso: String?) {
        progress.visibility = View.VISIBLE
        repo.setLessonAvailability(tab.classroom.id, l.id, iso) { result ->
            if (view == null) return@setLessonAvailability
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

    private fun slovenian(pattern: String) =
        SimpleDateFormat(pattern, Locale.Builder().setLanguage("sl").setRegion("SI").build())

    private fun shortDate(iso: String?): String =
        IsoDate.parse(iso)?.let { slovenian("d. MMM").format(Date(it)) }.orEmpty()

    private fun isoUtc(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)

    private fun schoolYear(): String {
        val now = Calendar.getInstance()
        val start = if (now.get(Calendar.MONTH) >= Calendar.SEPTEMBER) now.get(Calendar.YEAR)
        else now.get(Calendar.YEAR) - 1
        return "$start/${(start + 1) % 100}"
    }

    private fun compact(xp: Int): String =
        if (xp >= 1000) String.format(Locale.US, "%.1fk", xp / 1000.0) else xp.toString()

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun tint(res: Int) = ColorStateList.valueOf(color(res))
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}