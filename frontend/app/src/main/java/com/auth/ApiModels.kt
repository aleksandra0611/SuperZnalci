package com.example.superznalci

//defines the request/response contract

/**
 * Wire contract, shaped by what the existing backend models can store:
 *   User      { name, email, passwordHash, role, classroomId }
 *   Classroom { name, teacherId, joinCode, gradeNumber, subjectIds }
 *
 * POST /api/auth/login               LoginRequest               -> AuthResponse
 * POST /api/auth/register/student    StudentRegistrationRequest -> AuthResponse
 * POST /api/auth/register/teacher    TeacherRegistrationRequest -> TeacherRegistrationResponse
 *
 * Status codes the app understands:
 *   400 validation, 401 bad credentials, 404 unknown class code, 409 e-mail already used
 */

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
    val classroomName: String,
    val gradeNumber: Int,
    val department: String,     // not stored (it is inside classroomName); only needed to build the join code
    val subjects: List<String>  // Subject.name values; backend resolves them to Classroom.subjectIds
) {
    override fun toString() = "TeacherRegistrationRequest(email=$email, classroom=$classroomName)"
}

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val classroomId: String? = null   // only students have one
)

data class ClassroomDto(
    val id: String,
    val name: String,
    val joinCode: String,
    val gradeNumber: Int
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

fun String.cleanName(): String = trim().replace(Regex("\\s+"), " ")