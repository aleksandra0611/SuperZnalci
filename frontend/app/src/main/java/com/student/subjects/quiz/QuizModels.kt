package com.example.superznalci
import com.google.gson.JsonElement

object QuizConfig {
    const val USE_REAL_LESSONS_ENDPOINT = true

    const val START_HEARTS = 5
}

// GET /api/questions (teacher screens, lesson summaries)
class QuestionsEnvelope(val questions: List<QuestionDto>?)

data class NamedRefDto(val id: String? = null, val name: String? = null)

data class QuestionDto(
    val id: String? = null,
    val type: String? = null,
    val text: String? = null,
    val answers: JsonElement? = null,
    val correctAnswers: JsonElement? = null,  // never sent by the attempt API before answering
    val explanation: String? = null,
    val blankCount: Int? = null,              // attempt API: number of blanks of a FILL_IN_BLANK question
    val points: Int = 0,
    val image: String? = null,
    val subjectId: String? = null,
    val gradeNumber: Int = 0,
    val topic: NamedRefDto? = null,
    val lesson: NamedRefDto? = null
)

// --- attempt API: the server checks every answer, the phone never gets correct answers in advance ---
//   start    POST /api/lessons/{lessonId}/attempts      attemptId + questions without correct answers
//   answer   POST /api/attempts/{attemptId}/answers     { questionId, givenAnswer } -> correct?, correct answers, explanation
//   finish   POST /api/attempts/{attemptId}/finish      points, accuracy, stars, XP, streak, new badges

class StartAttemptResponse(
    val attemptId: String,
    val lessonId: String? = null,
    val lessonName: String? = null,
    val isPractice: Boolean = false,
    val questions: List<QuestionDto>? = null
)

// givenAnswer: a string, a list of strings or a list of { left, right } pairs, depending on the type
class AnswerRequest(val questionId: String, val givenAnswer: JsonElement)

class AnswerResultDto(
    val isCorrect: Boolean = false,
    val earnedPoints: Int = 0,
    val maxPoints: Int = 0,
    val correctAnswers: JsonElement? = null,
    val explanation: String? = null
)

class NewBadgeDto(val name: String? = null)

class FinishResultDto(
    val points: Int = 0,
    val maxPoints: Int = 0,
    val accuracy: Double = 0.0,
    val stars: Int = 0,
    val correctCount: Int = 0,
    val totalQuestions: Int = 0,
    val xpEarned: Int = 0,
    val totalXp: Int = 0,
    val streak: Int = 0,
    val xpAlreadyAwarded: Boolean = false,
    val newBadges: List<NewBadgeDto>? = null
)

enum class QuestionType { MULTIPLE_CHOICE, MULTIPLE_SELECT, TRUE_FALSE, FILL_BLANK, IMAGE_QUESTION, MATCHING, ORDERING, UNKNOWN }

enum class AnswerOutcome { CORRECT, WRONG, SKIPPED }

data class QuizQuestion(
    val id: String,
    val type: QuestionType,
    val text: String,
    val answers: List<String>,      // choice options
    val matchLeft: List<String>,    // MATCHING only
    val matchRight: List<String>,   // MATCHING only
    val blankCount: Int,            // FILL_BLANK / IMAGE_QUESTION: number of input fields
    val hint: String?,
    val points: Int,
    val image: String?,
    val topicName: String,
    val lessonId: String?
)

// What the server said about one answer
data class AnswerResult(
    val correct: Boolean,
    val points: Int,
    val correctAnswers: List<String>,           // choices / blanks
    val correctPairs: Map<String, String>,      // MATCHING: left -> right
    val explanation: String?
)

data class QuizSession(val attemptId: String, val questions: List<QuizQuestion>)

fun JsonElement?.asStringList(): List<String> {
    if (this == null || !isJsonArray) return emptyList()
    return asJsonArray.mapNotNull { e ->
        if (e.isJsonPrimitive) e.asString else null
    }
}

interface QuizRepository {
    fun startLesson(lessonId: String, callback: ResultCallback<QuizSession>)

    fun submitAnswer(
        attemptId: String,
        questionId: String,
        givenAnswer: JsonElement,
        callback: ResultCallback<AnswerResult>
    )

    fun finish(attemptId: String, callback: ResultCallback<FinishResultDto>)
}

object QuizMapper {

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

    fun toQuestion(dto: QuestionDto, lessonId: String?): QuizQuestion? {
        val id = dto.id ?: return null
        val text = dto.text.orEmpty().trim()
        if (text.isEmpty()) return null

        var type = typeFor(dto.type)
        // answers are sent back to the server exactly as received, so they are not trimmed
        val answers = dto.answers.asStringList().filter { it.isNotBlank() }
        var matchLeft = emptyList<String>()
        var matchRight = emptyList<String>()
        var blankCount = 0

        when (type) {
            QuestionType.TRUE_FALSE,
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.MULTIPLE_SELECT ->
                if (answers.size < 2) type = QuestionType.UNKNOWN
            QuestionType.FILL_BLANK -> {
                blankCount = dto.blankCount ?: 0
                if (blankCount < 1) type = QuestionType.UNKNOWN
            }
            QuestionType.IMAGE_QUESTION -> blankCount = 1
            QuestionType.MATCHING -> {
                val obj = dto.answers?.takeIf { it.isJsonObject }?.asJsonObject
                matchLeft = obj?.get("left").asStringList()
                matchRight = obj?.get("right").asStringList()
                if (matchLeft.isEmpty() || matchRight.size < matchLeft.size) type = QuestionType.UNKNOWN
            }
            else -> Unit
        }

        return QuizQuestion(
            id = id,
            type = type,
            text = text,
            answers = answers,
            matchLeft = matchLeft,
            matchRight = matchRight,
            blankCount = blankCount,
            hint = null,
            points = dto.points,
            image = dto.image,
            topicName = dto.topic?.name.orEmpty(),
            lessonId = lessonId
        )
    }

    fun toResult(dto: AnswerResultDto): AnswerResult {
        val pairs = mutableMapOf<String, String>()
        val list = mutableListOf<String>()
        dto.correctAnswers?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { e ->
            when {
                e.isJsonPrimitive -> list += e.asString
                e.isJsonObject -> {
                    val o = e.asJsonObject
                    val left = o.get("left")?.takeIf { it.isJsonPrimitive }?.asString
                    val right = o.get("right")?.takeIf { it.isJsonPrimitive }?.asString
                    if (left != null && right != null) pairs[left] = right
                }
            }
        }
        return AnswerResult(
            correct = dto.isCorrect,
            points = dto.earnedPoints,
            correctAnswers = list,
            correctPairs = pairs,
            explanation = dto.explanation?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
