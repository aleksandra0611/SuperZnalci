package com.example.superznalci

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LoginRequest(
    val email: String,
    val password: String,
    val role: String
) {
    override fun toString() = "LoginRequest(email=$email, role=$role)"
}

data class StudentRegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
    val joinCode: String
) {
    override fun toString() = "StudentRegistrationRequest(email=$email)"
}

data class TeacherRegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
    val classroomName: String,
    val gradeNumber: Int,
    val department: String,
    val subjects: List<String>
) {
    override fun toString() = "TeacherRegistrationRequest(email=$email, classroom=$classroomName)"
}

data class UserBadgeDto(
    val badgeId: String,
    val awardedAt: String? = null // ISO-8601
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val classroomId: String? = null,   // only students have one
    val avatar: String? = null,
    val totalXp: Int = 0,
    val streak: Int = 0,
    val registeredAt: String? = null,  // ISO-8601
    val lastActiveAt: String? = null,  // ISO-8601, null until the first login
    val lastStreakAt: String? = null,
    val badges: List<UserBadgeDto> = emptyList()
)

data class SchoolDto(val id: String, val name: String)

data class ClassroomDto(
    val id: String,
    val name: String,
    val joinCode: String,
    val gradeNumber: Int,
    val subjectIds: List<String> = emptyList(),
    val teacherName: String? = null,
    val studentCount: Int = 0
)

// Response of login and student registration
data class AuthResponse(
    val token: String,
    val user: UserDto
)

// Response of teacher registration
data class TeacherRegistrationResponse(
    val token: String,
    val user: UserDto,
    val classroom: ClassroomDto
)

// content / progress models (used by the profile screen)

data class BadgeDto(
    val id: String,
    val name: String,
    val condition: String    // npr. 7-day streak
)

data class TopicDto(
    val id: String,
    val name: String,
    val description: String? = null
)

data class GradeDto(
    val gradeNumber: Int,
    val topics: List<TopicDto> = emptyList()
)

data class SubjectDto(
    val id: String,
    val name: String,
    val icon: String? = null,
    val grades: List<GradeDto> = emptyList()
)

// A lesson without its questions: enough to count and name lessons
data class LessonSummaryDto(
    val id: String,
    val topicId: String,
    val name: String,
    val order: Int,
    val scope: String? = null,
    val isPublic: Boolean = true,
    val questionCount: Int = 0,
    val totalPoints: Int = 0,
    val description: String? = null,
    val status: String? = null,
    val stars: Int = 0,
    val bestAccuracy: Double? = null
)

data class AttemptAnswerDto(
    val questionId: String,
    val isCorrect: Boolean
)

data class AttemptDto(
    val id: String,
    val studentId: String,
    val lessonId: String,
    val points: Int,
    val accuracy: Double, 
    val date: String,                  // ISO-8601
    val finishedAt: String? = null,
    val answers: List<AttemptAnswerDto> = emptyList()
)

class AttemptsEnvelope(val attempts: List<AttemptDto>?)

/**
 * Classroom.name is the only place where grade, department and school can be kept,
 * so registration writes it and the profile reads it back. Both use this one class.
 * pr. 8.A – OŠ Primoža Trubarja, Ljubljana
 */
object ClassroomNaming {
    private const val SEPARATOR = " – "

    fun build(gradeNumber: Int, department: String, school: String) =
        "$gradeNumber.$department$SEPARATOR$school"

    // Returns (class, school). A name without the separator gives (name, "") 
    fun parse(name: String): Pair<String, String> {
        val index = name.indexOf(SEPARATOR)
        return if (index < 0) {
            name.trim() to ""
        } else {
            name.substring(0, index).trim() to name.substring(index + SEPARATOR.length).trim()
        }
    }
}

// Converts between epoch milliseconds and the ISO-8601 strings the backend sends
object IsoDate {
    private const val OUT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private const val IN = "yyyy-MM-dd'T'HH:mm:ss"

    fun format(millis: Long): String =
        SimpleDateFormat(OUT, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    // Accepts "2026-01-01T00:00:00.000Z" and "2026-01-01T00:00:00Z"
    fun parse(iso: String?): Long? {
        if (iso == null || iso.length < 19) return null
        return try {
            SimpleDateFormat(IN, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso.substring(0, 19))?.time
        } catch (e: ParseException) {
            null
        }
    }
}

data class StatisticsSummaryDto(
    val totalXp: Int = 0,
    val streak: Int = 0,
    val completedQuizzes: Int = 0
)

data class SubjectStatDto(
    val subjectId: String = "",
    val subjectName: String = "",
    val totalPoints: Int = 0,
    val completedQuizzes: Int = 0,
    val averageAccuracy: Double = 0.0
)

data class StatisticsDto(
    val summary: StatisticsSummaryDto = StatisticsSummaryDto(),
    val subjectStatistics: List<SubjectStatDto> = emptyList()
)

interface StatsRepository {
    fun load(callback: ResultCallback<StatisticsDto>)
}

class RetrofitStatsRepository(private val api: ApiService) : StatsRepository {
    override fun load(callback: ResultCallback<StatisticsDto>) {
        api.myStatistics().go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(it)) }
    }
}

fun String.cleanName(): String = trim().replace(Regex("\\s+"), " ")

fun UserDto.streakAnchorMillis(): Long? = IsoDate.parse(lastStreakAt ?: lastActiveAt)

// GET /api/badges
class BadgeApiDto(
    val id: String? = null,
    val _id: String? = null,
    val name: String? = null,
    val condition: String? = null,
    val description: String? = null
)

class BadgesEnvelope(val badges: List<BadgeApiDto>?)

fun BadgeApiDto.toBadgeDto(): BadgeDto? {
    val key = id ?: _id ?: return null
    return BadgeDto(key, name.orEmpty(), condition ?: description.orEmpty())
}