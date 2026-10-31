package com.example.superznalci

import kotlin.math.abs

interface ClassroomInsightsSource {
    fun loadOverview(classroomId: String, studentIds: List<String>, callback: ResultCallback<ClassroomOverviewDto>)
    fun loadStudentResults(classroomId: String, studentIds: List<String>, callback: ResultCallback<List<StudentResultDto>>)
    fun regenerateJoinCode(classroomId: String, callback: ResultCallback<String>)
}

class RetrofitClassroomInsightsSource(private val api: ApiService) : ClassroomInsightsSource {

    override fun loadOverview(
        classroomId: String, studentIds: List<String>, callback: ResultCallback<ClassroomOverviewDto>
    ) {
        api.classroomOverview(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it))
        }
    }

    override fun loadStudentResults(
        classroomId: String, studentIds: List<String>, callback: ResultCallback<List<StudentResultDto>>
    ) {
        api.studentResults(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.results.orEmpty()))
        }
    }

    override fun regenerateJoinCode(classroomId: String, callback: ResultCallback<String>) {
        api.regenerateJoinCode(classroomId).go({ callback(ApiResult.Failure(it)) }) { env ->
            val code = env.joinCode
            if (code.isNullOrBlank()) callback(ApiResult.Failure(null)) else callback(ApiResult.Success(code))
        }
    }
}

class MockClassroomInsightsSource : ClassroomInsightsSource {

    private val codes = HashMap<String, String>()

    override fun loadStudentResults(
        classroomId: String, studentIds: List<String>, callback: ResultCallback<List<StudentResultDto>>
    ) {
        callback(ApiResult.Success(studentIds.mapIndexed { i, id -> resultFor(i, id) }))
    }

    override fun loadOverview(
        classroomId: String, studentIds: List<String>, callback: ResultCallback<ClassroomOverviewDto>
    ) {
        val results = studentIds.mapIndexed { i, id -> resultFor(i, id) }
        val accuracies = results.mapNotNull { it.accuracy }
        callback(
            ApiResult.Success(
                ClassroomOverviewDto(
                    submittedThisWeek = studentIds.size * 7 / 8,
                    averageAccuracy = if (accuracies.isEmpty()) 0 else accuracies.average().toInt(),
                    struggleTopics = if (studentIds.isEmpty()) emptyList() else listOf("deljenje", "ulomki")
                )
            )
        )
    }

    override fun regenerateJoinCode(classroomId: String, callback: ResultCallback<String>) {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = "ZN-" + (1..4).map { alphabet.random() }.joinToString("")
        codes[classroomId] = code
        callback(ApiResult.Success(code))
    }

    private fun resultFor(index: Int, id: String): StudentResultDto {
        val h = abs(id.hashCode())
        return when (index % 3) {
            0 -> StudentResultDto(id, 10, 10, 90 + h % 10, badge = "Mojster ulomkov")
            1 -> StudentResultDto(id, 8, 10, 70 + h % 18, mistakeCount = 1 + h % 3, mistakeTopic = "Množenje")
            else -> StudentResultDto(id, 5, 10, 40 + h % 28, weakTopic = "geometriji")
        }
    }
}


interface QuestionAuthoringSource {
    fun create(request: QuestionCreateRequest, callback: ResultCallback<QuestionRowDto>)
    fun delete(questionId: String, callback: ResultCallback<Unit>)

    fun overlay(lessonId: String, remote: List<QuestionRowUi>): List<QuestionRowUi>
}

class RetrofitQuestionAuthoringSource(private val api: ApiService) : QuestionAuthoringSource {

    override fun create(request: QuestionCreateRequest, callback: ResultCallback<QuestionRowDto>) {
        api.createQuestion(request).go({ callback(ApiResult.Failure(it)) }) { env ->
            val q = env.question
            if (q == null) callback(ApiResult.Failure(null)) else callback(ApiResult.Success(q))
        }
    }

    override fun delete(questionId: String, callback: ResultCallback<Unit>) {
        api.deleteQuestion(questionId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(Unit))
        }
    }

    override fun overlay(lessonId: String, remote: List<QuestionRowUi>) = remote
}

class MockQuestionAuthoringSource : QuestionAuthoringSource {

    private val created = mutableListOf<Pair<String, QuestionRowDto>>()
    private val deleted = mutableSetOf<String>()
    private var nextId = 1

    override fun create(request: QuestionCreateRequest, callback: ResultCallback<QuestionRowDto>) {
        val row = QuestionRowDto("mock-q-${nextId++}", request.text, request.type, request.points)
        created += request.lessonId to row
        callback(ApiResult.Success(row))
    }

    override fun delete(questionId: String, callback: ResultCallback<Unit>) {
        created.removeAll { it.second.id == questionId }
        deleted += questionId
        callback(ApiResult.Success(Unit))
    }

    override fun overlay(lessonId: String, remote: List<QuestionRowUi>): List<QuestionRowUi> {
        val mine = created.filter { it.first == lessonId }
            .map { QuestionRowUi(it.second.id, it.second.text, it.second.type, it.second.points) }
        return (remote + mine).filter { it.id !in deleted }
    }
}