package com.example.superznalci

// Data for the Subjects screen, in the shape of the backend models.
// SubjectsMapper (SubjectsScreen.kt) turns it into what the screen shows.
//
// The real implementation fills it from these calls (or from one aggregate endpoint):
//   user           GET /api/users/me
//   classroom      GET /api/classrooms/{user.classroomId}
//   classroomInfo  teacher name + number of students of that classroom  (SMALL BACKEND ADDITION,
//                  e.g. extra fields on the classroom response: teacherName, studentCount)
//   subjects       GET /api/subjects?ids={classroom.subjectIds}
//   lessons        GET /api/lessons?topicIds=...   summaries WITHOUT questions, but with
//                  isPublic, questionCount (= questions.length) and totalPoints (= sum of question points)
//   assignments    GET /api/assignments?classroomId={classroom.id}
//   attempts       GET /api/attempts?studentId=me
//
// A lesson is visible to the student when lesson.isPublic == true OR it is assigned to the classroom.

// Mirrors the Assignment model (dueDate is ISO-8601 or null)
data class AssignmentDto(
    val id: String,
    val classroomId: String,
    val lessonId: String,
    val dueDate: String? = null
)

// Values the Classroom model does not store but the screen shows
data class ClassroomInfoDto(
    val teacherName: String? = null,
    val studentCount: Int = 0
)

data class SubjectsData(
    val user: UserDto,
    val classroom: ClassroomDto?,
    val classroomInfo: ClassroomInfoDto,
    val subjects: List<SubjectDto>,
    val lessons: List<LessonSummaryDto>,
    val assignments: List<AssignmentDto>,
    val attempts: List<AttemptDto>
)

interface SubjectsRepository {
    // Loads everything for the logged-in user (SessionStore). 401 = not logged in
    fun loadSubjectsData(callback: ResultCallback<SubjectsData>)
}

/**
 * Mock: reuses MockProfileRepository (user, classroom, subjects, lessons, attempts) and adds
 * what is new for this screen: assignments and the classroom info.
 */
class MockSubjectsRepository(
    private val auth: MockAuthRepository,
    private val profile: ProfileRepository
) : SubjectsRepository {

    override fun loadSubjectsData(callback: ResultCallback<SubjectsData>) {
        profile.loadProfileData { result ->
            when (result) {
                is ApiResult.Failure -> callback(result)
                is ApiResult.Success -> callback(ApiResult.Success(build(result.data)))
            }
        }
    }

    private fun build(p: ProfileData): SubjectsData {
        val classroom = p.classroom
        val isDemoClass = classroom?.joinCode == MockData.DEMO_JOIN_CODE
        val now = System.currentTimeMillis()

        // Only the demo classroom has assignments (teacher-made, non-public lessons)
        val assignments =
            if (classroom != null && isDemoClass) {
                MockData.DEMO_ASSIGNMENTS.mapIndexed { index, (lessonId, dueInDays) ->
                    AssignmentDto(
                        id = "assignment-${index + 1}",
                        classroomId = classroom.id,
                        lessonId = lessonId,
                        dueDate = dueInDays?.let { IsoDate.format(now + it * DAY_MS) }
                    )
                }
            } else {
                emptyList()
            }

        // Only the lessons of the student's subjects are returned (assigned ones included)
        val topicIds = p.subjects
            .flatMap { s -> s.grades.filter { it.gradeNumber == classroom?.gradeNumber }.flatMap { it.topics } }
            .map { it.id }
            .toSet()
        val lessons = MockData.LESSONS.filter { it.topicId in topicIds }

        val info =
            if (isDemoClass) {
                val teacher = auth.userIdByEmail(MockData.DEMO_TEACHER_EMAIL)?.let { auth.findUser(it) }
                ClassroomInfoDto(teacher?.let { "prof. ${it.name}" }, MockData.DEMO_STUDENT_COUNT)
            } else {
                ClassroomInfoDto(null, 1)   // mock: teacher unknown, only the new student
            }

        return SubjectsData(p.user, classroom, info, p.subjects, lessons, assignments, p.attempts)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
