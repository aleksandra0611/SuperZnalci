
const express = require("express");

const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");

const {
  startAttempt,
  listMyAttempts,
} = require("../controllers/attemptController");

const router = express.Router();

router.use(authenticate);

router.post(
  "/lessons/:lessonId/attempts",
  requireRole("STUDENT"),
  startAttempt
);

router.get(
  "/attempts",
  requireRole("STUDENT"),
  listMyAttempts
);

module.exports = router;
