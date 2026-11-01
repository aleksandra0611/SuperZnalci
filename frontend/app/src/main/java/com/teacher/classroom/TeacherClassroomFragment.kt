package com.example.superznalci

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.util.Locale

class TeacherClassroomFragment : Fragment(R.layout.fragment_teacher_classroom) {

    private val repo: TeacherClassroomRepository get() = ServiceLocator.teacherClassroom
    private val classesRepo: TeacherSubjectsRepository get() = ServiceLocator.teacherSubjects

    private var tabs: List<TeacherClassTab> = emptyList()
    private var classIndex = 0
    private val dashboards = HashMap<String, ClassroomDashboard>()
    private val editors = HashMap<String, List<EditorSubjectUi>>()

    private var sortMode = StudentSort.ACCURACY_DESC
    private var subjectIndex = 0
    private var lessonIndex = 0
    private var selectedType = QuestionTypes.CHOICE
    private var editingQuestionId: String? = null

    private lateinit var progress: ProgressBar
    private lateinit var scroll: ScrollView
    private lateinit var content: View
    private lateinit var spinnerSubject: Spinner
    private lateinit var spinnerLesson: Spinner
    private lateinit var etQuestion: EditText
    private lateinit var etPoints: EditText
    private lateinit var etExplanation: EditText
    private lateinit var etOptions: EditText
    private lateinit var etCorrectAnswer: EditText
    private lateinit var optionsSection: View

    private val selectedTab: TeacherClassTab? get() = tabs.getOrNull(classIndex)
    private val dashboard: ClassroomDashboard? get() = selectedTab?.let { dashboards[it.classroom.id] }
    private val editor: List<EditorSubjectUi> get() = selectedTab?.let { editors[it.classroom.id] }.orEmpty()
    private val currentSubject: EditorSubjectUi? get() = editor.getOrNull(subjectIndex)
    private val currentLesson: EditorLessonUi? get() = currentSubject?.lessons?.getOrNull(lessonIndex)

    private val typeTiles by lazy {
        mapOf(
            R.id.typeChoice to QuestionTypes.CHOICE,
            R.id.typeTrueFalse to QuestionTypes.TRUE_FALSE,
            R.id.typePairs to QuestionTypes.PAIRS,
            R.id.typeFill to QuestionTypes.FILL_BLANK,
            R.id.typeImage to QuestionTypes.IMAGE
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        progress = view.findViewById(R.id.progress)
        scroll = view.findViewById(R.id.scroll)
        content = view.findViewById(R.id.content)
        spinnerSubject = view.findViewById(R.id.spinnerSubject)
        spinnerLesson = view.findViewById(R.id.spinnerLesson)
        etQuestion = view.findViewById(R.id.etQuestion)
        etPoints = view.findViewById(R.id.etPoints)
        etExplanation = view.findViewById(R.id.etExplanation)
        etOptions = view.findViewById(R.id.etOptions)
        etCorrectAnswer = view.findViewById(R.id.etCorrectAnswer)
        optionsSection = view.findViewById(R.id.optionsSection)

        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        listOf(
            R.id.btnNewAssignment, R.id.navLearn
        ).forEach { view.findViewById<View>(it).setOnClickListener(comingSoon) }

        view.findViewById<View>(R.id.btnNewClass).setOnClickListener {
            TeacherContentDialogs.newClass(requireContext()) { loadClasses() }
        }
        TeacherNav.bind(view, R.id.navClassroom, this)

        view.findViewById<View>(R.id.btnEditContent).setOnClickListener {
            scroll.smoothScrollTo(0, view.findViewById<View>(R.id.editorCard).top)
        }
        view.findViewById<View>(R.id.btnCopy).setOnClickListener { selectedTab?.let { copyCode(it.classroom.joinCode) } }
        view.findViewById<View>(R.id.btnRefresh).setOnClickListener { confirmRegenerate() }
        view.findViewById<View>(R.id.btnSort).setOnClickListener { pickSort() }
        // The visible editor is the only form for creating new questions.
        view.findViewById<View>(R.id.btnAddQuestion).setOnClickListener {
            saveQuestionFromForm()
        }
        view.findViewById<View>(R.id.btnResetForm).setOnClickListener { resetForm() }

        typeTiles.forEach { (id, type) ->
            view.findViewById<View>(id).setOnClickListener {
                if (type == QuestionTypes.PAIRS || type == QuestionTypes.IMAGE) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Ta vrsta vprašanja še ni podprta.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else selectType(type)
            }
        }
        selectType(selectedType)

        SessionStore.user?.let {
            view.findViewById<TextView>(R.id.tvStreak).text = "🔥 ${it.streak}"
            view.findViewById<TextView>(R.id.tvXp).text = "⭐ ${compact(it.totalXp)}"
            view.findViewById<TextView>(R.id.tvTeacherName).text = getString(R.string.teacher_name_fmt, it.name)
        }

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
                        view?.findViewById<TextView>(R.id.tvStudentsEmpty)?.apply {
                            setText(R.string.tc_empty_classes)
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
        subjectIndex = 0
        lessonIndex = 0
        renderTabs()
        renderHeader()
        val tab = selectedTab ?: return
        loadDashboard(tab)
        loadEditor(tab)
    }

    private fun loadDashboard(tab: TeacherClassTab) {
        if (dashboards.containsKey(tab.classroom.id)) {
            renderDashboard()
            return
        }
        progress.visibility = View.VISIBLE
        repo.loadClassroom(tab.classroom) { result ->
            if (view == null) return@loadClassroom
            progress.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    dashboards[tab.classroom.id] = result.data
                    if (selectedTab?.classroom?.id == tab.classroom.id) renderDashboard()
                }
                is ApiResult.Failure -> onFailure(result.httpCode)
            }
        }
    }

    private fun loadEditor(tab: TeacherClassTab) {
        if (editors.containsKey(tab.classroom.id)) {
            bindEditor()
            return
        }
        repo.loadEditor(tab.classroom) { result ->
            if (view == null) return@loadEditor
            when (result) {
                is ApiResult.Success -> {
                    editors[tab.classroom.id] = result.data
                    if (selectedTab?.classroom?.id == tab.classroom.id) bindEditor()
                }
                is ApiResult.Failure -> onFailure(result.httpCode)
            }
        }
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

    private fun renderTabs() {
        val root = view ?: return
        val row = root.findViewById<LinearLayout>(R.id.classTabs)
        row.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            val selected = i == classIndex
            row.addView(TextView(requireContext()).apply {
                text = if (selected) "${prettyClass(tab)}  ●" else prettyClass(tab)
                textSize = 14f
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setBackgroundResource(R.drawable.bg_t_pill)
                backgroundTintList = tint(if (selected) R.color.t_green else R.color.t_chip_bg)
                setTextColor(color(if (selected) android.R.color.white else R.color.t_text))
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { if (i != classIndex) selectClass(i) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) })
        }
    }

    private fun renderHeader() {
        val root = view ?: return
        val tab = selectedTab ?: return
        content.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.tvStudentTotal).text =
            getString(R.string.tc_students_count, tab.studentCount)
        root.findViewById<TextView>(R.id.tvJoinCode).text = tab.classroom.joinCode
        root.findViewById<TextView>(R.id.tvStudentsClass).text = prettyClass(tab)
        root.findViewById<TextView>(R.id.tvEditorGrade).text =
            getString(R.string.tc_grade_badge, tab.classroom.gradeNumber)
    }

    private fun renderDashboard() {
        val root = view ?: return
        val data = dashboard ?: return
        val total = selectedTab?.studentCount ?: data.students.size
        val o = data.overview

        root.findViewById<ProgressBar>(R.id.accuracyRing).progress = o.averageAccuracy.coerceIn(0, 100)
        root.findViewById<TextView>(R.id.tvAccuracyValue).text =
            getString(R.string.tc_accuracy_pct, o.averageAccuracy)
        root.findViewById<TextView>(R.id.tvActivityLabel).setText(
            when {
                o.averageAccuracy >= 80 -> R.string.tc_activity_great
                o.averageAccuracy >= 60 -> R.string.tc_activity_good
                else -> R.string.tc_activity_low
            }
        )

        val struggle = o.struggleTopics.orEmpty().joinToString(" in ")
        val desc = StringBuilder(getString(R.string.tc_overview_submitted, o.submittedThisWeek, total))
        if (struggle.isNotEmpty()) desc.append(' ').append(getString(R.string.tc_overview_struggle, struggle))
        root.findViewById<TextView>(R.id.tvOverviewDesc).text = desc

        renderStudents()
    }

    private fun renderStudents() {
        val root = view ?: return
        val data = dashboard ?: return
        val container = root.findViewById<LinearLayout>(R.id.studentsContainer)
        val empty = root.findViewById<TextView>(R.id.tvStudentsEmpty)
        container.removeAllViews()

        if (data.students.isEmpty()) {
            empty.setText(R.string.tc_empty_students)
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        val sorted = when (sortMode) {
            StudentSort.ACCURACY_DESC -> data.students.sortedByDescending { it.accuracy ?: -1 }
            StudentSort.ACCURACY_ASC -> data.students.sortedBy { it.accuracy ?: 101 }
            StudentSort.NAME -> data.students.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
        sorted.forEachIndexed { i, s ->
            val card = layoutInflater.inflate(R.layout.item_tc_student, container, false)
            bindStudent(card, s, i)
            container.addView(card)
        }
    }

    private fun bindStudent(card: View, s: StudentRowUi, position: Int) {
        val comingSoon = View.OnClickListener { toast(R.string.teacher_coming_soon) }
        val acc = s.accuracy
        val tone = when {
            acc == null -> R.color.ts_muted
            acc >= 90 -> R.color.t_green
            acc >= 70 -> R.color.ts_amber_text
            else -> R.color.tc_red
        }

        card.findViewById<TextView>(R.id.tcAvatar).apply {
            text = s.initials
            backgroundTintList = tint(listOf(R.color.t_mint, R.color.t_indigo_soft, R.color.t_orange_soft)[position % 3])
        }
        card.findViewById<TextView>(R.id.tcName).text = s.name
        card.findViewById<TextView>(R.id.tcMeta).text =
            if (s.total > 0) getString(R.string.tc_student_progress, s.completed, s.total)
            else getString(R.string.tc_student_no_data)
        card.findViewById<TextView>(R.id.tcPercent).apply {
            text = if (acc == null) "–" else getString(R.string.tc_accuracy_pct, acc)
            setTextColor(color(tone))
        }
        card.findViewById<TextView>(R.id.tcPercentLabel).setText(
            if (acc != null && acc < 70) R.string.tc_needs_support else R.string.tc_accuracy_label
        )

        val rowBadge = card.findViewById<View>(R.id.rowBadge)
        val rowAlert = card.findViewById<View>(R.id.rowAlert)
        val alertText = card.findViewById<TextView>(R.id.tcAlertText)
        val alertBtn = card.findViewById<TextView>(R.id.btnAlert)
        rowBadge.visibility = View.GONE
        rowAlert.visibility = View.GONE

        when {
            acc != null && acc < 70 -> {
                rowAlert.visibility = View.VISIBLE
                rowAlert.backgroundTintList = tint(R.color.tc_red_soft)
                alertText.setTextColor(color(R.color.tc_red))
                alertText.text = getString(R.string.tc_needs_help_at, s.weakTopic ?: s.mistakeTopic.orEmpty())
                alertBtn.setText(R.string.tc_assign_practice)
                alertBtn.backgroundTintList = tint(R.color.t_indigo)
            }
            s.mistakeCount > 0 -> {
                rowAlert.visibility = View.VISIBLE
                rowAlert.backgroundTintList = tint(R.color.t_chip_bg)
                alertText.setTextColor(color(R.color.t_text))
                alertText.text = resources.getQuantityString(
                    R.plurals.tc_mistakes, s.mistakeCount, s.mistakeCount, s.mistakeTopic.orEmpty()
                )
                alertBtn.setText(R.string.tc_view_mistakes)
                alertBtn.backgroundTintList = tint(R.color.t_indigo_soft)
            }
            s.badge != null -> {
                rowBadge.visibility = View.VISIBLE
                card.findViewById<TextView>(R.id.tcBadge).text = s.badge
            }
        }
        alertBtn.setOnClickListener(comingSoon)
    }

    // editor

    private fun bindEditor() {
        val root = view ?: return
        val subjects = editor
        val tab = selectedTab ?: return

        subjectIndex = subjectIndex.coerceIn(0, (subjects.size - 1).coerceAtLeast(0))
        lessonIndex = 0

        spinnerSubject.onItemSelectedListener = null
        spinnerSubject.adapter = spinnerAdapter(subjects.map { "${it.name} ${tab.classroom.gradeNumber}".trim() })
        spinnerSubject.setSelection(subjectIndex, false)
        spinnerSubject.onItemSelectedListener = onPick { pos ->
            if (pos != subjectIndex) {
                subjectIndex = pos
                lessonIndex = 0
                resetForm()
                bindLessons()
            }
        }
        bindLessons()
        // Vnos pravilnih rešitev opravi TeacherContentDialogs.question().
        root.findViewById<View>(R.id.btnAddQuestion).isEnabled = currentLesson != null
    }

    private fun bindLessons() {
        val lessons = currentSubject?.lessons.orEmpty()
        lessonIndex = lessonIndex.coerceIn(0, (lessons.size - 1).coerceAtLeast(0))

        spinnerLesson.onItemSelectedListener = null
        spinnerLesson.adapter = spinnerAdapter(lessons.map { it.name })
        spinnerLesson.setSelection(lessonIndex, false)
        spinnerLesson.onItemSelectedListener = onPick { pos ->
            if (pos != lessonIndex) {
                lessonIndex = pos
                resetForm()
                renderQuestions()
            }
        }
        renderQuestions()
        view?.findViewById<View>(R.id.btnAddQuestion)?.isEnabled = currentLesson != null
    }

    private fun renderQuestions() {
        val root = view ?: return
        val container = root.findViewById<LinearLayout>(R.id.questionsContainer)
        val empty = root.findViewById<TextView>(R.id.tvQuestionsEmpty)
        val title = root.findViewById<TextView>(R.id.tvQuestionsTitle)
        container.removeAllViews()

        val lesson = currentLesson
        if (lesson == null) {
            title.setText(R.string.tc_questions_none)
            empty.setText(R.string.tc_questions_empty)
            empty.visibility = View.VISIBLE
            return
        }
        val label = lesson.topicName.ifBlank { lesson.name }
        title.text = getString(R.string.tc_questions_title, label, lesson.questions.size)
        empty.visibility = if (lesson.questions.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(R.string.tc_questions_empty)

        lesson.questions.forEachIndexed { i, q ->
            val row = layoutInflater.inflate(R.layout.item_tc_question, container, false)
            row.findViewById<TextView>(R.id.tcQNumber).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.tcQText).text = q.text
            row.findViewById<TextView>(R.id.tcQMeta).text =
                getString(R.string.tc_question_meta, typeLabel(q.type), q.points)
            row.findViewById<View>(R.id.btnQEdit).setOnClickListener {
                editQuestionInForm(lesson.id, q.id)
            }
            row.findViewById<View>(R.id.btnQDelete).setOnClickListener {
                TeacherContentDialogs.deleteQuestion(requireContext(), lesson.id, q.id) {
                    selectedTab?.let { editors.remove(it.classroom.id); loadEditor(it) }
                }
            }
            container.addView(row)
        }
    }

    private fun selectType(type: String) {
        selectedType = type
        if (::optionsSection.isInitialized) {
            optionsSection.visibility = if (type == QuestionTypes.CHOICE) View.VISIBLE else View.GONE
            etCorrectAnswer.hint = when (type) {
                QuestionTypes.TRUE_FALSE -> "true ali false"
                QuestionTypes.FILL_BLANK -> "Beseda ali besedilo za praznino"
                else -> "Ena izmed zgoraj navedenih možnosti"
            }
        }
        val root = view ?: return
        typeTiles.forEach { (id, t) ->
            val on = t == type
            root.findViewById<TextView>(id).apply {
                backgroundTintList = tint(if (on) R.color.t_green_soft else R.color.t_chip_bg)
                setTextColor(color(if (on) R.color.t_green else R.color.t_text))
                setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    private fun resetForm() {
        editingQuestionId = null
        view?.findViewById<TextView>(R.id.btnAddQuestion)?.setText(R.string.tc_add_question)
        etQuestion.text.clear()
        etQuestion.error = null
        etPoints.setText("10")
        etExplanation.text.clear()
        etOptions.text.clear()
        etCorrectAnswer.text.clear()
        selectType(QuestionTypes.CHOICE)
    }

    // Load a stored question into the existing visible editor, not a second dialog.
    private fun editQuestionInForm(lessonId: String, questionId: String) {
        ApiClient.service.ownedLessonQuestions(lessonId).enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (!isAdded || view == null) return
                if (!response.isSuccessful) {
                    android.widget.Toast.makeText(requireContext(), "Napaka pri nalaganju: HTTP ${response.code()}", android.widget.Toast.LENGTH_LONG).show()
                    return
                }
                val question = response.body()?.getAsJsonArray("questions")
                    ?.map { it.asJsonObject }
                    ?.firstOrNull { it.get("id")?.asString == questionId }
                if (question == null) {
                    android.widget.Toast.makeText(requireContext(), "Vprašanja ni bilo mogoče najti.", android.widget.Toast.LENGTH_LONG).show()
                    return
                }
                val type = question.get("type")?.asString ?: QuestionTypes.CHOICE
                if (type !in listOf(QuestionTypes.CHOICE, QuestionTypes.TRUE_FALSE, QuestionTypes.FILL_BLANK)) {
                    android.widget.Toast.makeText(requireContext(), "Ta vrsta vprašanja še ni podprta za urejanje.", android.widget.Toast.LENGTH_LONG).show()
                    return
                }
                editingQuestionId = questionId
                selectType(type)
                etQuestion.setText(question.get("text")?.asString.orEmpty())
                etPoints.setText(question.get("points")?.asString ?: "10")
                etExplanation.setText(question.get("explanation")?.takeUnless { it.isJsonNull }?.asString.orEmpty())
                val options = question.get("answers")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.joinToString(";") { it.asString }.orEmpty()
                etOptions.setText(options)
                val correct = question.get("correctAnswers")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.firstOrNull()?.asString.orEmpty()
                etCorrectAnswer.setText(correct)
                view?.findViewById<TextView>(R.id.btnAddQuestion)?.text = "Shrani spremembe"
                scroll.smoothScrollTo(0, view?.findViewById<View>(R.id.editorCard)?.top ?: 0)
            }
            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                if (!isAdded || view == null) return
                android.widget.Toast.makeText(requireContext(), "Ni povezave: ${t.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun saveQuestionFromForm() {
        val tab = selectedTab ?: return
        val lesson = currentLesson ?: run {
            android.widget.Toast.makeText(requireContext(), "Najprej izberi svojo lekcijo.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val questionText = etQuestion.text.toString().trim()
        val correctAnswer = etCorrectAnswer.text.toString().trim()
        val explanation = etExplanation.text.toString().trim()
        val points = etPoints.text.toString().trim().toIntOrNull()
        val type = selectedType

        if (questionText.isBlank()) { etQuestion.error = "Vnesi vprašanje"; return }
        if (points == null || points !in 1..100) { etPoints.error = "Vnesi 1–100 točk"; return }
        if (correctAnswer.isBlank()) { etCorrectAnswer.error = "Vnesi pravilen odgovor"; return }

        val options = etOptions.text.toString().split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (type == QuestionTypes.CHOICE &&
            (options.size !in 2..8 || options.size != options.distinct().size || correctAnswer !in options)
        ) {
            etOptions.error = "Vnesi 2–8 različnih možnosti, ločenih s ;. Pravilen odgovor mora biti ena izmed njih."
            return
        }
        if (type == QuestionTypes.TRUE_FALSE && correctAnswer.lowercase(java.util.Locale.ROOT) !in listOf("true", "false")) {
            etCorrectAnswer.error = "Vnesi true ali false"
            return
        }
        if (type !in listOf(QuestionTypes.CHOICE, QuestionTypes.TRUE_FALSE, QuestionTypes.FILL_BLANK)) {
            android.widget.Toast.makeText(requireContext(), "Ta vrsta še ni podprta", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val body = JsonObject().apply {
            addProperty("text", questionText)
            addProperty("type", type)
            addProperty("points", points)
            addProperty("explanation", explanation)
            add("correctAnswers", JsonArray().apply { add(correctAnswer) })
            when (type) {
                QuestionTypes.CHOICE -> add("answers", JsonArray().apply { options.forEach { add(it) } })
                QuestionTypes.TRUE_FALSE -> add("answers", JsonArray().apply { add("true"); add("false") })
                else -> add("answers", com.google.gson.JsonNull.INSTANCE)
            }
        }
        progress.visibility = View.VISIBLE
        val editId = editingQuestionId
        val request = if (editId == null) ApiClient.service.newTeacherQuestion(lesson.id, body)
                      else ApiClient.service.editTeacherQuestion(lesson.id, editId, body)
        request.enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (!isAdded || view == null) return
                progress.visibility = View.GONE
                if (response.isSuccessful) {
                    resetForm()
                    editors.remove(tab.classroom.id)
                    loadEditor(tab)
                    android.widget.Toast.makeText(requireContext(), "Vprašanje je shranjeno.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(requireContext(), "Shranjevanje ni uspelo: HTTP ${response.code()}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                if (!isAdded || view == null) return
                progress.visibility = View.GONE
                android.widget.Toast.makeText(requireContext(), "Omrežna napaka: ${t.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun confirmDelete(lesson: EditorLessonUi, q: QuestionRowUi) {
        // Brisanje naj ostane izključeno, dokler ustvarjanje/urejanje vprašanj ni dokončano.
        toast(R.string.tc_question_delete_failed)
        return
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.tc_delete_confirm)
            .setPositiveButton(R.string.tc_delete) { _, _ -> deleteQuestion(lesson, q) }
            .setNegativeButton(R.string.tc_cancel, null)
            .show()
    }

    private fun deleteQuestion(lesson: EditorLessonUi, q: QuestionRowUi) {
        val tab = selectedTab ?: return
        progress.visibility = View.VISIBLE
        repo.deleteQuestion(q.id) { result ->
            if (view == null) return@deleteQuestion
            progress.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    mutateLesson(tab.classroom.id, lesson.id) { list -> list.filter { it.id != q.id } }
                    renderQuestions()
                }
                is ApiResult.Failure -> if (result.httpCode == 401) logout() else toast(R.string.tc_question_delete_failed)
            }
        }
    }

    private fun mutateLesson(classId: String, lessonId: String, f: (List<QuestionRowUi>) -> List<QuestionRowUi>) {
        editors[classId] = editors[classId].orEmpty().map { s ->
            s.copy(lessons = s.lessons.map { l -> if (l.id == lessonId) l.copy(questions = f(l.questions)) else l })
        }
    }

    // actions

    private fun pickSort() {
        val labels = arrayOf(
            getString(R.string.tc_sort_best),
            getString(R.string.tc_sort_worst),
            getString(R.string.tc_sort_name)
        )
        AlertDialog.Builder(requireContext())
            .setSingleChoiceItems(labels, sortMode.ordinal) { dialog, which ->
                sortMode = StudentSort.values()[which]
                renderStudents()
                dialog.dismiss()
            }
            .show()
    }

    private fun copyCode(code: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("joinCode", code))
        toast(R.string.teacher_code_copied)
    }

    private fun confirmRegenerate() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.tc_regenerate_confirm)
            .setPositiveButton(R.string.tc_regenerate) { _, _ -> regenerateCode() }
            .setNegativeButton(R.string.tc_cancel, null)
            .show()
    }

    private fun regenerateCode() {
        val tab = selectedTab ?: return
        progress.visibility = View.VISIBLE
        repo.regenerateJoinCode(tab.classroom.id) { result ->
            if (view == null) return@regenerateJoinCode
            progress.visibility = View.GONE
            when (result) {
                is ApiResult.Success -> {
                    tabs = tabs.map {
                        if (it.classroom.id == tab.classroom.id) it.copy(classroom = it.classroom.copy(joinCode = result.data)) else it
                    }
                    renderHeader()
                }
                is ApiResult.Failure -> if (result.httpCode == 401) logout() else toast(R.string.tc_regenerate_failed)
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

    private fun typeLabel(type: String): String = when (type) {
        QuestionTypes.CHOICE -> getString(R.string.tc_type_choice_long)
        QuestionTypes.TRUE_FALSE -> getString(R.string.tc_type_true_false)
        QuestionTypes.PAIRS -> getString(R.string.tc_type_pairs)
        QuestionTypes.FILL_BLANK -> getString(R.string.tc_type_fill)
        QuestionTypes.IMAGE -> getString(R.string.tc_type_image)
        else -> type
    }

    private fun prettyClass(tab: TeacherClassTab): String =
        ClassroomNaming.parse(tab.classroom.name).first.replace(Regex("^(\\d+)\\.?\\s*"), "$1. ")

    private fun spinnerAdapter(items: List<String>) =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)

    private fun onPick(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) = block(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun compact(xp: Int): String =
        if (xp >= 1000) String.format(Locale.US, "%.1fk", xp / 1000.0) else xp.toString()

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun tint(res: Int) = ColorStateList.valueOf(color(res))
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
