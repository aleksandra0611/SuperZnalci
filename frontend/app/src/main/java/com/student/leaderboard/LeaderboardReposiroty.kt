package com.example.superznalci

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
                                    lastAttempt = lastAttemptOf(p),
                                    badgeCatalog = p.badgeCatalog,
                                    newBadges = recentBadges(p),
                                    stars = p.lessons.sumOf { it.stars }
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private fun lastAttemptOf(p: ProfileData): LastAttemptDto? {
        val lessonById = p.lessons.associateBy { it.id }
        val subjectByTopic = HashMap<String, String>()
        p.subjects.forEach { s -> s.grades.forEach { g -> g.topics.forEach { t -> subjectByTopic[t.id] = s.name } } }

        val latest = p.attempts
            .filter { it.finishedAt != null }
            .sortedByDescending { IsoDate.parse(it.finishedAt) ?: 0L }
            .firstNotNullOfOrNull { a -> lessonById[a.lessonId]?.let { lesson -> a to lesson } }
            ?: return null

        val (attempt, lesson) = latest
        val start = IsoDate.parse(attempt.date) ?: 0L
        val end = IsoDate.parse(attempt.finishedAt) ?: start
        return LastAttemptDto(
            attempt = attempt,
            lessonName = lesson.name,
            subjectName = subjectByTopic[lesson.topicId].orEmpty(),
            durationSeconds = ((end - start) / 1000).toInt().coerceAtLeast(0)
        )
    }

    private fun recentBadges(p: ProfileData): List<BadgeDto> {
        val byId = p.badgeCatalog.associateBy { it.id }
        return p.user.badges
            .sortedByDescending { IsoDate.parse(it.awardedAt) ?: 0L }
            .mapNotNull { byId[it.badgeId] }
            .take(3)
    }
}