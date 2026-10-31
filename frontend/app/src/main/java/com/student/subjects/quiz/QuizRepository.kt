package com.example.superznalci

import com.google.gson.JsonElement

class RetrofitQuizRepository(private val api: ApiService) : QuizRepository {

    override fun startLesson(lessonId: String, callback: ResultCallback<QuizSession>) {
        api.startAttempt(lessonId).go({ callback(ApiResult.Failure(it)) }) { response ->
            val questions = response.questions.orEmpty().mapNotNull { QuizMapper.toQuestion(it, lessonId) }
            callback(ApiResult.Success(QuizSession(response.attemptId, questions)))
        }
    }

    override fun submitAnswer(
        attemptId: String,
        questionId: String,
        givenAnswer: JsonElement,
        callback: ResultCallback<AnswerResult>
    ) {
        api.submitAnswer(attemptId, AnswerRequest(questionId, givenAnswer))
            .go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(QuizMapper.toResult(it))) }
    }

    override fun finish(attemptId: String, callback: ResultCallback<FinishResultDto>) {
        api.finishAttempt(attemptId).go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(it)) }
    }
}
