package com.example.superznalci

object TeacherLeaderboardConfig {
    // student-insights, student detail, praise
    const val USE_REAL_INSIGHTS = true
}

// GET /api/classrooms/{id}/student-insights?subjectId= 
class TeacherInsightsEnvelope(val insights: List<TeacherStudentInsightDto>?)

data class TeacherStudentInsightDto(
    val studentId: String,
    val xp: Int? = null,
    val accuracy: Int? = null,
    val tasksDone: Int = 0,
    val level: Int = 1,
    val title: String? = null,  
    val needsHelp: Boolean = false,
    val helpTopic: String? = null
)

// GET /api/classrooms/{id}/students/{studentId}/detail
data class TeacherStudentDetailDto(
    val studentId: String = "",
    val assignedDone: Int = 0,
    val assignedTotal: Int = 0,
    val badgeTotal: Int = 0,
    val badges: List<TeacherBadgeDto>? = null,
    val recentQuizzes: List<TeacherQuizResultDto>? = null
)

data class TeacherBadgeDto(
    val id: String,
    val name: String,
    val subtitle: String? = null
)

data class TeacherQuizResultDto(
    val id: String,
    val title: String,
    val score: Int = 0,
    val total: Int = 0,
    val accuracy: Int = 0,
    val finishedAt: String? = null,
    val mistakeCount: Int = 0,
    val mistakeNote: String? = null,
    val xpGained: Int = 0
)

// POST /api/classrooms/{id}/students/{studentId}/praise
class TeacherPraiseRequest(val message: String)

// UI models

data class TlSubjectUi(val id: String, val name: String)

data class TlStudentUi(
    val id: String,
    val name: String,
    val initials: String,
    val streak: Int,
    val xp: Int,
    val accuracy: Int?,
    val tasksDone: Int,
    val level: Int,
    val title: String?,
    val needsHelp: Boolean,
    val helpTopic: String?,
    val rank: Int
)

data class TlBoard(
    val students: List<TlStudentUi>,
    val averageXp: Int,
    val averageAccuracy: Int,
    val loadedAt: Long
)