const mongoose = require('mongoose');

// Answer is embedded in the attempt
const answerSchema = new mongoose.Schema({
    questionId: { type: mongoose.Schema.Types.ObjectId, required: true }, // _id of a question inside Lesson.questions
    givenAnswer: { type: mongoose.Schema.Types.Mixed, default: null },
    isCorrect: { type: Boolean, required: true },
}, { _id: false });

const attemptSchema = new mongoose.Schema({
    studentId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    lessonId: { type: mongoose.Schema.Types.ObjectId, ref: 'Lesson', required: true },
    points: { type: Number, required: true, min: 0 },
    accuracy: { type: Number, required: true, min: 0, max: 100 }, // percent
    date: { type: Date, default: Date.now },
    answers: [answerSchema],
});

//studentId + lessonId: quickly finds all attempts for 1 student in 1 lesson
attemptSchema.index({ studentId: 1, lessonId: 1 });

//studentId + date: quickly finds a student’s most recent attempts
attemptSchema.index({ studentId: 1, date: -1 });

module.exports = mongoose.model('Attempt', attemptSchema);