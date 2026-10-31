const mongoose = require("mongoose");

const Attempt = require("../models/attemptModel");
const Lesson = require("../models/lessonModel");
const { calculateStars } = require("./scoreService");


const UNLOCK_MIN_ACCURACY = 50;

const LESSON_STATUS = {
  DONE: "DONE",       // zaključena z vsaj UNLOCK_MIN_ACCURACY %
  CURRENT: "CURRENT", // odklenjena, še ni opravljena
  LOCKED: "LOCKED",   // prejšnja lekcija še ni opravljena
};


async function getLessonStatuses({ studentId, topicIds }) {
  const statuses = new Map();

  if (!mongoose.isValidObjectId(studentId) || !topicIds || topicIds.length === 0) {
    return statuses;
  }

  const lessons = await Lesson.find(
    { topicId: { $in: topicIds }, scope: "SHARED" },
    { topicId: 1, order: 1 }
  )
    .sort({ order: 1, _id: 1 })
    .lean();

  if (lessons.length === 0) {
    return statuses;
  }

  const best = await Attempt.aggregate([
    {
      $match: {
        studentId: new mongoose.Types.ObjectId(String(studentId)),
        lessonId: { $in: lessons.map((l) => l._id) },
        finishedAt: { $ne: null },
      },
    },
    { $group: { _id: "$lessonId", bestAccuracy: { $max: "$accuracy" } } },
  ]);

  const bestByLesson = new Map(
    best.map((b) => [String(b._id), b.bestAccuracy])
  );

  // lekcije so že urejene po vrstnem redu, odklepamo po temah
  const previousPassed = new Map(); // topicId -> ali je prejšnja lekcija opravljena

  for (const lesson of lessons) {
    const topicId = String(lesson.topicId);
    const bestAccuracy = bestByLesson.has(String(lesson._id))
      ? bestByLesson.get(String(lesson._id))
      : null;
    const passed = bestAccuracy !== null && bestAccuracy >= UNLOCK_MIN_ACCURACY;
    const unlocked = !previousPassed.has(topicId) || previousPassed.get(topicId);

    let status = LESSON_STATUS.LOCKED;
    if (passed) status = LESSON_STATUS.DONE;
    else if (unlocked) status = LESSON_STATUS.CURRENT;

    statuses.set(String(lesson._id), {
      status,
      stars: bestAccuracy === null ? 0 : calculateStars(bestAccuracy),
      bestAccuracy,
    });

    previousPassed.set(topicId, passed);
  }

  return statuses;
}


async function isLessonUnlocked({ studentId, lesson }) {
  const statuses = await getLessonStatuses({
    studentId,
    topicIds: [lesson.topicId],
  });

  const lessonStatus = statuses.get(String(lesson._id));

  return Boolean(lessonStatus) && lessonStatus.status !== LESSON_STATUS.LOCKED;
}

module.exports = {
  UNLOCK_MIN_ACCURACY,
  LESSON_STATUS,
  getLessonStatuses,
  isLessonUnlocked,
};
