package com.example.superznalci

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

interface StudentInsightsSource {
    fun loadInsights(
        classroomId: String, subjectId: String?, studentIds: List<String>,
        callback: ResultCallback<List<TeacherStudentInsightDto>>
    )
    fun loadDetail(classroomId: String, studentId: String, callback: ResultCallback<TeacherStudentDetailDto>)
    fun sendPraise(classroomId: String, studentId: String, message: String, callback: ResultCallback<Unit>)
}

class RetrofitStudentInsightsSource(private val api: ApiService) : StudentInsightsSource {

    override fun loadInsights(
        classroomId: String, subjectId: String?, studentIds: List<String>,
        callback: ResultCallback<List<TeacherStudentInsightDto>>
    ) {
        api.studentInsights(classroomId, subjectId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.insights.orEmpty()))
        }
    }

    override fun loadDetail(classroomId: String, studentId: String, callback: ResultCallback<TeacherStudentDetailDto>) {
        api.studentDetail(classroomId, studentId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it))
        }
    }

    override fun sendPraise(classroomId: String, studentId: String, message: String, callback: ResultCallback<Unit>) {
        api.sendPraise(classroomId, studentId, TeacherPraiseRequest(message))
            .go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(Unit)) }
    }
}

class MockStudentInsightsSource : StudentInsightsSource {

    override fun loadInsights(
        classroomId: String, subjectId: String?, studentIds: List<String>,
        callback: ResultCallback<List<TeacherStudentInsightDto>>
    ) {
        callback(ApiResult.Success(studentIds.mapIndexed { i, id -> insightFor(i, id, subjectId) }))
    }

    override fun loadDetail(classroomId: String, studentId: String, callback: ResultCallback<TeacherStudentDetailDto>) {
        val h = abs(studentId.hashCode())
        val now = System.currentTimeMillis()
        callback(
            ApiResult.Success(
                TeacherStudentDetailDto(
                    studentId = studentId,
                    assignedDone = 10 - h % 3,
                    assignedTotal = 10,
                    badgeTotal = 12,
                    badges = listOf(
                        TeacherBadgeDto("b1", "Mojster ulomkov", "Zlata stopnja"),
                        TeacherBadgeDto("b2", "Kahoot Prvak", "3× zmagovalec"),
                        TeacherBadgeDto("b3", "Ogenj znanja", "5+ dni serije")
                    ),
                    recentQuizzes = listOf(
                        TeacherQuizResultDto(
                            "q1", "Ulomki in seštevanje", 10, 10, 100,
                            iso(now - 3 * HOUR), mistakeCount = 0, xpGained = 120
                        ),
                        TeacherQuizResultDto(
                            "q2", "Kotne funkcije in trikotniki", 9, 10, 90,
                            iso(now - 26 * HOUR), mistakeCount = 1, mistakeNote = "kot γ", xpGained = 100
                        )
                    )
                )
            )
        )
    }

    override fun sendPraise(classroomId: String, studentId: String, message: String, callback: ResultCallback<Unit>) {
        callback(ApiResult.Success(Unit))
    }

    private fun insightFor(index: Int, id: String, subjectId: String?): TeacherStudentInsightDto {
        val h = abs(id.hashCode())
        val acc = (97 - index * 3 / 2).coerceAtLeast(45)
        val needsHelp = acc < 70
        return TeacherStudentInsightDto(
            studentId = id,
            xp = if (subjectId != null) 400 + acc * 10 + h % 50 else null,
            accuracy = acc,
            tasksDone = (10 - index / 6).coerceAtLeast(3),
            level = (9 - index / 4).coerceAtLeast(2),
            title = if (index % 3 == 0) "Mojster geometrije" else null,
            needsHelp = needsHelp,
            helpTopic = if (needsHelp) "ulomkih" else null
        )
    }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private companion object {
        const val HOUR = 3_600_000L
    }
}