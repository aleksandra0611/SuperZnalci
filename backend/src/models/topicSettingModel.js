const mongoose = require('mongoose');

const schema = new mongoose.Schema({
  classroomId: {type: mongoose.Schema.Types.ObjectId, ref: 'Classroom', required: true},
  topicId: {type: mongoose.Schema.Types.ObjectId, required: true},
  visibleToStudents: {type: Boolean, default: true}
});

schema.index({classroomId: 1, topicId: 1}, {unique: true});

module.exports = mongoose.model('TopicSetting', schema);
