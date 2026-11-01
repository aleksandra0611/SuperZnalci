const express=require('express');
const {authenticate}=require('../middleware/auth');
const {requireRole}=require('../middleware/roles');
const Assignment=require('../models/assignmentModel');

const router=express.Router();

router.get('/',authenticate,requireRole('STUDENT'),async (req,res,next)=>{
 try {
   if(!req.user.classroomId)return res.json({assignments:[]});
   if(req.query.classroomId && String(req.query.classroomId)!==String(req.user.classroomId))return res.status(403).json({message:'Nimate dostopa.'});
   const docs=await Assignment.find({classroomId:req.user.classroomId}).lean();
   res.json({assignments:docs.map(a=>({id:String(a._id),classroomId:String(a.classroomId),lessonId:String(a.lessonId),dueDate:a.dueDate||null,availableFrom:a.availableFrom||null,allowRetry:!!a.allowRetry}))});
 }catch(e){next(e);}
});

module.exports=router;
