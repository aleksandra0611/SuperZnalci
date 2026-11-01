const mongoose = require('mongoose');
const Classroom = require('../models/classroomModel');
const User = require('../models/userModel');
const Subject = require('../models/subjectModel');
const Lesson = require('../models/lessonModel');
const Attempt = require('../models/attemptModel');
const Assignment = require('../models/assignmentModel');
const Badge = require('../models/badgesModel');
const TopicSetting = require('../models/topicSettingModel');
const TeacherMessage = require('../models/teacherMessageModel');
const {generateUniqueJoinCode} = require('../utils/joinCode');

const validId = mongoose.isValidObjectId;
const round = n => Math.round(n || 0);
const oId = id => String(id);

async function ownClass(req, res) {
  const id = req.params.classroomId;
  if (!validId(id)) {res.status(400).json({message:'Neveljaven classroomId.'});return null;}
  const c = await Classroom.findById(id).lean();
  if (!c) {res.status(404).json({message:'Učilnica ne obstaja.'});return null;}
  if (oId(c.teacherId) !== oId(req.user._id)) {res.status(403).json({message:'Nimate dostopa do učilnice.'});return null;}
  return c;
}

async function ownStudent(req,res,c,id) {
  if(!validId(id)){res.status(400).json({message:'Neveljaven studentId.'});return null;}
  const s=await User.findOne({_id:id,classroomId:c._id,role:'STUDENT'}).lean();
  if(!s){res.status(404).json({message:'Učenec ne obstaja v tej učilnici.'});return null;}
  return s;
}

async function topicContext(c) {
  const subs=await Subject.find({_id:{$in:c.subjectIds}}).lean();
  const topics=new Map(); const subjectOfTopic=new Map();
  for(const s of subs) for(const g of s.grades||[]) if(g.gradeNumber===c.gradeNumber)
    for(const t of g.topics||[]) {topics.set(oId(t._id),{name:t.name,subjectId:oId(s._id)});subjectOfTopic.set(oId(t._id),oId(s._id));}
  return {topics,subjectOfTopic};
}

async function classData(c) {
  const students=await User.find({classroomId:c._id,role:'STUDENT'}).lean();
  const {topics,subjectOfTopic}=await topicContext(c);
  const lessons=await Lesson.find({topicId:{$in:[...topics.keys()].map(x=>new mongoose.Types.ObjectId(x))},$or:[{scope:'SHARED'},{scope:'CLASSROOM',authorId:c.teacherId}]}).lean();
  const studentIds=students.map(s=>s._id);
  const attempts=await Attempt.find({studentId:{$in:studentIds},lessonId:{$in:lessons.map(l=>l._id)},finishedAt:{$ne:null},isPractice:false}).lean();
  const assignments=await Assignment.find({classroomId:c._id}).lean();
  const lessonById=new Map(lessons.map(l=>[oId(l._id),l]));
  const byStudent=new Map(students.map(s=>[oId(s._id),[]]));
  for(const a of attempts) byStudent.get(oId(a.studentId))?.push(a);
  return {students,topics,subjectOfTopic,lessons,attempts,assignments,lessonById,byStudent};
}

function studentStats(s, d, subjectId=null) {
  const attempts=(d.byStudent.get(oId(s._id))||[]).filter(a=>!subjectId||d.subjectOfTopic.get(oId(d.lessonById.get(oId(a.lessonId))?.topicId))===subjectId);
  const answered=attempts.reduce((n,a)=>n+(a.answers||[]).length,0);
  const correct=attempts.reduce((n,a)=>n+(a.answers||[]).filter(x=>x.isCorrect).length,0);
  const wrong=answered-correct;
  const completed=new Set(attempts.map(a=>oId(a.lessonId))).size;
  const totalLessons=d.lessons.filter(l=>!subjectId||d.subjectOfTopic.get(oId(l.topicId))===subjectId).length;
  const accuracy=answered?round(correct*100/answered):null;
  const points=attempts.reduce((n,a)=>n+(a.points||0),0);
  return {attempts,correct,wrong,completed,totalLessons,accuracy,points};
}

function recentWeek(date){
  return new Date(date).getTime()>=Date.now()-7*86400000;
}

async function overview(req,res,next) {try{
  const c=await ownClass(req,res);if(!c)return;
  const d=await classData(c);const studentsWithAttempts=d.students.filter(s=>(d.byStudent.get(oId(s._id))||[]).some(a=>recentWeek(a.finishedAt)));
  const accuracies=d.students.map(s=>studentStats(s,d).accuracy).filter(x=>x!==null);
  const topics=new Map();for(const a of d.attempts)for(const x of a.answers||[])if(!x.isCorrect){const l=d.lessonById.get(oId(a.lessonId));if(l){const name=d.topics.get(oId(l.topicId))?.name;if(name)topics.set(name,(topics.get(name)||0)+1);}}
  const struggleTopics=[...topics.entries()].sort((a,b)=>b[1]-a[1]).slice(0,3).map(x=>x[0]);
  const submittedThisWeek=d.attempts.filter(a=>recentWeek(a.finishedAt)).length;
  const averageAccuracy=accuracies.length?round(accuracies.reduce((a,b)=>a+b,0)/accuracies.length):0;
  const needHelpCount=d.students.filter(s=>{const st=studentStats(s,d);return st.accuracy!==null&&st.accuracy<50;}).length;
  res.json({submittedThisWeek,averageAccuracy,struggleTopics,weekActive:submittedThisWeek>0,completedCount:studentsWithAttempts.length,totalStudents:d.students.length,needHelpCount});
}catch(e){next(e);}}

async function studentResults(req,res,next){try{
  const c=await ownClass(req,res);if(!c)return;const d=await classData(c);
  res.json({results:d.students.map(s=>{const st=studentStats(s,d);return {studentId:oId(s._id),completedLessons:st.completed,totalLessons:st.totalLessons,accuracy:st.accuracy,badge:null,mistakeCount:st.wrong,mistakeTopic:null,weakTopic:null};})});
}catch(e){next(e);}}

async function lessonStats(req,res,next){try{
  const c=await ownClass(req,res);if(!c)return;const d=await classData(c);
  res.json({studentCount:d.students.length,lessons:d.lessons.map(l=>{const a=d.attempts.filter(x=>oId(x.lessonId)===oId(l._id));const byStudent=new Map();for(const t of a){const id=oId(t.studentId);byStudent.set(id,Math.max(byStudent.get(id)||0,t.accuracy));}const accuracies=[...byStudent.values()];return {lessonId:oId(l._id),completedCount:accuracies.length,completionPercent:d.students.length?round(100*accuracies.length/d.students.length):0,averageAccuracy:accuracies.length?round(accuracies.reduce((x,y)=>x+y,0)/accuracies.length):null};})});
}catch(e){next(e);}}

function assignmentDto(a){return {id:oId(a._id),classroomId:oId(a.classroomId),lessonId:oId(a.lessonId),availableFrom:a.availableFrom||null,dueDate:a.dueDate||null,allowRetry:!!a.allowRetry};}

async function listAssignments(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const a=await Assignment.find({classroomId:c._id}).lean();res.json({assignments:a.map(assignmentDto)});}catch(e){next(e);}}

function dateOrError(value){if(value===null)return null;if(typeof value!=='string')throw Error('Neveljaven datum.');const x=new Date(value);if(!Number.isFinite(x.getTime()))throw Error('Neveljaven datum.');return x;}

async function putAssignment(req,res,next){try{
  const c=await ownClass(req,res);if(!c)return;const id=req.params.lessonId;if(!validId(id))return res.status(400).json({message:'Neveljaven lessonId.'});
  const lesson=await Lesson.findById(id).lean();const {topics}=await topicContext(c);
  if(!lesson||!topics.has(oId(lesson.topicId))||lesson.scope==='PRACTICE'||(lesson.scope==='CLASSROOM'&&oId(lesson.authorId)!==oId(c.teacherId)))return res.status(403).json({message:'Te lekcije ni dovoljeno dodeliti.'});
  const update={};for(const key of ['availableFrom','dueDate'])if(Object.prototype.hasOwnProperty.call(req.body||{},key))update[key]=dateOrError(req.body[key]);
  if(Object.prototype.hasOwnProperty.call(req.body||{},'allowRetry')){if(typeof req.body.allowRetry!=='boolean')return res.status(400).json({message:'allowRetry mora biti boolean.'});update.allowRetry=req.body.allowRetry;}
  const a=await Assignment.findOneAndUpdate({classroomId:c._id,lessonId:lesson._id},{$set:update,$setOnInsert:{classroomId:c._id,lessonId:lesson._id}},{upsert:true,returnDocument:'after',runValidators:true});res.json({assignment:assignmentDto(a)});
}catch(e){if(e.message==='Neveljaven datum.')return res.status(400).json({message:e.message});next(e);}}

async function topicSettings(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const x=await TopicSetting.find({classroomId:c._id}).lean();res.json({settings:x.map(t=>({classroomId:oId(t.classroomId),topicId:oId(t.topicId),visibleToStudents:t.visibleToStudents}))});}catch(e){next(e);}}

async function putTopicSetting(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const id=req.params.topicId;if(!validId(id)||typeof req.body?.visibleToStudents!=='boolean')return res.status(400).json({message:'Neveljavni podatki.'});const {topics}=await topicContext(c);if(!topics.has(id))return res.status(404).json({message:'Tema ne pripada učilnici.'});const t=await TopicSetting.findOneAndUpdate({classroomId:c._id,topicId:id},{$set:{visibleToStudents:req.body.visibleToStudents}},{upsert:true,returnDocument:'after',runValidators:true});res.json({setting:{classroomId:oId(c._id),topicId:oId(t.topicId),visibleToStudents:t.visibleToStudents}});}catch(e){next(e);}}

async function regenerateJoinCode(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const code=await generateUniqueJoinCode(c.gradeNumber,c.name);await Classroom.updateOne({_id:c._id,teacherId:req.user._id},{$set:{joinCode:code}});res.json({joinCode:code});}catch(e){next(e);}}

async function studentInsights(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const d=await classData(c);const subjectId=req.query.subjectId||null;if(subjectId&&(!validId(subjectId)||!c.subjectIds.some(x=>oId(x)===subjectId)))return res.status(400).json({message:'Neveljaven subjectId.'});res.json({insights:d.students.map(s=>{const st=studentStats(s,d,subjectId);return {studentId:oId(s._id),xp:subjectId?st.points:s.totalXp,accuracy:st.accuracy,tasksDone:st.completed,level:Math.max(1,Math.floor(s.totalXp/100)+1),title:null,needsHelp:st.accuracy!==null&&st.accuracy<50,helpTopic:null};})});}catch(e){next(e);}}

async function studentDetail(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const s=await ownStudent(req,res,c,req.params.studentId);if(!s)return;const d=await classData(c);const attempts=d.byStudent.get(oId(s._id))||[];const assigned=new Set(d.assignments.map(a=>oId(a.lessonId)));const done=new Set(attempts.filter(a=>assigned.has(oId(a.lessonId))).map(a=>oId(a.lessonId)));const badgeIds=(s.badges||[]).map(b=>b.badgeId);const badges=await Badge.find({_id:{$in:badgeIds}}).lean();const named=new Map(badges.map(b=>[oId(b._id),b]));res.json({studentId:oId(s._id),assignedDone:done.size,assignedTotal:assigned.size,badgeTotal:badgeIds.length,badges:(s.badges||[]).map(b=>({id:oId(b.badgeId),name:named.get(oId(b.badgeId))?.name||'Značka',subtitle:null})),recentQuizzes:attempts.sort((a,b)=>new Date(b.finishedAt)-new Date(a.finishedAt)).slice(0,10).map(a=>{const l=d.lessonById.get(oId(a.lessonId));return {id:oId(a._id),title:l?.name||'Lekcija',score:a.points||0,total:(l?.questions||[]).reduce((n,q)=>n+(q.points||0),0),accuracy:round(a.accuracy),finishedAt:a.finishedAt,mistakeCount:(a.answers||[]).filter(x=>!x.isCorrect).length,mistakeNote:null,xpGained:a.points||0};})});}catch(e){next(e);}}

async function struggling(req,res,next){try{const c=await ownClass(req,res);if(!c)return;const d=await classData(c);res.json({students:d.students.flatMap(s=>{const st=studentStats(s,d);let reason=null;if(st.completed===0)reason='NOT_STARTED';else if(st.wrong>0&&st.accuracy!==null&&st.accuracy<50)reason='MISTAKES';else if(s.lastActiveAt&&Date.now()-new Date(s.lastActiveAt).getTime()>7*86400000)reason='INACTIVE';return reason?[{studentId:oId(s._id),name:s.name,avatar:s.avatar,reason,mistakeCount:st.wrong,lessonName:null,lastActiveAt:s.lastActiveAt}]:[];})});}catch(e){next(e);}}

async function message(req,res,next,type){try{const c=await ownClass(req,res);if(!c)return;const id=type==='PRAISE'?req.params.studentId:req.body?.studentId;const s=await ownStudent(req,res,c,id);if(!s)return;const value=type==='PRAISE'?req.body?.message:'Opomnik za učenje';if(typeof value!=='string'||value.trim().length<1||value.length>500)return res.status(400).json({message:'Neveljavno sporočilo.'});await TeacherMessage.create({classroomId:c._id,studentId:s._id,teacherId:req.user._id,type,message:value.trim()});res.json(type==='REMINDER'?{sent:true}:{saved:true});}catch(e){next(e);}}

async function praise(req,res,next){return message(req,res,next,'PRAISE');}

async function reminder(req,res,next){return message(req,res,next,'REMINDER');}

async function teacherExtras(req,res,next){try{const cs=await Classroom.find({teacherId:req.user._id}).lean();const all=await Promise.all(cs.map(async c=>{const d=await classData(c);return {c,d};}));const attempts=all.flatMap(x=>x.d.attempts);const acc=attempts.length?round(attempts.reduce((n,a)=>n+a.accuracy,0)/attempts.length):0;const quizzesCreated=await Lesson.countDocuments({authorId:req.user._id,scope:'CLASSROOM'});res.json({isVerified:false,quizzesCreated,averageAccuracy:acc,accuracyDelta:0,classrooms:cs.map(c=>({classroomId:oId(c._id),shift:null})),settings:{emailNotifications:req.user.emailNotifications??true,weeklyReport:req.user.weeklyReport??true}});}catch(e){next(e);}}

async function updateSettings(req,res,next){try{const update={};for(const k of ['emailNotifications','weeklyReport']){if(Object.prototype.hasOwnProperty.call(req.body||{},k)){if(typeof req.body[k]!=='boolean')return res.status(400).json({message:`${k} mora biti boolean.`});update[k]=req.body[k];}}if(!Object.keys(update).length)return res.status(400).json({message:'Ni nastavitev.'});const user=await User.findOneAndUpdate({_id:req.user._id,role:'TEACHER'},{$set:update},{returnDocument:'after'});if(!user)return res.status(404).json({message:'Učitelj ne obstaja.'});res.json({emailNotifications:user.emailNotifications,weeklyReport:user.weeklyReport});}catch(e){next(e);}}

module.exports={overview,studentResults,lessonStats,listAssignments,putAssignment,topicSettings,putTopicSetting,regenerateJoinCode,studentInsights,studentDetail,struggling,praise,reminder,teacherExtras,updateSettings};

module.exports.studentMistakes = async function studentMistakes(req,res,next) {
 try {
   const c=await ownClass(req,res);if(!c)return;
   const s=await ownStudent(req,res,c,req.params.studentId);if(!s)return;
   const id=req.params.attemptId;
   if(!validId(id))return res.status(400).json({message:'Neveljaven attemptId.'});
   const a=await Attempt.findOne({_id:id,studentId:s._id,finishedAt:{$ne:null},isPractice:false}).lean();
   if(!a)return res.status(404).json({message:'Zaključen poskus ne obstaja.'});
   const l=await Lesson.findById(a.lessonId).lean();
   if(!l)return res.status(404).json({message:'Lekcija ne obstaja.'});
   const {topics}=await topicContext(c);
   if(!topics.has(oId(l.topicId))||l.scope==='PRACTICE'||(l.scope==='CLASSROOM'&&oId(l.authorId)!==oId(c.teacherId)))
     return res.status(403).json({message:'Nimate dostopa do lekcije.'});
   const byQuestion=new Map((l.questions||[]).map(q=>[oId(q._id),q]));
   res.json({attemptId:oId(a._id),studentId:oId(s._id),lessonId:oId(l._id),lessonName:l.name,
     mistakes:(a.answers||[]).filter(x=>x.isCorrect===false).map(x=>{
       const q=byQuestion.get(oId(x.questionId));return {questionId:oId(x.questionId),text:q?.text||null,type:q?.type||null,givenAnswer:x.givenAnswer,correctAnswers:q?.correctAnswers??null,explanation:q?.explanation??null,earnedPoints:x.points,maxPoints:q?.points??null};
     })});
 }catch(e){next(e);}
};
