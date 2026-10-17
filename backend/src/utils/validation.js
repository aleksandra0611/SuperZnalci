const { isWellFormedJoinCode } = require("./joinCode");

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD = 8;
const MAX_PASSWORD = 72; 

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

module.exports = { cleanName, validateStudentRegistration, validateTeacherRegistration };