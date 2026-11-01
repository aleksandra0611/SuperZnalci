package com.example.superznalci

interface TeacherClassroomRepository {
    fun loadClassroom(classroom: ClassroomDto, callback: ResultCallback<ClassroomDashboard>)
    fun regenerateJoinCode(classroomId: String, callback: ResultCallback<String>)

    fun loadEditor(classroom: ClassroomDto, callback: ResultCallback<List<EditorSubjectUi>>)
    fun addQuestion(lessonId: String, draft: QuestionDraft, callback: ResultCallback<QuestionRowUi>)
    fun deleteQuestion(questionId: String, callback: ResultCallback<Unit>)
}

class RetrofitTeacherClassroomRepository(
    private val api: ApiService,
    private val insights: ClassroomInsightsSource,
    private val authoring: QuestionAuthoringSource
) : TeacherClassroomRepository {

    private class Join(
        private var pending: Int,
        private val onFail: (Int?) -> Unit,
        private val onDone: () -> Unit
    ) {
        private var failed = false
        fun ok() {
            if (failed) return
            if (--pending == 0) onDone()
        }
        fun fail(code: Int?) {
            if (failed) return
            failed = true
            onFail(code)
        }
    }

    // dashboard

    override fun loadClassroom(classroom: ClassroomDto, callback: ResultCallback<ClassroomDashboard>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }

        api.classroomStudents(classroom.id).go(fail) { env ->
            val students = env.students.orEmpty()
            val ids = students.map { it.id }

            var results: List<StudentResultDto> = emptyList()
            var overview = ClassroomOverviewDto()

            val join = Join(2, fail) {
                callback(ApiResult.Success(ClassroomDashboard(buildRows(students, results), overview)))
            }
            val soft: (Int?) -> Unit = { if (it == 401) join.fail(it) else join.ok() }

            insights.loadStudentResults(classroom.id, ids) { r ->
                when (r) {
                    is ApiResult.Success -> { results = r.data; join.ok() }
                    is ApiResult.Failure -> soft(r.httpCode)
                }
            }
            insights.loadOverview(classroom.id, ids) { r ->
                when (r) {
                    is ApiResult.Success -> { overview = r.data; join.ok() }
                    is ApiResult.Failure -> soft(r.httpCode)
                }
            }
        }
    }

    override fun regenerateJoinCode(classroomId: String, callback: ResultCallback<String>) {
        insights.regenerateJoinCode(classroomId, callback)
    }

    private fun buildRows(students: List<StudentDto>, results: List<StudentResultDto>): List<StudentRowUi> {
        val byId = results.associateBy { it.studentId }
        return students.map { s ->
            val r = byId[s.id]
            StudentRowUi(
                id = s.id,
                name = s.name,
                initials = initialsOf(s.name),
                accuracy = r?.accuracy,
                completed = r?.completedLessons ?: 0,
                total = r?.totalLessons ?: 0,
                badge = r?.badge,
                mistakeCount = r?.mistakeCount ?: 0,
                mistakeTopic = r?.mistakeTopic,
                weakTopic = r?.weakTopic
            )
        }
    }

    private fun initialsOf(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }

    // editor

    private class DerivedLesson(val id: String, val topicId: String, val name: String, val questions: List<QuestionRowUi>)

    private fun lessonsFromQuestions(questions: List<QuestionDto>): List<DerivedLesson> {
        val byLesson = LinkedHashMap<String, MutableList<QuestionDto>>()
        questions.forEach { q ->
            val lessonId = q.lesson?.id ?: return@forEach
            if (q.topic?.id.isNullOrBlank()) return@forEach
            byLesson.getOrPut(lessonId) { mutableListOf() }.add(q)
        }
        return byLesson.map { (id, qs) ->
            DerivedLesson(
                id = id,
                topicId = qs.first().topic?.id.orEmpty(),
                name = qs.first().lesson?.name.orEmpty(),
                questions = qs.map { QuestionRowUi(it.id.orEmpty(), it.text.orEmpty(), it.type.orEmpty(), it.points) }
            )
        }
    }

    override fun loadEditor(classroom: ClassroomDto, callback: ResultCallback<List<EditorSubjectUi>>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }
        val subjectIds = classroom.subjectIds.orEmpty()
        if (subjectIds.isEmpty()) {
            callback(ApiResult.Success(emptyList()))
            return
        }

        api.subjects(subjectIds.joinToString(","), classroom.gradeNumber).go(fail) { env ->
            val subjects = env.subjects.orEmpty()
            if (subjects.isEmpty()) {
                callback(ApiResult.Success(emptyList()))
                return@go
            }
            val derived = HashMap<String, List<DerivedLesson>>()
            val join = Join(subjects.size, fail) {
                // Include empty teacher-created lessons: questions endpoint alone cannot list them.
                val topicIds=subjects.flatMap { subject ->
                    subject.grades.filter { it.gradeNumber == classroom.gradeNumber }
                        .flatMap { it.topics.map { t -> t.id } }
                }.distinct()
                if(topicIds.isEmpty()) {
                    callback(ApiResult.Success(subjects.map { sub -> buildSubject(sub,classroom.gradeNumber,derived[sub.id].orEmpty()) }))
                } else api.lessons(topicIds.joinToString(",")).go(fail) { envelope ->
                    val fromApi=envelope.lessons.orEmpty().filter { it.scope == "CLASSROOM" }
                    val merged=subjects.map { subject ->
                        val ids=subject.grades.filter { it.gradeNumber == classroom.gradeNumber }
                            .flatMap { it.topics.map { t -> t.id } }.toSet()
                        val existing=derived[subject.id].orEmpty().associateBy { it.id }
                        val lessons=fromApi.filter { it.topicId in ids }.map { l ->
                            val previous=existing[l.id]
                            DerivedLesson(l.id,l.topicId,l.name,previous?.questions.orEmpty())
                        }
                        buildSubject(subject,classroom.gradeNumber,lessons)
                    }
                    callback(ApiResult.Success(merged))
                }
            }
            subjects.forEach { subject ->
                api.questions(subject.id, classroom.gradeNumber, null).go(join::fail) { qEnv ->
                    derived[subject.id] = lessonsFromQuestions(qEnv.questions.orEmpty())
                    join.ok()
                }
            }
        }
    }

    private fun buildSubject(subject: SubjectDto, grade: Int, lessons: List<DerivedLesson>): EditorSubjectUi {
        val topicNames = subject.grades.orEmpty()
            .filter { it.gradeNumber == grade }
            .flatMap { it.topics.orEmpty() }
            .associate { it.id to it.name }
        return EditorSubjectUi(
            id = subject.id,
            name = subject.name,
            lessons = lessons.map { l ->
                EditorLessonUi(
                    id = l.id,
                    topicId = l.topicId,
                    topicName = topicNames[l.topicId].orEmpty(),
                    name = l.name,
                    questions = authoring.overlay(l.id, l.questions)
                )
            }
        )
    }

    override fun addQuestion(lessonId: String, draft: QuestionDraft, callback: ResultCallback<QuestionRowUi>) {
        val request = QuestionCreateRequest(
            lessonId = lessonId,
            type = draft.type,
            text = draft.text,
            points = draft.points,
            explanation = draft.explanation
        )
        authoring.create(request) { result ->
            when (result) {
                is ApiResult.Success -> {
                    val q = result.data
                    callback(ApiResult.Success(QuestionRowUi(q.id, q.text, q.type, q.points)))
                }
                is ApiResult.Failure -> callback(ApiResult.Failure(result.httpCode))
            }
        }
    }

    override fun deleteQuestion(questionId: String, callback: ResultCallback<Unit>) {
        authoring.delete(questionId, callback)
    }
}