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
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import retrofit2.http.Path
import retrofit2.http.DELETE
import retrofit2.http.PUT


object ApiConfig {
    // Android emulator -> your computer. For a real phone use the computer's LAN IP
    const val BASE_URL = "http://10.0.2.2:3000/api/"
    const val IMAGE_BASE_URL = "http://10.0.2.2:3000/" 
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

    // scope: null = SHARED + the teacher's own lessons, "PRACTICE" = Vadnica lessons
    @GET("questions")
    fun questions(
        @Query("subjectId") subjectId: String,
        @Query("gradeNumber") gradeNumber: Int?,
        @Query("type") type: String?,
        @Query("scope") scope: String? = null
    ): Call<QuestionsEnvelope>

    @GET("lessons")
    fun lessons(@Query("topicIds") topicIds: String): Call<LessonsEnvelope>

    @GET("students/me/statistics")
    fun myStatistics(): Call<StatisticsDto>

    // --- attempt API: solving a lesson, every answer is checked on the server ---
    @POST("lessons/{lessonId}/attempts")
    fun startAttempt(@Path("lessonId") lessonId: String): Call<StartAttemptResponse>

    @POST("attempts/{attemptId}/answers")
    fun submitAnswer(@Path("attemptId") attemptId: String, @Body body: AnswerRequest): Call<AnswerResultDto>

    @POST("attempts/{attemptId}/finish")
    fun finishAttempt(@Path("attemptId") attemptId: String): Call<FinishResultDto>

    @GET("attempts")
    fun attempts(): Call<AttemptsEnvelope>

    @GET("attempts/{id}/mistakes")
    fun attemptMistakes(@Path("id") attemptId: String): Call<MistakesDto>

    // --- Vadnica (practice content, no classroom needed) ---
    @GET("practice/subjects")
    fun practiceSubjects(@Query("gradeNumber") gradeNumber: Int): Call<PracticeSubjectsEnvelope>

    @GET("practice/subjects/{id}")
    fun practiceSubject(
        @Path("id") subjectId: String,
        @Query("gradeNumber") gradeNumber: Int
    ): Call<PracticeSubjectDetailDto>

    @GET("classrooms/{id}/leaderboard")
    fun leaderboard(@Path("id") classroomId: String, @Query("period") period: String): Call<LeaderboardEnvelope>

    // --- teacher profile: REAL endpoints ---
    @GET("classrooms")
    fun myClassrooms(): Call<ClassroomsEnvelope>

    @GET("classrooms/{id}/students")
    fun classroomStudents(@Path("id") classroomId: String): Call<StudentsEnvelope>

    // --- teacher profile: MISSING on the backend (mocked while TeacherConfig.USE_REAL_EXTRAS = false) ---
    @GET("teachers/me/overview")
    fun teacherExtras(): Call<TeacherExtrasDto>

    @PATCH("users/me/settings")
    fun updateSettings(@Body body: TeacherSettingsDto): Call<TeacherSettingsDto>

    // --- teacher subjects: MISSING on the backend (mocked while TeacherSubjectsConfig.USE_REAL_* = false) ---
    @GET("classrooms/{id}/lesson-stats")
    fun lessonStats(@Path("id") classroomId: String): Call<LessonStatsEnvelope>

    @GET("classrooms/{id}/assignments")
    fun assignments(@Path("id") classroomId: String): Call<AssignmentsEnvelope>

    @PUT("classrooms/{id}/assignments/{lessonId}")
    fun putAssignment(
        @Path("id") classroomId: String,
        @Path("lessonId") lessonId: String,
        @Body body: AssignmentRequest
    ): Call<AssignmentEnvelope>

    @GET("classrooms/{id}/topic-settings")
    fun topicSettings(@Path("id") classroomId: String): Call<TopicSettingsEnvelope>

    @PUT("classrooms/{id}/topic-settings/{topicId}")
    fun putTopicSetting(
        @Path("id") classroomId: String,
        @Path("topicId") topicId: String,
        @Body body: TopicSettingRequest
    ): Call<TopicSettingEnvelope>

    @GET("classrooms/{id}/overview")
    fun classroomOverview(@Path("id") classroomId: String): Call<ClassroomOverviewDto>

    @GET("classrooms/{id}/student-results")
    fun studentResults(@Path("id") classroomId: String): Call<StudentResultsEnvelope>

    @POST("classrooms/{id}/join-code")
    fun regenerateJoinCode(@Path("id") classroomId: String): Call<JoinCodeEnvelope>

    @POST("questions")
    fun createQuestion(@Body body: QuestionCreateRequest): Call<QuestionCreatedEnvelope>

    @DELETE("questions/{id}")
    fun deleteQuestion(@Path("id") questionId: String): Call<Unit>

    @GET("classrooms/{id}/student-insights")
    fun studentInsights(
        @Path("id") classroomId: String,
        @Query("subjectId") subjectId: String?
    ): Call<TeacherInsightsEnvelope>

    @GET("classrooms/{id}/students/{studentId}/detail")
    fun studentDetail(
        @Path("id") classroomId: String,
        @Path("studentId") studentId: String
    ): Call<TeacherStudentDetailDto>

    @POST("classrooms/{id}/students/{studentId}/praise")
    fun sendPraise(
        @Path("id") classroomId: String,
        @Path("studentId") studentId: String,
        @Body body: TeacherPraiseRequest
    ): Call<Unit>

    // --- teacher learn: MISSING on the backend (mocked while TeacherLearnConfig.USE_REAL_EXTRAS = false) ---
    @GET("classrooms/{id}/overview")
    fun classOverview(@Path("id") classroomId: String): Call<ClassOverviewDto>

    @GET("classrooms/{id}/struggling-students")
    fun strugglingStudents(@Path("id") classroomId: String): Call<StrugglingEnvelope>

    @POST("classrooms/{id}/reminders")
    fun sendReminder(@Path("id") classroomId: String, @Body body: ReminderRequest): Call<ReminderResponse>

    @GET("badges")
    fun badges(): Call<BadgesEnvelope>
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

internal fun <T> Call<T>.go(onFail: (Int?) -> Unit, onOk: (T) -> Unit) {
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            val body = response.body()
            if (response.isSuccessful && body != null) {
                onOk(body)
            } else {
                onFail(response.code())
            }
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

class RetrofitProfileRepository(
    private val api: ApiService,
    private val lessonsRepo: LessonsRepository
) : ProfileRepository {

    override fun loadProfileData(callback: ResultCallback<ProfileData>) {
        api.me().go({ callback(ApiResult.Failure(it)) }) { userEnvelope ->
            val user = userEnvelope.user.safe()

            if (user.classroomId == null) {
                finish(callback, user, null, emptyList(), emptyList())
                return@go
            }

            api.myClassroom().go(
                onFail = { code ->
                    if (code == 404) finish(callback, user, null, emptyList(), emptyList())
                    else callback(ApiResult.Failure(code))
                }
            ) { classroomEnvelope ->
                val classroom = classroomEnvelope.classroom.safe()

                if (classroom.subjectIds.isEmpty()) {
                    finish(callback, user, classroom, emptyList(), emptyList())
                    return@go
                }

                api.subjects(classroom.subjectIds.joinToString(","), classroom.gradeNumber).go(
                    onFail = { callback(ApiResult.Failure(it)) }
                ) { subjectsEnvelope ->
                    val subjects = subjectsEnvelope.subjects.orEmpty().map { it.safe() }
                    lessonsRepo.loadLessons(subjects, classroom.gradeNumber) { result ->
                        when (result) {
                            is ApiResult.Success -> {
                                finish(callback, user, classroom, subjects, result.data)
                            }
                            is ApiResult.Failure -> {
                                if (result.httpCode == 401) callback(ApiResult.Failure(401))
                                else finish(callback, user, classroom, subjects, emptyList())
                            }
                        }
                    }
                }
            }
        }
    }

        private fun finish(
        callback: ResultCallback<ProfileData>,
        user: UserDto,
        classroom: ClassroomDto?,
        subjects: List<SubjectDto>,
        lessons: List<LessonSummaryDto>
    ) {
        SessionStore.token?.let { SessionStore.save(it, user) }

        api.attempts().go(
            onFail = { code ->
                if (code == 401) callback(ApiResult.Failure(401))
                else loadBadges(callback, user, classroom, subjects, lessons, emptyList())
            }
        ) { envelope ->
            val finished = envelope.attempts.orEmpty().filter { it.finishedAt != null }
            loadBadges(callback, user, classroom, subjects, lessons, finished)
        }
    }

    private fun loadBadges(
        callback: ResultCallback<ProfileData>,
        user: UserDto,
        classroom: ClassroomDto?,
        subjects: List<SubjectDto>,
        lessons: List<LessonSummaryDto>,
        attempts: List<AttemptDto>
    ) {
        api.badges().go(
            onFail = { code ->
                if (code == 401) callback(ApiResult.Failure(401))
                else deliver(callback, user, classroom, subjects, lessons, attempts, emptyList())
            }
        ) { envelope ->
            val catalog = envelope.badges.orEmpty().mapNotNull { it.toBadgeDto() }
            deliver(callback, user, classroom, subjects, lessons, attempts, catalog)
        }
    }

    private fun deliver(
        callback: ResultCallback<ProfileData>,
        user: UserDto,
        classroom: ClassroomDto?,
        subjects: List<SubjectDto>,
        lessons: List<LessonSummaryDto>,
        attempts: List<AttemptDto>,
        badgeCatalog: List<BadgeDto>
    ) {
        callback(
            ApiResult.Success(
                ProfileData(
                    user = user,
                    classroom = classroom,
                    subjects = subjects,
                    lessons = lessons,
                    attempts = attempts,
                    badgeCatalog = badgeCatalog
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

    val lessons: LessonsRepository = RetrofitLessonsRepository(api)
    val teacherExtras: TeacherExtrasSource =
        if (TeacherConfig.USE_REAL_EXTRAS) RetrofitTeacherExtrasSource(api)
        else MockTeacherExtrasSource()
    val teacher: TeacherRepository = RetrofitTeacherRepository(api, teacherExtras)

    val auth: AuthRepository = RetrofitAuthRepository(api)
    val profile: ProfileRepository = RetrofitProfileRepository(api, lessons)
    val subjects: SubjectsRepository = RetrofitSubjectsRepository(profile)
    val schools: SchoolsRepository = RetrofitSchoolsRepository(api)
    val passwordReset: PasswordResetRepository = UnavailablePasswordResetRepository()
    val quiz: QuizRepository = RetrofitQuizRepository(api)
    val stats: StatsRepository = RetrofitStatsRepository(api)
    val practice: PracticeRepository = RetrofitPracticeRepository(api)
    val leaderboard: LeaderboardRepository = RetrofitLeaderboardRepository(api, profile)
    val learnExtras: LearnExtrasSource = RetrofitLearnExtrasSource()
    val learn: LearnRepository = LearnRepositoryImpl(
        profile = profile,
        extras = learnExtras
    )
     val classroomContent: ClassroomContentSource =
        if (TeacherSubjectsConfig.USE_REAL_CONTENT) RetrofitClassroomContentSource(api)
        else MockClassroomContentSource()
    val topicSettings: TopicSettingsSource =
        if (TeacherSubjectsConfig.USE_REAL_TOPIC_SETTINGS) RetrofitTopicSettingsSource(api)
        else MockTopicSettingsSource()
    val teacherSubjects: TeacherSubjectsRepository =
        RetrofitTeacherSubjectsRepository(api, classroomContent, topicSettings)
    val classroomInsights: ClassroomInsightsSource =
        if (TeacherClassroomConfig.USE_REAL_INSIGHTS) RetrofitClassroomInsightsSource(api)
        else MockClassroomInsightsSource()
    val questionAuthoring: QuestionAuthoringSource =
        if (TeacherClassroomConfig.USE_REAL_AUTHORING) RetrofitQuestionAuthoringSource(api)
        else MockQuestionAuthoringSource()
    val teacherClassroom: TeacherClassroomRepository =
        RetrofitTeacherClassroomRepository(api, classroomInsights, questionAuthoring)
    val studentInsights: StudentInsightsSource =
        if (TeacherLeaderboardConfig.USE_REAL_INSIGHTS) RetrofitStudentInsightsSource(api)
        else MockStudentInsightsSource()
    val teacherLeaderboard: TeacherLeaderboardRepository =
        RetrofitTeacherLeaderboardRepository(api, studentInsights)
    val teacherLearnSource: TeacherLearnSource =
    if (TeacherLearnConfig.USE_REAL_EXTRAS) RetrofitTeacherLearnSource(api)
        else MockTeacherLearnSource(classroomContent)
    val teacherLearn: TeacherLearnRepository =
        RetrofitTeacherLearnRepository(teacherSubjects, teacherLearnSource)
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
