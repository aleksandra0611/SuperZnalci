const mongoose = require("mongoose");
const Lesson = require("../models/lessonModel");
const Classroom = require("../models/classroomModel");
const Subject = require("../models/subjectModel");

// Only owned CLASSROOM content may be modified. SHARED/PRACTICE are immutable.
async function canEditLesson(user, lesson) {
  if (!lesson || lesson.scope !== "CLASSROOM" ||
      String(lesson.authorId) !== String(user._id)) return false;
  const classes = await Classroom.find({teacherId:user._id}).select("gradeNumber subjectIds").lean();
  if (!classes.length) return false;
  const subjectIds = [...new Set(classes.flatMap(c=>(c.subjectIds||[]).map(String)))];
  const subs = await Subject.find({_id:{$in:subjectIds}}).select("grades").lean();
  for (const c of classes) for (const s of subs) {
    if (!(c.subjectIds||[]).some(x=>String(x)===String(s._id))) continue;
    if ((s.grades||[]).some(g=>g.gradeNumber===c.gradeNumber &&
        (g.topics||[]).some(t=>String(t._id)===String(lesson.topicId)))) return true;
  }
  return false;
}

function validText(s) { return typeof s==='string' && s.trim().length>0 && s.trim().length<=1000; }

function validate(body) {
  if (!validText(body.text) || !Number.isInteger(body.points) || body.points<=0 || body.points>100) return false;
  if (!['TRUE_FALSE','MULTIPLE_CHOICE','FILL_IN_BLANK'].includes(body.type)) return false;
  if (!Array.isArray(body.correctAnswers) || body.correctAnswers.length!==1 || !validText(body.correctAnswers[0])) return false;
  if (body.type==='TRUE_FALSE') return ['true','false'].includes(body.correctAnswers[0].toLowerCase()) &&
    Array.isArray(body.answers) && body.answers.length===2;
  if (body.type==='MULTIPLE_CHOICE') return Array.isArray(body.answers) && body.answers.length>=2 && body.answers.length<=8 &&
    body.answers.every(validText) && body.answers.includes(body.correctAnswers[0]);
  return body.answers===null || body.answers===undefined;
}

async function createQuestion(req,res,next) {try {
  const {lessonId}=req.body||{};
  if (!mongoose.isValidObjectId(lessonId)) return res.status(400).json({message:'Neveljaven lessonId.'});
  if (!validate(req.body)) return res.status(400).json({message:'Neveljavni podatki vprašanja. Podprti: TRUE_FALSE, MULTIPLE_CHOICE, FILL_IN_BLANK.'});
  const lesson=await Lesson.findById(lessonId);
  if (!lesson) return res.status(404).json({message:'Lekcija ne obstaja.'});
  if (!(await canEditLesson(req.user,lesson))) return res.status(403).json({message:'Urejati je mogoče le lastne CLASSROOM lekcije.'});
  lesson.questions.push({
    type:req.body.type,text:req.body.text.trim(),
    answers:req.body.answers??null,correctAnswers:req.body.correctAnswers,
    explanation:typeof req.body.explanation==='string'?req.body.explanation.trim():null,
    difficulty:'MEDIUM',points:req.body.points,image:null
  });
  await lesson.save();
  const q=lesson.questions[lesson.questions.length-1];
  return res.status(201).json({question:{id:String(q._id),type:q.type,text:q.text,points:q.points}});
} catch(e){next(e);}}

async function deleteQuestion(req,res,next) {try {
  const id=req.params.id;
  if(!mongoose.isValidObjectId(id))return res.status(400).json({message:'Neveljaven questionId.'});
  const lesson=await Lesson.findOne({'questions._id':id});
  if(!lesson)return res.status(404).json({message:'Vprašanje ne obstaja.'});
  if(!(await canEditLesson(req.user,lesson)))return res.status(403).json({message:'Urejati je mogoče le lastne CLASSROOM lekcije.'});
  // Preserve historical scoring references by forbidding delete if attempts exist.
  const Attempt=require('../models/attemptModel');
  const referenced=await Attempt.exists({lessonId:lesson._id,questionIds:new mongoose.Types.ObjectId(id)});
  if(referenced)return res.status(409).json({message:'Vprašanja ni mogoče izbrisati, ker ga že uporabljajo poskusi.'});
  lesson.questions.pull({_id:id});
  await lesson.save();
  return res.status(204).send();
} catch(e){next(e);}}

module.exports={createQuestion,deleteQuestion};
