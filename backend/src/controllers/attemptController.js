
const mongoose = require("mongoose");

const Attempt = require("../models/attemptModel");
const Lesson = require("../models/lessonModel");
const Subject = require("../models/subjectModel");
const Classroom = require("../models/classroomModel");
const Assignment = require("../models/assignmentModel");

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

module.exports = {
  startAttempt,
  listMyAttempts,
};
