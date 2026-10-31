const express = require("express");

const {
  listSchools,
} = require("../controllers/schoolController");

const router = express.Router();

router.get("/", listSchools);

module.exports = router;