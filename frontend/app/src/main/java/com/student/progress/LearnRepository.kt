package com.example.superznalci

import android.os.Handler
import android.os.Looper
import java.util.Calendar

object LearnConfig {
    const val USE_REAL_EXTRAS = false
    const val MOCK_DELAY_MS = 250L
}

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
        callback(
            ApiResult.Success(
                LearnExtras(
                    user = profile.user,
                    classroomInfo = ClassroomInfoDto(),            // MISSING: teacherName, studentCount
                    assignments = emptyList(),                     // MISSING: GET /api/assignments
                    attempts = emptyList(),                        // MISSING: GET /api/attempts
                    dailyGoal = 3,                                 // MISSING: User.dailyGoal
                    streakRecord = profile.user.streak,            // MISSING: User.longestStreak
                    missions = emptyList(),                        // MISSING: GET /api/missions
                    activity = null                                // MISSING: GET /api/classrooms/{id}/activity
                )
            )
        )
    }
}


class MockLearnExtrasSource : LearnExtrasSource {

    private val main = Handler(Looper.getMainLooper())

    override fun load(profile: ProfileData, callback: ResultCallback<LearnExtras>) {
        main.postDelayed({ callback(ApiResult.Success(build(profile))) }, LearnConfig.MOCK_DELAY_MS)
    }

    private fun build(p: ProfileData): LearnExtras {
        val now = System.currentTimeMillis()
        val grade = p.classroom?.gradeNumber
        val subject = p.subjects.firstOrNull()

        val user = p.user.copy(
            streak = if (p.user.streak > 0) p.user.streak else 5,
            totalXp = if (p.user.totalXp > 0) p.user.totalXp else 420,
            lastActiveAt = IsoDate.format(now)
        )

        val topics = subject?.grades.orEmpty().filter { it.gradeNumber == grade }.flatMap { it.topics }
        val ordered = topics.flatMap { t -> p.lessons.filter { it.topicId == t.id }.sortedBy { it.order } }

        val doneCount = (ordered.size - 1).coerceIn(0, 3)
        val attempts = ordered.take(doneCount).mapIndexed { i, lesson ->
            val total = lesson.questionCount.coerceAtLeast(2)
            val wrong = if (i == 1) 1 else 0
            AttemptDto(
                id = "mock-attempt-$i",
                studentId = user.id,
                lessonId = lesson.id,
                points = lesson.totalPoints.takeIf { it > 0 } ?: 50,
                accuracy = (total - wrong) * 100.0 / total,
                date = IsoDate.format(now - (doneCount - i) * 20 * 60_000L),
                answers = List(total) { k -> AttemptAnswerDto("mock-question-$i-$k", k >= wrong) }
            )
        }

        val current = ordered.getOrNull(doneCount)
        val today18 = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val classroomId = p.classroom?.id ?: "mock-classroom"
        val assignments = listOfNotNull(
            current?.let { AssignmentDto("mock-assignment-1", classroomId, it.id, IsoDate.format(today18)) }
        )

        val xpToday = attempts.sumOf { it.points }
        val subjectName = subject?.name ?: "predmetu"
        val missions = listOf(
            MissionDto(
                "mock-mission-1", "FLAWLESS_LESSON", "Lekcija brez napake",
                target = 1, progress = attempts.count { it.accuracy >= 100.0 }.coerceAtMost(1), rewardXp = 15
            ),
            MissionDto(
                "mock-mission-2", "EARN_XP", "Zberi 50 XP – $subjectName",
                target = 50, progress = xpToday.coerceAtMost(50), rewardXp = 20
            ),
            MissionDto("mock-mission-3", "DUEL", "Hitri dvoboj s sošolcem", target = 1, progress = 0, rewardXp = 30)
        )

        val (className, _) = ClassroomNaming.parse(p.classroom?.name.orEmpty())
        val activity = ActivityDto(
            id = "mock-activity-1",
            message = "Luka je pravkar dosegel 8. raven!",
            detail = "Razred ${className.ifBlank { "8.A" }} tekmuje ta teden",
            names = listOf("Maja Novak", "Luka Kovač", "Jan Novak")
        )

        return LearnExtras(
            user = user,
            classroomInfo = ClassroomInfoDto(teacherName = "prof. Horvat", studentCount = 24),
            assignments = assignments,
            attempts = attempts,
            dailyGoal = 3,
            streakRecord = 12,
            missions = missions,
            activity = activity
        )
    }
}