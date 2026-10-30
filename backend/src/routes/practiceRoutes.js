const express = require("express");
const { authenticate } = require("../middleware/auth");
const { listPracticeSubjects } = require("../controllers/practiceController");

const router = express.Router();

router.use(authenticate);

router.get("/subjects", listPracticeSubjects);

module.exports = router;
