const Badge = require("../models/badgesModel");
const User = require("../models/userModel");

const {
  toBadgeDto,
} = require("../services/rewardsService");

/**
 * GET /api/badges
 */
async function listBadges(req, res, next) {
  try {
    const badges = await Badge.find({
      code: { $exists: true },
    })
      .sort({ name: 1 })
      .lean();

    // Za prijavljenega učenca preverimo, katere značke že ima
    let earnedMap = new Map();

    if (req.user.role === "STUDENT") {
      const user = await User.findById(
        req.user._id
      ).lean();

      if (!user) {
        return res.status(404).json({
          message: "Uporabnik ne obstaja.",
        });
      }

      earnedMap = new Map(
        (user.badges || []).map((entry) => [
          String(entry.badgeId),
          entry.awardedAt,
        ])
      );
    }

    const result = badges.map((badge) =>
      toBadgeDto(
        badge,
        earnedMap.get(String(badge._id)) || null
      )
    );

    return res.status(200).json({
      badges: result,
      count: result.length,
    });

  } catch (error) {
    next(error);
  }
}

/**
 * GET /api/badges/me
 */
async function getMyBadges(req, res, next) {
  try {
    const user = await User.findById(
      req.user._id
    ).lean();

    if (!user) {
      return res.status(404).json({
        message: "Uporabnik ne obstaja.",
      });
    }

    const earned = user.badges || [];

    const badgeIds = earned.map(
      (entry) => entry.badgeId
    );

    const badges = await Badge.find({
      _id: { $in: badgeIds },
    }).lean();

    const badgeMap = new Map(
      badges.map((badge) => [
        String(badge._id),
        badge,
      ])
    );

    const result = earned
      .map((entry) => {
        const badge = badgeMap.get(
          String(entry.badgeId)
        );

        return badge
          ? toBadgeDto(badge, entry.awardedAt)
          : null;
      })
      .filter(Boolean);

    return res.status(200).json({
      badges: result,
      count: result.length,
    });

  } catch (error) {
    next(error);
  }
}

module.exports = {
  listBadges,
  getMyBadges,
};
