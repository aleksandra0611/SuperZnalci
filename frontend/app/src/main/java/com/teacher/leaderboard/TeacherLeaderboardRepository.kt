package com.example.superznalci

interface TeacherLeaderboardRepository {
    fun loadSubjects(classroom: ClassroomDto, callback: ResultCallback<List<TlSubjectUi>>)
    fun loadBoard(classroom: ClassroomDto, subjectId: String?, callback: ResultCallback<TlBoard>)
    fun loadDetail(classroomId: String, studentId: String, callback: ResultCallback<TeacherStudentDetailDto>)
    fun sendPraise(classroomId: String, studentId: String, message: String, callback: ResultCallback<Unit>)
}

class RetrofitTeacherLeaderboardRepository(
    private val api: ApiService,
    private val insights: StudentInsightsSource
) : TeacherLeaderboardRepository {

    // REAL: GET /api/subjects
    override fun loadSubjects(classroom: ClassroomDto, callback: ResultCallback<List<TlSubjectUi>>) {
        val ids = classroom.subjectIds.orEmpty()
        if (ids.isEmpty()) {
            callback(ApiResult.Success(emptyList()))
            return
        }
        api.subjects(ids.joinToString(","), classroom.gradeNumber).go({ callback(ApiResult.Failure(it)) }) { env ->
            callback(ApiResult.Success(env.subjects.orEmpty().map { TlSubjectUi(it.id, it.name) }))
        }
    }

    // REAL: GET /api/classrooms/{id}/students  +  MISSING: student-insights (soft failure)
    override fun loadBoard(classroom: ClassroomDto, subjectId: String?, callback: ResultCallback<TlBoard>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }
        api.classroomStudents(classroom.id).go(fail) { env ->
            val students = env.students.orEmpty()
            insights.loadInsights(classroom.id, subjectId, students.map { it.id }) { r ->
                when (r) {
                    is ApiResult.Success -> callback(ApiResult.Success(build(students, r.data, subjectId)))
                    is ApiResult.Failure ->
                        if (r.httpCode == 401) callback(ApiResult.Failure(401))
                        else callback(ApiResult.Success(build(students, emptyList(), subjectId)))
                }
            }
        }
    }

    override fun loadDetail(classroomId: String, studentId: String, callback: ResultCallback<TeacherStudentDetailDto>) {
        insights.loadDetail(classroomId, studentId, callback)
    }

    override fun sendPraise(classroomId: String, studentId: String, message: String, callback: ResultCallback<Unit>) {
        insights.sendPraise(classroomId, studentId, message, callback)
    }

    private fun build(students: List<StudentDto>, insights: List<TeacherStudentInsightDto>, subjectId: String?): TlBoard {
        val byId = insights.associateBy { it.studentId }

        val rows = students.map { s ->
            val i = byId[s.id]
            val accuracy = i?.accuracy
            TlStudentUi(
                id = s.id,
                name = s.name,
                initials = initialsOf(s.name),
                streak = s.streak,
                xp = if (subjectId != null) (i?.xp ?: 0) else s.totalXp,
                accuracy = accuracy,
                tasksDone = i?.tasksDone ?: 0,
                level = i?.level ?: 1,
                title = i?.title,
                needsHelp = (i?.needsHelp == true) || (accuracy != null && accuracy < 70),
                helpTopic = i?.helpTopic,
                rank = 0
            )
        }.sortedWith(compareByDescending<TlStudentUi> { it.xp }.thenBy { it.name })
            .mapIndexed { index, s -> s.copy(rank = index + 1) }

        val accuracies = rows.mapNotNull { it.accuracy }
        return TlBoard(
            students = rows,
            averageXp = if (rows.isEmpty()) 0 else rows.sumOf { it.xp } / rows.size,
            averageAccuracy = if (accuracies.isEmpty()) 0 else accuracies.average().toInt(),
            loadedAt = System.currentTimeMillis()
        )
    }

    private fun initialsOf(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }
}