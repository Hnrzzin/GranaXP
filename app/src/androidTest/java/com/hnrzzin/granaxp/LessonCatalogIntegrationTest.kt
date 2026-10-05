package com.hnrzzin.granaxp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hnrzzin.granaxp.repositories.DailyMissionRepository
import com.hnrzzin.granaxp.repositories.LessonRepository
import com.hnrzzin.granaxp.viewmodel.LessonUiState
import com.hnrzzin.granaxp.viewmodel.LessonViewModel
import com.hnrzzin.granaxp.viewmodel.DailyMissionViewModel
import com.hnrzzin.granaxp.viewmodel.UserViewModel
import com.hnrzzin.granaxp.ui.theme.screens.LearnScreen
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Run only with the opt-in APK and a fresh Emulator Suite --project demo-granaxp. */
@RunWith(AndroidJUnit4::class)
class LessonCatalogIntegrationTest {
    @get:Rule
    val executionProof = InstrumentationExecutionProof()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun realSdkDistinguishesEmptyLoadedAndFailedCatalogsAcrossTheLearningFlow(): Unit = runBlocking {
        assumeTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        // Fail before any request if initialization ever selects a live project.
        assertEquals("demo-granaxp", FirebaseApp.getInstance().options.projectId)
        assertEquals("127.0.0.1:8080", FirebaseFirestore.getInstance().firestoreSettings.host)
        withTimeout(60_000) {
            val auth = FirebaseAuth.getInstance()
            val email = "block10-2-${System.nanoTime()}@example.test"
            val password = "local-test-password"
            val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
            val db = FirebaseFirestore.getInstance()
            val repository = LessonRepository(uid)
            val viewModels = ViewModelStore()
            try {
                db.collection("users").document(uid).set(mapOf(
                    "name" to "Teste local", "email" to email,
                    "level" to 1, "xp" to 0, "nextLevelXp" to 100,
                )).await()

                assertTrue("Use a fresh demo catalog", repository.getLessons().isEmpty())
                lateinit var trail: LessonViewModel
                lateinit var daily: DailyMissionViewModel
                lateinit var user: UserViewModel
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    trail = LessonViewModel(uid)
                    daily = DailyMissionViewModel()
                    user = UserViewModel(uid)
                    viewModels.put("trail", trail)
                    viewModels.put("daily", daily)
                    viewModels.put("user", user)
                }
                compose.setContent {
                    MaterialTheme {
                        LearnScreen(trail, daily, {}, {}, user, {})
                    }
                }
                val empty = awaitTrail(trail)
                assertTrue(empty is LessonUiState.Success)
                assertTrue((empty as LessonUiState.Success).lessons.isEmpty())
                compose.onNodeWithText("Nenhum conteúdo de aprendizagem disponível.").assertIsDisplayed()

                // A cached empty snapshot must not hide an unavailable catalog server.
                db.disableNetwork().await()
                try {
                    try {
                        repository.getLessons()
                        fail("Offline cache must not become a confirmed empty catalog")
                    } catch (failure: FirebaseFirestoreException) {
                        assertEquals(FirebaseFirestoreException.Code.UNAVAILABLE, failure.code)
                    }
                    refreshTrail(trail)
                    assertTrue(awaitTrail(trail) is LessonUiState.Error)
                    compose.onNodeWithText("Falha ao carregar a trilha:", substring = true).assertIsDisplayed()
                    compose.onNodeWithText("Nenhum conteúdo de aprendizagem disponível.").assertDoesNotExist()
                } finally {
                    db.enableNetwork().await()
                }
                refreshTrail(trail)
                assertTrue(awaitTrail(trail) is LessonUiState.Success)
                compose.onNodeWithText("Nenhum conteúdo de aprendizagem disponível.").assertIsDisplayed()

                adminDocument("lessons?documentId=block10-2-b", "POST", """
                    {"fields":{"title":{"stringValue":"Segunda"},"order":{"integerValue":"1"}}}
                """.trimIndent())
                adminDocument("lessons?documentId=block10-2-a", "POST", """
                    {"fields":{"title":{"stringValue":"Primeira"},"order":{"integerValue":"0"}}}
                """.trimIndent())
                val lessons = repository.getLessons()
                assertEquals(listOf("block10-2-a", "block10-2-b"), lessons.map { it.id })
                assertEquals(listOf("Primeira", "Segunda"), lessons.map { it.title })
                refreshTrail(trail)
                val loaded = awaitTrail(trail)
                assertTrue(loaded is LessonUiState.Success)
                assertEquals(listOf("block10-2-a", "block10-2-b"),
                    (loaded as LessonUiState.Success).lessons.map { it.lesson.id })
                compose.onNodeWithText("Primeira").assertIsDisplayed()
                compose.onNodeWithText("Nenhum conteúdo de aprendizagem disponível.").assertDoesNotExist()

                // A real authenticated callable proves Auth + Functions share the demo project.
                assertFalse(DailyMissionRepository().openDailyMission(1_791_115_200_000).available)

                // Keep Auth, modules and progress readable; fail specifically inside getLessons.
                adminDocument("lessons?documentId=block10-2-invalid", "POST", """
                    {"fields":{"title":{"integerValue":"123"},"order":{"integerValue":"2"}}}
                """.trimIndent())
                refreshTrail(trail)
                assertTrue(awaitTrail(trail) is LessonUiState.Error)
                compose.onNodeWithText("Falha ao carregar a trilha:", substring = true).assertIsDisplayed()
                compose.onNodeWithText("Nenhum conteúdo de aprendizagem disponível.").assertDoesNotExist()
                adminDocument("lessons/block10-2-invalid", "DELETE")
                refreshTrail(trail)
                assertTrue(awaitTrail(trail) is LessonUiState.Success)
                compose.onNodeWithText("Primeira").assertIsDisplayed()

                auth.signOut()
                try {
                    repository.getLessons()
                    fail("Permission failure must not become an empty catalog")
                } catch (failure: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, failure.code)
                }
                refreshTrail(trail)
                assertTrue(awaitTrail(trail) is LessonUiState.Error)
                compose.onNodeWithText("Falha ao carregar a trilha:", substring = true).assertIsDisplayed()
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
                adminDocument("lessons/block10-2-a", "DELETE")
                adminDocument("lessons/block10-2-b", "DELETE")
                adminDocument("lessons/block10-2-invalid", "DELETE")
                adminDocument("users/$uid", "DELETE")
                auth.signInWithEmailAndPassword(email, password).await().user!!.delete().await()
                auth.signOut()
            }
        }
    }

    private fun refreshTrail(viewModel: LessonViewModel) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.fetchLessons() }
    }

    private suspend fun awaitTrail(viewModel: LessonViewModel): LessonUiState {
        return withTimeout(15_000) {
            while (viewModel.uiState.value is LessonUiState.Loading) delay(25)
            viewModel.uiState.value
        }
    }

    private fun adminDocument(path: String, method: String, body: String? = null) {
        val connection = URL(
            "http://127.0.0.1:8080/v1/projects/demo-granaxp/databases/(default)/documents/$path",
        ).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            assertTrue("Local fixture $method $path: ${connection.responseCode}",
                connection.responseCode in 200..299 || (method == "DELETE" && connection.responseCode == 404))
        } finally {
            connection.disconnect()
        }
    }
}
