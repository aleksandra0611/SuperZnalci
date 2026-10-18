const { isWellFormedJoinCode } = require("./joinCode");

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD = 8;
const MAX_PASSWORD = 72;
const MAX_AVATAR = 300;

const isNonEmptyString = (v) => typeof v === "string" && v.trim().length > 0;
const cleanName = (v) => String(v).trim().replace(/\s+/g, " ");

function validateCommon(body) {
  const errors = [];
  if (!isNonEmptyString(body.name) || cleanName(body.name).length < 2 || cleanName(body.name).length > 100) {
    errors.push("Ime je obvezno (2 do 100 znakov).");
  }
  if (!isNonEmptyString(body.email) || !EMAIL_REGEX.test(body.email.trim())) {
    errors.push("E-pošta ni veljavna.");
  }
  if (typeof body.password !== "string" || body.password.length < MIN_PASSWORD || body.password.length > MAX_PASSWORD) {
    errors.push(`Geslo mora imeti od ${MIN_PASSWORD} do ${MAX_PASSWORD} znakov.`);
  }
  return errors;
}

function validateStudentRegistration(body) {
  const errors = validateCommon(body);
  if (!isNonEmptyString(body.joinCode) || !isWellFormedJoinCode(body.joinCode)) {
    errors.push("Koda razreda ni v pravilni obliki (npr. ZN-8AK42).");
  }
  return errors;
}

function validateTeacherRegistration(body) {
  const errors = validateCommon(body);
  if (!isNonEmptyString(body.classroomName) || cleanName(body.classroomName).length > 150) {
    errors.push("Naziv učilnice je obvezen (do 150 znakov).");
  }
  if (!Number.isInteger(body.gradeNumber) || body.gradeNumber < 1 || body.gradeNumber > 9) {
    errors.push("Razred mora biti število od 1 do 9.");
  }
  if (!isNonEmptyString(body.department)) {
    errors.push("Oddelek je obvezen.");
  }
  if (!Array.isArray(body.subjects) || !body.subjects.every((s) => typeof s === "string")) {
    errors.push("Predmeti morajo biti seznam imen.");
  }
  return errors;
}

function validateLogin(body) {
  const errors = [];
  if (!isNonEmptyString(body.email)) {
    errors.push("E-pošta je obvezna.");
  }
  if (typeof body.password !== "string" || body.password.length === 0) {
    errors.push("Geslo je obvezno.");
  }
  if (
    body.role !== undefined &&
    !(typeof body.role === "string" && ["STUDENT", "TEACHER"].includes(body.role.toUpperCase()))
  ) {
    errors.push("Vloga mora biti STUDENT ali TEACHER.");
  }
  return errors;
}

function validateProfileUpdate(body) {
  const errors = [];
  const hasName = body.name !== undefined;
  const hasAvatar = body.avatar !== undefined;

  if (!hasName && !hasAvatar) {
    errors.push("Ni podatkov za posodobitev (name ali avatar).");
  }
  if (hasName) {
    if (!isNonEmptyString(body.name) || cleanName(body.name).length < 2 || cleanName(body.name).length > 100) {
      errors.push("Ime je obvezno (2 do 100 znakov).");
    }
  }
  // avatar je lahko niz ali null (odstranitev avatarja)
  if (hasAvatar && body.avatar !== null) {
    if (typeof body.avatar !== "string" || body.avatar.trim().length === 0 || body.avatar.length > MAX_AVATAR) {
      errors.push(`Avatar mora biti niz do ${MAX_AVATAR} znakov ali null.`);
    }
  }
  return errors;
}

function validatePasswordChange(body) {
  const errors = [];
  if (typeof body.currentPassword !== "string" || body.currentPassword.length === 0) {
    errors.push("Trenutno geslo je obvezno.");
  }
  if (
    typeof body.newPassword !== "string" ||
    body.newPassword.length < MIN_PASSWORD ||
    body.newPassword.length > MAX_PASSWORD
  ) {
    errors.push(`Novo geslo mora imeti od ${MIN_PASSWORD} do ${MAX_PASSWORD} znakov.`);
  } else if (body.newPassword === body.currentPassword) {
    errors.push("Novo geslo mora biti drugačno od trenutnega.");
  }
  return errors;
}

function validateEmailChange(body) {
  const errors = [];
  if (!isNonEmptyString(body.email) || !EMAIL_REGEX.test(body.email.trim())) {
    errors.push("E-pošta ni veljavna.");
  }
  if (typeof body.currentPassword !== "string" || body.currentPassword.length === 0) {
    errors.push("Trenutno geslo je obvezno.");
  }
  return errors;
}

module.exports = {
  cleanName,
  validateStudentRegistration,
  validateTeacherRegistration,
  validateLogin,
  validateProfileUpdate,
  validatePasswordChange,
  validateEmailChange,
};