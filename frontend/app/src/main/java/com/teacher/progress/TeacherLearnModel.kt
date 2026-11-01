package com.example.superznalci

object TeacherLearnConfig {
    const val USE_REAL_EXTRAS = true
}

// GET /api/classrooms/{id}/overview
data class ClassOverviewDto(
    val weekActive: Boolean = true,
    val completedCount: Int = 0,
    val totalStudents: Int = 0,
    val needHelpCount: Int = 0
)

object StruggleReason {
    const val MISTAKES = "MISTAKES"
    const val NOT_STARTED = "NOT_STARTED"
    const val INACTIVE = "INACTIVE"
}

// GET /api/classrooms/{id}/struggling-students
class StrugglingEnvelope(val students: List<StrugglingStudentDto>?)

data class StrugglingStudentDto(
    val studentId: String,
    val name: String,
    val avatar: String? = null,
    val reason: String = StruggleReason.INACTIVE,
    val mistakeCount: Int = 0,
    val lessonName: String? = null,
    val lastActiveAt: String? = null
)

// POST /api/classrooms/{id}/reminders
class ReminderRequest(val studentId: String)
class ReminderResponse(val sent: Boolean = true)

// UI models

enum class PathState { OPEN, LOCKED, DRAFT }

data class PathModuleUi(
    val subjectName: String,
    val topicId: String,
    val number: Int,
    val name: String,
    val state: PathState,
    val lessonCount: Int,
    val averageAccuracy: Int?,
    val unlockAtMillis: Long?,
    val scheduledLessonIds: List<String>,
    val lessonIds: List<String>
)

data class HomeworkLessonUi(val lessonId: String, val moduleTopicId: String, val label: String)

data class TeacherLearnData(
    val classroom: ClassroomDto,
    val modules: List<PathModuleUi>,
    val homeworkLessons: List<HomeworkLessonUi>,
    val overview: ClassOverviewDto,
    val struggling: List<StrugglingStudentDto>
)