package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkAchievementsTest {
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
