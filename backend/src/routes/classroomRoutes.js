const express = require("express");
const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");
const {
  listMyClassrooms,
  getMyClassroom,
  listClassroomStudents,
} = require("../controllers/classroomController");

const router = express.Router();

router.use(authenticate); // vse poti spodaj zahtevajo prijavo

router.get("/", requireRole("TEACHER"), listMyClassrooms);
router.get("/me", requireRole("STUDENT"), getMyClassroom);
router.get("/:classroomId/students", requireRole("TEACHER"), listClassroomStudents);

module.exports = router;