package com.hnrzzin.granaxp

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.AuthRepository
import com.hnrzzin.granaxp.repositories.LessonRepository
import com.hnrzzin.granaxp.repositories.UserRepository
import com.hnrzzin.granaxp.ui.theme.screens.LearnScreen
import com.hnrzzin.granaxp.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real UI + ViewModels + SDK, existing local catalog and a new fictitious user only. */
@RunWith(AndroidJUnit4::class)
class LessonProgressiveIntegrationTest {
    @get:Rule val proof = InstrumentationExecutionProof()
    @get:Rule val compose = createComposeRule()

    private fun waitForText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun threeLessonsAdvanceReopenValidateAndRewardOnlyOnCompletion(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        assertEquals("demo-granaxp", FirebaseApp.getInstance().options.projectId)
        assertEquals("127.0.0.1:8080", FirebaseFirestore.getInstance().firestoreSettings.host)
        val cases = listOf(
            Triple("2iT6j1pA0vvq62oX7jO7", 1, 50),
            Triple("gMmIv1XNtMhIrhBzDFKc", 2, 50),
            Triple("Eo6rq9c4HDAtZRR6TAjp", 3, 80),
        )
        assertEquals(3, cases.size)
        val auth = AuthRepository()
        auth.logout()
        val email = "e5-ui-${System.nanoTime()}@example.test"
        val uid = auth.registerWithEmail(email, "test-${UUID.randomUUID()}")
        auth.createUserProfile(UserModel(id = uid, name = "Ensaio local", email = email))
        val repository = LessonRepository(uid)
        val displayProfile = UserRepository(uid)
        val store = ViewModelStore()
        val witnessed = mutableListOf<String>()
        fun witness(step: String) { witnessed += step; Log.i("E5UiWitness", "PASS $step") }
        lateinit var trail: LessonViewModel
        lateinit var daily: DailyMissionViewModel
        lateinit var user: UserViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            trail = LessonViewModel(uid); daily = DailyMissionViewModel(); user = UserViewModel(uid)
            store.put("trail", trail); store.put("daily", daily); store.put("user", user)
        }
        try {
            compose.setContent { MaterialTheme { LearnScreen(trail, daily, {}, {}, user, {}) } }
            waitForText("Escolha seu Caminho")
            val lessons = repository.getLessons().associateBy { it.id }
            assertEquals(cases.map { it.first }.toSet(), lessons.keys)
            compose.onNodeWithContentDescription("Abrir módulo: Organização Financeira").performClick()
            compose.onNodeWithText("Reserva de Emergência").assertIsNotEnabled()
            witness("reserva_bloqueada_antes_da_primeira_licao")
            for ((position, case) in cases.withIndex()) {
                val (id, expectedLevel, expectedXp) = case
                val lesson = lessons.getValue(id)
                if (position == 2) {
                    compose.onNodeWithText("Voltar aos módulos").performClick()
                    compose.onNodeWithContentDescription("Abrir módulo: Investimentos").performClick()
                }
                val opened = repository.openLesson(id)
                val before = auth.getUserProfile(uid)!!
                compose.onNodeWithText(lesson.title).assertIsEnabled().performClick()
                waitForText("Etapa 1 de ${opened.blocks.size}")
                for ((index, block) in opened.blocks.withIndex()) {
                    waitForText("Etapa ${index + 1} de ${opened.blocks.size}")
                    if (block.title.isNotBlank()) compose.onNodeWithText(block.title).assertExists()
                    val next = opened.blocks.getOrNull(index + 1)
                    if (next != null && next.title.isNotBlank() && next.title != block.title) {
                        compose.onNodeWithText(next.title).assertDoesNotExist()
                    }
                    val unchanged = auth.getUserProfile(uid)!!
                    assertEquals(before.level, unchanged.level); assertEquals(before.xp, unchanged.xp)
                    if (index == 1) {
                        compose.onNodeWithText("Anterior").performClick()
                        waitForText("Etapa 1 de ${opened.blocks.size}")
                        compose.onNodeWithText("Continuar").performClick()
                    }
                    if (block.type.equals("ACTIVITY", ignoreCase = true)) {
                        val button = if (index == opened.blocks.lastIndex) "Concluir lição" else "Continuar"
                        compose.onNodeWithText(button).assertIsNotEnabled()
                        val right = block.correctAnswerIds.single()
                        val wrongLabel = block.alternatives.entries.first { it.key != right }.value
                        compose.onNodeWithText(wrongLabel).performScrollTo().performClick()
                        compose.onNodeWithText("Responder").performScrollTo().performClick()
                        compose.waitUntil(20_000) {
                            (trail.contentUiState.value as? LessonContentUiState.Success)
                                ?.activityAnswers?.get(block.id)?.result == ActivityAnswerResult.INCORRECT
                        }
                        compose.onNodeWithText(button).assertIsNotEnabled()
                        assertEquals(before.xp, auth.getUserProfile(uid)!!.xp)
                        compose.onNodeWithText(block.alternatives.getValue(right)).performScrollTo().performClick()
                        compose.onNodeWithText("Responder").performScrollTo().performClick()
                        compose.waitUntil(20_000) {
                            block.id in (trail.contentUiState.value as? LessonContentUiState.Success)
                                ?.completedActivityIds.orEmpty()
                        }
                        compose.onNodeWithText(button).assertIsEnabled()
                        if (position == 0 && block.id == opened.blocks.first {
                            it.type.equals("ACTIVITY", ignoreCase = true)
                        }.id) {
                            repeat(index + 1) { compose.onNodeWithContentDescription("Voltar").performClick() }
                            waitForText("Voltar aos módulos")
                            compose.onNodeWithText(lesson.title).performClick()
                            waitForText("Etapa 1 de ${opened.blocks.size}")
                            repeat(index) { compose.onNodeWithText("Continuar").performClick() }
                            compose.onNodeWithText("Continuar").assertIsEnabled()
                            assertTrue(block.id in (trail.contentUiState.value as LessonContentUiState.Success).completedActivityIds)
                            assertEquals(0, auth.getUserProfile(uid)!!.xp)
                            witness("reabertura_retem_atividade_confirmada_sem_xp")
                        }
                        witness("${block.id}_erro_bloqueado_acerto_confirmado")
                    }
                    if (index < opened.blocks.lastIndex) compose.onNodeWithText("Continuar").performClick()
                }
                compose.onNodeWithText("Concluir lição").assertIsEnabled().performClick()
                waitForText("Voltar aos módulos")
                val profile = displayProfile.getDisplayUser()!!
                assertEquals(expectedLevel, profile.level); assertEquals(expectedXp, profile.xp)
                assertEquals(0, auth.getUserProfile(uid)!!.xp)
                assertEquals(0, repository.completeLesson(id).xpEarned)
                val same = displayProfile.getDisplayUser()!!
                assertEquals(profile.level, same.level); assertEquals(profile.xp, same.xp)
                witness("${id}_conclusao_ui_retorna_modulo_repeticao_zero_xp")
            }
            val progress = repository.getLessonProgress()
            assertEquals(3, progress.count { it.isCompleted })
            assertTrue("Reabertura deve ser exercitada", "reabertura_retem_atividade_confirmada_sem_xp" in witnessed)
            val output = JSONObject().put("project", "demo-granaxp").put("email", email)
                .put("steps", JSONArray(witnessed)).put("lessons", 3).put("activities", 9)
                .put("level", 3).put("xp", 80)
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir
                .resolve("e5-ui-report.json").writeText(output.toString(2))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            auth.logout()
        }
    }
}
