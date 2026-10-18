const express = require("express");
const { registerStudent, registerTeacher, login, me } = require("../controllers/authController");
const { authenticate } = require("../middleware/auth");

const router = express.Router();

router.post("/register/student", registerStudent);
router.post("/register/teacher", registerTeacher);
router.post("/login", login);
router.get("/me", authenticate, me);

module.exports = router;