const Subject = require("../models/subjectModel");
const Lesson = require("../models/lessonModel");
const { QUESTION_TYPES, LESSON_SCOPES } = require("../models/lessonModel");
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

module.exports = { listQuestions };
