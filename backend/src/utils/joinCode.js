const crypto = require("crypto");
const Classroom = require("../models/classroomModel");

const PATTERN = /^ZN-[1-9][A-Z]{2}\d{2}$/;
const LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

function normalizeJoinCode(code) {
  return String(code).trim().toUpperCase();
}

function isWellFormedJoinCode(code) {
  return PATTERN.test(normalizeJoinCode(code));
}

function departmentLetter(department) {
  const tokens = String(department)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .split(/\s+/);
  const letter = (tokens[tokens.length - 1] || "").charAt(0).toUpperCase();
  return /[A-Z]/.test(letter) ? letter : "A";
}

function randomCode(gradeNumber, department) {
  const extra = LETTERS[crypto.randomInt(26)];
  const digits = crypto.randomInt(10, 100); 
  return `ZN-${gradeNumber}${departmentLetter(department)}${extra}${digits}`;
}

async function generateUniqueJoinCode(gradeNumber, department) {
  for (let i = 0; i < 20; i++) {
    const code = randomCode(gradeNumber, department);
    if (!(await Classroom.exists({ joinCode: code }))) return code;
  }
  throw new Error("Ni uspelo ustvariti enolične kode razreda.");
}

module.exports = { normalizeJoinCode, isWellFormedJoinCode, generateUniqueJoinCode };