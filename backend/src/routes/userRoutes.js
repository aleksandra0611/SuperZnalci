const express = require("express");
const { authenticate } = require("../middleware/auth");
const { getMe, updateMe, changePassword, changeEmail } = require("../controllers/userController");

const router = express.Router();

router.use(authenticate);

router.get("/me", getMe);
router.patch("/me", updateMe);
router.patch("/me/password", changePassword);
router.patch("/me/email", changeEmail);

module.exports = router;