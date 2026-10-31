package com.example.superznalci

import android.os.Handler
import android.os.Looper


object LeaderboardConfig {
    const val USE_REAL_LEADERBOARD = false
    const val MOCK_DELAY_MS = 350L
}

class LeaderboardEnvelope(val leaderboard: LeaderboardDto?)

data class LeaderboardEntryDto(
    val userId: String,
    val name: String,
    val avatar: String? = null,
    val weeklyXp: Int = 0
)

data class LeaderboardDto(
    val classroomId: String,
    val period: String = "week",
    val entries: List<LeaderboardEntryDto> = emptyList()
)

data class LastAttemptDto(
    val attempt: AttemptDto,
    val lessonName: String,
    val subjectName: String,
    val durationSeconds: Int
)

data class LeaderboardData(
    val user: UserDto,
    val classroom: ClassroomDto?,
    val leaderboard: LeaderboardDto,
    val lastAttempt: LastAttemptDto?,
    val badgeCatalog: List<BadgeDto>,
    val newBadges: List<BadgeDto>,
    val stars: Int
)

interface LeaderboardRepository {
    fun loadLeaderboard(callback: ResultCallback<LeaderboardData>)
}

// real implementation

class RetrofitLeaderboardRepository(
    private val api: ApiService,
    private val profile: ProfileRepository
) : LeaderboardRepository {

    override fun loadLeaderboard(callback: ResultCallback<LeaderboardData>) {
        profile.loadProfileData { result ->
            when (result) {
                is ApiResult.Failure -> callback(result)
                is ApiResult.Success -> {
                    val p = result.data
                    val classroom = p.classroom
                    if (classroom == null) {
                        callback(ApiResult.Failure(404))
                        return@loadProfileData
                    }
                    api.leaderboard(classroom.id, "week").go({ callback(ApiResult.Failure(it)) }) { envelope ->
                        callback(
                            ApiResult.Success(
                                LeaderboardData(
                                    user = p.user,
                                    classroom = classroom,
                                    leaderboard = envelope.leaderboard ?: LeaderboardDto(classroom.id),
                                    lastAttempt = null,                // MISSING: GET /api/attempts
                                    badgeCatalog = p.badgeCatalog,
                                    newBadges = emptyList(),           // MISSING: GET /api/badges
                                    stars = 0                          // MISSING: needs attempts
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

// mock implementation (same shapes as the database)

class MockLeaderboardRepository : LeaderboardRepository {

    private val main = Handler(Looper.getMainLooper())

    override fun loadLeaderboard(callback: ResultCallback<LeaderboardData>) {
        main.postDelayed({ callback(ApiResult.Success(build())) }, LeaderboardConfig.MOCK_DELAY_MS)
    }

    private fun build(): LeaderboardData {
        val now = System.currentTimeMillis()

        val base = SessionStore.user ?: UserDto(
            id = "mock-user-me",
            name = "Jan Novak",
            email = "jan.novak@example.com",
            role = "student",
            classroomId = "mock-classroom-8a"
        )
        val me = base.copy(
            totalXp = if (base.totalXp > 0) base.totalXp else 1420,
            streak = if (base.streak > 0) base.streak else 5,
            lastActiveAt = IsoDate.format(now)
        )

        val classroom = ClassroomDto(
            id = me.classroomId ?: "mock-classroom-8a",
            name = ClassroomNaming.build(8, "A", "OŠ Primoža Trubarja, Ljubljana"),
            joinCode = "MAT8A7",
            gradeNumber = 8,
            subjectIds = listOf("mock-subject-math")
        )

        val entries = listOf(
            LeaderboardEntryDto("mock-user-1", "Luka Kovač", null, 2450),
            LeaderboardEntryDto("mock-user-2", "Maja Novak", null, 2210),
            LeaderboardEntryDto("mock-user-3", "Žan Petek", null, 1980),
            LeaderboardEntryDto("mock-user-4", "Nina Horvat", null, 1730),
            LeaderboardEntryDto("mock-user-5", "Tilen Zupan", null, 1560),
            LeaderboardEntryDto("mock-user-6", "Ema Kranjc", null, 1465),
            LeaderboardEntryDto(me.id, me.name, me.avatar, 1420),
            LeaderboardEntryDto("mock-user-8", "Rok Mlakar", null, 1310),
            LeaderboardEntryDto("mock-user-9", "Lara Vidmar", null, 1190)
        )

        val attempt = AttemptDto(
            id = "mock-attempt-1",
            studentId = me.id,
            lessonId = "mock-lesson-fractions",
            points = 120,
            accuracy = 90.0,
            date = IsoDate.format(now - 60_000L),
            answers = List(10) { i -> AttemptAnswerDto("mock-question-$i", isCorrect = i != 4) }
        )

        val badges = listOf(
            BadgeDto("mock-badge-1", "5 dni zapored", "Zagon"),
            BadgeDto("mock-badge-2", "Mojster ulomkov", "Tema rešena"),
            BadgeDto("mock-badge-3", "Hitri računar", "< 3 minute")
        )

        return LeaderboardData(
            user = me,
            classroom = classroom,
            leaderboard = LeaderboardDto(classroom.id, "week", entries),
            lastAttempt = LastAttemptDto(
                attempt = attempt,
                lessonName = "Ulomki in seštevanje",
                subjectName = "Matematika",
                durationSeconds = 165
            ),
            badgeCatalog = badges,
            newBadges = badges,
            stars = 420
        )
    }
}