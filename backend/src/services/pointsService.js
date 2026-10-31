const mongoose = require("mongoose");

const User = require("../models/userModel");
const Attempt = require("../models/attemptModel");

const TIMEZONE = "Europe/Ljubljana";
const XP_PER_POINT = 1;

function pointsError(message, status = 400) {
  const error = new Error(message);
  error.status = status;
  return error;
}

/**
 * Dodeli XP za zaključen attempt.
 *
 * Funkcijo sme poklicati samo backend po
 * uspešnem zaključku kviza.
 *
 * Dodeljevanje je idempotentno:
 * isti attempt se lahko obdela večkrat,
 * vendar XP prejme samo enkrat.
 */
async function awardPointsForAttempt({
  attemptId,
  studentId,
}) {
  if (
    !mongoose.isValidObjectId(attemptId) ||
    !mongoose.isValidObjectId(studentId)
  ) {
    throw pointsError("Neveljaven ID.");
  }

  const attempt = await Attempt.findOne({
    _id: attemptId,
    studentId,
  }).lean();

  if (!attempt) {
    throw pointsError(
      "Poskus ne obstaja.",
      404
    );
  }

  // XP dodeljujemo samo za zaključen kviz.
  if (!attempt.finishedAt) {
    throw pointsError(
      "Kviz še ni zaključen.",
      409
    );
  }

  if (
    !Number.isFinite(attempt.points) ||
    attempt.points < 0
  ) {
    throw pointsError(
      "Poskus vsebuje neveljavne točke."
    );
  }

  const earnedXp =
    attempt.points * XP_PER_POINT;

  const now = attempt.finishedAt;

  const dayDifference = {
    $dateDiff: {
      startDate: {
        $ifNull: [
          "$lastStreakAt",
          new Date("1970-01-01T00:00:00.000Z"),
        ],
      },
      endDate: now,
      unit: "day",
      timezone: TIMEZONE,
    },
  };

  
  const newStreak = {
    $switch: {
      branches: [
        // Starejši attempt ali isti dan:
        // streak se ne spremeni
        {
          case: { $lte: [dayDifference, 0] },
          then: { $ifNull: ["$streak", 0] },
        },

        // Naslednji koledarski dan:
        // streak povečamo za 1
        {
          case: { $eq: [dayDifference, 1] },
          then: {
            $add: [
              { $ifNull: ["$streak", 0] },
              1,
            ],
          },
        },
      ],

      // Če je učenec izpustil dan,
      // se streak začne ponovno pri 1
      default: 1,
    },
  };

  // Pogoj preprečuje, da isti attempt dvakrat poveča XP
  const updatedUser = await User.findOneAndUpdate(
    {
      _id: studentId,
      role: "STUDENT",
      xpAwardedAttemptIds: {
        $ne: attempt._id,
      },
    },
    [
      {
        $set: {
          totalXp: {
            $add: [
              { $ifNull: ["$totalXp", 0] },
              earnedXp,
            ],
          },
          streak: newStreak,
          lastStreakAt: {
            $cond: [
              {
                $or: [
                  { $eq: ["$lastStreakAt", null] },
                  { $gt: [dayDifference, 0] }, 
                ],
              },
              now,
              "$lastStreakAt",
            ],
          },
          xpAwardedAttemptIds: {
            $concatArrays: [
              {
                $ifNull: [
                  "$xpAwardedAttemptIds",
                  [],
                ],
              },
              [attempt._id],
            ],
          },
        },
      },
    ],
    {
      returnDocument: "after",
      updatePipeline: true,
    }
  );

  if (!updatedUser) {
    const user = await User.findOne({
      _id: studentId,
      role: "STUDENT",
    }).lean();

    if (!user) {
      throw pointsError(
        "Učenec ne obstaja.",
        404
      );
    }

    const alreadyAwarded = (
      user.xpAwardedAttemptIds || []
    ).some(
      (id) => String(id) === String(attemptId)
    );

    if (!alreadyAwarded) {
      throw pointsError(
        "XP ni bilo mogoče dodeliti.",
        409
      );
    }

    return {
      xpEarned: 0,
      totalXp: user.totalXp,
      streak: user.streak,
      alreadyAwarded: true,
    };
  }

  return {
    xpEarned: earnedXp,
    totalXp: updatedUser.totalXp,
    streak: updatedUser.streak,
    alreadyAwarded: false,
  };
}

module.exports = {
  awardPointsForAttempt,
};