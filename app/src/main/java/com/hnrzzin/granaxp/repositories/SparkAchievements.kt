package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.model.RequirementType

/** Values are derived from user-editable data and are never stored as canonical unlocks. */
internal data class SparkAchievementFacts(
    val transactionCount: Int = 0,
    val goalCount: Int = 0,
    val lessonCount: Int = 0,
    val monthlySavings: Int = 0,
    val completedModules: Set<String> = emptySet(),
    val highestStreak: Int = 0,
)

internal fun deriveSparkAchievements(
    catalog: List<AchievementModel>,
    facts: SparkAchievementFacts,
): List<AchievementProgressModel> = catalog.map { achievement ->
    val count = when (achievement.requirementType) {
        RequirementType.TRANSACTION_COUNT -> facts.transactionCount
        RequirementType.GOAL_COUNT -> facts.goalCount
        RequirementType.LESSON_COUNT -> facts.lessonCount
        RequirementType.MONTHLY_SAVINGS -> facts.monthlySavings
        RequirementType.MODULE_COMPLETION -> if (achievement.referenceId in facts.completedModules) 1 else 0
        RequirementType.STREAK -> facts.highestStreak
    }.coerceAtLeast(0)
    AchievementProgressModel(
        id = achievement.id,
        achievementId = achievement.id,
        currentProgress = count.coerceAtMost(achievement.requirementValue.coerceAtLeast(0)),
        isUnlocked = achievement.requirementValue > 0 && count >= achievement.requirementValue,
    )
}
