package com.example.superznalci


object TeacherClassroomConfig {
    const val USE_REAL_INSIGHTS = false
    const val USE_REAL_AUTHORING = false
}

// GET /api/classrooms/{id}/overview
data class ClassroomOverviewDto(
    val submittedThisWeek: Int = 0,
    val averageAccuracy: Int = 0,
    val struggleTopics: List<String>? = null
)

// GET /api/classrooms/{id}/student-results
class StudentResultsEnvelope(val results: List<StudentResultDto>?)

data class StudentResultDto(
    val studentId: String,
    val completedLessons: Int = 0,
    val totalLessons: Int = 0,
    val accuracy: Int? = null,
    val badge: String? = null,     
    val mistakeCount: Int = 0,
    val mistakeTopic: String? = null, 
    val weakTopic: String? = null 
)

// POST /api/classrooms/{id}/join-code  
class JoinCodeEnvelope(val joinCode: String?)

// POST /api/questions 
// DELETE /api/questions/{id}
class QuestionCreatedEnvelope(val question: QuestionRowDto?)

data class QuestionRowDto(
    val id: String,
    val text: String,
    val type: String,
    val points: Int = 10
)

class QuestionCreateRequest(
    val lessonId: String,
    val type: String,
    val text: String,
    val points: Int,
    val explanation: String? = null,
    val answers: List<String>? = null,
    val correctAnswers: List<String>? = null
)

object QuestionTypes {
    const val CHOICE = "MULTIPLE_CHOICE"
    const val TRUE_FALSE = "TRUE_FALSE"
    const val PAIRS = "MATCHING"
    const val FILL_BLANK = "FILL_IN_BLANK"
    const val IMAGE = "IMAGE_QUESTION"
}

// UI models

enum class StudentSort { ACCURACY_DESC, ACCURACY_ASC, NAME }

data class StudentRowUi(
    val id: String,
    val name: String,
    val initials: String,
    val accuracy: Int?,
    val completed: Int,
    val total: Int,
    val badge: String?,
    val mistakeCount: Int,
    val mistakeTopic: String?,
    val weakTopic: String?
)

data class ClassroomDashboard(
    val students: List<StudentRowUi>,
    val overview: ClassroomOverviewDto
)

data class QuestionRowUi(
    val id: String,
    val text: String,
    val type: String,
    val points: Int
)

data class EditorLessonUi(
    val id: String,
    val topicId: String,
    val topicName: String,
    val name: String,
    val questions: List<QuestionRowUi>
)

data class EditorSubjectUi(
    val id: String,
    val name: String,
    val lessons: List<EditorLessonUi>
)

data class QuestionDraft(
    val type: String,
    val text: String,
    val points: Int,
    val explanation: String?
)