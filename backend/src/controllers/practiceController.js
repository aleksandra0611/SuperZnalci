const Subject = require("../models/subjectModel");
const Lesson = require("../models/lessonModel");
const { toPracticeSubjectDto } = require("../utils/dto");

// Vadnica: osnovna vsebina (javne lekcije), dostopna vsem prijavljenim, brez učilnice

// GET /api/practice/subjects?gradeNumber=8
// Vrne predmete, ki imajo v razredu vsaj eno javno lekcijo, s številom tem, lekcij in vprašanj.
async function listPracticeSubjects(req, res, next) {
  try {
    const gradeNumber = Number(req.query.gradeNumber);
    if (req.query.gradeNumber === undefined || !Number.isInteger(gradeNumber) || gradeNumber < 1 || gradeNumber > 9) {
      return res.status(400).json({ message: "Razred mora biti število od 1 do 9." });
    }

    const subjects = await Subject.find({ "grades.gradeNumber": gradeNumber }).sort({ name: 1 }).lean();
    const topicIds = subjects.flatMap((s) =>
      s.grades.filter((g) => g.gradeNumber === gradeNumber).flatMap((g) => g.topics.map((t) => t._id))
    );

    // število javnih lekcij in vprašanj po temah
    const counts = await Lesson.aggregate([
      { $match: { topicId: { $in: topicIds }, isPublic: true } },
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

module.exports = { listPracticeSubjects };
