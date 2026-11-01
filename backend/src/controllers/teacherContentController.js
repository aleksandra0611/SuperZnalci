const mongoose = require('mongoose');
const Subject = require('../models/subjectModel');
const Lesson = require('../models/lessonModel');
const Classroom = require('../models/classroomModel');
const Attempt = require('../models/attemptModel');
const Assignment = require('../models/assignmentModel');
const User = require('../models/userModel');
const { generateUniqueJoinCode } = require('../utils/joinCode');
const idOk = mongoose.isValidObjectId;
const asId = x => String(x);
const clean = (v, max=120) => typeof v === 'string' && v.trim().length > 0 && v.trim().length <= max ? v.trim() : null;

async function ownClass(req,res,id) {
 if (!idOk(id)) {res.status(400).json({message:'Neveljaven ID učilnice.'});return null;}
 const c=await Classroom.findById(id);
 if(!c){res.status(404).json({message:'Učilnica ne obstaja.'});return null;}
 if(asId(c.teacherId)!==asId(req.user._id)){res.status(403).json({message:'Nimate dostopa.'});return null;}
 return c;
}
async function ownTopic(req,res,classroomId,topicId){
 const c=await ownClass(req,res,classroomId);if(!c)return null;
 if(!idOk(topicId)){res.status(400).json({message:'Neveljavna tema.'});return null;}
 const s=await Subject.findOne({_id:{$in:c.subjectIds},'grades.topics._id':topicId});
 const grade=s?.grades.find(g=>g.gradeNumber===c.gradeNumber && g.topics.some(t=>asId(t._id)===asId(topicId)));
 if(!grade){res.status(403).json({message:'Tema ne pripada tej učilnici in razredu.'});return null;}
 return {c,s,grade};
}
async function editableLesson(req,res,lessonId){
 if(!idOk(lessonId)){res.status(400).json({message:'Neveljavna lekcija.'});return null;}
 const lesson=await Lesson.findById(lessonId);
 if(!lesson){res.status(404).json({message:'Lekcija ne obstaja.'});return null;}
 if(lesson.scope!=='CLASSROOM'||asId(lesson.authorId)!==asId(req.user._id)){
  res.status(403).json({message:'Urejate lahko samo svoje CLASSROOM lekcije.'});return null;
 }
 const classes=await Classroom.find({teacherId:req.user._id});
 const ok=await Promise.all(classes.map(async c=>{
  const s=await Subject.findOne({_id:{$in:c.subjectIds},'grades.topics._id':lesson.topicId});
  return s?.grades.some(g=>g.gradeNumber===c.gradeNumber&&g.topics.some(t=>asId(t._id)===asId(lesson.topicId)));
 }));
 if(!ok.some(Boolean)){res.status(403).json({message:'Nimate dostopa do teme.'});return null;}
 return lesson;
}
async function createClass(req,res,next){try{
 const name=clean(req.body?.name);const grade=Number(req.body?.gradeNumber);
 if(!name||!Number.isInteger(grade)||grade<1||grade>9)return res.status(400).json({message:'Neveljavno ime ali razred.'});
 const ids=req.body?.subjectIds||[];
 if(!Array.isArray(ids)||!ids.every(idOk))return res.status(400).json({message:'Neveljavni ID-ji predmetov.'});
 const subjects=await Subject.find({_id:{$in:ids}});
 if(subjects.length!==new Set(ids.map(String)).size)return res.status(400).json({message:'Nekateri predmeti ne obstajajo.'});
 const department=clean(req.body?.department,10)||'A';
 const joinCode=await generateUniqueJoinCode(grade,department);
 const c=await Classroom.create({name,gradeNumber:grade,subjectIds:ids,teacherId:req.user._id,joinCode});
 res.status(201).json({classroom:{id:asId(c._id),name:c.name,gradeNumber:c.gradeNumber,joinCode:c.joinCode,subjectIds:c.subjectIds.map(asId)}});
}catch(e){next(e)}}
async function createSubject(req,res,next){try{
 const name=clean(req.body?.name);if(!name)return res.status(400).json({message:'Vnesite ime predmeta.'});
 const c=await ownClass(req,res,req.params.classroomId);if(!c)return;
 // Subjects are a shared catalog: a new subject becomes globally searchable.
 let s=await Subject.findOne({name:new RegExp('^'+name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'$','i')});
 if(!s){try{s=await Subject.create({name,grades:[]});}catch(e){if(e.code!==11000)throw e;s=await Subject.findOne({name});}}
 if(!s)return res.status(409).json({message:'Predmeta ni mogoče ustvariti.'});
 // Register the classroom grade even when the subject has no topics yet.
 // Otherwise GET /api/subjects?gradeNumber=8 filters the new subject out.
 if(!s.grades.some(g => g.gradeNumber === c.gradeNumber)) {
   await Subject.updateOne({_id:s._id, 'grades.gradeNumber':{$ne:c.gradeNumber}},
     {$push:{grades:{gradeNumber:c.gradeNumber,topics:[]}}});
 }
 await Classroom.updateOne({_id:c._id,teacherId:req.user._id},{$addToSet:{subjectIds:s._id}});
 res.status(201).json({subject:{id:asId(s._id),name:s.name}});
}catch(e){next(e)}}
async function createTopic(req,res,next){try{
 const c=await ownClass(req,res,req.params.classroomId);if(!c)return;
 const name=clean(req.body?.name);const sid=req.body?.subjectId;
 if(!name||!idOk(sid)||!c.subjectIds.some(x=>asId(x)===asId(sid)))return res.status(400).json({message:'Neveljaven predmet ali ime teme.'});
 const s=await Subject.findById(sid);if(!s)return res.status(404).json({message:'Predmet ne obstaja.'});
 let grade=s.grades.find(g=>g.gradeNumber===c.gradeNumber);
 if(!grade){s.grades.push({gradeNumber:c.gradeNumber,topics:[]});grade=s.grades.find(g=>g.gradeNumber===c.gradeNumber);}
 if(grade.topics.some(t=>t.name.toLowerCase()===name.toLowerCase()))return res.status(409).json({message:'Tema s tem imenom že obstaja.'});
 grade.topics.push({name,description:clean(req.body?.description,500)});
 await s.save();const t=grade.topics[grade.topics.length-1];
 res.status(201).json({topic:{id:asId(t._id),name:t.name,subjectId:asId(s._id),gradeNumber:c.gradeNumber}});
}catch(e){next(e)}}
async function createLesson(req,res,next){try{
 const ctx=await ownTopic(req,res,req.params.classroomId,req.body?.topicId);if(!ctx)return;
 const name=clean(req.body?.name);if(!name)return res.status(400).json({message:'Vnesite ime lekcije.'});
 const last=await Lesson.findOne({topicId:req.body.topicId}).sort({order:-1}).select('order').lean();
 const lesson=await Lesson.create({topicId:req.body.topicId,name,order:(last?.order||0)+1,scope:'CLASSROOM',authorId:req.user._id,questions:[]});
 res.status(201).json({lesson:{id:asId(lesson._id),topicId:asId(lesson.topicId),name:lesson.name,order:lesson.order,scope:lesson.scope}});
}catch(e){next(e)}}
async function renameLesson(req,res,next){try{
 const l=await editableLesson(req,res,req.params.lessonId);if(!l)return;
 const name=clean(req.body?.name);if(!name)return res.status(400).json({message:'Neveljavno ime.'});
 l.name=name;await l.save();res.json({lesson:{id:asId(l._id),name:l.name}});
}catch(e){next(e)}}
async function deleteLesson(req,res,next){try{
 const l=await editableLesson(req,res,req.params.lessonId);if(!l)return;
 if(await Attempt.exists({lessonId:l._id})||await Assignment.exists({lessonId:l._id}))return res.status(409).json({message:'Lekcija ima poskuse ali domače naloge in je ni mogoče izbrisati.'});
 await Lesson.deleteOne({_id:l._id,scope:'CLASSROOM',authorId:req.user._id});res.status(204).send();
}catch(e){next(e)}}
function checkedQuestion(b){
 b = b || {};
 const text=clean(b?.text,1000),type=b?.type,points=Number(b?.points);
 if(!text||!Number.isInteger(points)||points<1||points>100)return null;
 if(!['MULTIPLE_CHOICE','TRUE_FALSE','FILL_IN_BLANK'].includes(type))return null;
 const correct=Array.isArray(b.correctAnswers)?b.correctAnswers.map(x=>clean(x,150)):[];
 if(correct.length!==1||!correct[0])return null;
 let answers=null;
 if(type==='TRUE_FALSE') {if(!['true','false'].includes(correct[0].toLowerCase()))return null;answers=['true','false'];}
 if(type==='MULTIPLE_CHOICE'){
  if(!Array.isArray(b.answers)||b.answers.length<2||b.answers.length>8||!b.answers.every(x=>clean(x,150)))return null;
  answers=b.answers.map(x=>x.trim());if(!answers.includes(correct[0]))return null;
 }
 return {type,text,points,answers,correctAnswers:correct,explanation:clean(b.explanation,2000),difficulty:'MEDIUM',image:null};
}
// GET only the questions from a teacher-owned CLASSROOM lesson.
// Deliberately does not expose answer keys to students.
async function listOwnedLessonQuestions(req,res,next){try{
 const l=await editableLesson(req,res,req.params.lessonId);if(!l)return;
 return res.json({questions:l.questions.map(q=>({
  id:asId(q._id),text:q.text,type:q.type,points:q.points,
  answers:q.answers,correctAnswers:q.correctAnswers,
  explanation:q.explanation||''
 }))});
}catch(e){next(e)}}
async function createQuestion(req,res,next){try{
 const l=await editableLesson(req,res,req.params.lessonId);if(!l)return;
 const q=checkedQuestion(req.body);if(!q)return res.status(400).json({message:'Neveljavni podatki vprašanja.'});
 l.questions.push(q);await l.save();const x=l.questions[l.questions.length-1];res.status(201).json({question:{id:asId(x._id),text:x.text,type:x.type,points:x.points}});
}catch(e){next(e)}}
async function updateQuestion(req,res,next){try{
 const l=await editableLesson(req,res,req.params.lessonId);if(!l)return;
 const q=l.questions.id(req.params.questionId);if(!q)return res.status(404).json({message:'Vprašanje ne obstaja.'});
 if(await Attempt.exists({lessonId:l._id,questionIds:q._id}))return res.status(409).json({message:'Vprašanje ima zgodovino poskusov in ga ni mogoče spreminjati.'});
 const nextQuestion=checkedQuestion(req.body);if(!nextQuestion)return res.status(400).json({message:'Neveljavni podatki vprašanja.'});
 Object.assign(q,nextQuestion);await l.save();res.json({question:{id:asId(q._id),text:q.text,type:q.type,points:q.points}});
}catch(e){next(e)}}
async function deleteQuestion(req,res,next){try{
 const l=await editableLesson(req,res,req.params.lessonId);if(!l)return;
 const q=l.questions.id(req.params.questionId);if(!q)return res.status(404).json({message:'Vprašanje ne obstaja.'});
 if(await Attempt.exists({lessonId:l._id,questionIds:q._id}))return res.status(409).json({message:'Vprašanje se že uporablja v poskusih.'});
 l.questions.pull({_id:q._id});await l.save();res.status(204).send();
}catch(e){next(e)}}
module.exports={createClass,createSubject,createTopic,createLesson,renameLesson,deleteLesson,listOwnedLessonQuestions,createQuestion,updateQuestion,deleteQuestion};
