
const mongoose = require("mongoose");
const Attempt = require("../models/attemptModel");

function attemptError(message, status = 400) {
  const error = new Error(message);
  error.status = status;
  return error;
}

/**
 * Shrani odgovor, ki ga je backend že preveril.
 *
 * To funkcijo bo poklical Answer validation controller.
 * Podatek isCorrect se NE sme vzeti neposredno iz
 * frontend requesta.
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

  const included = attempt.questionIds.some(
    (id) => String(id) === String(questionId)
  );

  if (!included) {
    throw attemptError(
      "Vprašanje ne pripada temu poskusu.",
      400
    );
  }

  const alreadyAnswered = attempt.answers.some(
    (a) => String(a.questionId) === String(questionId)
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
 * Shrani končni rezultat, ki ga je izračunal Calculate score backend.
 *
 * points in accuracy morata biti izračunana na strežniku, ne poslana iz Android aplikacije.
 */
async function finishStoredAttempt({
  attemptId,
  studentId,
  points,
  accuracy,
}) {
  if (
    !mongoose.isValidObjectId(attemptId) ||
    !mongoose.isValidObjectId(studentId)
  ) {
    throw attemptError("Neveljaven ID.");
  }

  if (
    typeof points !== "number" ||
    !Number.isFinite(points) ||
    points < 0 ||
    typeof accuracy !== "number" ||
    !Number.isFinite(accuracy) ||
    accuracy < 0 ||
    accuracy > 100
  ) {
    throw attemptError("Neveljaven rezultat kviza.");
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

  if (attempt.answers.length !== attempt.questionIds.length) {
    throw attemptError(
      "Vsa vprašanja še niso odgovorjena.",
      400
    );
  }

  const updated = await Attempt.findOneAndUpdate(
    {
      _id: attemptId,
      studentId,
      finishedAt: null,
      "answers.0": { $exists: true },
    },
    {
      $set: {
        points,
        accuracy,
        finishedAt: new Date(),
      },
    },
    { new: true, runValidators: true }
  );

  if (!updated) {
    throw attemptError(
      "Poskusa ni mogoče zaključiti.",
      409
    );
  }

  return updated;
}

module.exports = {
  saveValidatedAnswer,
  finishStoredAttempt,
};
