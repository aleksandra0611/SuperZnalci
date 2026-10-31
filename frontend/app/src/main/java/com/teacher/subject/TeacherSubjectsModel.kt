package com.example.superznalci


// GET /api/classrooms/{id}/lesson-stats
class LessonStatsEnvelope(val studentCount: Int = 0, val lessons: List<LessonStatDto>?)

data class LessonStatDto(
    val lessonId: String,
    val completedCount: Int = 0,
    val completionPercent: Int = 0,
    val averageAccuracy: Int? = null
)

// Assignment: GET/PUT /api/classrooms/{id}/assignments
class AssignmentsEnvelope(val assignments: List<ClassroomAssignmentDto>?)
class AssignmentEnvelope(val assignment: ClassroomAssignmentDto?)

data class ClassroomAssignmentDto(
    val id: String,
    val classroomId: String,
    val lessonId: String,
    val availableFrom: String? = null,
    val dueDate: String? = null
)

class AssignmentRequest(val availableFrom: String? = null, val dueDate: String? = null)


class TopicSettingsEnvelope(val settings: List<TopicSettingDto>?)
class TopicSettingEnvelope(val setting: TopicSettingDto?)
class TopicSettingRequest(val visibleToStudents: Boolean)

data class TopicSettingDto(
    val classroomId: String,
    val topicId: String,
    val visibleToStudents: Boolean = true
)

object TeacherSubjectsConfig {
    const val USE_REAL_CONTENT = false 
    const val USE_REAL_TOPIC_SETTINGS = false 
}


enum class LessonStatus { PUBLISHED, SCHEDULED, DRAFT }

data class TeacherClassTab(val classroom: ClassroomDto, val studentCount: Int)

data class LessonRowUi(
    val id: String,
    val number: Int,
    val name: String,
    val questionCount: Int,
    val status: LessonStatus,
    val availableFrom: String?,
    val completionPercent: Int?, 
    val averageAccuracy: Int?
)

data class ModuleUi(
    val topicId: String,
    val number: Int,
    val name: String,
    val lessons: List<LessonRowUi>,
    val visibleToStudents: Boolean
) {
    val isActive: Boolean get() = lessons.any { it.status == LessonStatus.PUBLISHED }
}

data class SubjectUi(
    val id: String,
    val name: String,
    val gradeNumber: Int,
    val modules: List<ModuleUi>
) {
    val title: String get() = "$name $gradeNumber"
    val lessonCount: Int get() = modules.sumOf { it.lessons.size }
    val questionCount: Int get() = modules.sumOf { m -> m.lessons.sumOf { it.questionCount } }
    val isActive: Boolean get() = modules.any { it.isActive }
}