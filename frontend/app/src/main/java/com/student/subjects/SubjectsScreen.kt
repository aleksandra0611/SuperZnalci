package com.example.superznalci

import androidx.annotation.DrawableRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// What the Subjects screen shows. Nothing in here knows about the backend: SubjectsMapper (below)
// converts the backend-shaped SubjectsData into these classes.

enum class LessonState { DONE, CURRENT, LOCKED }

data class LessonItem(
    val id: String,
    val number: Int,
    val title: String,
    val description: String?,
    val state: LessonState,
    val stars: Int, 
    val correct: Int, 
    val total: Int, 
    val questionCount: Int,
    val xp: Int,
    val minutes: Int,
    val dueLabel: String?, 
    val unlockAfter: Int? 
)

data class TopicItem(
    val id: String,
    val number: Int,
    val name: String,
    val lessonsDone: Int,
    val lessonsTotal: Int,
    val percent: Int,
    val lessons: List<LessonItem>,
    val expandedByDefault: Boolean
)

// One chip in the horizontal "Tvoji predmeti" row
data class SubjectTab(
    val id: String,
    val name: String,
    val topicCount: Int,
    val percent: Int,
    val accent: SubjectAccent,
    @DrawableRes val iconRes: Int
)

// The selected subject (big card + topics)
data class ActiveSubject(
    val id: String,
    val title: String,
    val teacher: String?,
    val xp: Int,
    val level: Int,
    val percent: Int,
    val lessonsDone: Int,
    val lessonsTotal: Int,
    val stars: Int,
    val topics: List<TopicItem>
)

data class SubjectsScreen(
    val streakDays: Int,
    val stars: Int,
    val gradeNumber: Int?,
    val className: String,
    val school: String,
    val classCode: String,
    val studentCount: Int,
    val challengeDone: Int,
    val challengeGoal: Int,
    val tabs: List<SubjectTab>,
    val active: ActiveSubject?
)

/**
 * Backend data -> screen data. Calculated HERE (the backend does not store it):
 *   lesson state     DONE = at least one attempt (same rule as the profile's "lessons done");
 *                    the first lesson of a topic without attempt is CURRENT, the rest are LOCKED
 *   stars            best accuracy per lesson, same thresholds as ProfileMapper.starsFor
 *   subject XP       sum of Attempt.points of the subject's lessons
 *   level            ProfileMapper.levelFor(User.totalXp)
 *   minutes          ~0.8 min per question
 *   weekly challenge number of attempts since Monday (no backend model for it yet)
 *   visible lessons  Lesson.isPublic or assigned to the classroom
 */
object SubjectsMapper {

    const val WEEKLY_GOAL = 5
    private const val DAY_MS = 86_400_000L

    private val ACCENTS = SubjectAccent.values()

    private class Ctx(
        val subject: SubjectDto,
        val index: Int,
        val topics: List<TopicDto>,
        val lessons: List<LessonSummaryDto>
    )

    fun toScreen(
        data: SubjectsData,
        selectedId: String?,
        now: Long = System.currentTimeMillis()
    ): SubjectsScreen {
        val user = data.user
        val classroom = data.classroom
        val grade = classroom?.gradeNumber
        val (className, school) = ClassroomNaming.parse(classroom?.name.orEmpty())

        val dueByLesson: Map<String, String?> = data.assignments.associate { it.lessonId to it.dueDate }
        val best: Map<String, AttemptDto> = data.attempts
            .groupBy { it.lessonId }
            .mapValues { (_, list) -> list.reduce { a, b -> if (b.accuracy > a.accuracy) b else a } }

        val visible = data.lessons.filter { it.isPublic || it.id in dueByLesson }

        val contexts = data.subjects.mapIndexed { index, subject ->
            val topics = subject.grades.filter { it.gradeNumber == grade }.flatMap { it.topics }
            val topicIds = topics.map { it.id }.toSet()
            Ctx(subject, index, topics, visible.filter { it.topicId in topicIds })
        }

        val tabs = contexts.map { ctx ->
            val done = ctx.lessons.count { it.id in best }
            SubjectTab(
                id = ctx.subject.id,
                name = ctx.subject.name,
                topicCount = ctx.topics.size,
                percent = percent(done, ctx.lessons.size),
                accent = ACCENTS[ctx.index % ACCENTS.size],
                iconRes = ProfileMapper.iconFor(ctx.subject.icon)
            )
        }

        // selected subject, else the one of the latest attempt, else the first
        val latest = data.attempts.maxByOrNull { IsoDate.parse(it.date) ?: 0L }
        val activeCtx = contexts.find { it.subject.id == selectedId }
            ?: latest?.let { a -> contexts.find { c -> c.lessons.any { it.id == a.lessonId } } }
            ?: contexts.firstOrNull()

        val active = activeCtx?.let { ctx ->
            val lessonIds = ctx.lessons.map { it.id }.toSet()
            val done = lessonIds.count { it in best }
            ActiveSubject(
                id = ctx.subject.id,
                title = if (grade != null) "${ctx.subject.name} $grade" else ctx.subject.name,
                teacher = data.classroomInfo.teacherName,
                xp = data.attempts.filter { it.lessonId in lessonIds }.sumOf { it.points },
                level = ProfileMapper.levelFor(user.totalXp),
                percent = percent(done, lessonIds.size),
                lessonsDone = done,
                lessonsTotal = lessonIds.size,
                stars = lessonIds.sumOf { id -> best[id]?.let { ProfileMapper.starsFor(it.accuracy) } ?: 0 },
                topics = buildTopics(ctx, best, dueByLesson)
            )
        }

        return SubjectsScreen(
            streakDays = ProfileMapper.effectiveStreak(user.streak, IsoDate.parse(user.lastActiveAt), now),
            stars = best.values.sumOf { ProfileMapper.starsFor(it.accuracy) },
            gradeNumber = grade,
            className = className.ifBlank { "—" },
            school = school,
            classCode = classroom?.joinCode ?: "—",
            studentCount = data.classroomInfo.studentCount,
            challengeDone = attemptsThisWeek(data.attempts, now).coerceAtMost(WEEKLY_GOAL),
            challengeGoal = WEEKLY_GOAL,
            tabs = tabs,
            active = active
        )
    }

    private fun buildTopics(
        ctx: Ctx,
        best: Map<String, AttemptDto>,
        due: Map<String, String?>
    ): List<TopicItem> {
        val rows = ctx.topics.mapIndexed { index, topic ->
            val sorted = ctx.lessons.filter { it.topicId == topic.id }.sortedBy { it.order }

            var unlocked = true
            var previousOrder: Int? = null
            val items = sorted.map { lesson ->
                val attempt = best[lesson.id]
                val state = when {
                    attempt != null -> LessonState.DONE
                    unlocked -> LessonState.CURRENT
                    else -> LessonState.LOCKED
                }
                val item = LessonItem(
                    id = lesson.id,
                    number = lesson.order,
                    title = lesson.name,
                    description = lesson.description,
                    state = state,
                    stars = attempt?.let { ProfileMapper.starsFor(it.accuracy) } ?: 0,
                    correct = attempt?.answers?.count { it.isCorrect } ?: 0,
                    total = attempt?.answers?.size ?: lesson.questionCount,
                    questionCount = lesson.questionCount,
                    xp = lesson.totalPoints,
                    minutes = ((lesson.questionCount * 4 + 4) / 5).coerceAtLeast(1),
                    dueLabel = due[lesson.id]
                        ?.let { formatShort(IsoDate.parse(it)) }
                        ?.takeIf { it.isNotBlank() },
                    unlockAfter = if (state == LessonState.LOCKED) previousOrder else null
                )
                unlocked = attempt != null
                previousOrder = lesson.order
                item
            }

            val done = items.count { it.state == LessonState.DONE }
            TopicItem(
                id = topic.id,
                number = index + 1,
                name = topic.name,
                lessonsDone = done,
                lessonsTotal = items.size,
                percent = percent(done, items.size),
                lessons = items,
                expandedByDefault = false
            )
        }

        // the first topic that is not finished is opened
        val firstOpen = rows.indexOfFirst { it.lessonsDone < it.lessonsTotal }
        return rows.mapIndexed { i, t -> t.copy(expandedByDefault = i == firstOpen) }
    }

    private fun percent(done: Int, total: Int) = if (total == 0) 0 else done * 100 / total

    private fun attemptsThisWeek(attempts: List<AttemptDto>, now: Long): Int {
        val offset = TimeZone.getDefault().getOffset(now)
        val today = (now + offset) / DAY_MS
        val monday = today - ((today + 3) % 7)
        val weekStart = monday * DAY_MS - offset
        return attempts.count { (IsoDate.parse(it.date) ?: 0L) >= weekStart }
    }

    private fun formatShort(millis: Long?): String =
        if (millis == null) ""
        else SimpleDateFormat("d. M.", Locale.Builder().setLanguage("sl").setRegion("SI").build())
            .format(Date(millis))
}
