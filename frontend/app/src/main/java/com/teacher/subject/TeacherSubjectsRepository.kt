package com.example.superznalci

interface TeacherSubjectsRepository {
    fun loadClasses(callback: ResultCallback<List<TeacherClassTab>>)
    fun loadClassSubjects(classroom: ClassroomDto, callback: ResultCallback<List<SubjectUi>>)

    fun setLessonAvailability(
        classroomId: String,
        lessonId: String,
        availableFromIso: String?,
        callback: ResultCallback<Unit>
    )

    fun setTopicVisible(
        classroomId: String,
        topicId: String,
        visible: Boolean,
        callback: ResultCallback<Unit>
    )
}

class RetrofitTeacherSubjectsRepository(
    private val api: ApiService,
    private val content: ClassroomContentSource,
    private val topicSettings: TopicSettingsSource
) : TeacherSubjectsRepository {

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

    override fun loadClasses(callback: ResultCallback<List<TeacherClassTab>>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }
        api.myClassrooms().go(fail) { env ->
            val classrooms = env.classrooms.orEmpty()
            if (classrooms.isEmpty()) {
                callback(ApiResult.Success(emptyList()))
                return@go
            }
            val counts = HashMap<String, Int>()
            val join = Join(classrooms.size, fail) {
                studentCounts.putAll(counts)
                callback(ApiResult.Success(classrooms.map { TeacherClassTab(it, counts[it.id] ?: 0) }))
            }
            classrooms.forEach { c ->
                api.classroomStudents(c.id).go(join::fail) {
                    counts[c.id] = it.students.orEmpty().size
                    join.ok()
                }
            }
        }
    }

    private class DerivedLesson(val id: String, val topicId: String, val name: String, val questionCount: Int)

    private fun lessonsFromQuestions(questions: List<QuestionDto>): List<DerivedLesson> {
        val byLesson = LinkedHashMap<String, MutableList<QuestionDto>>()
        questions.forEach { q ->
            val lessonId = q.lesson?.id ?: return@forEach
            if (q.topic?.id.isNullOrBlank()) return@forEach
            byLesson.getOrPut(lessonId) { mutableListOf() }.add(q)
        }
        return byLesson.map { (id, qs) ->
            DerivedLesson(id, qs.first().topic?.id.orEmpty(), qs.first().lesson?.name.orEmpty(), qs.size)
        }
    }

    override fun loadClassSubjects(classroom: ClassroomDto, callback: ResultCallback<List<SubjectUi>>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }
        val subjectIds = classroom.subjectIds.orEmpty()
        if (subjectIds.isEmpty()) {
            callback(ApiResult.Success(emptyList()))
            return
        }

        api.subjects(subjectIds.joinToString(","), classroom.gradeNumber).go(fail) { subjectsEnv ->
            val subjects = subjectsEnv.subjects.orEmpty()
            val lessons = ArrayList<DerivedLesson>()

            val questionsJoin = Join(subjects.size, fail) {
                loadExtras(classroom, subjects, lessons, callback)
            }
            if (subjects.isEmpty()) {
                callback(ApiResult.Success(emptyList()))
                return@go
            }
            subjects.forEach { subject ->
                api.questions(subject.id, classroom.gradeNumber, null).go(questionsJoin::fail) { env ->
                    lessons += lessonsFromQuestions(env.questions.orEmpty())
                    questionsJoin.ok()
                }
            }
        }
    }

    private fun loadExtras(
        classroom: ClassroomDto,
        subjects: List<SubjectDto>,
        lessons: List<DerivedLesson>,
        callback: ResultCallback<List<SubjectUi>>
    ) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }
        var stats: List<LessonStatDto> = emptyList()
        var assignments: List<ClassroomAssignmentDto> = emptyList()
        var settings: List<TopicSettingDto> = emptyList()

        val join = Join(3, fail) {
            callback(ApiResult.Success(build(classroom, subjects, lessons, stats, assignments, settings)))
        }
        val soft: (Int?) -> Unit = { if (it == 401) join.fail(it) else join.ok() }

        content.loadAssignments(classroom.id, lessons.map { it.id }) { r ->
            when (r) {
                is ApiResult.Success -> { assignments = r.data; join.ok() }
                is ApiResult.Failure -> soft(r.httpCode)
            }
            content.loadLessonStats(classroom.id, studentCountOf(classroom)) { s ->
                when (s) {
                    is ApiResult.Success -> { stats = s.data; join.ok() }
                    is ApiResult.Failure -> soft(s.httpCode)
                }
            }
        }
        topicSettings.load(classroom.id) { r ->
            when (r) {
                is ApiResult.Success -> { settings = r.data; join.ok() }
                is ApiResult.Failure -> soft(r.httpCode)
            }
        }
    }

    private val studentCounts = HashMap<String, Int>()
    private fun studentCountOf(c: ClassroomDto) = studentCounts[c.id] ?: 0

    override fun setLessonAvailability(
        classroomId: String,
        lessonId: String,
        availableFromIso: String?,
        callback: ResultCallback<Unit>
    ) {
        content.putAssignment(classroomId, lessonId, availableFromIso) { result ->
            when (result) {
                is ApiResult.Success -> callback(ApiResult.Success(Unit))
                is ApiResult.Failure -> callback(ApiResult.Failure(result.httpCode))
            }
        }
    }

    override fun setTopicVisible(
        classroomId: String,
        topicId: String,
        visible: Boolean,
        callback: ResultCallback<Unit>
    ) {
        topicSettings.setVisible(classroomId, topicId, visible) { result ->
            when (result) {
                is ApiResult.Success -> callback(ApiResult.Success(Unit))
                is ApiResult.Failure -> callback(ApiResult.Failure(result.httpCode))
            }
        }
    }

    private fun build(
        classroom: ClassroomDto,
        subjects: List<SubjectDto>,
        lessons: List<DerivedLesson>,
        stats: List<LessonStatDto>,
        assignments: List<ClassroomAssignmentDto>,
        settings: List<TopicSettingDto>,
        now: Long = System.currentTimeMillis()
    ): List<SubjectUi> {
        val statByLesson = stats.associateBy { it.lessonId }
        val assignmentByLesson = assignments.associateBy { it.lessonId }
        val hiddenTopics = settings.filter { !it.visibleToStudents }.map { it.topicId }.toSet()
        val lessonsByTopic = lessons.groupBy { it.topicId }

        return subjects.map { subject ->
            val topics = subject.grades.orEmpty()
                .filter { it.gradeNumber == classroom.gradeNumber }
                .flatMap { it.topics.orEmpty() }

            val modules = topics.mapIndexed { index, topic ->
                val rows = lessonsByTopic[topic.id].orEmpty()
                    .mapIndexed { i, l ->
                        val assignment = assignmentByLesson[l.id]
                        val from = assignment?.availableFrom
                        val fromMillis = IsoDate.parse(from)
                        val status = when {
                            assignment == null -> LessonStatus.DRAFT
                            fromMillis != null && fromMillis > now -> LessonStatus.SCHEDULED
                            else -> LessonStatus.PUBLISHED
                        }
                        val stat = statByLesson[l.id]
                        LessonRowUi(
                            id = l.id,
                            number = i + 1,
                            name = l.name,
                            questionCount = l.questionCount,
                            status = status,
                            availableFrom = from,
                            completionPercent = stat?.completionPercent,
                            averageAccuracy = stat?.averageAccuracy
                        )
                    }
                ModuleUi(topic.id, index + 1, topic.name, rows, topic.id !in hiddenTopics)
            }
            SubjectUi(subject.id, subject.name, classroom.gradeNumber, modules)
        }
    }
}