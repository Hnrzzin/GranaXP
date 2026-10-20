package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.model.StreakModel
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.datetime.LocalDate

/** Educational feedback only. Catalog answers are public in the Spark edition. */
internal fun isCorrectEducationalAnswer(
    selected: Set<String>,
    answers: Set<String>,
    available: Set<String>,
): Boolean = answers.isNotEmpty() && answers.all { it in available } &&
    selected.isNotEmpty() && selected.all { it in available } && selected == answers

/** Display score derived from the user's own progress; never used for access control. */
internal fun educationalXpTotal(
    lessons: List<LessonModel>,
    progress: List<LessonProgressModel>,
): Int {
    val completed = progress.filter { it.isCompleted }.map { it.lessonId }.toSet()
    return lessons.filter { it.id in completed }.sumOf { it.xpReward.coerceAtLeast(0) }
}

internal fun educationalDisplayUser(stored: UserModel, totalXp: Int): UserModel {
    var remaining = totalXp.coerceAtLeast(0)
    var level = 1
    var next = 100
    while (remaining >= next && level < 1_000_000) {
        remaining -= next
        level++
        next = (next * 1.2).toInt().coerceAtLeast(next + 1)
    }
    return stored.copy(level = level, xp = remaining, nextLevelXp = next)
}

internal fun educationalStreak(completedDates: List<String>, today: String): StreakModel {
    val todayDay = LocalDate.parse(today).toEpochDays()
    val days = completedDates.mapNotNull { runCatching { LocalDate.parse(it).toEpochDays() }.getOrNull() }
        .filter { it <= todayDay }.distinct().sorted()
    if (days.isEmpty()) return StreakModel()
    var run = 0
    var highest = 0
    var last: Int? = null
    for (day in days) {
        run = if (last == null || day - last >= 5) 1 else run + 1
        highest = maxOf(highest, run)
        last = day
    }
    val current = if (todayDay - days.last() >= 5) 0 else run
    return StreakModel(
        currentStreak = current,
        highestStreak = highest,
        lastMissionCompletedDate = LocalDate.fromEpochDays(days.last()).toString(),
        claimedMilestones = listOf(3, 7, 10, 15, 30).filter { it <= highest },
    )
}
