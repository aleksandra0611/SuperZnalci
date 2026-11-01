package com.example.superznalci

import androidx.annotation.DrawableRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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

data class SubjectTab(
    val id: String,
    val name: String,
    val topicCount: Int,
    val percent: Int,
    val accent: SubjectAccent,
    @DrawableRes val iconRes: Int
)

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
    val challengeDone: Int,
    val challengeGoal: Int,
    val tabs: List<SubjectTab>,
    val active: ActiveSubject?
)

/**
 * Backend data -> screen data.
 *   lesson state     comes from GET /api/lessons: DONE / CURRENT / LOCKED (the server applies the unlock rule).
 *                    null (PRACTICE, CLASSROOM lessons) is treated as CURRENT
 *   stars            lesson.stars from the server
 *   correct / total  estimated from lesson.bestAccuracy and the question count
 *   subject XP       overwritten in SubjectsFragment.bindStats() from /students/me/statistics
 *   level            ProfileMapper.levelFor(User.totalXp)
 *   visible lessons  the server already filters by access, so nothing is filtered here
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

        val contexts = data.subjects.mapIndexed { index, subject ->
            val topics = subject.grades
                .filter { it.gradeNumber == grade }
                .flatMap { it.topics }

            val topicIds = topics.map { it.id }.toSet()

            val subjectLessons = data.lessons.filter { lesson ->
                lesson.scope == "SHARED" &&
                    lesson.topicId in topicIds
            }

            Ctx(
                subject = subject,
                index = index,
                topics = topics,
                lessons = subjectLessons
            )
        }

        val tabs = contexts.map { ctx ->
            val done = ctx.lessons.count { it.status == "DONE" }
            SubjectTab(
                id = ctx.subject.id,
                name = ctx.subject.name,
                topicCount = ctx.topics.size,
                percent = percent(done, ctx.lessons.size),
                accent = ACCENTS[ctx.index % ACCENTS.size],
                iconRes = ProfileMapper.iconFor(ctx.subject.icon)
            )
        }

        val latest = data.attempts.maxByOrNull { IsoDate.parse(it.date) ?: 0L }
        val activeCtx = contexts.find { it.subject.id == selectedId }
            ?: latest?.let { a -> contexts.find { c -> c.lessons.any { it.id == a.lessonId } } }
            ?: contexts.firstOrNull()

        val active = activeCtx?.let { ctx ->
            val done = ctx.lessons.count { it.status == "DONE" }
            ActiveSubject(
                id = ctx.subject.id,
                title = if (grade != null) "${ctx.subject.name} $grade" else ctx.subject.name,
                teacher = data.classroomInfo.teacherName,
                xp = user.totalXp, // replaced by SubjectsFragment.bindStats()
                level = ProfileMapper.levelFor(user.totalXp),
                percent = percent(done, ctx.lessons.size),
                lessonsDone = done,
                lessonsTotal = ctx.lessons.size,
                stars = ctx.lessons.sumOf { it.stars },
                topics = buildTopics(ctx, dueByLesson)
            )
        }

        return SubjectsScreen(
            streakDays = ProfileMapper.effectiveStreak(user.streak, user.streakAnchorMillis(), now),
            stars = data.lessons.sumOf { it.stars },
            gradeNumber = grade,
            className = className.ifBlank { "—" },
            school = school,
            classCode = classroom?.joinCode ?: "—",
            challengeDone = attemptsThisWeek(data.attempts, now).coerceAtMost(WEEKLY_GOAL),
            challengeGoal = WEEKLY_GOAL,
            tabs = tabs,
            active = active
        )
    }

    private fun buildTopics(
        ctx: Ctx,
        due: Map<String, String?>
    ): List<TopicItem> {
        val rows = ctx.topics.mapIndexed { index, topic ->
            val sorted = ctx.lessons.filter { it.topicId == topic.id }.sortedBy { it.order }

            var previousOrder: Int? = null
            val items = sorted.map { lesson ->
                val state = when (lesson.status) {
                    "DONE" -> LessonState.DONE
                    "LOCKED" -> LessonState.LOCKED
                    else -> LessonState.CURRENT
                }
                val correct = if (state == LessonState.DONE && lesson.bestAccuracy != null)
                    (lesson.bestAccuracy / 100.0 * lesson.questionCount).roundToInt()
                else 0

                val item = LessonItem(
                    id = lesson.id,
                    number = lesson.order,
                    title = lesson.name,
                    description = lesson.description,
                    state = state,
                    stars = lesson.stars.coerceIn(0, 3),
                    correct = correct,
                    total = lesson.questionCount,
                    questionCount = lesson.questionCount,
                    xp = lesson.totalPoints,
                    minutes = ((lesson.questionCount * 4 + 4) / 5).coerceAtLeast(1),
                    dueLabel = due[lesson.id]
                        ?.let { formatShort(IsoDate.parse(it)) }
                        ?.takeIf { it.isNotBlank() },
                    unlockAfter = if (state == LessonState.LOCKED) previousOrder else null
                )
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