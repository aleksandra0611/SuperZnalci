const express = require("express");
const { authenticate } = require("../middleware/auth");
const { listSubjects, getSubject } = require("../controllers/subjectController");

const router = express.Router();

router.use(authenticate);

router.get("/", listSubjects);
router.get("/:subjectId", getSubject);

module.exports = router;