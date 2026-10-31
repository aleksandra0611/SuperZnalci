package com.example.superznalci

// Vadnica: practice content (lessons with scope PRACTICE), available to every student, also without a classroom.
//   subjects   GET /api/practice/subjects?gradeNumber=X        subjects with topic, lesson and question counts
//   subject    GET /api/practice/subjects/{id}?gradeNumber=X   topics with lessons and the student's best result

data class PracticeTopicDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val lessonCount: Int = 0,
    val questionCount: Int = 0
)

data class PracticeSubjectDto(
    val id: String,
    val name: String,
    val icon: String? = null,
    val topicCount: Int = 0,
    val lessonCount: Int = 0,
    val questionCount: Int = 0,
    val topics: List<PracticeTopicDto> = emptyList()
)

class PracticeSubjectsEnvelope(val gradeNumber: Int, val subjects: List<PracticeSubjectDto>?)

// stars / bestAccuracy / bestPoints come from the student's finished attempts (completed = at least one)
data class PracticeLessonDto(
    val id: String,
    val name: String,
    val order: Int = 0,
    val questionCount: Int = 0,
    val completed: Boolean = false,
    val stars: Int = 0,
    val bestAccuracy: Double? = null,
    val bestPoints: Int? = null
)

data class PracticeTopicDetailDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val lessonCount: Int = 0,
    val completedCount: Int = 0,
    val progress: Int = 0,
    val lessons: List<PracticeLessonDto>? = null
)

data class PracticeSubjectInfoDto(val id: String, val name: String, val icon: String? = null)

data class PracticeSubjectDetailDto(
    val subject: PracticeSubjectInfoDto,
    val gradeNumber: Int,
    val topics: List<PracticeTopicDetailDto>? = null
)

object PracticeConfig {
    // Practice content exists only for grade 8 for now, so a student without a classroom starts there
    const val DEFAULT_GRADE = 8
}

interface PracticeRepository {
    // The grade the screen opens with: the student's classroom grade, otherwise PracticeConfig.DEFAULT_GRADE
    fun loadDefaultGrade(callback: (Int) -> Unit)

    fun loadSubjects(gradeNumber: Int, callback: ResultCallback<List<PracticeSubjectDto>>)

    fun loadSubject(subjectId: String, gradeNumber: Int, callback: ResultCallback<PracticeSubjectDetailDto>)
}

class RetrofitPracticeRepository(private val api: ApiService) : PracticeRepository {

    override fun loadDefaultGrade(callback: (Int) -> Unit) {
        if (SessionStore.user?.classroomId == null) {
            callback(PracticeConfig.DEFAULT_GRADE)
            return
        }
        // a missing classroom is not an error here, the student can still practise
        api.myClassroom().go({ callback(PracticeConfig.DEFAULT_GRADE) }) {
            callback(it.classroom.gradeNumber)
        }
    }

    override fun loadSubjects(gradeNumber: Int, callback: ResultCallback<List<PracticeSubjectDto>>) {
        api.practiceSubjects(gradeNumber).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.subjects.orEmpty()))
        }
    }

    override fun loadSubject(subjectId: String, gradeNumber: Int, callback: ResultCallback<PracticeSubjectDetailDto>) {
        api.practiceSubject(subjectId, gradeNumber).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it))
        }
    }
}
