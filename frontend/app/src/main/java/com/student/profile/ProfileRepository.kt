package com.example.superznalci

import android.os.Handler
import android.os.Looper

/**
 * Everything the profile screen needs, in the shape of the backend models.
 * ProfileMapper turns it into what the screen shows (StudentProfile).
 *
 * The real implementation fills it from these calls (or from one aggregate endpoint):
 *   user          GET /api/users/me                     (fresh XP, streak, badges)
 *   classroom     GET /api/classrooms/{user.classroomId}
 *   subjects      GET /api/subjects?ids={classroom.subjectIds}
 *   lessons       GET /api/lessons?topicIds=...         (no questions needed)
 *   attempts      GET /api/attempts?studentId=me        (newest first)
 *   badgeCatalog  GET /api/badges
 */
data class ProfileData(
    val user: UserDto,
    val classroom: ClassroomDto?,
    val subjects: List<SubjectDto>,
    val lessons: List<LessonSummaryDto>,
    val attempts: List<AttemptDto>,
    val badgeCatalog: List<BadgeDto>
)

interface ProfileRepository {
    // Loads the profile of the logged-in user (SessionStore). 401 = not logged in
    fun loadProfileData(callback: ResultCallback<ProfileData>)
}

class MockProfileRepository(private val auth: MockAuthRepository) : ProfileRepository {

    private val handler = Handler(Looper.getMainLooper())

    // Only the demo student has quiz history; every other account starts empty
    private val demoAttempts: List<AttemptDto> by lazy {
        val demoId = auth.userIdByEmail(MockData.DEMO_STUDENT_EMAIL)
        if (demoId == null) emptyList() else buildDemoAttempts(demoId)
    }

    override fun loadProfileData(callback: ResultCallback<ProfileData>) {
        val user = SessionStore.user?.let { auth.findUser(it.id) }

        val result: ApiResult<ProfileData> = if (user == null) {
            ApiResult.Failure(401)
        } else {
            val classroom = auth.findClassroom(user.classroomId)
            val grade = classroom?.gradeNumber
            val subjectIds = classroom?.subjectIds.orEmpty()

            val subjects = MockData.SUBJECTS.filter { it.id in subjectIds }
            val topicIds = subjects
                .flatMap { subject ->
                    subject.grades.filter { it.gradeNumber == grade }.flatMap { g -> g.topics.map { it.id } }
                }
                .toSet()
            val lessons = MockData.LESSONS.filter { it.topicId in topicIds }

            val attempts = demoAttempts
                .filter { it.studentId == user.id }
                .sortedByDescending { it.date }  // ISO strings sort like dates

            ApiResult.Success(ProfileData(user, classroom, subjects, lessons, attempts, MockData.BADGES))
        }

        handler.postDelayed({ callback(result) }, DELAY_MS)
    }

    private class Seed(
        val subject: String,
        val topic: Int,
        val lesson: Int,
        val daysAgo: Int,
        val correct: Int,
        val total: Int
    )

    private fun buildDemoAttempts(studentId: String): List<AttemptDto> {
        val now = System.currentTimeMillis()

        val seeds = listOf(
            Seed("math", 1, 1, 12, 8, 8),
            Seed("math", 1, 2, 11, 7, 8),
            Seed("math", 1, 3, 10, 8, 8),
            Seed("math", 1, 4, 9, 6, 8),
            Seed("math", 2, 1, 8, 8, 8),
            Seed("math", 2, 2, 6, 5, 8),
            Seed("math", 2, 3, 5, 7, 8),
            Seed("math", 2, 4, 3, 8, 8),
            Seed("math", 3, 1, 1, 7, 8),
            Seed("physics", 1, 1, 4, 6, 8),
            Seed("physics", 1, 2, 2, 4, 8)
        )

        return seeds.mapIndexed { index, seed ->
            AttemptDto(
                id = "attempt-${index + 1}",
                studentId = studentId,
                lessonId = MockData.lessonId(seed.subject, 8, seed.topic, seed.lesson),
                points = seed.correct * 10,
                accuracy = seed.correct * 100.0 / seed.total,
                date = IsoDate.format(now - seed.daysAgo * DAY_MS),
                answers = List(seed.total) { i -> AttemptAnswerDto("q${i + 1}", i < seed.correct) }
            )
        }
    }

    private companion object {
        const val DELAY_MS = 400L
        const val DAY_MS = 86_400_000L
    }
}