const express = require("express");

const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");

const {
  startAttempt,
  listMyAttempts,
  submitAnswer,
  finishAttempt,
} = require("../controllers/attemptController");

const {
  getAttemptMistakes,
} = require("../controllers/mistakesController");

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

router.post(
  "/attempts/:attemptId/finish",
  requireRole("STUDENT"),
  finishAttempt
);

router.get(
  "/attempts/:attemptId/mistakes",
  requireRole("STUDENT"),
  getAttemptMistakes
);

router.get(
  "/attempts",
  requireRole("STUDENT"),
  listMyAttempts
);

module.exports = router;