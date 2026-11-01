const mongoose = require("mongoose");

const passwordResetSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: "User",
    required: true,
    unique: true,
  },

  codeHash: {
    type: String,
    required: true,
  },

  expiresAt: {
    type: Date,
    required: true,
  },

  attempts: {
    type: Number,
    default: 0,
    min: 0,
  },

  lastRequestedAt: {
    type: Date,
    required: true,
  },
});

module.exports = mongoose.model(
  "PasswordReset",
  passwordResetSchema
);