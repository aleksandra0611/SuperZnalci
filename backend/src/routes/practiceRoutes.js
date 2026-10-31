const express = require("express");
const { authenticate } = require("../middleware/auth");
const { listPracticeSubjects, getPracticeSubject } = require("../controllers/practiceController");

const router = express.Router();

router.use(authenticate);

router.get("/subjects", listPracticeSubjects);
router.get("/subjects/:subjectId", getPracticeSubject);

module.exports = router;
