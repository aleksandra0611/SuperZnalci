package com.example.superznalci

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import java.util.Locale


data class PodiumItem(
    val rank: Int,
    val name: String, 
    val initials: String,
    val xp: Int,
    val avatar: String?,
    val isMe: Boolean
)

data class MyRank(
    val rank: Int,
    val name: String,
    val xp: Int,
    val gainedXp: Int, 
    val xpToNext: Int?, 
    val nextRank: Int?,
    val progress: Int
)

data class LastResult(
    val chip: String,
    val lessonName: String,
    val xp: Int,
    val accuracy: Int,
    val timeLabel: String,
    val wrongCount: Int
)

data class CheerTarget(val userId: String, val name: String, val rank: Int)

data class LeaderboardScreen(
    val streakDays: Int,
    val stars: Int,
    val className: String,
    val result: LastResult?,
    val podium: List<PodiumItem>,
    val me: MyRank?,
    val cheer: CheerTarget?,
    val badges: List<BadgeItem>
)

object LeaderboardMapper {

    private class BadgeStyle(
        @ColorRes val color: Int,
        @DrawableRes val icon: Int? = null,
        val glyph: String? = null
    )

    private val BADGE_STYLES = listOf(
        BadgeStyle(R.color.badge_orange, glyph = "🔥"),
        BadgeStyle(R.color.badge_yellow, glyph = "Σ"),
        BadgeStyle(R.color.badge_blue, icon = R.drawable.ic_bolt),
        BadgeStyle(R.color.badge_purple, icon = R.drawable.ic_trophy)
    )

    fun toScreen(data: LeaderboardData, now: Long = System.currentTimeMillis()): LeaderboardScreen {
        val user = data.user
        val (className, _) = ClassroomNaming.parse(data.classroom?.name.orEmpty())
        val classLabel = className.ifBlank { "—" }.replaceFirst(".", ". ")

        val ranked = data.leaderboard.entries.sortedByDescending { it.weeklyXp }

        val podium = ranked.take(3).mapIndexed { index, e ->
            PodiumItem(
                rank = index + 1,
                name = shortName(e.name),
                initials = initials(e.name),
                xp = e.weeklyXp,
                avatar = e.avatar,
                isMe = e.userId == user.id
            )
        }

        val gained = data.lastAttempt?.attempt?.points ?: 0
        val meIndex = ranked.indexOfFirst { it.userId == user.id }
        val me = if (meIndex < 0) null else {
            val mine = ranked[meIndex]
            val above = ranked.getOrNull(meIndex - 1)
            val below = ranked.getOrNull(meIndex + 1)
            val low = below?.weeklyXp ?: 0
            val progress = if (above == null) 100 else {
                val range = (above.weeklyXp - low).coerceAtLeast(1)
                ((mine.weeklyXp - low) * 100 / range).coerceIn(0, 100)
            }
            MyRank(
                rank = meIndex + 1,
                name = user.name,
                xp = mine.weeklyXp,
                gainedXp = gained,
                xpToNext = above?.let { (it.weeklyXp - mine.weeklyXp).coerceAtLeast(1) },
                nextRank = above?.let { meIndex },
                progress = progress
            )
        }

        val others = ranked.withIndex().filter { it.index < 3 && it.value.userId != user.id }
        val cheerPick = others.firstOrNull { it.index == 1 } ?: others.firstOrNull()
        val cheer = cheerPick?.let { CheerTarget(it.value.userId, shortName(it.value.name), it.index + 1) }

        val result = data.lastAttempt?.let { last ->
            LastResult(
                chip = "${last.subjectName.uppercase(Locale.getDefault())} • $classLabel",
                lessonName = last.lessonName,
                xp = last.attempt.points,
                accuracy = last.attempt.accuracy.toInt(),
                timeLabel = "${last.durationSeconds / 60}m ${last.durationSeconds % 60}s",
                wrongCount = last.attempt.answers.count { !it.isCorrect }
            )
        }

        val badges = data.newBadges.mapIndexed { index, badge ->
            val style = BADGE_STYLES[index % BADGE_STYLES.size]
            BadgeItem(badge.name, badge.condition, style.color, style.icon, style.glyph)
        }

        return LeaderboardScreen(
            streakDays = ProfileMapper.effectiveStreak(user.streak, IsoDate.parse(user.lastActiveAt), now),
            stars = data.stars,
            className = classLabel,
            result = result,
            podium = podium,
            me = me,
            cheer = cheer,
            badges = badges
        )
    }

    fun shortName(name: String): String {
        val parts = name.cleanName().split(" ")
        return if (parts.size > 1) "${parts.first()} ${parts.last().first().uppercaseChar()}." else parts.first()
    }

    fun initials(name: String): String {
        val parts = name.cleanName().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts.first().take(1).uppercase(Locale.getDefault())
            else -> "${parts.first().first()}${parts.last().first()}".uppercase(Locale.getDefault())
        }
    }
}