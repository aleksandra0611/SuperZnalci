const router = require('express').Router();
const {authenticate}=require('../middleware/auth');
const {requireRole}=require('../middleware/roles');
const TeacherMessage=require('../models/teacherMessageModel');
router.use(authenticate,requireRole('STUDENT'));
router.get('/',async(req,res,next)=>{try{
 const messages=await TeacherMessage.find({studentId:req.user._id,classroomId:req.user.classroomId}).sort({createdAt:-1}).limit(100).lean();
 res.json({messages:messages.map(m=>({id:String(m._id),type:m.type,message:m.message,createdAt:m.createdAt,teacherId:String(m.teacherId)}))});
}catch(e){next(e)}});
module.exports=router;
