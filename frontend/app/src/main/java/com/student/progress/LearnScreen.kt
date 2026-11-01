package com.example.superznalci

import java.util.TimeZone

enum class NodeState { DONE, CURRENT, LOCKED }

data class PathNode(
    val id: String,
    val lessonId: String?,
    val title: String,
    val order: Int,
    val total: Int,
    val state: NodeState,
    val stars: Int,
    val questionCount: Int,
    val isBoss: Boolean
)

data class Chapter(
    val subjectId: String,
    val subjectTitle: String,
    val number: Int,
    val title: String,
    val nodes: List<PathNode>
)

data class Homework(
    val lessonId: String,
    val subjectId: String,
    val subjectTitle: String,
    val lessonName: String,
    val teacher: String?,
    val className: String,
    val dueMillis: Long?
)

enum class MissionKind { FLAWLESS_LESSON, EARN_XP, DUEL, OTHER }

enum class MissionState { CLAIMABLE, CLAIMED, PROGRESS, ACTION }

data class MissionItem(
    val id: String,
    val kind: MissionKind,
    val title: String,
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val percent: Int,
    val state: MissionState
)

data class ActivityItem(val message: String, val detail: String, val initials: List<String>)

data class LearnScreen(
    val userInitials: String,
    val streakDays: Int,
    val streakRecord: Int,
    val xp: Int,
    val dailyDone: Int,
    val dailyGoal: Int,
    val gradeNumber: Int?,
    val homework: Homework?,
    val chapter: Chapter?,
    val missions: List<MissionItem>,
    val hoursToReset: Int,
    val activity: ActivityItem?
)

object LearnMapper {

    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L

    private class Ctx(
        val subject: SubjectDto,
        val topics: List<TopicDto>,
        val lessons: List<LessonSummaryDto>
    )

    fun toScreen(data: LearnData, now: Long = System.currentTimeMillis()): LearnScreen {
        val user = data.user
        val classroom = data.classroom
        val grade = classroom?.gradeNumber
        val (className, _) = ClassroomNaming.parse(classroom?.name.orEmpty())

        val best: Map<String, AttemptDto> = data.attempts
            .groupBy { it.lessonId }
            .mapValues { (_, list) -> list.reduce { a, b -> if (b.accuracy > a.accuracy) b else a } }

        val dueByLesson = data.assignments.associate { it.lessonId to it.dueDate }
        val visible = data.lessons.filter { !it.isPublic }

        val contexts = data.subjects.map { subject ->
            val topics = subject.grades.filter { it.gradeNumber == grade }.flatMap { it.topics }
            val topicIds = topics.map { it.id }.toSet()
            Ctx(subject, topics, visible.filter { it.topicId in topicIds })
        }

        val homeworkPick = data.assignments
            .filter { it.lessonId !in best }
            .mapNotNull { a ->
                val ctx = contexts.find { c -> c.lessons.any { it.id == a.lessonId } } ?: return@mapNotNull null
                val lesson = ctx.lessons.first { it.id == a.lessonId }
                Triple(a, ctx, lesson)
            }
            .minByOrNull { IsoDate.parse(it.first.dueDate) ?: Long.MAX_VALUE }

        val latest = data.attempts.maxByOrNull { IsoDate.parse(it.date) ?: 0L }
        val activeCtx = latest?.let { a -> contexts.find { c -> c.lessons.any { it.id == a.lessonId } } }
            ?: homeworkPick?.second
            ?: contexts.firstOrNull()

        val homework = homeworkPick?.let { (assignment, ctx, lesson) ->
            Homework(
                lessonId = lesson.id,
                subjectId = ctx.subject.id,
                subjectTitle = if (grade != null) "${ctx.subject.name} $grade" else ctx.subject.name,
                lessonName = lesson.name,
                teacher = data.classroomInfo.teacherName,
                className = className.ifBlank { "—" },
                dueMillis = IsoDate.parse(assignment.dueDate)
            )
        }

        val offset = TimeZone.getDefault().getOffset(now)
        val startOfDay = ((now + offset) / DAY_MS) * DAY_MS - offset
        val doneToday = data.attempts.count { (IsoDate.parse(it.finishedAt ?: it.date) ?: 0L) >= startOfDay }
        val hoursToReset = (((startOfDay + DAY_MS - now) + HOUR_MS - 1) / HOUR_MS).toInt().coerceIn(1, 24)

        return LearnScreen(
            userInitials = LeaderboardMapper.initials(user.name),
            streakDays = ProfileMapper.effectiveStreak(user.streak, user.streakAnchorMillis(), now),
            streakRecord = data.streakRecord,
            xp = user.totalXp,
            dailyDone = doneToday,
            dailyGoal = data.dailyGoal.coerceAtLeast(1),
            gradeNumber = grade,
            homework = homework,
            chapter = activeCtx?.let { buildChapter(it, grade, best) },
            missions = data.missions.map { toMission(it) },
            hoursToReset = hoursToReset,
            activity = data.activity?.let {
                ActivityItem(it.message, it.detail, it.names.map { n -> LeaderboardMapper.initials(n) }.take(3))
            }
        )
    }

    private fun buildChapter(ctx: Ctx, grade: Int?, best: Map<String, AttemptDto>): Chapter? {
        if (ctx.topics.isEmpty()) return null

        fun lessonsOf(topic: TopicDto) = ctx.lessons.filter { it.topicId == topic.id }.sortedBy { it.order }

                val topicIndex = ctx.topics.indexOfFirst { t -> lessonsOf(t).any { it.status != "DONE" } }
            .takeIf { it >= 0 }
            ?: ctx.topics.indexOfLast { lessonsOf(it).isNotEmpty() }.takeIf { it >= 0 }
            ?: 0
        val topic = ctx.topics[topicIndex]
        val lessons = lessonsOf(topic)

        val nodes = lessons.map { lesson ->
            PathNode(
                id = lesson.id,
                lessonId = lesson.id,
                title = lesson.name,
                order = lesson.order,
                total = lessons.size,
                state = when (lesson.status) {
                    "DONE" -> NodeState.DONE
                    "LOCKED" -> NodeState.LOCKED
                    else -> NodeState.CURRENT
                },
                stars = lesson.stars.coerceIn(0, 3),
                questionCount = lesson.questionCount,
                isBoss = false
            )
        }

        val boss = PathNode(
            id = "boss-${topic.id}",
            lessonId = null,
            title = "", 
            order = lessons.size + 1,
            total = lessons.size,
            state = if (nodes.isNotEmpty() && nodes.all { it.state == NodeState.DONE }) NodeState.CURRENT else NodeState.LOCKED,
            stars = 0,
            questionCount = 0,
            isBoss = true
        )

        return Chapter(
            subjectId = ctx.subject.id,
            subjectTitle = if (grade != null) "${ctx.subject.name} $grade" else ctx.subject.name,
            number = topicIndex + 1,
            title = topic.name,
            nodes = nodes + boss
        )
    }

    private fun toMission(m: MissionDto): MissionItem {
        val kind = when (m.type.uppercase()) {
            "FLAWLESS_LESSON" -> MissionKind.FLAWLESS_LESSON
            "EARN_XP" -> MissionKind.EARN_XP
            "DUEL" -> MissionKind.DUEL
            else -> MissionKind.OTHER
        }
        val finished = m.progress >= m.target
        val state = when {
            finished && m.claimed -> MissionState.CLAIMED
            finished -> MissionState.CLAIMABLE
            kind == MissionKind.DUEL -> MissionState.ACTION
            else -> MissionState.PROGRESS
        }
        return MissionItem(
            id = m.id,
            kind = kind,
            title = m.title,
            progress = m.progress,
            target = m.target,
            rewardXp = m.rewardXp,
            percent = if (m.target <= 0) 0 else (m.progress * 100 / m.target).coerceIn(0, 100),
            state = state
        )
    }
}