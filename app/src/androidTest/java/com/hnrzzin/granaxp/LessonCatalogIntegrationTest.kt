package com.hnrzzin.granaxp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
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
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicReference

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
                val visibleTrail = mutableStateOf(trail)
                compose.setContent {
                    MaterialTheme {
                        LearnScreen(visibleTrail.value, daily, {}, {}, user, {})
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

                // Two independent module sequences are assembled from the same server snapshot.
                val organizationId = "organizacao_financeira"
                val investmentsId = "investimentos"
                adminDocument("modules?documentId=$organizationId", "POST", """
                    {"fields":{"title":{"stringValue":"Organização Financeira"},
                      "description":{"stringValue":"Lições modulares locais"},
                      "order":{"integerValue":"0"}}}
                """.trimIndent())
                adminDocument("modules?documentId=$investmentsId", "POST", """
                    {"fields":{"title":{"stringValue":"Investimentos"},
                      "description":{"stringValue":"Módulo independente"},
                      "order":{"integerValue":"1"}}}
                """.trimIndent())
                for ((id, title, order) in listOf(
                    Triple("block10-2-a", "Primeira", 0), Triple("block10-2-b", "Segunda", 1),
                )) {
                    adminDocument("lessons/$id", "PATCH", """
                        {"fields":{"title":{"stringValue":"$title"},
                          "description":{"stringValue":"Teste local"},
                          "duration":{"integerValue":"1"},
                          "xpReward":{"integerValue":"0"},
                          "order":{"integerValue":"$order"},
                          "moduleId":{"stringValue":"$organizationId"}}}
                    """.trimIndent())
                }
                refreshTrail(trail)
                val onlineModules = awaitTrail(trail) as LessonUiState.Success
                assertEquals(listOf(organizationId, investmentsId),
                    onlineModules.modules.map { it.module.idModule })
                assertEquals(listOf("block10-2-a", "block10-2-b"),
                    onlineModules.modules[0].lessons.map { it.lesson.id })
                assertTrue(onlineModules.modules[1].lessons.isEmpty())
                compose.onNodeWithText("Organização Financeira").assertIsDisplayed()

                // The server confirms a third lesson. Network loss after that read must
                // not cause another modular query or discard any confirmed lesson.
                adminDocument("lessons?documentId=block10-3-c", "POST", """
                    {"fields":{"title":{"stringValue":"Terceira"},
                      "description":{"stringValue":"Teste local"},
                      "duration":{"integerValue":"1"},
                      "xpReward":{"integerValue":"0"},
                      "order":{"integerValue":"2"},
                      "moduleId":{"stringValue":"$investmentsId"}}}
                """.trimIndent())
                // The spy delegates the authoritative read to the real Repository,
                // then cuts the SDK connection before fetchLessons() can continue.
                // No state is assembled or injected by the test.
                val repositoryField = LessonViewModel::class.java.getDeclaredField("repository")
                repositoryField.isAccessible = true
                val originalRepository = repositoryField.get(trail) as LessonRepository
                val observedSnapshot = AtomicReference<List<String>>()
                val offlineModuleCount = AtomicReference<Int>()
                val timedRepository = Mockito.spy(originalRepository)
                Mockito.doAnswer {
                    runBlocking {
                        val snapshot = originalRepository.getLessons()
                        observedSnapshot.set(snapshot.map { it.id })
                        db.disableNetwork().await()
                        offlineModuleCount.set(db.collection("lessons")
                            .whereEqualTo("moduleId", investmentsId)
                            .get(Source.DEFAULT).await().size())
                        snapshot
                    }
                }.`when`(timedRepository).getLessons()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    repositoryField.set(trail, timedRepository)
                }
                try {
                    refreshTrail(trail)
                    val assembled = awaitTrail(trail) as LessonUiState.Success
                    assertEquals(listOf("block10-2-a", "block10-2-b", "block10-3-c"),
                        observedSnapshot.get())
                    assertEquals("The offline modular query must diverge from the server snapshot",
                        0, offlineModuleCount.get())
                    assertEquals(3, assembled.lessons.size)
                    assertEquals(listOf("block10-2-a", "block10-2-b"),
                        assembled.modules[0].lessons.map { it.lesson.id })
                    assertEquals(listOf("block10-3-c"),
                        assembled.modules[1].lessons.map { it.lesson.id })
                    assertTrue(assembled.modules[0].isLessonUnlocked(0))
                    assertFalse(assembled.modules[0].isLessonUnlocked(1))
                    assertTrue(assembled.modules[1].isLessonUnlocked(0))
                    compose.onNodeWithText("Organização Financeira").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Abrir módulo: Organização Financeira").performClick()
                    compose.onNodeWithText("Primeira").assertIsDisplayed()
                    compose.onNodeWithText("Voltar aos módulos").performClick()
                    compose.onNodeWithContentDescription("Abrir módulo: Investimentos").performClick()
                    compose.onNodeWithText("Terceira").assertIsDisplayed()
                    compose.onNodeWithText("Nenhuma lição disponível neste módulo.").assertDoesNotExist()
                    compose.onNodeWithText("Voltar aos módulos").performClick()
                } finally {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        repositoryField.set(trail, originalRepository)
                    }
                    db.enableNetwork().await()
                }
                refreshTrail(trail)
                val recovered = awaitTrail(trail) as LessonUiState.Success
                assertEquals(3, recovered.lessons.size)
                assertEquals(listOf("block10-3-c"),
                    recovered.modules[1].lessons.map { it.lesson.id })

                // Reopening the screen offline with a warm cache must show an error,
                // never the previous modular content as if it were current.
                db.disableNetwork().await()
                try {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        trail = LessonViewModel(uid)
                        viewModels.put("trail", trail)
                        visibleTrail.value = trail
                    }
                    assertTrue(awaitTrail(trail) is LessonUiState.Error)
                    compose.onNodeWithText("Falha ao carregar a trilha:", substring = true).assertIsDisplayed()
                    compose.onNodeWithText("Organização Financeira").assertDoesNotExist()
                    compose.onNodeWithText("Terceira").assertDoesNotExist()
                } finally {
                    db.enableNetwork().await()
                }
                refreshTrail(trail)
                val reopened = awaitTrail(trail) as LessonUiState.Success
                assertEquals(3, reopened.lessons.size)
                assertEquals(listOf("block10-3-c"),
                    reopened.modules[1].lessons.map { it.lesson.id })
                compose.onNodeWithText("Organização Financeira").assertIsDisplayed()
                adminDocument("lessons/block10-3-c", "DELETE")
                refreshTrail(trail)
                val afterRemoval = awaitTrail(trail) as LessonUiState.Success
                assertEquals(2, afterRemoval.lessons.size)
                assertTrue(afterRemoval.modules[1].lessons.isEmpty())
                compose.onNodeWithText("Terceira").assertDoesNotExist()
                compose.onNodeWithContentDescription("Abrir módulo: Investimentos").performClick()
                compose.onNodeWithText("Nenhuma lição disponível neste módulo.").assertIsDisplayed()
                compose.onNodeWithText("Voltar aos módulos").performClick()

                // A direct Firestore mission read also stays inside the demo project.
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
                compose.onNodeWithContentDescription("Abrir módulo: Organização Financeira").performClick()
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
                adminDocument("lessons/block10-3-c", "DELETE")
                adminDocument("modules/organizacao_financeira", "DELETE")
                adminDocument("modules/investimentos", "DELETE")
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
