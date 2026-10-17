package com.example.superznalci

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Request/response contract. Every response class mirrors a backend model:
//   UserDto -> User, ClassroomDto -> Classroom, SubjectDto/GradeDto/TopicDto -> Subject,
//   LessonSummaryDto -> Lesson (without questions), AttemptDto -> Attempt, BadgeDto -> Badge
//
// POST /api/auth/login               LoginRequest               -> AuthResponse
// POST /api/auth/register/student    StudentRegistrationRequest -> AuthResponse
// POST /api/auth/register/teacher    TeacherRegistrationRequest -> TeacherRegistrationResponse
//
// Status codes the app understands:
//   400 validation, 401 bad credentials, 404 unknown class code, 409 e-mail already used
//
// WHEN THE REAL BACKEND ARRIVES (two things the JSON will do differently from the mock):
//   1. Mongo sends "_id", these classes use "id". Either map it on the server or annotate the
//      fields with @SerializedName(value = "id", alternate = ["_id"]) once Gson is added.
//   2. Mongo dates are ISO-8601 strings ("2026-01-01T00:00:00.000Z"), so all dates here are
//      Strings. Convert them with IsoDate, never declare them as Long.

// toString() is overridden so passwords never end up in Logcat

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
    val joinCode: String        // backend finds the Classroom by joinCode -> User.classroomId
) {
    override fun toString() = "StudentRegistrationRequest(email=$email)"
}

data class TeacherRegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
    val classroomName: String,  // build it with ClassroomNaming.build(...)
    val gradeNumber: Int,
    val department: String,     // not stored (it is inside classroomName); only needed to build the join code
    val subjects: List<String>  // Subject.name values; backend resolves them to Classroom.subjectIds
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
    val badges: List<UserBadgeDto> = emptyList()
)

data class ClassroomDto(
    val id: String,
    val name: String,
    val joinCode: String,
    val gradeNumber: Int,
    val subjectIds: List<String> = emptyList()
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
    val isPublic: Boolean = true,
    val questionCount: Int = 0,
    val totalPoints: Int = 0,
    val description: String? = null
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
    val answers: List<AttemptAnswerDto> = emptyList()
)

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

fun String.cleanName(): String = trim().replace(Regex("\\s+"), " ")