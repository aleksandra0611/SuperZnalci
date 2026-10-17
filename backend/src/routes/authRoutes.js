const express = require("express");
const { registerStudent, registerTeacher } = require("../controllers/authController");

const router = express.Router();

router.post("/register/student", registerStudent);
router.post("/register/teacher", registerTeacher);

module.exports = router;