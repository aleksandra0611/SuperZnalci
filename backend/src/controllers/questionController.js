const Subject = require("../models/subjectModel");
const Lesson = require("../models/lessonModel");
const { QUESTION_TYPES, LESSON_SCOPES } = require("../models/lessonModel");
const Classroom = require("../models/classroomModel");
const mongoose = require("mongoose");
const { toQuestionDto } = require("../utils/dto");

const OBJECT_ID = /^[0-9a-fA-F]{24}$/;


async function listQuestions(req, res, next) {
  try {
    const { subjectId } = req.query;
    if (!subjectId || !OBJECT_ID.test(String(subjectId))) {
      return res.status(400).json({ message: "Parameter subjectId mora biti veljaven id predmeta." });
    }

    let gradeNumber = null;
    if (req.query.gradeNumber !== undefined) {
      gradeNumber = Number(req.query.gradeNumber);
      if (!Number.isInteger(gradeNumber) || gradeNumber < 1 || gradeNumber > 9) {
        return res.status(400).json({ message: "Razred mora biti število od 1 do 9." });
      }
    }

    let type = null;
    if (req.query.type !== undefined) {
      type = String(req.query.type).trim().toUpperCase();
      if (!QUESTION_TYPES.includes(type)) {
        return res
          .status(400)
          .json({ message: `Neveljavna vrsta vprašanja. Dovoljene: ${QUESTION_TYPES.join(", ")}.` });
      }
    }

    let scope = null;
    if (req.query.scope !== undefined) {
      scope = String(req.query.scope).trim().toUpperCase();
      if (!LESSON_SCOPES.includes(scope)) {
        return res.status(400).json({ message: `Neveljaven scope. Dovoljeni: ${LESSON_SCOPES.join(", ")}.` });
      }
    }

    const subject = await Subject.findById(subjectId).lean();
    if (!subject) {
      return res.status(404).json({ message: "Predmet ne obstaja." });
    }

    // topicId -> podatki o temi in razredu
    const topics = new Map();
    for (const g of subject.grades || []) {
      if (gradeNumber !== null && g.gradeNumber !== gradeNumber) continue;
      for (const t of g.topics || []) {
        topics.set(t._id.toString(), { id: t._id, name: t.name, gradeNumber: g.gradeNumber });
      }
    }
    if (topics.size === 0) {
      return res.status(200).json({ questions: [] });
    }

    const visibility =
      scope === null
        ? { $or: [{ scope: "SHARED" }, { authorId: req.user._id }] }
        : scope === "CLASSROOM"
          ? { scope, authorId: req.user._id }
          : { scope };

    const pipeline = [
      { $match: { topicId: { $in: [...topics.values()].map((t) => t.id) }, ...visibility } },
      { $sort: { topicId: 1, order: 1 } },
      { $unwind: "$questions" },
    ];
    if (type) {
      pipeline.push({ $match: { "questions.type": type } });
    }
    pipeline.push({
      $project: {
        _id: 0,
        lessonId: "$_id",
        lessonName: "$name",
        lessonScope: "$scope",
        topicId: 1,
        question: "$questions",
      },
    });

    const rows = await Lesson.aggregate(pipeline);
    const questions = rows.map((r) =>
      toQuestionDto(r.question, {
        subject,
        topic: topics.get(r.topicId.toString()),
        lesson: { id: r.lessonId, name: r.lessonName, scope: r.lessonScope },
      })
    );

    return res.status(200).json({ questions });
  } catch (err) {
    next(err);
  }
}


// GET /api/questions/quiz
async function generateQuiz(req, res, next) {
  try {
    const {
      subjectId,
      gradeNumber,
      topicId,
      lessonId,
      scope = "SHARED",
    } = req.query;

    if (!subjectId || !OBJECT_ID.test(String(subjectId))) {
      return res.status(400).json({
        message: "Parameter subjectId mora biti veljaven id predmeta.",
      });
    }

    const grade = Number(gradeNumber);

    if (!Number.isInteger(grade) || grade < 1 || grade > 9) {
      return res.status(400).json({
        message: "Razred mora biti število od 1 do 9.",
      });
    }

    if (topicId && !OBJECT_ID.test(String(topicId))) {
      return res.status(400).json({
        message: "Neveljaven id področja.",
      });
    }

    if (lessonId && !OBJECT_ID.test(String(lessonId))) {
      return res.status(400).json({
        message: "Neveljaven id lekcije.",
      });
    }

    const selectedScope = String(scope).trim().toUpperCase();

    if (!LESSON_SCOPES.includes(selectedScope)) {
      return res.status(400).json({
        message: `Neveljaven scope. Dovoljeni: ${LESSON_SCOPES.join(", ")}.`,
      });
    }

    const count = req.query.count === undefined
      ? 7
      : Number(req.query.count);

    if (!Number.isInteger(count) || count < 1 || count > 20) {
      return res.status(400).json({
        message: "Število vprašanj mora biti med 1 in 20.",
      });
    }

    const subject = await Subject.findById(subjectId).lean();

    if (!subject) {
      return res.status(404).json({
        message: "Predmet ne obstaja.",
      });
    }

    const topics = [];

    for (const gradeData of subject.grades || []) {
      if (gradeData.gradeNumber !== grade) continue;

      for (const topic of gradeData.topics || []) {
        if (topicId && topic._id.toString() !== String(topicId)) {
          continue;
        }

        topics.push(topic._id);
      }
    }

    if (topics.length === 0) {
      return res.status(200).json({
        subjectId,
        gradeNumber: grade,
        count: 0,
        questions: [],
      });
    }

    let visibility;

    if (selectedScope === "CLASSROOM") {
      if (req.user.role === "TEACHER") {
        visibility = {
          scope: "CLASSROOM",
          authorId: req.user._id,
        };
      } else if (req.user.role === "STUDENT") {
        if (!req.user.classroomId) {
          return res.status(403).json({
            message: "Niste član nobene učilnice.",
          });
        }

        const classroom = await Classroom.findById(
          req.user.classroomId
        ).lean();

        if (!classroom) {
          return res.status(404).json({
            message: "Učilnica ne obstaja.",
          });
        }

        visibility = {
          scope: "CLASSROOM",
          authorId: classroom.teacherId,
        };
      }
    } else {
      // PRACTICE and SHARED content is available to authenticated users
      visibility = {
        scope: selectedScope,
      };
    }

    const match = {
      topicId: { $in: topics },
      ...visibility,
    };

    if (lessonId) {
      match._id = new mongoose.Types.ObjectId(lessonId);
    }

    const questions = await Lesson.aggregate([
      { $match: match },

      { $unwind: "$questions" },

      { $sample: { size: count } },

      {
        $project: {
          _id: 0,
          id: { $toString: "$questions._id" },
          type: "$questions.type",
          text: "$questions.text",
          answers: "$questions.answers",
          difficulty: "$questions.difficulty",
          points: "$questions.points",
          image: "$questions.image",
          lessonId: { $toString: "$_id" },
          lessonName: "$name",
        },
      },
    ]);

    return res.status(200).json({
      subjectId,
      gradeNumber: grade,
      count: questions.length,
      questions,
    });

  } catch (err) {
    next(err);
  }
}

module.exports = {
  listQuestions,
  generateQuiz,
};
