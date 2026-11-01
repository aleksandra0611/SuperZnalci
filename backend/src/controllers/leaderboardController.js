const Classroom = require("../models/classroomModel");
const User = require("../models/userModel");
const Attempt = require("../models/attemptModel");

const OBJECT_ID = /^[0-9a-fA-F]{24}$/;
const TZ = "Europe/Ljubljana";

// Monday 00:00 in Slovenian time (a DST change inside the week can shift this by 1 h, which is fine for a ranking)
function weekStart(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: TZ,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(now);

  const get = (type) => Number(parts.find((p) => p.type === type).value);
  const y = get("year");
  const m = get("month") - 1;
  const d = get("day");

  const localAsUtc = Date.UTC(y, m, d, get("hour"), get("minute"), get("second"));
  const offset = localAsUtc - Math.floor(now.getTime() / 1000) * 1000;

  const dayOfWeek = new Date(Date.UTC(y, m, d)).getUTCDay(); // 0 = Sunday
  const sinceMonday = (dayOfWeek + 6) % 7;

  return new Date(Date.UTC(y, m, d - sinceMonday) - offset);
}

// GET /api/classrooms/:classroomId/leaderboard?period=week
async function getClassroomLeaderboard(req, res, next) {
  try {
    const { classroomId } = req.params;

    if (!OBJECT_ID.test(classroomId)) {
      return res.status(400).json({ message: "Neveljaven id učilnice." });
    }

    const period = String(req.query.period || "week").toLowerCase();
    if (period !== "week") {
      return res.status(400).json({ message: "Podprto je samo obdobje week." });
    }

    const classroom = await Classroom.findById(classroomId).lean();
    if (!classroom) {
      return res.status(404).json({ message: "Učilnica ne obstaja." });
    }

    const isOwner =
      req.user.role === "TEACHER" &&
      String(classroom.teacherId) === String(req.user._id);
    const isMember =
      req.user.role === "STUDENT" &&
      String(req.user.classroomId) === String(classroom._id);

    if (!isOwner && !isMember) {
      return res.status(403).json({ message: "Nimate dostopa do te učilnice." });
    }

    const students = await User.find({ classroomId: classroom._id, role: "STUDENT" })
      .select("_id name avatar")
      .lean();

    const rows = students.length
      ? await Attempt.aggregate([
          {
            $match: {
              studentId: { $in: students.map((s) => s._id) },
              finishedAt: { $gte: weekStart() },
            },
          },
          { $group: { _id: "$studentId", weeklyXp: { $sum: "$points" } } },
        ])
      : [];

    const xpByStudent = new Map(rows.map((r) => [String(r._id), r.weeklyXp]));

    const entries = students
      .map((s) => ({
        userId: String(s._id),
        name: s.name,
        avatar: s.avatar ?? null,
        weeklyXp: xpByStudent.get(String(s._id)) || 0,
      }))
      .sort((a, b) => b.weeklyXp - a.weeklyXp || a.name.localeCompare(b.name, "sl"));

    return res.status(200).json({
      leaderboard: { classroomId: String(classroom._id), period, entries },
    });
  } catch (err) {
    next(err);
  }
}

module.exports = { getClassroomLeaderboard };