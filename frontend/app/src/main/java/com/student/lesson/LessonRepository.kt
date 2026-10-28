package com.example.superznalci

class LessonsEnvelope(val lessons: List<LessonSummaryDto>?) //delete if we donćt add GET /api/lessons

interface LessonsRepository {
    fun loadLessons(
        subjects: List<SubjectDto>,
        gradeNumber: Int?,
        callback: ResultCallback<List<LessonSummaryDto>>
    )
}

class QuestionsDerivedLessonsRepository(private val api: ApiService) : LessonsRepository {

    override fun loadLessons(
        subjects: List<SubjectDto>,
        gradeNumber: Int?,
        callback: ResultCallback<List<LessonSummaryDto>>
    ) {
        if (subjects.isEmpty()) {
            callback(ApiResult.Success(emptyList()))
            return
        }

        val bySubject = arrayOfNulls<List<LessonSummaryDto>>(subjects.size)
        var pending = subjects.size
        var failed = false

        subjects.forEachIndexed { index, subject ->
            api.questions(subject.id, gradeNumber, null).go(
                { code ->
                    if (!failed) {
                        failed = true
                        callback(ApiResult.Failure(code))
                    }
                }
            ) { envelope ->
                bySubject[index] = lessonsFromQuestions(envelope.questions.orEmpty())
                pending--
                if (pending == 0 && !failed) {
                    callback(ApiResult.Success(bySubject.filterNotNull().flatten()))
                }
            }
        }
    }

    private fun lessonsFromQuestions(questions: List<QuestionDto>): List<LessonSummaryDto> {
        val byLesson = LinkedHashMap<String, MutableList<QuestionDto>>()
        questions.forEach { q ->
            val lessonId = q.lesson?.id ?: return@forEach
            if (q.topic?.id.isNullOrBlank()) return@forEach
            byLesson.getOrPut(lessonId) { mutableListOf() }.add(q)
        }

        val countPerTopic = HashMap<String, Int>()
        return byLesson.map { (lessonId, lessonQuestions) ->
            val first = lessonQuestions.first()
            val topicId = first.topic?.id.orEmpty()
            val position = (countPerTopic[topicId] ?: 0) + 1
            countPerTopic[topicId] = position

            LessonSummaryDto(
                id = lessonId,
                topicId = topicId,
                name = first.lesson?.name.orEmpty(),
                order = position,
                isPublic = true,
                questionCount = lessonQuestions.size,
                totalPoints = lessonQuestions.sumOf { it.points },
                description = null
            )
        }
    }
}

// delete if we donćt add GET /api/lessons
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