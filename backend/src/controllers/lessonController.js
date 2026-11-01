
const mongoose = require("mongoose");

const Lesson = require("../models/lessonModel");
const Subject = require("../models/subjectModel");
const Classroom = require("../models/classroomModel");

const {
  getLessonStatuses,
} = require("../services/lessonUnlockService");

const MAX_TOPIC_IDS = 200;

/**
 * GET /api/lessons?topicIds=id1,id2
 *
 * STUDENT:
 * - PRACTICE lekcije so vidne vsem učencem.
 * - SHARED lekcije so vidne za predmet in razred.
 * - CLASSROOM lekcije vidi učenec svojega učitelja.
 * - Za seznam lekcij Assignment ni potreben.
 *
 * TEACHER:
 * - Vidi PRACTICE, SHARED in lastne CLASSROOM lekcije.
 *
 * SHARED lekcije dobijo status:
 * DONE / CURRENT / LOCKED in stars.
 */
async function listLessons(req, res, next) {
  try {
    const { topicIds } = req.query;

    if (
      typeof topicIds !== "string" ||
      !topicIds.trim()
    ) {
      return res.status(400).json({
        message: "Parameter topicIds je obvezen.",
      });
    }

    const ids = [
      ...new Set(
        topicIds
          .split(",")
          .map((id) => id.trim())
          .filter(Boolean)
      ),
    ];

    if (
      ids.length === 0 ||
      ids.length > MAX_TOPIC_IDS ||
      !ids.every((id) =>
        /^[0-9a-fA-F]{24}$/.test(id)
      )
    ) {
      return res.status(400).json({
        message:
          "topicIds mora vsebovati od 1 do 200 veljavnih ID-jev tem.",
      });
    }

    const requestedTopicIds = ids.map(
      (id) => new mongoose.Types.ObjectId(id)
    );

    let accessFilter;

    if (req.user.role === "STUDENT") {
      const accessRules = [
        { scope: "PRACTICE" },
      ];

      if (req.user.classroomId) {
        const classroom = await Classroom.findById(
          req.user.classroomId
        ).lean();

        if (classroom) {
          const subjects = await Subject.find({
            _id: {
              $in: classroom.subjectIds || [],
            },
          })
            .select("grades")
            .lean();

          const allowedTopicIds = new Set();

          for (const subject of subjects) {
            for (const grade of subject.grades || []) {
              if (
                grade.gradeNumber !==
                classroom.gradeNumber
              ) {
                continue;
              }

              for (const topic of grade.topics || []) {
                allowedTopicIds.add(
                  String(topic._id)
                );
              }
            }
          }

          const allowedTopicObjectIds = [
            ...allowedTopicIds,
          ].map(
            (id) => new mongoose.Types.ObjectId(id)
          );

          accessRules.push({
            scope: "SHARED",
            topicId: {
              $in: allowedTopicObjectIds,
            },
          });

          accessRules.push({
            scope: "CLASSROOM",
            authorId: classroom.teacherId,
            topicId: {
              $in: allowedTopicObjectIds,
            },
          });
        }
      }

      accessFilter = {
        $or: accessRules,
      };

    } else if (req.user.role === "TEACHER") {
      accessFilter = {
        $or: [
          { scope: "PRACTICE" },
          { scope: "SHARED" },
          {
            scope: "CLASSROOM",
            authorId: req.user._id,
          },
        ],
      };

    } else {
      return res.status(403).json({
        message: "Nimate dostopa do lekcij.",
      });
    }

    const lessons = await Lesson.find({
      $and: [
        {
          topicId: {
            $in: requestedTopicIds,
          },
        },
        accessFilter,
      ],
    })
      .select(
        "topicId name order scope questions.points"
      )
      .sort({
        topicId: 1,
        order: 1,
        name: 1,
      })
      .lean();

    let statuses = new Map();

    if (req.user.role === "STUDENT") {
      statuses = await getLessonStatuses({
        studentId: req.user._id,
        topicIds: requestedTopicIds,
      });
    }

    const result = lessons.map((lesson) => {
      const questions = lesson.questions || [];

      const totalPoints = questions.reduce(
        (sum, question) =>
          sum + (Number(question.points) || 0),
        0
      );

      const lessonStatus = statuses.get(
        String(lesson._id)
      );

      const usesUnlocking =
        req.user.role === "STUDENT" &&
        lesson.scope === "SHARED";

      return {
        id: String(lesson._id),
        topicId: String(lesson.topicId),
        name: lesson.name,
        order: lesson.order ?? 0,

        scope: lesson.scope,
        isPublic: lesson.scope === "PRACTICE",

        questionCount: questions.length,
        totalPoints,
        description: null,

        status: usesUnlocking
          ? (lessonStatus?.status ?? "LOCKED")
          : null,

        stars: usesUnlocking
          ? (lessonStatus?.stars ?? 0)
          : 0,

        bestAccuracy: usesUnlocking
          ? (lessonStatus?.bestAccuracy ?? null)
          : null,
      };
    });

    return res.status(200).json({
      lessons: result,
    });

  } catch (error) {
    next(error);
  }
}

module.exports = {
  listLessons,
};