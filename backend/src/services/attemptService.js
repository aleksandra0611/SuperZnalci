const mongoose = require("mongoose");
const Attempt = require("../models/attemptModel");
const Lesson = require("../models/lessonModel");
const {
  calculateScore,
} = require("./scoreService");

function attemptError(message, status = 400) {
  const error = new Error(message);
  error.status = status;
  return error;
}

/**
 * Shrani odgovor, ki ga je backend že preveril.
 */
async function saveValidatedAnswer({
  attemptId,
  studentId,
  questionId,
  givenAnswer,
  isCorrect,
  points,
}) {
  if (
    !mongoose.isValidObjectId(attemptId) ||
    !mongoose.isValidObjectId(studentId) ||
    !mongoose.isValidObjectId(questionId)
  ) {
    throw attemptError("Neveljaven ID.");
  }

  if (
    typeof isCorrect !== "boolean" ||
    typeof points !== "number" ||
    !Number.isFinite(points) ||
    points < 0
  ) {
    throw attemptError("Neveljavni podatki odgovora.");
  }

  const attempt = await Attempt.findOne({
    _id: attemptId,
    studentId,
  });

  if (!attempt) {
    throw attemptError("Poskus ne obstaja.", 404);
  }

  if (attempt.finishedAt !== null) {
    throw attemptError("Poskus je že zaključen.", 409);
  }

  const included = (attempt.questionIds || []).some(
    (id) => String(id) === String(questionId)
  );

  if (!included) {
    throw attemptError(
      "Vprašanje ne pripada temu poskusu."
    );
  }

  const alreadyAnswered = attempt.answers.some(
    (answer) =>
      String(answer.questionId) === String(questionId)
  );

  if (alreadyAnswered) {
    throw attemptError(
      "Na to vprašanje ste že odgovorili.",
      409
    );
  }

  const updated = await Attempt.findOneAndUpdate(
    {
      _id: attemptId,
      studentId,
      finishedAt: null,
      questionIds: questionId,
      "answers.questionId": { $ne: questionId },
    },
    {
      $push: {
        answers: {
          questionId,
          givenAnswer,
          isCorrect,
          points,
        },
      },
    },
    { new: true, runValidators: true }
  );

  if (!updated) {
    throw attemptError(
      "Odgovora ni mogoče shraniti.",
      409
    );
  }

  return updated;
}

/**
 * Zaključi attempt:
 * - preveri odgovore
 * - izračuna rezultat na backendu
 * - shrani points, accuracy in finishedAt
 */
async function finishStoredAttempt({
  attemptId,
  studentId,
}) {
  if (
    !mongoose.isValidObjectId(attemptId) ||
    !mongoose.isValidObjectId(studentId)
  ) {
    throw attemptError("Neveljaven ID.");
  }

  const attempt = await Attempt.findOne({
    _id: attemptId,
    studentId,
  }).lean();

  if (!attempt) {
    throw attemptError(
      "Poskus ne obstaja.",
      404
    );
  }

  if (attempt.finishedAt !== null) {
    throw attemptError(
      "Poskus je že zaključen.",
      409
    );
  }

  const lesson = await Lesson.findById(
    attempt.lessonId
  ).lean();

  if (!lesson) {
    throw attemptError(
      "Lekcija ne obstaja.",
      404
    );
  }

  const result = calculateScore(attempt, lesson);

  const finishedAt = new Date();

  const updated = await Attempt.findOneAndUpdate(
    {
      _id: attemptId,
      studentId,
      finishedAt: null,
      "answers": {
        $size: result.totalQuestions,
      },
    },
    {
      $set: {
        points: result.points,
        accuracy: result.accuracy,
        finishedAt,
      },
    },
    {
      new: true,
      runValidators: true,
    }
  );

  if (!updated) {
    throw attemptError(
      "Poskusa ni mogoče zaključiti.",
      409
    );
  }

  return {
    attempt: updated,
    result,
  };
}

module.exports = {
  saveValidatedAnswer,
  finishStoredAttempt,
};