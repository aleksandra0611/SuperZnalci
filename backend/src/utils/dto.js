function toUserDto(user) {
  return {
    id: user._id.toString(),
    name: user.name,
    email: user.email,
    role: user.role,
    classroomId: user.classroomId ? user.classroomId.toString() : null,
    avatar: user.avatar,
    totalXp: user.totalXp,
    streak: user.streak,
    registeredAt: user.registeredAt, // Date se v JSON zapiše kot ISO-8601
    lastActiveAt: user.lastActiveAt,
    badges: user.badges.map((b) => ({
      badgeId: b.badgeId.toString(),
      awardedAt: b.awardedAt,
    })),
  };
}

// Učenec v seznamu učilnice (za učitelja), brez e-pošte in značk
function toStudentDto(user) {
  return {
    id: user._id.toString(),
    name: user.name,
    avatar: user.avatar ?? null,
    totalXp: user.totalXp,
    streak: user.streak,
    lastActiveAt: user.lastActiveAt,
  };
}

function toClassroomDto(classroom) {
  return {
    id: classroom._id.toString(),
    name: classroom.name,
    joinCode: classroom.joinCode,
    gradeNumber: classroom.gradeNumber,
    subjectIds: classroom.subjectIds.map((id) => id.toString()),
  };
}

// Če je podan gradeNumber, ostane v predmetu samo ta razred
function toSubjectDto(subject, gradeNumber = null) {
  const grades = (subject.grades || [])
    .filter((g) => gradeNumber === null || g.gradeNumber === gradeNumber)
    .sort((a, b) => a.gradeNumber - b.gradeNumber)
    .map((g) => ({
      gradeNumber: g.gradeNumber,
      topics: (g.topics || []).map((t) => ({
        id: t._id.toString(),
        name: t.name,
        description: t.description ?? null,
      })),
    }));

  return {
    id: subject._id.toString(),
    name: subject.name,
    icon: subject.icon ?? null,
    grades,
  };
}

// Predmet v Vadnici: samo teme izbranega razreda, ki imajo lekcije za Vadnico, s števili
function toPracticeSubjectDto(subject, gradeNumber, countsByTopic) {
  const topics = (subject.grades || [])
    .filter((g) => g.gradeNumber === gradeNumber)
    .flatMap((g) => g.topics || [])
    .map((t) => {
      const c = countsByTopic.get(t._id.toString());
      return {
        id: t._id.toString(),
        name: t.name,
        description: t.description ?? null,
        lessonCount: c ? c.lessonCount : 0,
        questionCount: c ? c.questionCount : 0,
      };
    })
    .filter((t) => t.lessonCount > 0);

  return {
    id: subject._id.toString(),
    name: subject.name,
    icon: subject.icon ?? null,
    topicCount: topics.length,
    lessonCount: topics.reduce((n, t) => n + t.lessonCount, 0),
    questionCount: topics.reduce((n, t) => n + t.questionCount, 0),
    topics,
  };
}

// Vprašanje s pravilnimi odgovori (za učitelja) in podatki, kam spada
function toQuestionDto(question, { subject, topic, lesson }) {
  return {
    id: question._id.toString(),
    type: question.type,
    text: question.text,
    answers: question.answers ?? null,
    correctAnswers: question.correctAnswers,
    explanation: question.explanation ?? null,
    difficulty: question.difficulty,
    points: question.points,
    image: question.image ?? null,
    subjectId: subject._id.toString(),
    gradeNumber: topic.gradeNumber,
    topic: { id: topic.id.toString(), name: topic.name },
    lesson: { id: lesson.id.toString(), name: lesson.name, scope: lesson.scope },
  };
}

module.exports = {
  toUserDto,
  toStudentDto,
  toClassroomDto,
  toSubjectDto,
  toPracticeSubjectDto,
  toQuestionDto,
};