package com.example.superznalci

data class ProfileData(
    val user: UserDto,
    val classroom: ClassroomDto?,
    val subjects: List<SubjectDto>,
    val lessons: List<LessonSummaryDto>,
    val attempts: List<AttemptDto>,
    val badgeCatalog: List<BadgeDto>
)

interface ProfileRepository {
    fun loadProfileData(callback: ResultCallback<ProfileData>)
}