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
    val dueDate: String? = null,
    val assignedAt: String? = null
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

