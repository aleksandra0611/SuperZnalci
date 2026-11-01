const express = require("express");
const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");
const { listQuestions, generateQuiz } = require("../controllers/questionController");

const { createQuestion, deleteQuestion } = require("../controllers/teacherQuestionController");

const router = express.Router();

router.use(authenticate);

router.get(
  "/quiz",
  requireRole("STUDENT", "TEACHER"),
  generateQuiz
);

router.get("/", requireRole("TEACHER"), listQuestions);
router.post("/", requireRole("TEACHER"), createQuestion);
router.delete("/:id", requireRole("TEACHER"), deleteQuestion);

module.exports = router;
