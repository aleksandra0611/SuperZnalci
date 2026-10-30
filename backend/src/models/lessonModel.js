const mongoose = require('mongoose');

//Question is embedded in the lesson.
//answers / correctAnswers are Mixed because their shape depends on the type:
// - TRUE_FALSE, MULTIPLE_CHOICE, MULTIPLE_SELECT: array of strings
// - FILL_IN_BLANK, IMAGE_QUESTION: answers = null, correctAnswers = array of strings
// - MATCHING: answers = { left: [...], right: [...] }, correctAnswers = [{ left, right }]

const QUESTION_TYPES = ['TRUE_FALSE', 'FILL_IN_BLANK', 'MULTIPLE_CHOICE', 'MULTIPLE_SELECT', 'MATCHING', 'IMAGE_QUESTION'];

//Who can see and use the lesson:
// - PRACTICE: official content only for Vadnica (free practice), visible to everyone, cannot be assigned to a classroom
// - SHARED: official or shared content that teachers can assign to their classrooms, not shown in Vadnica
// - CLASSROOM: lesson created by a teacher (authorId), visible only to the author and their classroom
const LESSON_SCOPES = ['PRACTICE', 'SHARED', 'CLASSROOM'];

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
    scope: { type: String, enum: LESSON_SCOPES, required: true },
    questions: [questionSchema],
});

lessonSchema.index({ topicId: 1, order: 1 });

module.exports = mongoose.model('Lesson', lessonSchema);
module.exports.QUESTION_TYPES = QUESTION_TYPES;
module.exports.LESSON_SCOPES = LESSON_SCOPES;