const Subject = require("../models/subjectModel");
const { toSubjectDto } = require("../utils/dto");

const MAX_IDS = 50;
const OBJECT_ID = /^[0-9a-fA-F]{24}$/;

// GET /api/subjects?ids=a,b&gradeNumber=8
async function listSubjects(req, res, next) {
  try {
    const filter = {};

    if (req.query.ids !== undefined) {
      const ids = String(req.query.ids)
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      if (ids.length === 0 || ids.length > MAX_IDS || !ids.every((id) => OBJECT_ID.test(id))) {
        return res
          .status(400)
          .json({ message: "Parameter ids mora biti seznam veljavnih id-jev, ločenih z vejico." });
      }
      filter._id = { $in: ids };
    }

    let gradeNumber = null;
    if (req.query.gradeNumber !== undefined) {
      gradeNumber = Number(req.query.gradeNumber);
      if (!Number.isInteger(gradeNumber) || gradeNumber < 1 || gradeNumber > 9) {
        return res.status(400).json({ message: "Razred mora biti število od 1 do 9." });
      }
      filter["grades.gradeNumber"] = gradeNumber;
    }

    const subjects = await Subject.find(filter).sort({ name: 1 }).lean();
    return res.status(200).json({ subjects: subjects.map((s) => toSubjectDto(s, gradeNumber)) });
  } catch (err) {
    next(err);
  }
}

// GET /api/subjects/:subjectId
async function getSubject(req, res, next) {
  try {
    const { subjectId } = req.params;
    if (!OBJECT_ID.test(subjectId)) {
      return res.status(400).json({ message: "Neveljaven id predmeta." });
    }

    const subject = await Subject.findById(subjectId).lean();
    if (!subject) {
      return res.status(404).json({ message: "Predmet ne obstaja." });
    }

    return res.status(200).json({ subject: toSubjectDto(subject) });
  } catch (err) {
    next(err);
  }
}

module.exports = { listSubjects, getSubject };