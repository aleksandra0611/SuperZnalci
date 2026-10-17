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
    registeredAt: user.registeredAt, 
    lastActiveAt: user.lastActiveAt,
    badges: user.badges.map((b) => ({
      badgeId: b.badgeId.toString(),
      awardedAt: b.awardedAt,
    })),
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

module.exports = { toUserDto, toClassroomDto };