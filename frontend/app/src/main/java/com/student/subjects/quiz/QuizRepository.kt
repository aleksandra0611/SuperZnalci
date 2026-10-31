package com.example.superznalci

class RetrofitQuizRepository(private val api: ApiService) : QuizRepository {

    override fun loadQuestions(
        subjectId: String,
        gradeNumber: Int?,
        lessonId: String?,
        practice: Boolean,
        callback: ResultCallback<List<QuizQuestion>>
    ) {
        val scope = if (practice) "PRACTICE" else null
        api.questions(subjectId, gradeNumber, null, scope).go({ callback(ApiResult.Failure(it)) }) { envelope ->
            val all = envelope.questions.orEmpty().mapNotNull { QuizMapper.toQuestion(it) }
            val result = if (lessonId != null) {
                all.filter { it.lessonId == lessonId }
            } else {
                all.shuffled().take(QuizConfig.QUESTIONS_PER_QUIZ)
            }
            callback(ApiResult.Success(result))
        }
    }

    override fun submitAttempt(attempt: QuizAttempt, callback: ResultCallback<Unit>) {
        callback(ApiResult.Success(Unit))
    }
}