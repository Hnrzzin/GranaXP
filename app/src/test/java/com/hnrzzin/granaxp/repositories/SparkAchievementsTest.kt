package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.RequirementType
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.viewmodel.combineAchievements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkAchievementsTest {
    @Test fun allEightCriteriaPreserveHistoricalUnlocksAndDoNotDuplicateCardsOnReload() {
        val catalog = listOf(
            AchievementModel(id = "iQo20q4jxFnAtkJBn8F4", requirementType = RequirementType.TRANSACTION_COUNT, requirementValue = 1),
            AchievementModel(id = "aKJgBEffC6cTvrCaXjBY", requirementType = RequirementType.GOAL_COUNT, requirementValue = 1),
            AchievementModel(id = "Yf1Gf6ez5AkPUUECOePP", requirementType = RequirementType.LESSON_COUNT, requirementValue = 3),
            AchievementModel(id = "conquista_investimentos", requirementType = RequirementType.MODULE_COMPLETION,
                requirementValue = 1, referenceId = "investimentos"),
            AchievementModel(id = "conquista_organizacao_financeira", requirementType = RequirementType.MODULE_COMPLETION,
                requirementValue = 1, referenceId = "organizacao_financeira"),
            *listOf(7, 15, 30).map { AchievementModel(id = "streak_$it", requirementType = RequirementType.STREAK,
                requirementValue = it) }.toTypedArray(),
        )
        val historical = AchievementProgressModel(id = "historical", achievementId = catalog[0].id,
            currentProgress = 1, isUnlocked = true)
        val duplicate = historical.copy(id = "duplicate", currentProgress = 0, isUnlocked = false)
        val facts = SparkAchievementFacts(goalCount = 1, lessonCount = 3,
            completedModules = setOf("investimentos", "organizacao_financeira"), highestStreak = 15)
        val local = deriveSparkAchievements(catalog, facts)
        val result = combineAchievements(catalog, emptyList(), listOf(historical, duplicate), local)
        assertEquals(8, result.size)
        assertEquals(8, result.map { it.achievement.id }.distinct().size)
        assertEquals(7, result.count { it.progress?.isUnlocked == true })
        assertEquals(historical, result.first().progress)
        assertFalse(result.last().progress!!.isUnlocked)
        assertEquals(15, result.last().progress!!.currentProgress)
        assertEquals(result, combineAchievements(catalog, emptyList(), listOf(historical, duplicate),
            deriveSparkAchievements(catalog, facts)))
        assertEquals(8, deriveSparkAchievements(catalog, facts.copy(transactionCount = 1, highestStreak = 30))
            .count { it.isUnlocked })
        assertFalse(historical.lastUpdated != null)
    }

    @Test fun derivesBadgesForDisplayWithoutCreatingStoredUnlocks() {
        val catalog = listOf(
            AchievementModel(id = "lesson", requirementType = RequirementType.LESSON_COUNT,
                requirementValue = 2),
            AchievementModel(id = "module", requirementType = RequirementType.MODULE_COMPLETION,
                requirementValue = 1, referenceId = "module-a"),
            AchievementModel(id = "streak", requirementType = RequirementType.STREAK,
                requirementValue = 7),
        )
        val facts = SparkAchievementFacts(lessonCount = 2,
            completedModules = setOf("module-a"), highestStreak = 3)
        val result = deriveSparkAchievements(catalog, facts).associateBy { it.achievementId }
        assertTrue(result.getValue("lesson").isUnlocked)
        assertTrue(result.getValue("module").isUnlocked)
        assertFalse(result.getValue("streak").isUnlocked)
        assertEquals(3, result.getValue("streak").currentProgress)
    }
}
