const Subject = require("../models/subjectModel");
const Lesson = require("../models/lessonModel");
const Attempt = require("../models/attemptModel");
const { calculateStars } = require("../services/scoreService");
const { toPracticeSubjectDto, toPracticeSubjectDetailDto } = require("../utils/dto");

const OBJECT_ID = /^[0-9a-fA-F]{24}$/;

function parseGrade(value) {
  const gradeNumber = Number(value);
  if (value === undefined || !Number.isInteger(gradeNumber) || gradeNumber < 1 || gradeNumber > 9) {
    return null;
  }
  return gradeNumber;
}

// Vadnica: lekcije s scope PRACTICE, dostopne vsem prijavljenim, brez učilnice
async function listPracticeSubjects(req, res, next) {
  try {
    const gradeNumber = parseGrade(req.query.gradeNumber);
    if (gradeNumber === null) {
      return res.status(400).json({ message: "Razred mora biti število od 1 do 9." });
    }

    const subjects = await Subject.find({ "grades.gradeNumber": gradeNumber }).sort({ name: 1 }).lean();
    const topicIds = subjects.flatMap((s) =>
      s.grades.filter((g) => g.gradeNumber === gradeNumber).flatMap((g) => g.topics.map((t) => t._id))
    );

    // število lekcij in vprašanj Vadnice po temah
    const counts = await Lesson.aggregate([
      { $match: { topicId: { $in: topicIds }, scope: "PRACTICE" } },
      {
        $group: {
          _id: "$topicId",
          lessonCount: { $sum: 1 },
          questionCount: { $sum: { $size: "$questions" } },
        },
      },
    ]);
    const countsByTopic = new Map(counts.map((c) => [c._id.toString(), c]));

    const result = subjects
      .map((s) => toPracticeSubjectDto(s, gradeNumber, countsByTopic))
      .filter((s) => s.lessonCount > 0);

    return res.status(200).json({ gradeNumber, subjects: result });
  } catch (err) {
    next(err);
  }
}

// Teme razreda z lekcijami Vadnice. Za učenca doda najboljši rezultat (zvezdice) pri vsaki lekciji 8in napredek po temah. Upoštevajo se samo zaključeni poskusi.
async function getPracticeSubject(req, res, next) {
  try {
    const { subjectId } = req.params;
    if (!OBJECT_ID.test(subjectId)) {
      return res.status(400).json({ message: "Neveljaven id predmeta." });
    }

    const gradeNumber = parseGrade(req.query.gradeNumber);
    if (gradeNumber === null) {
      return res.status(400).json({ message: "Razred mora biti število od 1 do 9." });
    }

    const subject = await Subject.findById(subjectId).lean();
    if (!subject) {
      return res.status(404).json({ message: "Predmet ne obstaja." });
    }

    const topicIds = (subject.grades || [])
      .filter((g) => g.gradeNumber === gradeNumber)
      .flatMap((g) => g.topics.map((t) => t._id));

    // vprašanj ne potrebujemo, samo njihovo število
    const lessons = await Lesson.find(
      { topicId: { $in: topicIds }, scope: "PRACTICE" },
      { topicId: 1, name: 1, order: 1, "questions._id": 1 }
    )
      .sort({ order: 1 })
      .lean();

    // najboljši rezultat učenca po lekcijah (učitelj napredka nima)
    const bestByLesson = new Map();
    if (req.user.role === "STUDENT" && lessons.length > 0) {
      const best = await Attempt.aggregate([
        {
          $match: {
            studentId: req.user._id,
            lessonId: { $in: lessons.map((l) => l._id) },
            finishedAt: { $ne: null },
          },
        },
        { $group: { _id: "$lessonId", bestAccuracy: { $max: "$accuracy" }, bestPoints: { $max: "$points" } } },
      ]);
      for (const b of best) {
        bestByLesson.set(b._id.toString(), { ...b, stars: calculateStars(b.bestAccuracy) });
      }
    }

    return res.status(200).json(toPracticeSubjectDetailDto(subject, gradeNumber, lessons, bestByLesson));
  } catch (err) {
    next(err);
  }
}

module.exports = { listPracticeSubjects, getPracticeSubject };
