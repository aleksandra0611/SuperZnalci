const Classroom = require("../models/classroomModel");
const { toClassroomDto } = require("../utils/dto");

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
      return res.status(404).json({ message: "Nimate učilnice." });
    }
    const classroom = await Classroom.findById(req.user.classroomId);
    if (!classroom) {
      return res.status(404).json({ message: "Učilnica ne obstaja." });
    }
    return res.status(200).json({ classroom: toClassroomDto(classroom) });
  } catch (err) {
    next(err);
  }
}

module.exports = { listMyClassrooms, getMyClassroom };