const express = require("express");

const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");

const {
  getMyStatistics,
} = require("../controllers/studentStatisticsController");

const router = express.Router();

router.use(authenticate);

// Samo učenci lahko pregledujejo svojo statistiko
router.get(
  "/me/statistics",
  requireRole("STUDENT"),
  getMyStatistics
);

module.exports = router;
