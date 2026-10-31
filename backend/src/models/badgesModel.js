const mongoose = require('mongoose');

const BADGE_CODES = [
  "FIRST_QUIZ",
  "XP_100",
  "XP_500",
  "STREAK_3",
  "STREAK_7",
  "PERFECT_QUIZ",
];

const badgeSchema = new mongoose.Schema({
    code: { type: String, enum: BADGE_CODES, required: true, unique: true },
    name: { type: String, required: true, unique: true, trim: true },
    condition: { type: String, required: true }, //npr "7-day streak"
});

module.exports = mongoose.model('Badge', badgeSchema);
module.exports.BADGE_CODES = BADGE_CODES;