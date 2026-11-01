package com.example.superznalci

import android.os.Handler
import android.os.Looper
import java.util.Calendar

data class MissionDto(
    val id: String,
    val type: String,
    val title: String,
    val target: Int,
    val progress: Int,
    val rewardXp: Int,
    val claimed: Boolean = false
)

data class ActivityDto(
    val id: String,
    val message: String,
    val detail: String,
    val names: List<String> = emptyList()
)

data class LearnExtras(
    val user: UserDto,
    val classroomInfo: ClassroomInfoDto,
    val assignments: List<AssignmentDto>,
    val attempts: List<AttemptDto>,
    val dailyGoal: Int,
    val streakRecord: Int,
    val missions: List<MissionDto>,
    val activity: ActivityDto?
)

data class LearnData(
    val user: UserDto,
    val classroom: ClassroomDto?,
    val classroomInfo: ClassroomInfoDto,
    val subjects: List<SubjectDto>,
    val lessons: List<LessonSummaryDto>,
    val assignments: List<AssignmentDto>,
    val attempts: List<AttemptDto>,
    val dailyGoal: Int,
    val streakRecord: Int,
    val missions: List<MissionDto>,
    val activity: ActivityDto?
)

interface LearnRepository {
    fun loadLearnData(callback: ResultCallback<LearnData>)
}

interface LearnExtrasSource {
    fun load(profile: ProfileData, callback: ResultCallback<LearnExtras>)
}

class LearnRepositoryImpl(
    private val profile: ProfileRepository,
    private val extras: LearnExtrasSource
) : LearnRepository {

    override fun loadLearnData(callback: ResultCallback<LearnData>) {
        profile.loadProfileData { result ->
            when (result) {
                is ApiResult.Failure -> callback(result)
                is ApiResult.Success -> {
                    val p = result.data
                    extras.load(p) { extrasResult ->
                        when (extrasResult) {
                            is ApiResult.Failure -> callback(extrasResult)
                            is ApiResult.Success -> {
                                val e = extrasResult.data
                                callback(
                                    ApiResult.Success(
                                        LearnData(
                                            user = e.user,
                                            classroom = p.classroom,
                                            classroomInfo = e.classroomInfo,
                                            subjects = p.subjects,
                                            lessons = p.lessons,
                                            assignments = e.assignments,
                                            attempts = e.attempts,
                                            dailyGoal = e.dailyGoal,
                                            streakRecord = e.streakRecord,
                                            missions = e.missions,
                                            activity = e.activity
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class RetrofitLearnExtrasSource : LearnExtrasSource {

    override fun load(profile: ProfileData, callback: ResultCallback<LearnExtras>) {
        val now = System.currentTimeMillis()
        val offset = java.util.TimeZone.getDefault().getOffset(now)
        val startOfDay = ((now + offset) / 86_400_000L) * 86_400_000L - offset

        val today = profile.attempts.filter { (IsoDate.parse(it.finishedAt ?: it.date) ?: 0L) >= startOfDay }
        val xpToday = today.sumOf { it.points }
        val flawless = if (today.any { it.accuracy >= 100.0 }) 1 else 0

        val missions = listOf(
            MissionDto(
                "daily-flawless", "FLAWLESS_LESSON", "Lekcija brez napake",
                target = 1, progress = flawless, rewardXp = 0, claimed = flawless >= 1
            ),
            MissionDto(
                "daily-xp", "EARN_XP", "Zberi 50 XP danes",
                target = 50, progress = xpToday.coerceAtMost(50), rewardXp = 0, claimed = xpToday >= 50
            )
        )

        callback(
            ApiResult.Success(
                LearnExtras(
                    user = profile.user,
                    classroomInfo = ClassroomInfoDto(),
                    assignments = emptyList(),
                    attempts = profile.attempts,
                    dailyGoal = 3,
                    streakRecord = 0,
                    missions = missions,
                    activity = null
                )
            )
        )
    }
}