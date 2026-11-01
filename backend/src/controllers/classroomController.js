const Classroom = require("../models/classroomModel");
const User = require("../models/userModel");
const { toClassroomDto, toStudentDto } = require("../utils/dto");

const OBJECT_ID = /^[0-9a-fA-F]{24}$/;

// GET /api/classrooms (samo učitelj)
async function listMyClassrooms(req, res, next) {
  try {
    const classrooms = await Classroom.find({ teacherId: req.user._id });
    return res.status(200).json({ classrooms: classrooms.map(toClassroomDto) });
  } catch (err) {
    next(err);
  }
}

// GET /api/classrooms/me (samo učenec)
async function getMyClassroom(req, res, next) {
  try {
    if (!req.user.classroomId) {
      return res.status(404).json({
        message: "Nimate učilnice.",
      });
    }

    const classroom = await Classroom.findById(
      req.user.classroomId
    ).lean();

    if (!classroom) {
      return res.status(404).json({
        message: "Učilnica ne obstaja.",
      });
    }

    const [teacher, studentCount] = await Promise.all([
      User.findById(classroom.teacherId)
        .select("name")
        .lean(),

      User.countDocuments({
        classroomId: classroom._id,
        role: "STUDENT",
      }),
    ]);

    return res.status(200).json({
      classroom: {
        ...toClassroomDto(classroom),
        teacherName: teacher?.name ?? null,
        studentCount,
      },
    });

  } catch (error) {
    next(error);
  }
}

// GET /api/classrooms/:classroomId/students (samo učitelj, lastnik učilnice)
async function listClassroomStudents(req, res, next) {
  try {
    const { classroomId } = req.params;
    if (!OBJECT_ID.test(classroomId)) {
      return res.status(400).json({ message: "Neveljaven id učilnice." });
    }

    const classroom = await Classroom.findById(classroomId).lean();
    if (!classroom) {
      return res.status(404).json({ message: "Učilnica ne obstaja." });
    }
    if (!classroom.teacherId.equals(req.user._id)) {
      return res.status(403).json({ message: "Nimate pravice za to učilnico." });
    }

    const students = await User.find({ classroomId: classroom._id, role: "STUDENT" })
      .sort({ name: 1 })
      .lean();
    return res.status(200).json({ students: students.map(toStudentDto) });
  } catch (err) {
    next(err);
  }
}

module.exports = { listMyClassrooms, getMyClassroom, listClassroomStudents };