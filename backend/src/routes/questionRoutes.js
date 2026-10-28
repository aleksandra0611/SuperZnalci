const express = require("express");
const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");
const { listQuestions } = require("../controllers/questionController");

const router = express.Router();

router.use(authenticate);

router.get("/", listQuestions);

module.exports = router;
