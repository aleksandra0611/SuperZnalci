package com.example.superznalci

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class DayState { DONE, TODAY, LOCKED, MISSED }

// Colour set used by a subject card / quiz result (picked by position, the backend has no colours)
enum class SubjectAccent(@ColorRes val bar: Int, @ColorRes val text: Int) {
    INDIGO(R.color.tertiary, R.color.tertiary),
    GREEN(R.color.primary, R.color.primary_dark),
    AMBER(R.color.secondary, R.color.amber_dark)
}

data class SubjectProgress(
    val id: String,
    val title: String,
    val lessonsDone: Int,
    val lessonsTotal: Int,
    val stars: Int,
    val accent: SubjectAccent,
    @DrawableRes val iconRes: Int,
    // true -> shows the green Nadaljuj button, false -> grey Preveri
    val isActive: Boolean
)

data class BadgeItem(
    val title: String,
    val description: String,
    @ColorRes val colorRes: Int,
    @DrawableRes val iconRes: Int? = null,
    // Text shown inside the badge instead of an icon
    val glyph: String? = null
)

// The next badge the student has not earned yet. The backend only has a text condition for it
data class LockedBadge(val title: String, val condition: String)
data class BadgeEntry(val badge: BadgeItem, val earned: Boolean)

data class QuizResult(
    val title: String,
    val subtitle: String,
    val score: Int,
    val maxScore: Int,
    val xp: Int,
    val accent: SubjectAccent,
    @DrawableRes val iconRes: Int
)

data class StudentProfile(
    val name: String,
    val email: String,
    val role: String,
    val className: String,
    val school: String,
    val classCode: String,
    val level: Int,
    val levelTitle: String,
    val xp: Int,
    val xpLevelStart: Int,
    val xpGoal: Int,
    val stars: Int,
    val streakDays: Int,
    val week: List<DayState>,
    val solvedTasks: Int,
    val accuracy: Int,
    val subjects: List<SubjectProgress>,
    val allBadges: List<BadgeEntry>,
    val badgesEarned: Int,
    val badgesTotal: Int,
    val badges: List<BadgeItem>,
    val lockedBadge: LockedBadge?,
    val recentResults: List<QuizResult>,
    val registeredAt: String,
    val lastActiveAt: String
)

/**
 * Backend data -> screen data. Everything the backend does not store is calculated HERE,
 * in one place:
 *   level / title     from User.totalXp
 *   stars             from the best accuracy per lesson (3 stars >= 90 %, 2 >= 70 %, 1 >= 50 %)
 *   solved tasks      number of answers in all attempts
 *   accuracy          average of Attempt.accuracy
 *   lessons done      lessons with at least one attempt
 *   week row          from User.streak + User.lastActiveAt
 *   badge colours     picked by position in the badge catalog
 * If the backend later returns any of these itself, only this file changes.
 */
object ProfileMapper {

    private const val DAY_MS = 86_400_000L
    private const val RECENT_RESULTS = 5

    private val LEVEL_START = listOf(0, 500, 1200, 2000, 2800, 3800, 5000, 6500)
    private val LEVEL_TITLES = listOf(
        "Začetnik", "Raziskovalec", "Vajenec", "Znalec",
        "Poznavalec", "Strokovnjak", "Mojster", "Legenda"
    )

    private val ACCENTS = SubjectAccent.values()

    private class BadgeStyle(
        @ColorRes val color: Int,
        @DrawableRes val icon: Int? = null,
        val glyph: String? = null
    )

    private val BADGE_STYLES = listOf(
        BadgeStyle(R.color.badge_yellow, glyph = "Σ"),
        BadgeStyle(R.color.badge_orange, glyph = "🔥"),
        BadgeStyle(R.color.badge_blue, icon = R.drawable.ic_bolt),
        BadgeStyle(R.color.badge_purple, icon = R.drawable.ic_trophy)
    )

    fun toStudentProfile(data: ProfileData, now: Long = System.currentTimeMillis()): StudentProfile {
        val user = data.user
        val classroom = data.classroom
        val grade = classroom?.gradeNumber
        val level = levelFor(user.totalXp)
        val (className, school) = ClassroomNaming.parse(classroom?.name.orEmpty())
        val allAnswers = data.attempts.flatMap { it.answers }

        // best accuracy and stars per lesson
        val bestAccuracy: Map<String, Double> = data.attempts
            .groupBy { it.lessonId }
            .mapValues { (_, attempts) -> attempts.maxOf { it.accuracy } }
        val lessonStars: Map<String, Int> = bestAccuracy.mapValues { starsFor(it.value) }

        // topic -> subject index, lesson -> subject index
        val topicToSubject = HashMap<String, Int>()
        data.subjects.forEachIndexed { index, subject ->
            subject.grades
                .filter { it.gradeNumber == grade }
                .forEach { g -> g.topics.forEach { topic -> topicToSubject[topic.id] = index } }
        }
        val lessonToSubject: Map<String, Int> = data.lessons
            .mapNotNull { lesson -> topicToSubject[lesson.topicId]?.let { lesson.id to it } }
            .toMap()

        // the subject of the latest attempt is the "active" one
        val latestAttempt = data.attempts.maxByOrNull { IsoDate.parse(it.date) ?: 0L }
        val activeIndex = latestAttempt?.let { lessonToSubject[it.lessonId] } ?: 0

        val subjects = data.subjects.mapIndexed { index, subject ->
            val lessons = data.lessons.filter { !it.isPublic && lessonToSubject[it.id] == index }
            SubjectProgress(
                id = subject.id,
                title = if (grade != null) "${subject.name} $grade" else subject.name,
                lessonsDone = lessons.count { it.status == "DONE" },
                lessonsTotal = lessons.size,
                stars = lessons.sumOf { it.stars },
                accent = ACCENTS[index % ACCENTS.size],
                iconRes = iconFor(subject.icon),
                isActive = index == activeIndex
            )
        }

        // recent results
        val lessonById = data.lessons.associateBy { it.id }
        val recentResults = data.attempts
            .sortedByDescending { IsoDate.parse(it.date) ?: 0L }
            .take(RECENT_RESULTS)
            .mapNotNull { attempt ->
                val lesson = lessonById[attempt.lessonId] ?: return@mapNotNull null
                val subjectIndex = lessonToSubject[attempt.lessonId]
                val subject = subjectIndex?.let { data.subjects[it] }
                QuizResult(
                    title = lesson.name,
                    subtitle = listOf(subject?.name.orEmpty(), formatShort(IsoDate.parse(attempt.date)))
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    score = attempt.answers.count { it.isCorrect },
                    maxScore = attempt.answers.size,
                    xp = attempt.points,
                    accent = ACCENTS[(subjectIndex ?: 0) % ACCENTS.size],
                    iconRes = iconFor(subject?.icon)
                )
            }

        // badges: earned ones come from User.badges, text from the catalog
        val earnedIds = user.badges.map { it.badgeId }.toSet()
        val allBadges = data.badgeCatalog.mapIndexed { index, badge ->
            val style = BADGE_STYLES[index % BADGE_STYLES.size]
            BadgeEntry(
                BadgeItem(badge.name, badge.condition, style.color, style.icon, style.glyph),
                badge.id in earnedIds
            )
        }
        val earned = data.badgeCatalog.withIndex()
            .filter { it.value.id in earnedIds }
            .map { (index, badge) ->
                val style = BADGE_STYLES[index % BADGE_STYLES.size]
                BadgeItem(badge.name, badge.condition, style.color, style.icon, style.glyph)
            }
        val locked = data.badgeCatalog
            .firstOrNull { it.id !in earnedIds }
            ?.let { LockedBadge(it.name, it.condition) }

        return StudentProfile(
            name = user.name,
            email = user.email,
            role = user.role,
            className = className.ifBlank { "—" },
            school = school,
            classCode = classroom?.joinCode ?: "—",
            level = level,
            levelTitle = titleFor(level),
            xp = user.totalXp,
            xpLevelStart = xpForLevel(level),
            xpGoal = xpForLevel(level + 1),
            stars = data.lessons.sumOf { it.stars },
            streakDays = effectiveStreak(user.streak, user.streakAnchorMillis(), now),
            week = weekFor(user.streak, user.streakAnchorMillis(), now),
            solvedTasks = allAnswers.size,
            accuracy = if (allAnswers.isEmpty()) 0
                    else (allAnswers.count { it.isCorrect } * 100.0 / allAnswers.size).roundToInt(),
            subjects = subjects,
            allBadges = allBadges,
            badgesEarned = earned.size,
            badgesTotal = data.badgeCatalog.size,
            badges = earned,
            lockedBadge = locked,
            recentResults = recentResults,
            registeredAt = formatDate(IsoDate.parse(user.registeredAt)),
            lastActiveAt = formatDate(IsoDate.parse(user.lastActiveAt))
        )
    }

    // levels

    fun levelFor(xp: Int): Int = LEVEL_START.indexOfLast { xp >= it } + 1

    // The first XP of a level. Levels above the table grow by 1500 XP each
    fun xpForLevel(level: Int): Int =
        if (level <= LEVEL_START.size) LEVEL_START[level - 1]
        else LEVEL_START.last() + 1500 * (level - LEVEL_START.size)

    fun titleFor(level: Int): String =
        LEVEL_TITLES[(level - 1).coerceIn(0, LEVEL_TITLES.lastIndex)]

    fun starsFor(accuracy: Double): Int = when {
        accuracy >= 90 -> 3
        accuracy >= 70 -> 2
        accuracy >= 50 -> 1
        else -> 0
    }

    // Subject.icon is a text key from the backend; unknown keys get the book icon
    fun iconFor(icon: String?): Int = when (icon) {
        "calculate", "calculator" -> R.drawable.ic_calculate
        "public", "globe" -> R.drawable.ic_public
        "atom" -> R.drawable.ic_bolt
        else -> R.drawable.ic_menu_book
    }

    fun effectiveStreak(streak: Int, lastActiveAt: Long?, now: Long): Int {
        val lastDay = lastActiveAt?.let { dayNumber(it) } ?: return 0
        return if (streak > 0 && lastDay >= dayNumber(now) - 1) streak else 0
    }


    fun weekFor(streak: Int, lastActiveAt: Long?, now: Long): List<DayState> {
        val today = dayNumber(now)
        val monday = today - ((today + 3) % 7)      // day 0 (1970-01-01) was a Thursday
        val lastDay = lastActiveAt?.let { dayNumber(it) }
        val activeDays = effectiveStreak(streak, lastActiveAt, now)

        val run: LongRange? =
            if (activeDays > 0 && lastDay != null) (lastDay - (activeDays - 1))..lastDay
            else null

        return List(7) { i ->
            val day = monday + i
            when {
                run != null && day in run -> DayState.DONE
                day == today -> DayState.TODAY
                day > today -> DayState.LOCKED
                else -> DayState.MISSED
            }
        }
    }

    // days since 1970 in the phone's time zone
    private fun dayNumber(millis: Long): Long =
        (millis + TimeZone.getDefault().getOffset(millis)) / DAY_MS

    // dates

    private fun slovenian(pattern: String) = SimpleDateFormat(
        pattern,
        Locale.Builder().setLanguage("sl").setRegion("SI").build()
    )

    private fun formatDate(millis: Long?): String =
        if (millis == null) "—" else slovenian("d. M. yyyy").format(Date(millis))

    private fun formatShort(millis: Long?): String =
        if (millis == null) "" else slovenian("d. M.").format(Date(millis))
}