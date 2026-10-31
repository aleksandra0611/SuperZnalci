const Attempt = require("../models/attemptModel");
const Lesson = require("../models/lessonModel");
const Subject = require("../models/subjectModel");
const User = require("../models/userModel");

const RECENT_ATTEMPTS_LIMIT = 10;

function round2(value) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function calculateStars(accuracy) {
  if (accuracy >= 90) return 3;
  if (accuracy >= 70) return 2;
  if (accuracy >= 50) return 1;
  return 0;
}

function average(values) {
  if (values.length === 0) return 0;

  return round2(
    values.reduce((sum, value) => sum + value, 0) /
      values.length
  );
}

function slovenianDay(date) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Ljubljana",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);

  const get = (type) =>
    parts.find((part) => part.type === type).value;

  return Date.UTC(
    Number(get("year")),
    Number(get("month")) - 1,
    Number(get("day"))
  );
}

function getCurrentStreak(student) {
  if (!student.lastStreakAt || !student.streak) {
    return 0;
  }

  const today = slovenianDay(new Date());
  const lastDay = slovenianDay(
    new Date(student.lastStreakAt)
  );

  const daysDifference =
    Math.round((today - lastDay) / 86400000);

  return daysDifference >= 0 && daysDifference <= 1
    ? student.streak
    : 0;
}

/**
 * GET /api/students/me/statistics */
async function getMyStatistics(req, res, next) {
  try {
    const student = await User.findOne({
      _id: req.user._id,
      role: "STUDENT",
    }).lean();

    if (!student) {
      return res.status(404).json({
        message: "Učenec ne obstaja.",
      });
    }

    const attempts = await Attempt.find({
      studentId: student._id,
    })
      .sort({ date: -1 })
      .lean();

    const completedAttempts = attempts.filter(
      (attempt) => attempt.finishedAt != null
    );

    const unfinishedAttempts = attempts.filter(
      (attempt) => attempt.finishedAt == null
    );

    const lessonIds = [
      ...new Set(
        completedAttempts.map(
          (attempt) => String(attempt.lessonId)
        )
      ),
    ];

    const lessons = await Lesson.find({
      _id: { $in: lessonIds },
    })
      .select("_id name topicId scope")
      .lean();

    const lessonMap = new Map(
      lessons.map((lesson) => [
        String(lesson._id),
        lesson,
      ])
    );

    const subjects = await Subject.find({})
      .select("_id name icon grades")
      .lean();

    const topicMap = new Map();

    for (const subject of subjects) {
      for (const grade of subject.grades || []) {
        for (const topic of grade.topics || []) {
          topicMap.set(String(topic._id), {
            subjectId: String(subject._id),
            subjectName: subject.name,
            subjectIcon: subject.icon ?? null,
            gradeNumber: grade.gradeNumber,
            topicId: String(topic._id),
            topicName: topic.name,
          });
        }
      }
    }

    let totalPointsEarned = 0;
    let totalCorrectAnswers = 0;
    let totalIncorrectAnswers = 0;
    let totalAnsweredQuestions = 0;
    let perfectQuizzes = 0;
    let practiceQuizzes = 0;
    let assignedQuizzes = 0;

    const quizAccuracies = [];
    const completedLessonIds = new Set();

    const subjectStatsMap = new Map();

    const history = [];

    for (const attempt of completedAttempts) {
      const lessonId = String(attempt.lessonId);
      const lesson = lessonMap.get(lessonId);

      const topic = lesson
        ? topicMap.get(String(lesson.topicId))
        : null;

      const answers = attempt.answers || [];

      const correctCount = answers.filter(
        (answer) => answer.isCorrect === true
      ).length;

      const incorrectCount = answers.filter(
        (answer) => answer.isCorrect === false
      ).length;

      const answeredCount =
        correctCount + incorrectCount;

      const points = attempt.points || 0;
      const accuracy = attempt.accuracy || 0;

      totalPointsEarned += points;
      totalCorrectAnswers += correctCount;
      totalIncorrectAnswers += incorrectCount;
      totalAnsweredQuestions += answeredCount;

      quizAccuracies.push(accuracy);
      completedLessonIds.add(lessonId);

      if (accuracy === 100) {
        perfectQuizzes++;
      }

      if (attempt.isPractice) {
        practiceQuizzes++;
      } else {
        assignedQuizzes++;
      }

      if (topic) {
        const subjectId = topic.subjectId;

        if (!subjectStatsMap.has(subjectId)) {
          subjectStatsMap.set(subjectId, {
            subjectId,
            subjectName: topic.subjectName,
            subjectIcon: topic.subjectIcon,

            completedQuizzes: 0,
            completedLessonIds: new Set(),

            totalPoints: 0,
            correctAnswers: 0,
            incorrectAnswers: 0,
            answeredQuestions: 0,

            accuracies: [],
          });
        }

        const stats = subjectStatsMap.get(subjectId);

        stats.completedQuizzes++;
        stats.completedLessonIds.add(lessonId);
        stats.totalPoints += points;

        stats.correctAnswers += correctCount;
        stats.incorrectAnswers += incorrectCount;
        stats.answeredQuestions += answeredCount;

        stats.accuracies.push(accuracy);
      }

      history.push({
        attemptId: String(attempt._id),
        lessonId,
        lessonName: lesson?.name ?? null,

        subjectId: topic?.subjectId ?? null,
        subjectName: topic?.subjectName ?? null,
        topicName: topic?.topicName ?? null,

        gradeNumber: topic?.gradeNumber ?? null,

        isPractice: attempt.isPractice,

        date: attempt.date,
        finishedAt: attempt.finishedAt,

        points,
        accuracy,
        stars: calculateStars(accuracy),

        correctCount,
        incorrectCount,
        totalQuestions: answeredCount,
      });
    }

    const subjectStatistics = [
      ...subjectStatsMap.values(),
    ].map((stats) => ({
      subjectId: stats.subjectId,
      subjectName: stats.subjectName,
      subjectIcon: stats.subjectIcon,

      completedQuizzes: stats.completedQuizzes,
      completedLessons: stats.completedLessonIds.size,

      totalPoints: stats.totalPoints,

      correctAnswers: stats.correctAnswers,
      incorrectAnswers: stats.incorrectAnswers,
      answeredQuestions: stats.answeredQuestions,

      averageAccuracy: average(stats.accuracies),

      overallAccuracy:
        stats.answeredQuestions > 0
          ? round2(
              (stats.correctAnswers /
                stats.answeredQuestions) * 100
            )
          : 0,
    }));

    subjectStatistics.sort(
      (a, b) =>
        b.completedQuizzes - a.completedQuizzes ||
        a.subjectName.localeCompare(b.subjectName, "sl")
    );

    const completedQuizzes = completedAttempts.length;

    const averageAccuracy = average(quizAccuracies);

    const overallAccuracy =
      totalAnsweredQuestions > 0
        ? round2(
            (totalCorrectAnswers /
              totalAnsweredQuestions) * 100
          )
        : 0;

    const completedDates = completedAttempts
      .map((attempt) => attempt.finishedAt)
      .filter(Boolean);

    const lastCompletedAt =
      completedDates.length > 0
        ? new Date(
            Math.max(
              ...completedDates.map((date) =>
                new Date(date).getTime()
              )
            )
          )
        : null;

    return res.status(200).json({
      studentId: String(student._id),
      studentName: student.name,

      summary: {
        totalXp: student.totalXp ?? 0,
        streak: getCurrentStreak(student),

        earnedBadges: (student.badges || []).length,

        completedQuizzes,
        unfinishedQuizzes: unfinishedAttempts.length,

        practiceQuizzes,
        assignedQuizzes,

        completedLessons: completedLessonIds.size,
        perfectQuizzes,

        totalPointsEarned,

        totalCorrectAnswers,
        totalIncorrectAnswers,
        totalAnsweredQuestions,

        averageAccuracy,
        overallAccuracy,

        lastCompletedAt,
      },

      subjectStatistics,

      recentAttempts: history
        .sort(
          (a, b) =>
            new Date(b.finishedAt) -
            new Date(a.finishedAt)
        )
        .slice(0, RECENT_ATTEMPTS_LIMIT),
    });

  } catch (error) {
    next(error);
  }
}

module.exports = {
  getMyStatistics,
};