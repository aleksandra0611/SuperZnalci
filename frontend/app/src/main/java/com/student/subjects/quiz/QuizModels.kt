package com.example.superznalci
import com.google.gson.JsonElement

object QuizConfig {
    const val USE_REAL_LESSONS_ENDPOINT = false

    const val QUESTIONS_PER_QUIZ = 7
    const val START_HEARTS = 5
}

class QuestionsEnvelope(val questions: List<QuestionDto>?)

data class NamedRefDto(val id: String? = null, val name: String? = null)

data class QuestionDto(
    val id: String? = null,
    val type: String? = null,
    val text: String? = null,
    val answers: JsonElement? = null,
    val correctAnswers: JsonElement? = null,
    val explanation: String? = null,
    val points: Int = 0,
    val image: String? = null,
    val subjectId: String? = null,
    val gradeNumber: Int = 0,
    val topic: NamedRefDto? = null,
    val lesson: NamedRefDto? = null
)

enum class QuestionType { MULTIPLE_CHOICE, MULTIPLE_SELECT, TRUE_FALSE, FILL_BLANK, IMAGE_QUESTION, MATCHING, ORDERING, UNKNOWN }

enum class AnswerOutcome { CORRECT, WRONG, SKIPPED }

data class QuizQuestion(
    val id: String,
    val type: QuestionType,
    val text: String,
    val answers: List<String>,
    val correctAnswers: List<String>,
    val explanation: String?,
    val hint: String?,
    val points: Int,
    val image: String?,
    val topicName: String,
    val lessonId: String?
)

fun JsonElement?.asStringList(): List<String> {
    if (this == null || !isJsonArray) return emptyList()
    return asJsonArray.mapNotNull { e ->
        if (e.isJsonPrimitive) e.asString else null
    }
}

data class QuizAnswer(val questionId: String, val isCorrect: Boolean)

data class QuizAttempt(
    val lessonId: String?,
    val answers: List<QuizAnswer>,
    val points: Int
)

data class QuizSummary(
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val xp: Int,
    val heartsLeft: Int
)

interface QuizRepository {
    fun loadQuestions(
        subjectId: String,
        gradeNumber: Int?,
        lessonId: String?,
        practice: Boolean,  // true = Vadnica lesson (scope PRACTICE)
        callback: ResultCallback<List<QuizQuestion>>
    )

    fun submitAttempt(attempt: QuizAttempt, callback: ResultCallback<Unit>)
}

object QuizMapper {

    private val TRUE_VALUES = setOf("true", "drži", "da", "1", "yes")

    fun typeFor(raw: String?): QuestionType {
        val t = raw.orEmpty().uppercase()
        return when {
            "TRUE" in t || "FALSE" in t -> QuestionType.TRUE_FALSE
            "SELECT" in t -> QuestionType.MULTIPLE_SELECT
            "MULTIPLE" in t || "CHOICE" in t -> QuestionType.MULTIPLE_CHOICE
            "BLANK" in t || "FILL" in t -> QuestionType.FILL_BLANK
            "IMAGE" in t -> QuestionType.IMAGE_QUESTION
            "MATCH" in t || "PAIR" in t -> QuestionType.MATCHING
            "ORDER" in t || "SEQUENCE" in t -> QuestionType.ORDERING
            else -> QuestionType.UNKNOWN
        }
    }

    fun toQuestion(dto: QuestionDto): QuizQuestion? {
        val id = dto.id ?: return null
        val text = dto.text.orEmpty().trim()
        if (text.isEmpty()) return null

        var type = typeFor(dto.type)
        var answers = dto.answers.asStringList().map { it.trim() }.filter { it.isNotEmpty() }
        var correct = dto.correctAnswers.asStringList().map { it.trim() }

        when (type) {
            QuestionType.TRUE_FALSE -> {
                val first = correct.firstOrNull()?.lowercase() ?: ""
                answers = listOf("true", "false")
                correct = listOf(if (first in TRUE_VALUES) "true" else "false")
            }
            QuestionType.MULTIPLE_CHOICE ->
                if (answers.size < 2 || correct.isEmpty() || correct.any { it !in answers }) type = QuestionType.UNKNOWN
            QuestionType.MULTIPLE_SELECT ->
                if (answers.size < 2 || correct.isEmpty() || correct.any { it !in answers }) type = QuestionType.UNKNOWN
            QuestionType.FILL_BLANK ->
                if (correct.isEmpty()) type = QuestionType.UNKNOWN
            QuestionType.IMAGE_QUESTION ->
                if (correct.isEmpty()) type = QuestionType.UNKNOWN
            else -> Unit
        }

        return QuizQuestion(
            id = id,
            type = type,
            text = text,
            answers = answers,
            correctAnswers = correct,
            explanation = dto.explanation?.trim()?.takeIf { it.isNotEmpty() },
            hint = null,
            points = dto.points,
            image = dto.image,
            topicName = dto.topic?.name.orEmpty(),
            lessonId = dto.lesson?.id
        )
    }
}