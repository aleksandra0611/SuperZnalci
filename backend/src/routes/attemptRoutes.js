const express = require("express");

const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");

const {
  startAttempt,
  listMyAttempts,
  submitAnswer,
} = require("../controllers/attemptController");

const router = express.Router();

router.use(authenticate);

router.post(
  "/lessons/:lessonId/attempts",
  requireRole("STUDENT"),
  startAttempt
);

router.post(
  "/attempts/:attemptId/answers",
  requireRole("STUDENT"),
  submitAnswer
);

router.get(
  "/attempts",
  requireRole("STUDENT"),
  listMyAttempts
);

module.exports = router;
