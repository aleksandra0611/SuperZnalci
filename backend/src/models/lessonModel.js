const mongoose = require('mongoose');

//Question is embedded in the lesson.
//answers / correctAnswers are Mixed because their shape depends on the type:
// - TRUE_FALSE, MULTIPLE_CHOICE, MULTIPLE_SELECT: array of strings
// - FILL_IN_BLANK, IMAGE_QUESTION: answers = null, correctAnswers = array of strings
// - MATCHING: answers = { left: [...], right: [...] }, correctAnswers = [{ left, right }]

const QUESTION_TYPES = ['TRUE_FALSE', 'FILL_IN_BLANK', 'MULTIPLE_CHOICE', 'MULTIPLE_SELECT', 'MATCHING', 'IMAGE_QUESTION'];

const questionSchema = new mongoose.Schema({
    type: { type: String, enum: QUESTION_TYPES, required: true },
    text: { type: String, required: true },
    answers: { type: mongoose.Schema.Types.Mixed, default: null },
    correctAnswers: { type: mongoose.Schema.Types.Mixed, required: true },
    explanation: { type: String, default: null },
    difficulty: { type: String, enum: ['EASY', 'MEDIUM', 'HARD'], required: true },
    points: { type: Number, required: true, min: 0 },
    image: { type: String, default: null },
});

const lessonSchema = new mongoose.Schema({
    topicId: { type: mongoose.Schema.Types.ObjectId, required: true },
    name: { type: String, required: true, trim: true },
    order: { type: Number, required: true, min: 1 },
    authorId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
    isPublic: { type: Boolean, default: false }, //true = official content, visible to everyone, false = visible only in classroom
    questions: [questionSchema],
});

lessonSchema.index({ topicId: 1, order: 1 });

module.exports = mongoose.model('Lesson', lessonSchema);
module.exports.QUESTION_TYPES = QUESTION_TYPES;