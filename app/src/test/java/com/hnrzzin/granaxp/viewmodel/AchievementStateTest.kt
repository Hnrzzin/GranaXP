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

class AchievementStateTest {
    @Test
    fun `checagens V1 preservam tipos e nao leem progresso se catalogo falhar`() = runBlocking {
        val cases = listOf(
            RequirementType.LESSON_COUNT to CategoriaConquista.EDUCACAO,
            RequirementType.GOAL_COUNT to null,
            RequirementType.TRANSACTION_COUNT to CategoriaConquista.FINANCAS,
        )
        val catalog = cases.mapIndexed { index, (type, category) ->
            AchievementModel(
                id = "v1-$index", requirementType = type,
                category = category ?: CategoriaConquista.FINANCAS,
            )
        } + AchievementModel(id = "streak", requirementType = RequirementType.STREAK)
        for ((type, category) in cases) {
            val (count, selected, progress) = loadLegacyAchievementCheckInputs(
                loadCount = { 2 }, loadCatalog = { catalog }, loadProgress = { emptyList() },
                requirementType = type, category = category,
            )
            assertEquals(2, count)
            assertEquals(listOf(type), selected.map { it.requirementType })
            assertEquals(emptyList<AchievementProgressModel>(), progress)

            var catalogReads = 0
            try {
                loadLegacyAchievementCheckInputs(
                    loadCount = { throw IllegalStateException("Contagem indisponivel") },
                    loadCatalog = { catalogReads++; catalog },
                    loadProgress = { emptyList() },
                    requirementType = type, category = category,
                )
                fail("A checagem de $type tratou falha da contagem como zero")
            } catch (_: IllegalStateException) {
                assertEquals(0, catalogReads)
            }

            var progressReads = 0
            try {
                loadLegacyAchievementCheckInputs(
                    loadCount = { 2 },
                    loadCatalog = { throw IllegalStateException("Firestore indisponivel") },
                    loadProgress = { progressReads++; emptyList() },
                    requirementType = type, category = category,
                )
                fail("A checagem de $type aceitou falha do catalogo")
            } catch (_: IllegalStateException) {
                assertEquals(0, progressReads)
            }

            try {
                loadLegacyAchievementCheckInputs(
                    loadCount = { 2 },
                    loadCatalog = { catalog },
                    loadProgress = { throw IllegalStateException("Progresso indisponivel") },
                    requirementType = type, category = category,
                )
                fail("A checagem de $type tratou falha do progresso como lista vazia")
            } catch (_: IllegalStateException) {
                // Nenhum resultado é produzido para que a ViewModel tente criar progresso novo.
            }
        }
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
