package com.example.superznalci

class LessonsEnvelope(val lessons: List<LessonSummaryDto>?)

interface LessonsRepository {
    fun loadLessons(
        subjects: List<SubjectDto>,
        gradeNumber: Int?,
        callback: ResultCallback<List<LessonSummaryDto>>
    )
}

class RetrofitLessonsRepository(private val api: ApiService) : LessonsRepository {

    override fun loadLessons(
        subjects: List<SubjectDto>,
        gradeNumber: Int?,
        callback: ResultCallback<List<LessonSummaryDto>>
    ) {
        val topicIds = subjects.flatMap { s -> s.grades.flatMap { g -> g.topics.map { it.id } } }
        if (topicIds.isEmpty()) {
            callback(ApiResult.Success(emptyList()))
            return
        }
        api.lessons(topicIds.joinToString(",")).go({ callback(ApiResult.Failure(it)) }) { envelope ->
            callback(ApiResult.Success(envelope.lessons.orEmpty()))
        }
    }
}