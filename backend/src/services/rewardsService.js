const mongoose = require("mongoose");

const User = require("../models/userModel");
const Attempt = require("../models/attemptModel");
const Badge = require("../models/badgesModel");

function rewardsError(message, status = 400) {
  const error = new Error(message);
  error.status = status;
  return error;
}

/**
 * Pretvori značko v JSON za frontend.
 */
function toBadgeDto(badge, awardedAt = null) {
  return {
    id: String(badge._id),
    code: badge.code,
    name: badge.name,
    condition: badge.condition,
    earned: awardedAt !== null,
    awardedAt,
  };
}

/**
 * Preveri, katere značke si je učenec prislužil.
 *
 * Funkcija se izvede po uspešnem
 * zaključku kviza in dodelitvi XP.
 */
async function awardRewardsForAttempt({
  attemptId,
  studentId,
}) {
  if (
    !mongoose.isValidObjectId(attemptId) ||
    !mongoose.isValidObjectId(studentId)
  ) {
    throw rewardsError("Neveljaven ID.");
  }

  // 1. Preverimo, ali attempt obstaja
  // in pripada prijavljenemu učencu.
  const attempt = await Attempt.findOne({
    _id: attemptId,
    studentId,
    finishedAt: { $ne: null },
  }).lean();

  if (!attempt) {
    throw rewardsError(
      "Zaključen poskus ne obstaja.",
      404
    );
  }

  // 2. Poiščemo učenca.
  const user = await User.findOne({
    _id: studentId,
    role: "STUDENT",
  }).lean();

  if (!user) {
    throw rewardsError(
      "Učenec ne obstaja.",
      404
    );
  }

  // Nagrade dodeljujemo šele, ko so
  // XP za ta attempt že zabeleženi.
  const xpProcessed = (
    user.xpAwardedAttemptIds || []
  ).some(
    (id) => String(id) === String(attemptId)
  );

  if (!xpProcessed) {
    throw rewardsError(
      "XP za ta poskus še niso bili obdelani.",
      409
    );
  }

  // 3. Preverimo število zaključenih kvizov.
  const completedCount = await Attempt.countDocuments({
    studentId,
    finishedAt: { $ne: null },
  });

  // 4. Določimo dosežene pogoje.
  const achievedCodes = new Set();

  if (completedCount >= 1) {
    achievedCodes.add("FIRST_QUIZ");
  }

  if (user.totalXp >= 100) {
    achievedCodes.add("XP_100");
  }

  if (user.totalXp >= 500) {
    achievedCodes.add("XP_500");
  }

  if (user.streak >= 3) {
    achievedCodes.add("STREAK_3");
  }

  if (user.streak >= 7) {
    achievedCodes.add("STREAK_7");
  }

  if (attempt.accuracy === 100) {
    achievedCodes.add("PERFECT_QUIZ");
  }

  // 5. Poiščemo definicije značk v katalogu.
  const badges = await Badge.find({
    code: { $in: [...achievedCodes] },
  }).lean();

  const missingCodes = [...achievedCodes].filter(
    (code) => !badges.some(
      (badge) => badge.code === code
    )
  );

  if (missingCodes.length > 0) {
    throw rewardsError(
      "Manjkajo značke v katalogu: " +
        missingCodes.join(", "),
      409
    );
  }

  // 6. Dodelimo značke.
  const newlyAwarded = [];

  for (const badge of badges) {
    const awardedAt = new Date();

    // Atomski pogoj:
    // značke ne dodamo, če jo uporabnik že ima.
    const updatedUser = await User.findOneAndUpdate(
      {
        _id: studentId,
        role: "STUDENT",
        "badges.badgeId": { $ne: badge._id },
      },
      {
        $push: {
          badges: {
            badgeId: badge._id,
            awardedAt,
          },
        },
      },
      {
        returnDocument: "after",
        runValidators: true,
      }
    );

    // Če je bila značka že osvojena,
    // findOneAndUpdate ne najde dokumenta.
    if (updatedUser) {
      newlyAwarded.push(
        toBadgeDto(badge, awardedAt)
      );
    }
  }

  // 7. Preberemo končno stanje uporabnika.
  const updatedUser = await User.findById(
    studentId
  ).lean();

  if (!updatedUser) {
    throw rewardsError(
      "Učenec ne obstaja.",
      404
    );
  }

  return {
    newBadges: newlyAwarded,
    newBadgesCount: newlyAwarded.length,
    totalBadges: (updatedUser.badges || []).length,
  };
}

module.exports = {
  awardRewardsForAttempt,
  toBadgeDto,
};
