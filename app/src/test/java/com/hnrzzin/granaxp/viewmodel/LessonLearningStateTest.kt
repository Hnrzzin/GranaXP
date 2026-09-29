package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.model.ModuleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonLearningStateTest {

    @Test
    fun legacyLessonsRemainAvailableWhenModuleCatalogIsEmpty() {
        val legacyLesson = LessonModel(id = "legacy-1", moduleId = "")

        val state = buildLearningContent(
            modules = emptyList(),
            allLessons = listOf(legacyLesson),
            progressList = emptyList(),
            lessonsByModule = emptyMap(),
        )

        assertTrue(state.modules.isEmpty())
        assertEquals(listOf("legacy-1"), state.legacyLessons.map { it.lesson.id })
        assertFalse(state.legacyLessons.single().isStarted)
        assertFalse(state.legacyLessons.single().isCompleted)
    }

    @Test
    fun lessonsAreGroupedWithoutMixingModuleAndLegacySequences() {
        val module = ModuleModel(idModule = "module-1")
        val moduleLesson = LessonModel(id = "module-lesson", moduleId = "module-1")
        val legacyLesson = LessonModel(id = "legacy-lesson", moduleId = "")

        val state = buildLearningContent(
            modules = listOf(module),
            allLessons = listOf(moduleLesson, legacyLesson),
            progressList = emptyList(),
            lessonsByModule = mapOf("module-1" to listOf(moduleLesson)),
        )

        assertEquals(listOf("module-lesson"), state.modules.single().lessons.map { it.lesson.id })
        assertEquals(listOf("legacy-lesson"), state.legacyLessons.map { it.lesson.id })
    }

    @Test(expected = IllegalStateException::class)
    fun lessonWithUnknownModuleIsRejectedInsteadOfJoiningGlobalSequence() {
        val orphanLesson = LessonModel(id = "orphan", moduleId = "missing-module")

        buildLearningContent(
            modules = emptyList(),
            allLessons = listOf(orphanLesson),
            progressList = emptyList(),
            lessonsByModule = emptyMap(),
        )
    }

    @Test
    fun moduleProgressIsCalculatedFromCompletedLessons() {
        val module = ModuleWithLessons(
            module = ModuleModel(idModule = "module-1"),
            lessons = listOf(
                lesson("lesson-1", completed = true),
                lesson("lesson-2", completed = false),
                lesson("lesson-3", completed = false),
            ),
        )

        assertEquals(1, module.completedLessons)
        assertEquals(3, module.totalLessons)
        assertEquals(1f / 3f, module.progress, 0.0001f)
        assertFalse(module.isCompleted)
    }

    @Test
    fun eachModuleStartsUnlockedAndOnlyDependsOnItsOwnPreviousLesson() {
        val firstModule = ModuleWithLessons(
            module = ModuleModel(idModule = "module-1"),
            lessons = listOf(
                lesson("module-1-lesson-1", completed = false),
                lesson("module-1-lesson-2", completed = false),
            ),
        )
        val secondModule = ModuleWithLessons(
            module = ModuleModel(idModule = "module-2"),
            lessons = listOf(
                lesson("module-2-lesson-1", completed = false),
                lesson("module-2-lesson-2", completed = false),
            ),
        )

        assertTrue(firstModule.isLessonUnlocked(0))
        assertFalse(firstModule.isLessonUnlocked(1))
        assertTrue(secondModule.isLessonUnlocked(0))
        assertFalse(secondModule.isLessonUnlocked(1))
    }

    @Test
    fun completedPreviousLessonUnlocksOnlyTheNextLessonInThatModule() {
        val module = ModuleWithLessons(
            module = ModuleModel(idModule = "module-1"),
            lessons = listOf(
                lesson("lesson-1", completed = true),
                lesson("lesson-2", completed = false),
                lesson("lesson-3", completed = false),
            ),
        )

        assertTrue(module.isLessonUnlocked(1))
        assertFalse(module.isLessonUnlocked(2))
    }

    @Test
    fun emptyModuleHasZeroProgress() {
        val module = ModuleWithLessons(
            module = ModuleModel(idModule = "module-1"),
            lessons = emptyList(),
        )

        assertEquals(0, module.completedLessons)
        assertEquals(0, module.totalLessons)
        assertEquals(0f, module.progress, 0f)
        assertFalse(module.isCompleted)
    }

    @Test
    fun moduleIsCompletedWhenEveryLessonIsCompleted() {
        val module = ModuleWithLessons(
            module = ModuleModel(idModule = "module-1"),
            lessons = listOf(
                lesson("lesson-1", completed = true),
                lesson("lesson-2", completed = true),
            ),
        )

        assertEquals(1f, module.progress, 0f)
        assertTrue(module.isCompleted)
    }

    private fun lesson(id: String, completed: Boolean): LessonWithProgress {
        return LessonWithProgress(
            lesson = LessonModel(id = id),
            progress = LessonProgressModel(
                lessonId = id,
                isCompleted = completed,
            ),
        )
    }
}
