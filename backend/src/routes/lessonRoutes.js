const express = require("express");

const { authenticate } = require("../middleware/auth");

const {
  listLessons,
} = require("../controllers/lessonController");

const router = express.Router();

router.use(authenticate);

// GET /api/lessons?topicIds=id1,id2
router.get("/", listLessons);

module.exports = router;