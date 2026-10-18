const bcrypt = require("bcryptjs");
const User = require("../models/userModel");
const Classroom = require("../models/classroomModel");
const Subject = require("../models/subjectModel");
const { signToken } = require("../utils/token");
const { toUserDto, toClassroomDto } = require("../utils/dto");
const { normalizeJoinCode, generateUniqueJoinCode } = require("../utils/joinCode");
const {
  cleanName,
  validateStudentRegistration,
  validateTeacherRegistration,
  validateLogin,
} = require("../utils/validation");

const SALT_ROUNDS = 10;
const DUPLICATE_KEY = 11000;

const DUMMY_HASH = bcrypt.hashSync("navidezno-geslo", SALT_ROUNDS);

// POST /api/auth/register/student
async function registerStudent(req, res, next) {
  try {
    const body = req.body || {};

    const errors = validateStudentRegistration(body);
    if (errors.length) return res.status(400).json({ message: errors[0], errors });

    const name = cleanName(body.name);
    const email = body.email.trim().toLowerCase();
    const joinCode = normalizeJoinCode(body.joinCode);

    const classroom = await Classroom.findOne({ joinCode });
    if (!classroom) {
      return res.status(404).json({ message: "Razred s to kodo ne obstaja." });
    }

    if (await User.exists({ email })) {
      return res.status(409).json({ message: "E-pošta je že registrirana." });
    }

    const passwordHash = await bcrypt.hash(body.password, SALT_ROUNDS);
    const user = await User.create({
      name,
      email,
      passwordHash,
      role: "STUDENT",
      classroomId: classroom._id,
    });

    return res.status(201).json({ token: signToken(user), user: toUserDto(user) });
  } catch (err) {
    if (err.code === DUPLICATE_KEY) {
      return res.status(409).json({ message: "E-pošta je že registrirana." });
    }
    next(err);
  }
}

// POST /api/auth/register/teacher
async function registerTeacher(req, res, next) {
  try {
    const body = req.body || {};

    const errors = validateTeacherRegistration(body);
    if (errors.length) return res.status(400).json({ message: errors[0], errors });

    const name = cleanName(body.name);
    const email = body.email.trim().toLowerCase();
    const classroomName = cleanName(body.classroomName);

    if (await User.exists({ email })) {
      return res.status(409).json({ message: "E-pošta je že registrirana." });
    }

    const wanted = new Set(body.subjects.map((s) => s.trim().toLowerCase()));
    const allSubjects = await Subject.find({}, "_id name");
    const subjectIds = allSubjects
      .filter((s) => wanted.has(s.name.toLowerCase()))
      .map((s) => s._id);

    const passwordHash = await bcrypt.hash(body.password, SALT_ROUNDS);
    const user = await User.create({ name, email, passwordHash, role: "TEACHER" });

    let classroom;
    try {
      classroom = await Classroom.create({
        name: classroomName,
        teacherId: user._id,
        joinCode: await generateUniqueJoinCode(body.gradeNumber, body.department),
        gradeNumber: body.gradeNumber,
        subjectIds,
      });
    } catch (err) {
      await User.deleteOne({ _id: user._id });
      throw err;
    }

    return res.status(201).json({
      token: signToken(user),
      user: toUserDto(user),
      classroom: toClassroomDto(classroom),
    });
  } catch (err) {
    if (err.code === DUPLICATE_KEY && err.keyPattern && err.keyPattern.email) {
      return res.status(409).json({ message: "E-pošta je že registrirana." });
    }
    next(err);
  }
}

// POST /api/auth/login
async function login(req, res, next) {
  try {
    const body = req.body || {};

    const errors = validateLogin(body);
    if (errors.length) return res.status(400).json({ message: errors[0], errors });

    const email = body.email.trim().toLowerCase();

    // passwordHash je v modelu skrit (select: false), zato ga tu izrecno zahtevamo
    const user = await User.findOne({ email }).select("+passwordHash");

    const passwordOk = await bcrypt.compare(
      body.password,
      user ? user.passwordHash : DUMMY_HASH
    );
    const roleOk = !body.role || (user && user.role === body.role.toUpperCase());

    if (!user || !passwordOk || !roleOk) {
      return res.status(401).json({ message: "Napačna e-pošta, geslo ali vloga." });
    }

    const now = new Date();
    await User.updateOne({ _id: user._id }, { lastActiveAt: now });
    user.lastActiveAt = now;

    return res.status(200).json({ token: signToken(user), user: toUserDto(user) });
  } catch (err) {
    next(err);
  }
}

// GET /api/auth/me (zaščiteno, najprej teče authenticate)
async function me(req, res) {
  return res.status(200).json({ user: toUserDto(req.user) });
}

module.exports = { registerStudent, registerTeacher, login, me };