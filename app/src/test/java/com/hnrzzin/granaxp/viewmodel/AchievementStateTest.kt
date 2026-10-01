package com.hnrzzin.granaxp.viewmodel

import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.model.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AchievementStateTest {
    @Test
    fun `progresso legado nao desbloqueia conquista de modulo`() {
        val module = AchievementModel(id = "badge-module", requirementType = RequirementType.MODULE_COMPLETION)
        val legacy = AchievementProgressModel(
            id = "legacy-random", achievementId = "badge-module", isUnlocked = true,
        )

        val result = combineAchievements(listOf(module), emptyList(), listOf(legacy))

        assertEquals(1, result.size)
        assertNull(result.single().progress)
    }

    @Test
    fun `progresso novo tem precedencia sem duplicar card e preserva unlockedAt`() {
        val unlockedAt = Timestamp(1_800_000_000, 0)
        val module = AchievementModel(id = "badge-module", requirementType = RequirementType.MODULE_COMPLETION)
        val lesson = AchievementModel(id = "badge-lesson", requirementType = RequirementType.LESSON_COUNT)
        val canonical = AchievementProgressModel(
            id = "badge-module", achievementId = "badge-module", currentProgress = 1,
            isUnlocked = true, unlockedAt = unlockedAt,
        )
        val legacyModule = AchievementProgressModel(
            id = "legacy-module", achievementId = "badge-module", currentProgress = 1, isUnlocked = true,
        )
        val legacyLesson = AchievementProgressModel(
            id = "legacy-lesson", achievementId = "badge-lesson", currentProgress = 3, isUnlocked = true,
        )

        val result = combineAchievements(
            listOf(module, lesson), listOf(canonical), listOf(legacyModule, legacyLesson),
        )

        assertEquals(2, result.size)
        assertSame(canonical, result[0].progress)
        assertSame(unlockedAt, result[0].progress?.unlockedAt)
        assertNull(result[0].progress?.lastUpdated)
        assertSame(legacyLesson, result[1].progress)
        assertNull(result[1].progress?.unlockedAt)
    }

    @Test
    fun `duplicatas legadas nao ocultam um desbloqueio ja registrado`() {
        val lesson = AchievementModel(id = "badge-lesson", requirementType = RequirementType.LESSON_COUNT)
        val unlocked = AchievementProgressModel(
            id = "first", achievementId = "badge-lesson", currentProgress = 3, isUnlocked = true,
        )
        val stale = AchievementProgressModel(
            id = "second", achievementId = "badge-lesson", currentProgress = 1, isUnlocked = false,
        )

        val result = combineAchievements(listOf(lesson), emptyList(), listOf(unlocked, stale))

        assertEquals(1, result.size)
        assertSame(unlocked, result.single().progress)
    }
}
