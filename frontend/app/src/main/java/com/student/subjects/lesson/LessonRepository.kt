package com.example.superznalci

class LessonsEnvelope(
    val lessons: List<LessonSummaryDto>?
)

interface LessonsRepository {
    fun loadLessons(
        subjects: List<SubjectDto>,
        gradeNumber: Int?,
        callback: ResultCallback<List<LessonSummaryDto>>
    )
}

class RetrofitLessonsRepository(
    private val api: ApiService
) : LessonsRepository {

    override fun loadLessons(
        subjects: List<SubjectDto>,
        gradeNumber: Int?,
        callback: ResultCallback<List<LessonSummaryDto>>
    ) {
        // Pridobimo samo teme izbranega razreda.
        val topicIds = subjects
            .flatMap { subject ->
                subject.grades
                    .filter { grade ->
                        gradeNumber == null ||
                            grade.gradeNumber == gradeNumber
                    }
                    .flatMap { grade ->
                        grade.topics.map { topic ->
                            topic.id
                        }
                    }
            }
            .distinct()

        if (topicIds.isEmpty()) {
            callback(ApiResult.Success(emptyList()))
            return
        }

        api.lessons(
            topicIds.joinToString(",")
        ).go(
            onFail = { code ->
                callback(ApiResult.Failure(code))
            }
        ) { envelope ->
            callback(
                ApiResult.Success(
                    envelope.lessons.orEmpty()
                )
            )
        }
    }
}