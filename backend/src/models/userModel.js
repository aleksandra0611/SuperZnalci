const mongoose = require('mongoose');

const userSchema = new mongoose.Schema({
    name: { type: String, required: true, trim: true },
    email: { type: String, required: true, unique: true, lowercase: true, trim: true },
    passwordHash: { type: String, required: true, select: false },  //cuvamo sifriranu lozinku
    role: { type: String, enum: ['ADMIN', 'TEACHER', 'STUDENT'], required: true },
    classroomId: { type: mongoose.Schema.Types.ObjectId, ref: 'Classroom', default: null },  //samo ucenici imaju ucionicu
    avatar: { type: String, default: null },
    totalXp: { type: Number, default: 0, min: 0 },
    streak: { type: Number, default: 0, min: 0 },
    lastStreakAt: { type: Date, default: null },
    xpAwardedAttemptIds: [{type: mongoose.Schema.Types.ObjectId, ref: "Attempt" }],  //Poskusi, za katere so bili XP že dodeljeni, tako isti attempt nikoli ne dobi XP dvakrat.
    registeredAt: { type: Date, default: Date.now },
    lastActiveAt: { type: Date, default: null },  //za streak, proveravamo kada je poslednji put bio aktivan

    badges: [{
        _id: false,
        badgeId: { type: mongoose.Schema.Types.ObjectId, ref: 'Badge', required: true },
        awardedAt: { type: Date, default: Date.now },
    }],
});

//samo ucenici imaju ucionicu
userSchema.pre('validate', function () {
    if (this.classroomId && this.role !== 'STUDENT') {
        throw new Error('Only students can have a classroomId');
    }
});

userSchema.index({ classroomId: 1 });

module.exports = mongoose.model('User', userSchema);