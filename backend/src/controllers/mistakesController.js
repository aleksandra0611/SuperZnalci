const mongoose = require("mongoose");

const Attempt = require("../models/attemptModel");
const Lesson = require("../models/lessonModel");

/**
 * GET /api/attempts/:attemptId/mistakes
 *
 * Vrne napačne odgovore prijavljenega učenca za posamezen zaključen kviz
 */
async function getAttemptMistakes(req, res, next) {
  try {
    const { attemptId } = req.params;

    if (!mongoose.isValidObjectId(attemptId)) {
      return res.status(400).json({
        message: "Neveljaven attemptId.",
      });
    }

    const attempt = await Attempt.findOne({
      _id: attemptId,
      studentId: req.user._id,
    }).lean();

    if (!attempt) {
      return res.status(404).json({
        message: "Poskus ne obstaja.",
      });
    }

    if (!attempt.finishedAt) {
      return res.status(409).json({
        message: "Kviz še ni zaključen.",
      });
    }

    const lesson = await Lesson.findById(
      attempt.lessonId
    ).lean();

    if (!lesson) {
      return res.status(404).json({
        message: "Lekcija ne obstaja.",
      });
    }

    const questionMap = new Map(
      (lesson.questions || []).map((question) => [
        String(question._id),
        question,
      ])
    );

    const answerMap = new Map(
      (attempt.answers || []).map((answer) => [
        String(answer.questionId),
        answer,
      ])
    );

    const mistakes = [];

    for (const questionObjectId of attempt.questionIds || []) {
      const questionId = String(questionObjectId);
      const answer = answerMap.get(questionId);

      if (!answer) {
        return res.status(409).json({
          message: "Poskus nima vseh shranjenih odgovorov.",
        });
      }

      if (answer.isCorrect === true) {
        continue;
      }

      const question = questionMap.get(questionId);

      if (!question) {
        return res.status(409).json({
          message:
            "Vprašanje iz poskusa ne obstaja več v lekciji.",
        });
      }

      mistakes.push({
        questionId,
        type: question.type,
        text: question.text,

        answers: question.answers ?? null,

        givenAnswer: answer.givenAnswer,

        correctAnswers: question.correctAnswers,

        explanation: question.explanation ?? null,

        image: question.image ?? null,
        difficulty: question.difficulty ?? null,

        isCorrect: false,
        earnedPoints: answer.points,
        maxPoints: question.points,
      });
    }

    const totalQuestions = (attempt.questionIds || []).length;
    const incorrectCount = mistakes.length;
    const correctCount = totalQuestions - incorrectCount;

    return res.status(200).json({
      attemptId: String(attempt._id),
      lessonId: String(lesson._id),
      lessonName: lesson.name,

      date: attempt.date,
      finishedAt: attempt.finishedAt,
      isPractice: attempt.isPractice,

      points: attempt.points,
      accuracy: attempt.accuracy,

      totalQuestions,
      correctCount,
      incorrectCount,

      mistakes,
    });

  } catch (error) {
    next(error);
  }
}

module.exports = {
  getAttemptMistakes,
};