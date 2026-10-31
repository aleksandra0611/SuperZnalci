const mongoose = require('mongoose');

// Answer is embedded in the attempt
const answerSchema = new mongoose.Schema({
    questionId: { type: mongoose.Schema.Types.ObjectId, required: true }, // _id of a question inside Lesson.questions
    givenAnswer: { type: mongoose.Schema.Types.Mixed, default: null },
    isCorrect: { type: Boolean, required: true },
    points: { type: Number, default: 0, min: 0 }, // points earned for this answer, kept even if the question's points change later
}, { _id: false });

const attemptSchema = new mongoose.Schema({
    studentId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    lessonId: { type: mongoose.Schema.Types.ObjectId, ref: 'Lesson', required: true },
    //the attempt is created when the lesson starts, points and accuracy are calculated when it is finished
    questionIds: [{
        type: mongoose.Schema.Types.ObjectId,
        required: true
    }],
    points: { type: Number, default: 0, min: 0 },
    accuracy: { type: Number, default: 0, min: 0, max: 100 }, // percent
    date: { type: Date, default: Date.now }, // when the lesson was started
    finishedAt: { type: Date, default: null }, // null = lesson still in progress, no more answers after it is set
    isPractice: { type: Boolean, default: false }, // true = Vadnica (PRACTICE lesson), not counted in teacher results
    answers: [answerSchema],
});

//studentId + lessonId: quickly finds all attempts for 1 student in 1 lesson
attemptSchema.index({ studentId: 1, lessonId: 1 });

//studentId + date: quickly finds a student’s most recent attempts
attemptSchema.index({ studentId: 1, date: -1 });

module.exports = mongoose.model('Attempt', attemptSchema);