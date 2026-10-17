package com.example.superznalci

import android.os.Handler
import android.os.Looper

// Gives the fragments one interface for authentication.
//
// HOW TO SWITCH TO THE BACKEND
// 1. Write `RetrofitAuthRepository : AuthRepository` and `RetrofitProfileRepository : ProfileRepository`
//    (same methods, same DTOs from ApiModels.kt). Map the HTTP response to ApiResult.Success(body)
//    or ApiResult.Failure(response.code()); on IOException use ApiResult.Failure(null).
// 2. In [ServiceLocator] replace the two mock lines.
// 3. Delete MockAuthRepository, MockProfileRepository and MockData.
// The fragments never talk to anything except the repository interfaces, so nothing else changes.

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    data class Failure(val httpCode: Int?) : ApiResult<Nothing>()
}

// Always called on the main thread
typealias ResultCallback<T> = (ApiResult<T>) -> Unit

interface AuthRepository {
    fun login(request: LoginRequest, callback: ResultCallback<AuthResponse>)
    fun registerStudent(request: StudentRegistrationRequest, callback: ResultCallback<AuthResponse>)
    fun registerTeacher(request: TeacherRegistrationRequest, callback: ResultCallback<TeacherRegistrationResponse>)
}

//gives the UI a single place to obtain the repository
object ServiceLocator {
    private val mockAuth = MockAuthRepository()
    private val mockProfile = MockProfileRepository(mockAuth)

    var auth: AuthRepository = mockAuth
    var profile: ProfileRepository = mockProfile
    var subjects: SubjectsRepository = MockSubjectsRepository(mockAuth, mockProfile)
    var passwordReset: PasswordResetRepository = MockPasswordResetRepository(mockAuth)
}

// Current login. In memory for now later persist the token 
object SessionStore {
    var token: String? = null
        private set
    var user: UserDto? = null
        private set

    fun save(token: String, user: UserDto) {
        this.token = token
        this.user = user
    }

    fun clear() {
        token = null
        user = null
    }
}

/**
 * In-memory fake of the backend. It behaves like the real one will:
 * same status codes, same validation, unique e-mails, join codes must exist.
 * Data is lost when the app process dies.
 */
class MockAuthRepository : AuthRepository {

    private class StoredUser(
        val id: String,
        val name: String,
        val email: String,
        var password: String,
        val role: String,
        val classroomId: String?,
        val totalXp: Int,
        val streak: Int,
        val badges: List<UserBadgeDto>,
        val registeredAt: Long,
        var lastActiveAt: Long? = null  // null until the first login, like User.lastActiveAt
    ) {
        fun toDto() = UserDto(
            id = id,
            name = name,
            email = email,
            role = role,
            classroomId = classroomId,
            totalXp = totalXp,
            streak = streak,
            registeredAt = IsoDate.format(registeredAt),
            lastActiveAt = lastActiveAt?.let { IsoDate.format(it) },
            badges = badges
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val users = mutableListOf<StoredUser>()
    private val classrooms = mutableListOf<ClassroomDto>()
    private var nextId = 1

    init {
        val now = System.currentTimeMillis()

        addUser("Maja Horvat", MockData.DEMO_TEACHER_EMAIL, MockData.DEMO_PASSWORD, Roles.TEACHER, null)

        val classroom = ClassroomDto(
            id = newId(),
            name = ClassroomNaming.build(8, "A", MockData.SCHOOLS[0]),
            joinCode = MockData.DEMO_JOIN_CODE,
            gradeNumber = 8,
            subjectIds = listOf(MockData.subjectId("math"), MockData.subjectId("physics"))
        )
        classrooms += classroom

        addUser(
            name = "Luka Novak",
            email = MockData.DEMO_STUDENT_EMAIL,
            password = MockData.DEMO_PASSWORD,
            role = Roles.STUDENT,
            classroomId = classroom.id,
            totalXp = 2450,
            streak = 5,
            badges = listOf(
                UserBadgeDto("badge-1", IsoDate.format(now - 20 * DAY_MS)),
                UserBadgeDto("badge-2", IsoDate.format(now - 10 * DAY_MS)),
                UserBadgeDto("badge-3", IsoDate.format(now - 3 * DAY_MS))
            )
        )
    }

    override fun login(request: LoginRequest, callback: ResultCallback<AuthResponse>) {
        val user = users.find { it.email == request.email.trim().lowercase() }
        // One answer for "no such user", "wrong password" and "wrong role card" (like the backend should)
        val result: ApiResult<AuthResponse> =
            if (user == null || user.password != request.password || user.role != request.role) {
                ApiResult.Failure(401)
            } else {
                user.lastActiveAt = System.currentTimeMillis()
                ApiResult.Success(AuthResponse(tokenFor(user), user.toDto()))
            }
        respond(result, callback)
    }

    override fun registerStudent(request: StudentRegistrationRequest, callback: ResultCallback<AuthResponse>) {
        val code = ClassCode.normalize(request.joinCode)
        val result: ApiResult<AuthResponse> = when {
            !ClassCode.isWellFormed(code) -> ApiResult.Failure(400)
            classrooms.none { it.joinCode == code } -> ApiResult.Failure(404)
            emailTaken(request.email) -> ApiResult.Failure(409)
            else -> {
                val classroom = classrooms.first { it.joinCode == code }
                val user = addUser(request.name, request.email, request.password, Roles.STUDENT, classroom.id)
                ApiResult.Success(AuthResponse(tokenFor(user), user.toDto()))
            }
        }
        respond(result, callback)
    }

    override fun registerTeacher(
        request: TeacherRegistrationRequest,
        callback: ResultCallback<TeacherRegistrationResponse>
    ) {
        val result: ApiResult<TeacherRegistrationResponse> = when {
            emailTaken(request.email) -> ApiResult.Failure(409)
            else -> {
                val user = addUser(request.name, request.email, request.password, Roles.TEACHER, null)

                // Same as the real backend: subject names -> Subject ids (unknown names are skipped)
                val subjectIds = request.subjects
                    .mapNotNull { wanted ->
                        MockData.SUBJECTS.find { it.name.equals(wanted.trim(), ignoreCase = true) }?.id
                    }
                    .distinct()

                val classroom = ClassroomDto(
                    id = newId(),
                    name = request.classroomName,
                    joinCode = uniqueJoinCode(request.gradeNumber, request.department),
                    gradeNumber = request.gradeNumber,
                    subjectIds = subjectIds
                )
                classrooms += classroom
                ApiResult.Success(TeacherRegistrationResponse(tokenFor(user), user.toDto(), classroom))
            }
        }
        respond(result, callback)
    }

    // lookups used by MockProfileRepository (the real backend answers these with its own endpoints)

    fun findUser(id: String): UserDto? = users.find { it.id == id }?.toDto()

    fun userIdByEmail(email: String): String? = users.find { it.email == email.lowercase() }?.id

    fun findClassroom(id: String?): ClassroomDto? {
        if (id.isNullOrBlank()) return null
        return classrooms.find { it.id == id }
    }

       fun updatePassword(email: String, newPassword: String): Boolean {
       val user = users.find { it.email == email.trim().lowercase() } ?: return false
       user.password = newPassword
       return true
   }

    // ---- helpers

    private fun addUser(
        name: String,
        email: String,
        password: String,
        role: String,
        classroomId: String?,
        totalXp: Int = 0,
        streak: Int = 0,
        badges: List<UserBadgeDto> = emptyList()
    ): StoredUser {
        val user = StoredUser(
            id = newId(),
            name = name,
            email = email.trim().lowercase(),
            password = password,
            role = role,
            classroomId = classroomId,
            totalXp = totalXp,
            streak = streak,
            badges = badges,
            registeredAt = System.currentTimeMillis()
        )
        users += user
        return user
    }

    private fun emailTaken(email: String) = users.any { it.email == email.trim().lowercase() }

    private fun newId() = "mock-${nextId++}"

    private fun tokenFor(user: StoredUser) = "mock-token-${user.id}"

    private fun uniqueJoinCode(grade: Int, department: String): String {
        while (true) {
            val code = ClassCode.generatePreview(grade, department)
            if (classrooms.none { it.joinCode == code }) return code
        }
    }

    // Fake network delay so loading states can be seen and tested
    private fun <T> respond(result: ApiResult<T>, callback: ResultCallback<T>) {
        handler.postDelayed({ callback(result) }, DELAY_MS)
    }

    private companion object {
        const val DELAY_MS = 700L
        const val DAY_MS = 86_400_000L
    }
}