package com.example.superznalci

import android.os.Handler
import android.os.Looper

//gives both fragments one interface for authentication

/**
 * HOW TO SWITCH TO THE BACKEND
 * 1. Write `RetrofitAuthRepository : AuthRepository` (same three methods, same DTOs from ApiModels.kt).
 *    Map the HTTP response to ApiResult.Success(body) or ApiResult.Failure(response.code());
 *    on IOException use ApiResult.Failure(null).
 * 2. In [ServiceLocator] change line:  `var auth: AuthRepository = RetrofitAuthRepository(...)`
 * 3. Delete [MockAuthRepository] and [MockData].
 * The fragments never talk to anything except [AuthRepository], so nothing else changes
 */

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
    var auth: AuthRepository = MockAuthRepository()
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

/** Everything that is fake is here*/
object MockData {
    val SCHOOLS = listOf(
        "OŠ Primoža Trubarja, Ljubljana",
        "OŠ Ivana Cankarja, Maribor",
        "OŠ Franceta Prešerna, Kranj"
    )

    // Demo accounts (password for both: geslo1234)
    const val DEMO_PASSWORD = "geslo1234"
    const val DEMO_TEACHER_EMAIL = "ucitelj@demo.si"
    const val DEMO_STUDENT_EMAIL = "ucenec@demo.si"
    const val DEMO_JOIN_CODE = "ZN-8AK42"
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
        val password: String,
        val role: String,
        val classroomId: String?
    ) {
        fun toDto() = UserDto(id, name, email, role, classroomId)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val users = mutableListOf<StoredUser>()
    private val classrooms = mutableListOf<ClassroomDto>()
    private var nextId = 1

    init {
        addUser("Maja Horvat", MockData.DEMO_TEACHER_EMAIL, MockData.DEMO_PASSWORD, Roles.TEACHER, null)
        val classroom = ClassroomDto(newId(), "8.A – ${MockData.SCHOOLS[0]}", MockData.DEMO_JOIN_CODE, 8)
        classrooms += classroom
        addUser("Luka Novak", MockData.DEMO_STUDENT_EMAIL, MockData.DEMO_PASSWORD, Roles.STUDENT, classroom.id)
    }

    override fun login(request: LoginRequest, callback: ResultCallback<AuthResponse>) {
        val user = users.find { it.email == request.email.trim().lowercase() }
        // One answer for "no such user", "wrong password" and "wrong role card" (like the backend should)
        val result: ApiResult<AuthResponse> = if (user == null || user.password != request.password || user.role != request.role) {
            ApiResult.Failure(401)
        } else {
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
                val classroom = ClassroomDto(
                    id = newId(),
                    name = request.classroomName,
                    joinCode = uniqueJoinCode(request.gradeNumber, request.department),
                    gradeNumber = request.gradeNumber
                )
                classrooms += classroom
                ApiResult.Success(TeacherRegistrationResponse(tokenFor(user), user.toDto(), classroom))
            }
        }
        respond(result, callback)
    }

    // ---- helpers

    private fun addUser(name: String, email: String, password: String, role: String, classroomId: String?): StoredUser {
        val user = StoredUser(newId(), name, email.trim().lowercase(), password, role, classroomId)
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
    }
}