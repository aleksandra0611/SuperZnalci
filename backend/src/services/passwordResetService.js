const crypto = require("node:crypto");
const bcrypt = require("bcryptjs");

const User = require("../models/userModel");
const PasswordReset = require("../models/passwordResetModel");

const {
  sendPasswordResetEmail,
} = require("./emailService");

const CODE_TTL_MS = 15 * 60 * 1000;
const RESEND_COOLDOWN_MS = 60 * 1000;
const MAX_ATTEMPTS = 5;
const SALT_ROUNDS = 10;

function normalizeEmail(email) {
  return email.trim().toLowerCase();
}

function createCode() {
  return String(
    crypto.randomInt(0, 1000000)
  ).padStart(6, "0");
}

function hashCode(userId, code) {
  const secret = process.env.RESET_CODE_SECRET;

  if (!secret || secret.length < 32) {
    throw new Error(
      "RESET_CODE_SECRET mora imeti vsaj 32 znakov."
    );
  }

  return crypto
    .createHmac("sha256", secret)
    .update(`${String(userId)}:${code}`)
    .digest("hex");
}

async function requestPasswordReset(email) {
  const normalizedEmail = normalizeEmail(email);

  const user = await User.findOne({
    email: normalizedEmail,
  })
    .select("_id email")
    .lean();

  if (!user) {
    return;
  }

  const now = new Date();
  const code = createCode();

  const payload = {
    codeHash: hashCode(user._id, code),
    expiresAt: new Date(
      now.getTime() + CODE_TTL_MS
    ),
    attempts: 0,
    lastRequestedAt: now,
  };

  const cooldownBoundary = new Date(
    now.getTime() - RESEND_COOLDOWN_MS
  );

  let updated = await PasswordReset.findOneAndUpdate(
    {
      userId: user._id,
      lastRequestedAt: {
        $lte: cooldownBoundary,
      },
    },
    {
      $set: payload,
    },
    {
      returnDocument: "after",
    }
  );

  if (!updated) {
    try {
      updated = await PasswordReset.create({
        userId: user._id,
        ...payload,
      });
    } catch (error) {
      if (error.code === 11000) {
        return;
      }
      throw error;
    }
  }

  try {
    await sendPasswordResetEmail({
      to: user.email,
      code,
    });
  } catch (error) {
    console.error(
      "Pošiljanje reset e-pošte ni uspelo:",
      error.message
    );
  }
}

async function resetPassword({
  email,
  code,
  newPassword,
}) {
  const normalizedEmail = normalizeEmail(email);

  const user = await User.findOne({
    email: normalizedEmail,
  })
    .select("_id")
    .lean();

  if (!user) {
    return false;
  }

  const now = new Date();
  const codeHash = hashCode(user._id, code);

  const passwordHash = await bcrypt.hash(
    newPassword,
    SALT_ROUNDS
  );

  const consumed = await PasswordReset.findOneAndDelete({
    userId: user._id,
    codeHash,
    expiresAt: { $gt: now },
    attempts: { $lt: MAX_ATTEMPTS },
  });

  if (!consumed) {
    await PasswordReset.updateOne(
      {
        userId: user._id,
        expiresAt: { $gt: now },
        attempts: { $lt: MAX_ATTEMPTS },
      },
      {
        $inc: { attempts: 1 },
      }
    );

    return false;
  }

  await User.updateOne(
    { _id: user._id },
    {
      $set: {
        passwordHash,
      },
    }
  );

  return true;
}

module.exports = {
  requestPasswordReset,
  resetPassword,
};