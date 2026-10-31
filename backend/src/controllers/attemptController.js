const mongoose = require("mongoose");

const Attempt = require("../models/attemptModel");
const Lesson = require("../models/lessonModel");
const Subject = require("../models/subjectModel");
const Classroom = require("../models/classroomModel");
const Assignment = require("../models/assignmentModel");
const { validateAnswer } = require("../services/answerValidationService");
const { saveValidatedAnswer } = require("../services/attemptService");
const { finishStoredAttempt } = require("../services/attemptService");
const { awardPointsForAttempt } = require("../services/pointsService");
const { calculateScore } = require("../services/scoreService");
const { awardRewardsForAttempt } = require("../services/rewardsService");

// POST /api/lessons/:lessonId/attempts
async function startAttempt(req, res, next) {
  try {
    const { lessonId } = req.params;

    if (!mongoose.isValidObjectId(lessonId)) {
      return res.status(400).json({
        message: "Neveljaven id lekcije.",
      });
    }

    const lesson = await Lesson.findById(lessonId).lean();

    if (!lesson) {
      return res.status(404).json({
        message: "Lekcija ne obstaja.",
      });
    }

    if (!lesson.questions || lesson.questions.length === 0) {
      return res.status(400).json({
        message: "Lekcija nima vprašanj.",
      });
    }

    // Preverimo dostop.
    // PRACTICE je dostopen vsakemu prijavljenemu učencu
    if (lesson.scope !== "PRACTICE") {
      if (!req.user.classroomId) {
        return res.status(403).json({
          message: "Učenec ni v nobeni učilnici.",
        });
      }

      const classroom = await Classroom.findById(
        req.user.classroomId
      ).lean();

      if (!classroom) {
        return res.status(403).json({
          message: "Učilnica ne obstaja.",
        });
      }

      const subject = await Subject.findOne({
        "grades.topics._id": lesson.topicId,
      }).lean();

      if (!subject) {
        return res.status(404).json({
          message: "Predmet za lekcijo ne obstaja.",
        });
      }

      const grade = subject.grades.find((g) =>
        g.topics.some(
          (t) => String(t._id) === String(lesson.topicId)
        )
      );

      const sameGrade =
        grade &&
        grade.gradeNumber === classroom.gradeNumber;

      const hasSubject = classroom.subjectIds.some(
        (id) => String(id) === String(subject._id)
      );

      const assignment = await Assignment.findOne({
        classroomId: classroom._id,
        lessonId: lesson._id,
      }).lean();

      const validAuthor =
        lesson.scope !== "CLASSROOM" ||
        String(lesson.authorId) ===
          String(classroom.teacherId);

      if (
        !sameGrade ||
        !hasSubject ||
        !assignment ||
        !validAuthor
      ) {
        return res.status(403).json({
          message: "Nimate dostopa do te lekcije.",
        });
      }
    }

    const questions = lesson.questions.map((question) => ({
      id: String(question._id),
      type: question.type,
      text: question.text,
      answers: question.answers,
      difficulty: question.difficulty,
      points: question.points,
      image: question.image,
      lessonId: String(lesson._id),
      lessonName: lesson.name,
    }));

    const attempt = await Attempt.create({
      studentId: req.user._id,
      lessonId: lesson._id,
      questionIds: lesson.questions.map((question) => question._id),
      points: 0,
      accuracy: 0,
      finishedAt: null,
      isPractice: lesson.scope === "PRACTICE",
      answers: [],
    });

    return res.status(201).json({
      message: "Kviz se je uspešno začel.",
      attemptId: String(attempt._id),
      lessonId: String(lesson._id),
      lessonName: lesson.name,
      isPractice: attempt.isPractice,
      count: questions.length,
      questions,
    });
  } catch (err) {
    next(err);
  }
}

// GET /api/attempts
// Vrne poskuse trenutno prijavljenega učenca
async function listMyAttempts(req, res, next) {
  try {
    const attempts = await Attempt.find({
      studentId: req.user._id,
    })
      .sort({ date: -1 })
      .lean();

    const result = attempts.map((attempt) => ({
      id: String(attempt._id),
      studentId: String(attempt.studentId),
      lessonId: String(attempt.lessonId),
      points: attempt.points,
      accuracy: attempt.accuracy,
      date: attempt.date,
      finishedAt: attempt.finishedAt,
      isPractice: attempt.isPractice,
      answers: attempt.answers.map((answer) => ({
        questionId: String(answer.questionId),
        givenAnswer: answer.givenAnswer,
        isCorrect: answer.isCorrect,
        points: answer.points,
      })),
    }));

    return res.status(200).json({
      attempts: result,
    });
  } catch (err) {
    next(err);
  }
}

/**
 * POST /api/attempts/:attemptId/answers
 *
 * Body:
 * {
 *   "questionId": "...",
 *   "givenAnswer": ...
 * }
 */
async function submitAnswer(req, res, next) {
  try {
    const { attemptId } = req.params;
    const { questionId, givenAnswer } = req.body || {};

    if (
      !mongoose.isValidObjectId(attemptId) ||
      !mongoose.isValidObjectId(questionId)
    ) {
      return res.status(400).json({
        message: "Neveljaven attemptId ali questionId.",
      });
    }

    if (
      !Object.prototype.hasOwnProperty.call(
        req.body || {},
        "givenAnswer"
      )
    ) {
      return res.status(400).json({
        message: "Manjka givenAnswer.",
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

    if (attempt.finishedAt !== null) {
      return res.status(409).json({
        message: "Poskus je že zaključen.",
      });
    }

    const included = (attempt.questionIds || []).some(
      (id) => String(id) === String(questionId)
    );

    if (!included) {
      return res.status(400).json({
        message: "Vprašanje ne pripada temu poskusu.",
      });
    }

    // Na vprašanje lahko odgovorimo samo enkrat
    const alreadyAnswered = attempt.answers.some(
      (answer) =>
        String(answer.questionId) === String(questionId)
    );

    if (alreadyAnswered) {
      return res.status(409).json({
        message: "Na to vprašanje ste že odgovorili.",
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

    const question = lesson.questions.find(
      (q) => String(q._id) === String(questionId)
    );

    if (!question) {
      return res.status(404).json({
        message: "Vprašanje ne obstaja.",
      });
    }

    let isCorrect;

    try {
      isCorrect = validateAnswer(
        question,
        givenAnswer
      );
    } catch (error) {
      if (error.status === 400) {
        return res.status(400).json({
          message: error.message,
        });
      }

      throw error;
    }

    const earnedPoints = isCorrect
      ? question.points
      : 0;

    const updatedAttempt = await saveValidatedAnswer({
      attemptId,
      studentId: req.user._id,
      questionId,
      givenAnswer,
      isCorrect,
      points: earnedPoints,
    });

    return res.status(200).json({
      message: "Odgovor je bil uspešno preverjen.",
      attemptId: String(updatedAttempt._id),
      questionId: String(question._id),
      isCorrect,
      earnedPoints,
      maxPoints: question.points,
      correctAnswers: question.correctAnswers,
      explanation: question.explanation,
      answeredCount: updatedAttempt.answers.length,
      totalQuestions: updatedAttempt.questionIds.length,
    });

  } catch (err) {
    if (err.status) {
      return res.status(err.status).json({
        message: err.message,
      });
    }

    next(err);
  }
}

/**
 * POST /api/attempts/:attemptId/finish
 *
 * Učenec zaključi kviz, izračuna rezultat in dodeli XP ter streak
 * Body ni potreben
 */
async function finishAttempt(req, res, next) {
  try {
    const { attemptId } = req.params;

    if (!mongoose.isValidObjectId(attemptId)) {
      return res.status(400).json({
        message: "Neveljaven attemptId.",
      });
    }

    let attempt;
    let result;

    const existingAttempt = await Attempt.findOne({
      _id: attemptId,
      studentId: req.user._id,
    }).lean();

    if (!existingAttempt) {
      return res.status(404).json({
        message: "Poskus ne obstaja.",
      });
    }

    if (existingAttempt.finishedAt) {
      attempt = existingAttempt;

      const lesson = await Lesson.findById(
        attempt.lessonId
      ).lean();

      if (!lesson) {
        return res.status(404).json({
          message: "Lekcija ne obstaja.",
        });
      }

      result = calculateScore(attempt, lesson);
    } else {
      try {
        const finished = await finishStoredAttempt({
          attemptId,
          studentId: req.user._id,
        });

        attempt = finished.attempt;
        result = finished.result;

      } catch (error) {
        if (error.status !== 409) {
          throw error;
        }

        const latest = await Attempt.findOne({
          _id: attemptId,
          studentId: req.user._id,
        }).lean();

        if (!latest || !latest.finishedAt) {
          throw error;
        }

        attempt = latest;

        const lesson = await Lesson.findById(
          attempt.lessonId
        ).lean();

        if (!lesson) {
          return res.status(404).json({
            message: "Lekcija ne obstaja.",
          });
        }

        result = calculateScore(attempt, lesson);
      }
    }

    const xpResult = await awardPointsForAttempt({
      attemptId,
      studentId: req.user._id,
    });

    const rewardsResult = await awardRewardsForAttempt({
      attemptId,
      studentId: req.user._id,
    });

    return res.status(200).json({
      message: "Kviz je uspešno zaključen.",

      attemptId: String(attempt._id),
      lessonId: String(attempt.lessonId),

      isPractice: attempt.isPractice,
      finishedAt: attempt.finishedAt,

      points: result.points,
      maxPoints: result.maxPoints,
      accuracy: result.accuracy,
      stars: result.stars,

      correctCount: result.correctCount,
      incorrectCount: result.incorrectCount,
      totalQuestions: result.totalQuestions,

      xpEarned: xpResult.xpEarned,
      totalXp: xpResult.totalXp,
      streak: xpResult.streak,
      xpAlreadyAwarded: xpResult.alreadyAwarded,

      newBadges: rewardsResult.newBadges,
      newBadgesCount: rewardsResult.newBadgesCount,
      totalBadges: rewardsResult.totalBadges,
    });

  } catch (err) {
    if (err.status) {
      return res.status(err.status).json({
        message: err.message,
      });
    }

    next(err);
  }
}

module.exports = {
  startAttempt,
  listMyAttempts,
  submitAnswer,
  finishAttempt,
};