const express = require("express");
const { registerStudent, registerTeacher, login, me } = require("../controllers/authController");
const { forgotPassword, resetPasswordHandler } = require("../controllers/passwordResetController");
const { authenticate } = require("../middleware/auth");
const rateLimit = require("express-rate-limit");

const router = express.Router();

const forgotPasswordLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: "draft-8",
  legacyHeaders: false,
  message: {
    message: "Preveč zahtevkov. Poskusite pozneje.",
  },
});

const resetPasswordLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 20,
  standardHeaders: "draft-8",
  legacyHeaders: false,
  message: {
    message: "Preveč poskusov. Poskusite pozneje.",
  },
});

router.post("/register/student", registerStudent);
router.post("/register/teacher", registerTeacher);
router.post("/login", login);
router.post("/forgot-password", forgotPasswordLimiter, forgotPassword);
router.post("/reset-password", resetPasswordLimiter, resetPasswordHandler);
router.get("/me", authenticate, me);

module.exports = router;