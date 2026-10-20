package com.hnrzzin.granaxp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.hnrzzin.granaxp.repositories.AchievementRepository
import com.hnrzzin.granaxp.ui.theme.screens.ProfileScreen
import com.hnrzzin.granaxp.viewmodel.AchievementViewModel
import com.hnrzzin.granaxp.viewmodel.UserViewModel
import com.hnrzzin.granaxp.viewmodel.combineAchievements
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic definitions/progress are seeded only in the dedicated demo emulator. */
@RunWith(AndroidJUnit4::class)
class SparkAchievementsIntegrationTest {
    @get:Rule val proof = InstrumentationExecutionProof()
    @get:Rule val compose = createComposeRule()

    @Test fun eightCardsDeriveReadOnlyAndPreserveHistoricalUnlocksOnReload(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        assertEquals("demo-granaxp", FirebaseApp.getInstance().options.projectId)
        val db = FirebaseFirestore.getInstance()
        assertEquals("127.0.0.1:8080", db.firestoreSettings.host)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val uid = auth.signInWithEmailAndPassword("achievement-fixture@example.test", "local-fixture-password").await().user!!.uid
        assertEquals("achievement-fixture-owner", uid)
        val repository = AchievementRepository()
        suspend fun snapshot(): Map<String, Any?> {
            val result = linkedMapOf<String, Any?>()
            result["profile"] = db.document("users/$uid").get(Source.SERVER).await().data
            for (collection in listOf("lessonProgress", "dailyMissionProgress", "achievementProgress", "transactions", "goals")) {
                result[collection] = db.collection("users/$uid/$collection").get(Source.SERVER).await()
                    .documents.associate { it.id to it.data }
            }
            result["legacy"] = db.collection("achievementProgress").whereEqualTo("userId", uid)
                .get(Source.SERVER).await().documents.associate { it.id to it.data }
            return result
        }
        val before = snapshot()
        val catalog = repository.getAchievements()
        assertEquals(8, catalog.size)
        val canonical = repository.getUserAchievementProgress(uid)
        val legacy = repository.getAchievementProgress(uid)
        val derived = repository.getLocalAchievementProgress(uid, catalog)
        val result = combineAchievements(catalog, canonical, legacy, derived)
        assertEquals(8, result.map { it.achievement.id }.distinct().size)
        assertEquals(8, result.count { it.progress?.isUnlocked == true })
        assertTrue(result.single { it.achievement.id == "iQo20q4jxFnAtkJBn8F4" }.progress!!.unlockedAt != null)
        assertEquals(result, combineAchievements(catalog, canonical, legacy,
            repository.getLocalAchievementProgress(uid, catalog)))
        val store = ViewModelStore()
        lateinit var user: UserViewModel
        lateinit var achievements: AchievementViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            user = UserViewModel(uid); achievements = AchievementViewModel(uid)
            store.put("user", user); store.put("achievements", achievements)
        }
        try {
            compose.setContent {
                MaterialTheme { ProfileScreen(user, achievements, {}, {}, {}, {}, {}) }
            }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("8/8").fetchSemanticsNodes().isNotEmpty() }
            for (achievement in catalog) compose.onNodeWithText(achievement.title).performScrollTo().assertIsDisplayed()
            assertEquals(before, snapshot())
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            auth.signOut()
        }
    }
}
