const bcrypt = require("bcryptjs");
const User = require("../models/userModel");
const { toUserDto } = require("../utils/dto");
const {
  cleanName,
  validateProfileUpdate,
  validatePasswordChange,
  validateEmailChange,
} = require("../utils/validation");

const SALT_ROUNDS = 10;
const DUPLICATE_KEY = 11000;

// GET /api/users/me
async function getMe(req, res) {
  return res.status(200).json({ user: toUserDto(req.user) });
}

// PATCH /api/users/me
async function updateMe(req, res, next) {
  try {
    const body = req.body || {};

    const errors = validateProfileUpdate(body);
    if (errors.length) return res.status(400).json({ message: errors[0], errors });

    const updates = {};
    if (body.name !== undefined) updates.name = cleanName(body.name);
    if (body.avatar !== undefined) {
      updates.avatar = body.avatar === null ? null : body.avatar.trim();
    }

    const user = await User.findByIdAndUpdate(req.user._id, updates, { new: true });
    return res.status(200).json({ user: toUserDto(user) });
  } catch (err) {
    next(err);
  }
}

// PATCH /api/users/me/password
async function changePassword(req, res, next) {
  try {
    const body = req.body || {};

    const errors = validatePasswordChange(body);
    if (errors.length) return res.status(400).json({ message: errors[0], errors });

    const user = await User.findById(req.user._id).select("+passwordHash");

    const ok = await bcrypt.compare(body.currentPassword, user.passwordHash);
    if (!ok) {
      return res.status(400).json({ message: "Trenutno geslo ni pravilno." });
    }

    user.passwordHash = await bcrypt.hash(body.newPassword, SALT_ROUNDS);
    await user.save();

    return res.status(200).json({ message: "Geslo je spremenjeno." });
  } catch (err) {
    next(err);
  }
}

// PATCH /api/users/me/email
async function changeEmail(req, res, next) {
  try {
    const body = req.body || {};

    const errors = validateEmailChange(body);
    if (errors.length) return res.status(400).json({ message: errors[0], errors });

    const email = body.email.trim().toLowerCase();
    const user = await User.findById(req.user._id).select("+passwordHash");

    const ok = await bcrypt.compare(body.currentPassword, user.passwordHash);
    if (!ok) {
      return res.status(400).json({ message: "Trenutno geslo ni pravilno." });
    }

    if (email === user.email) {
      return res.status(400).json({ message: "Nova e-pošta je enaka trenutni." });
    }
    if (await User.exists({ email, _id: { $ne: user._id } })) {
      return res.status(409).json({ message: "E-pošta je že registrirana." });
    }

    user.email = email;
    await user.save();

    return res.status(200).json({ user: toUserDto(user) });
  } catch (err) {
    if (err.code === DUPLICATE_KEY) {
      return res.status(409).json({ message: "E-pošta je že registrirana." });
    }
    next(err);
  }
}

module.exports = { getMe, updateMe, changePassword, changeEmail };