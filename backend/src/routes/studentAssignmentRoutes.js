const express = require("express");

const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");
const Assignment = require("../models/assignmentModel");

const router = express.Router();

// GET /api/assignments
// Vrne domače naloge prijavljenega učenca
router.get(
  "/",
  authenticate,
  requireRole("STUDENT"),
  async (req, res, next) => {
    try {
      if (!req.user.classroomId) {
        return res.status(200).json({
          assignments: [],
        });
      }

      const assignments = await Assignment.find({
        classroomId: req.user.classroomId,
      })
        .sort({ dueDate: 1 })
        .lean();

      const result = assignments.map((assignment) => ({
        id: String(assignment._id),
        classroomId: String(assignment.classroomId),
        lessonId: String(assignment.lessonId),

        assignedAt: assignment._id.getTimestamp(),

        availableFrom: assignment.availableFrom ?? null,
        dueDate: assignment.dueDate ?? null,
        allowRetry: assignment.allowRetry ?? true,
      }));

      return res.status(200).json({
        assignments: result,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;