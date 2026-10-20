package com.hnrzzin.granaxp.viewmodel

import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.model.CategoriaConquista
import com.hnrzzin.granaxp.model.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException

class AchievementStateTest {
    @Test fun cancelledAchievementRefreshDoesNotBecomeAnErrorState() = runBlocking {
        try {
            loadAchievementUiState(
                loadCatalog = { throw CancellationException("superseded refresh") },
                loadCanonicalProgress = { emptyList() },
                loadLegacyProgress = { emptyList() },
            )
            fail("Cancellation must propagate so a stale refresh cannot replace newer results")
        } catch (_: CancellationException) {
            // A superseded refresh must not publish an error in the current request.
        }
    }

    @Test
    fun `historico desbloqueado continua visivel com canonico ainda bloqueado`() {
        val definition = AchievementModel(id = "badge", requirementType = RequirementType.GOAL_COUNT)
        val historical = AchievementProgressModel(achievementId = "badge", isUnlocked = true)
        val pending = AchievementProgressModel(achievementId = "badge", isUnlocked = false)
        assertSame(historical, combineAchievements(listOf(definition), listOf(pending), listOf(historical)).single().progress)
    }
    @Test
    fun `catalogo vazio e sucesso sem cards`() = runBlocking {
        val state = loadAchievementUiState(
            loadCatalog = { emptyList() },
            loadCanonicalProgress = { emptyList() },
            loadLegacyProgress = { emptyList() },
        )

        assertTrue(state is AchievementUiState.Success)
        assertEquals(0, (state as AchievementUiState.Success).achievements.size)
    }

    @Test
    fun `falha global do catalogo chega como erro e nao consulta progresso`() = runBlocking {
        var progressReads = 0
        val state = loadAchievementUiState(
            loadCatalog = { throw IllegalStateException("Firestore indisponivel") },
            loadCanonicalProgress = { progressReads++; emptyList() },
            loadLegacyProgress = { progressReads++; emptyList() },
        )

        assertTrue(state is AchievementUiState.Error)
        assertEquals(0, progressReads)
    }

    @Test
    fun `falha de progresso legado chega como erro e nao como ausencia`() = runBlocking {
        val state = loadAchievementUiState(
            loadCatalog = { listOf(AchievementModel(id = "lesson")) },
            loadCanonicalProgress = { emptyList() },
            loadLegacyProgress = { throw IllegalStateException("Progresso indisponivel") },
        )

        assertTrue(state is AchievementUiState.Error)
    }

    @Test
    fun `progresso legado nao desbloqueia conquista de streak`() {
        val streak = AchievementModel(id = "badge-streak", requirementType = RequirementType.STREAK)
        val legacy = AchievementProgressModel(
            id = "legacy-streak", achievementId = "badge-streak", isUnlocked = true,
        )
        val result = combineAchievements(listOf(streak), emptyList(), listOf(legacy))
        assertEquals(1, result.size)
        assertNull(result.single().progress)
    }

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
