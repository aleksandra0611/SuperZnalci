package com.example.superznalci


class ClassroomsEnvelope(val classrooms: List<ClassroomDto>?)
class StudentsEnvelope(val students: List<StudentDto>?)

data class StudentDto(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val totalXp: Int = 0,
    val streak: Int = 0,
    val lastActiveAt: String? = null
)

object TeacherConfig {
    const val USE_REAL_EXTRAS = true
}

object Shifts {
    const val MORNING = "MORNING"
    const val AFTERNOON = "AFTERNOON"
}

data class TeacherSettingsDto(
    val emailNotifications: Boolean = true,
    val weeklyReport: Boolean = true
)

data class ClassroomExtraDto(
    val classroomId: String,
    val shift: String? = null
)

data class TeacherExtrasDto(
    val isVerified: Boolean = false,
    val quizzesCreated: Int = 0,
    val averageAccuracy: Double = 0.0,
    val accuracyDelta: Double = 0.0,
    val classrooms: List<ClassroomExtraDto>? = null,
    val settings: TeacherSettingsDto? = null
)


data class TeacherClassUi(
    val classroom: ClassroomDto,
    val studentCount: Int,
    val subjectNames: List<String>,
    val shift: String?
)

data class TeacherSubjectChip(
    val name: String, 
    val moduleCount: Int 
)

data class TeacherProfileData(
    val user: UserDto,
    val schoolName: String,
    val classes: List<TeacherClassUi>,
    val totalStudents: Int,
    val subjectChips: List<TeacherSubjectChip>,
    val gradeNumber: Int?,
    val extras: TeacherExtrasDto,
    val settings: TeacherSettingsDto
)
