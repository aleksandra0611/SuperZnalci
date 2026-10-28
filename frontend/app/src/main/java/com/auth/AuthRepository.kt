package com.example.superznalci


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