package com.example.superznalci

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException


object ApiConfig {
    // Android emulator -> your computer. For a real phone use the computer's LAN IP
    const val BASE_URL = "http://10.0.2.2:3000/api/"
}

class UserEnvelope(val user: UserDto)
class ClassroomEnvelope(val classroom: ClassroomDto)
class SubjectsEnvelope(val subjects: List<SubjectDto>?)
class SchoolsEnvelope(val schools: List<SchoolDto>?)

interface ApiService {
    @POST("auth/login")
    fun login(@Body body: LoginRequest): Call<AuthResponse>

    @POST("auth/register/student")
    fun registerStudent(@Body body: StudentRegistrationRequest): Call<AuthResponse>

    @POST("auth/register/teacher")
    fun registerTeacher(@Body body: TeacherRegistrationRequest): Call<TeacherRegistrationResponse>

    @GET("users/me")
    fun me(): Call<UserEnvelope>

    @GET("classrooms/me")
    fun myClassroom(): Call<ClassroomEnvelope>

    @GET("subjects")
    fun subjects(
        @Query("ids") ids: String,
        @Query("gradeNumber") gradeNumber: Int?
    ): Call<SubjectsEnvelope>

    @GET("schools")
    fun schools(): Call<SchoolsEnvelope>
}

object ApiClient {
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            SessionStore.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

@Suppress("CAST_NEVER_SUCCEEDS", "USELESS_ELVIS")
private fun UserDto.safe(): UserDto = copy(badges = (badges as List<UserBadgeDto>?) ?: emptyList())

@Suppress("CAST_NEVER_SUCCEEDS", "USELESS_ELVIS")
private fun ClassroomDto.safe(): ClassroomDto = copy(subjectIds = (subjectIds as List<String>?) ?: emptyList())

@Suppress("CAST_NEVER_SUCCEEDS", "USELESS_ELVIS")
private fun SubjectDto.safe(): SubjectDto = copy(
    grades = ((grades as List<GradeDto>?) ?: emptyList()).map {
        it.copy(topics = (it.topics as List<TopicDto>?) ?: emptyList())
    }
)

private fun <T> Call<T>.go(onFail: (Int?) -> Unit, onOk: (T) -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            val body = response.body()
            if (response.isSuccessful && body != null) onOk(body) else onFail(response.code())
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            onFail(if (t is IOException) null else 500)
        }
    })
}

class RetrofitAuthRepository(private val api: ApiService) : AuthRepository {

    override fun login(request: LoginRequest, callback: ResultCallback<AuthResponse>) {
        api.login(request).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.copy(user = it.user.safe())))
        }
    }

    override fun registerStudent(request: StudentRegistrationRequest, callback: ResultCallback<AuthResponse>) {
        api.registerStudent(request).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.copy(user = it.user.safe())))
        }
    }

    override fun registerTeacher(
        request: TeacherRegistrationRequest,
        callback: ResultCallback<TeacherRegistrationResponse>
    ) {
        api.registerTeacher(request).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.copy(user = it.user.safe(), classroom = it.classroom.safe())))
        }
    }
}

class RetrofitProfileRepository(private val api: ApiService) : ProfileRepository {

    override fun loadProfileData(callback: ResultCallback<ProfileData>) {
        api.me().go({ callback(ApiResult.Failure(it)) }) { userEnvelope ->
            val user = userEnvelope.user.safe()

            if (user.classroomId == null) {
                finish(callback, user, null, emptyList())
                return@go
            }

            api.myClassroom().go(
                onFail = { code ->
                    if (code == 404) finish(callback, user, null, emptyList())
                    else callback(ApiResult.Failure(code))
                }
            ) { classroomEnvelope ->
                val classroom = classroomEnvelope.classroom.safe()

                if (classroom.subjectIds.isEmpty()) {
                    finish(callback, user, classroom, emptyList())
                    return@go
                }

                api.subjects(classroom.subjectIds.joinToString(","), classroom.gradeNumber).go(
                    onFail = { callback(ApiResult.Failure(it)) }
                ) { subjectsEnvelope ->
                    val subjects = subjectsEnvelope.subjects.orEmpty().map { it.safe() }
                    finish(callback, user, classroom, subjects)
                }
            }
        }
    }

    private fun finish(
        callback: ResultCallback<ProfileData>,
        user: UserDto,
        classroom: ClassroomDto?,
        subjects: List<SubjectDto>
    ) {
        SessionStore.token?.let { SessionStore.save(it, user) }
        callback(
            ApiResult.Success(
                ProfileData(
                    user = user,
                    classroom = classroom,
                    subjects = subjects,
                    lessons = emptyList(),        // MISSING: GET /api/lessons
                    attempts = emptyList(),       // MISSING: GET /api/attempts
                    badgeCatalog = emptyList()    // MISSING: GET /api/badges
                )
            )
        )
    }
}

class RetrofitSubjectsRepository(private val profile: ProfileRepository) : SubjectsRepository {

    override fun loadSubjectsData(callback: ResultCallback<SubjectsData>) {
        profile.loadProfileData { result ->
            when (result) {
                is ApiResult.Failure -> callback(result)
                is ApiResult.Success -> {
                    val p = result.data
                    callback(
                        ApiResult.Success(
                            SubjectsData(
                                user = p.user,
                                classroom = p.classroom,
                                classroomInfo = ClassroomInfoDto(),   // MISSING: teacherName, studentCount
                                subjects = p.subjects,
                                lessons = p.lessons,
                                assignments = emptyList(),            // MISSING: GET /api/assignments
                                attempts = p.attempts
                            )
                        )
                    )
                }
            }
        }
    }
}

// The backend has no forgot-password / reset-password endpoints yet
class UnavailablePasswordResetRepository : PasswordResetRepository {
    override fun requestReset(request: ForgotPasswordRequest, callback: ResultCallback<Unit>) =
        callback(ApiResult.Failure(404))

    override fun resetPassword(request: ResetPasswordRequest, callback: ResultCallback<Unit>) =
        callback(ApiResult.Failure(404))
}

object ServiceLocator {
    private val api = ApiClient.service

    val auth: AuthRepository = RetrofitAuthRepository(api)
    val profile: ProfileRepository = RetrofitProfileRepository(api)
    val subjects: SubjectsRepository = RetrofitSubjectsRepository(profile)
    val schools: SchoolsRepository = RetrofitSchoolsRepository(api)
    val passwordReset: PasswordResetRepository = UnavailablePasswordResetRepository()
}

// new repository (same file, so it can use the private go()):
interface SchoolsRepository {
    fun loadSchools(callback: ResultCallback<List<String>>)
}

class RetrofitSchoolsRepository(private val api: ApiService) : SchoolsRepository {
    override fun loadSchools(callback: ResultCallback<List<String>>) {
        api.schools().go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.schools.orEmpty().map { s -> s.name }))
        }
    }
}

// Was MockData.SCHOOLS. TODO: load from the backend (GET /api/schools) when it exists
object Schools {
    val LIST = listOf(
        "OŠ Primoža Trubarja, Ljubljana",
        "OŠ Ivana Cankarja, Maribor",
        "OŠ Franceta Prešerna, Kranj"
    )
}