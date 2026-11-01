const express=require('express');
const {authenticate}=require('../middleware/auth');
const {requireRole}=require('../middleware/roles');
const t=require('../controllers/teacherIntegrationController');

const r=express.Router();

r.use(authenticate,requireRole('TEACHER'));
r.get('/teachers/me/overview',t.teacherExtras);
r.patch('/users/me/settings',t.updateSettings);
r.get('/classrooms/:classroomId/overview',t.overview);
r.get('/classrooms/:classroomId/student-results',t.studentResults);
r.get('/classrooms/:classroomId/lesson-stats',t.lessonStats);
r.get('/classrooms/:classroomId/assignments',t.listAssignments);
r.put('/classrooms/:classroomId/assignments/:lessonId',t.putAssignment);
r.get('/classrooms/:classroomId/topic-settings',t.topicSettings);
r.put('/classrooms/:classroomId/topic-settings/:topicId',t.putTopicSetting);
r.post('/classrooms/:classroomId/join-code',t.regenerateJoinCode);
r.get('/classrooms/:classroomId/student-insights',t.studentInsights);
r.get('/classrooms/:classroomId/students/:studentId/detail',t.studentDetail);
r.post('/classrooms/:classroomId/students/:studentId/praise',t.praise);
r.get('/classrooms/:classroomId/students/:studentId/attempts/:attemptId/mistakes',t.studentMistakes);
r.get('/classrooms/:classroomId/struggling-students',t.struggling);
r.post('/classrooms/:classroomId/reminders',t.reminder);

module.exports=r;
