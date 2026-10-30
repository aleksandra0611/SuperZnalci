const express = require("express");
const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");
const { listQuestions, generateQuiz } = require("../controllers/questionController");

const router = express.Router();

router.use(authenticate);

router.get(
  "/quiz",
  requireRole("STUDENT", "TEACHER"),
  generateQuiz
);

router.get("/", listQuestions);

module.exports = router;
