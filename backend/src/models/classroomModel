const mongoose = require('mongoose');

const classroomSchema = new mongoose.Schema({
    name: { type: String, required: true, trim: true },
    teacherId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },  //na backendu se to mora proveravati da li je ucitelj
    joinCode: { type: String, required: true, unique: true, uppercase: true, trim: true },
    gradeNumber: { type: Number, required: true, min: 1, max: 9 },

    subjectIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Subject' }],
});

classroomSchema.index({ teacherId: 1 });

module.exports = mongoose.model('Classroom', classroomSchema);