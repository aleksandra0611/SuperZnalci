const mongoose = require('mongoose');

const schema = new mongoose.Schema({
  classroomId: {type: mongoose.Schema.Types.ObjectId, ref: 'Classroom', required: true},
  studentId: {type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true},
  teacherId: {type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true},
  type: {type: String, enum: ['PRAISE', 'REMINDER'], required: true},
  message: {type: String, required: true, maxlength: 500}
}, {timestamps: true});

module.exports = mongoose.model('TeacherMessage', schema);
