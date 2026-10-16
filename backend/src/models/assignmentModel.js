const mongoose = require('mongoose');

const assignmentSchema = new mongoose.Schema({
    classroomId: { type: mongoose.Schema.Types.ObjectId, ref: 'Classroom', required: true },
    lessonId: { type: mongoose.Schema.Types.ObjectId, ref: 'Lesson', required: true },
    dueDate: { type: Date, default: null },
});

//the same lesson can be assigned to a classroom only once
assignmentSchema.index({ classroomId: 1, lessonId: 1 }, { unique: true });

module.exports = mongoose.model('Assignment', assignmentSchema);