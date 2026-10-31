/**
 * Izračun rezultata kviza.
 *
 * Podatke dobimo iz Attempt dokumenta,
 * ne iz request bodyja.
 */

function scoreError(message, status = 400) {
  const error = new Error(message);
  error.status = status;
  return error;
}

function calculateStars(accuracy) {
  if (accuracy >= 90) return 3;
  if (accuracy >= 70) return 2;
  if (accuracy >= 50) return 1;
  return 0;
}

function calculateScore(attempt, lesson) {
  if (!attempt || !lesson) {
    throw scoreError("Manjkajo podatki kviza.");
  }

  const questionIds = (attempt.questionIds || [])
    .map((id) => String(id));

  const answers = attempt.answers || [];

  if (questionIds.length === 0) {
    throw scoreError(
      "Poskus nima shranjenih vprašanj."
    );
  }

  if (new Set(questionIds).size !== questionIds.length) {
    throw scoreError(
      "Poskus vsebuje podvojena vprašanja."
    );
  }

  if (answers.length !== questionIds.length) {
    throw scoreError(
      "Vsa vprašanja še niso odgovorjena."
    );
  }

  const selectedIds = new Set(questionIds);

  const lessonQuestions = new Map(
    (lesson.questions || []).map((question) => [
      String(question._id),
      question,
    ])
  );

  let totalPoints = 0;
  let maxPoints = 0;
  let correctCount = 0;

  const answeredIds = new Set();

  for (const answer of answers) {
    const questionId = String(answer.questionId);

    if (
      !selectedIds.has(questionId) ||
      answeredIds.has(questionId)
    ) {
      throw scoreError(
        "Poskus vsebuje neveljavne ali podvojene odgovore."
      );
    }

    answeredIds.add(questionId);

    const question = lessonQuestions.get(questionId);

    if (!question) {
      throw scoreError(
        "Vprašanje iz poskusa ne obstaja več v lekciji.",
        409
      );
    }

    if (
      !Number.isFinite(question.points) ||
      question.points < 0
    ) {
      throw scoreError(
        "Vprašanje ima neveljavne točke."
      );
    }

    if (
      typeof answer.isCorrect !== "boolean" ||
      !Number.isFinite(answer.points) ||
      answer.points < 0
    ) {
      throw scoreError(
        "Poskus vsebuje neveljaven rezultat odgovora."
      );
    }

    // Pri Answer validation sva določili:
    // pravilen odgovor = vse točke vprašanja,
    // napačen odgovor = 0 točk.
    const expectedPoints = answer.isCorrect
      ? question.points
      : 0;

    if (answer.points !== expectedPoints) {
      throw scoreError(
        "Točke odgovora se ne ujemajo z vprašanjem.",
        409
      );
    }

    maxPoints += question.points;
    totalPoints += answer.points;

    if (answer.isCorrect) {
      correctCount++;
    }
  }

  const totalQuestions = questionIds.length;
  const incorrectCount = totalQuestions - correctCount;

  const accuracy = Number(
    ((correctCount / totalQuestions) * 100).toFixed(2)
  );

  return {
    points: totalPoints,
    maxPoints,
    accuracy,
    stars: calculateStars(accuracy),
    correctCount,
    incorrectCount,
    totalQuestions,
  };
}

module.exports = {
  calculateScore,
  calculateStars,
};