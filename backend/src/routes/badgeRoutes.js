const express = require("express");

const { authenticate } = require("../middleware/auth");
const { requireRole } = require("../middleware/roles");

const {
  listBadges,
  getMyBadges,
} = require("../controllers/badgeController");

const router = express.Router();

router.use(authenticate);

router.get("/", listBadges);

router.get(
  "/me",
  requireRole("STUDENT"),
  getMyBadges
);

module.exports = router;
