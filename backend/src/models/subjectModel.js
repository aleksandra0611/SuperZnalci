const mongoose = require('mongoose');

const topicSchema = new mongoose.Schema({
    name: { type: String, required: true, trim: true },
    description: { type: String, default: null },
}); //ima svoj id -> Lesson.topicId

const gradeSchema = new mongoose.Schema({
    gradeNumber: { type: Number, required: true, min: 1, max: 9 },
    topics: [topicSchema],
}, { _id: false });

const subjectSchema = new mongoose.Schema({
    name: { type: String, required: true, unique: true, trim: true },
    icon: { type: String, default: null },
    grades: [gradeSchema],
});

module.exports = mongoose.model('Subject', subjectSchema);