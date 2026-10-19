package com.hnrzzin.granaxp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.AuthRepository
import com.hnrzzin.granaxp.repositories.DailyMissionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SparkMissionIntegrationTest {
    @get:Rule val proof = InstrumentationExecutionProof()

    @Test fun wrongCorrectReopenAndReplayKeepOneCompletionAndZeroXp(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        assertEquals("demo-granaxp", FirebaseApp.getInstance().options.projectId)
        val auth = AuthRepository()
        auth.logout()
        val email = "spark-mission-${System.nanoTime()}@example.test"
        val uid = auth.registerWithEmail(email, "local-test-password")
        auth.createUserProfile(UserModel(id = uid, name = "Missão Spark", email = email))
        val repository = DailyMissionRepository()
        try {
            val now = System.currentTimeMillis()
            val opened = repository.openDailyMission(now)
            assertTrue(opened.available)
            assertFalse(opened.isCompleted)
            val mission = opened.mission!!
            val wrong = mission.alternatives.keys.first { it !in mission.correctAnswerIds }
            val failure = repository.submitAnswer(now, opened.date!!, mission.id, setOf(wrong))
            assertFalse(failure.correct)
            assertFalse(repository.openDailyMission(now).isCompleted)
            val correct = repository.submitAnswer(now, opened.date!!, mission.id, mission.correctAnswerIds)
            assertTrue(correct.correct)
            assertFalse(correct.alreadyCompleted)
            assertEquals(0, correct.xpEarned)
            val progressRef = FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("dailyMissionProgress").document(opened.date!!)
            val completedAt = progressRef.get(Source.SERVER).await().getTimestamp("completedAt")
            assertNotNull(completedAt)
            assertTrue(repository.openDailyMission(now).isCompleted)
            val replay = repository.submitAnswer(now, opened.date!!, mission.id, mission.correctAnswerIds)
            assertTrue(replay.alreadyCompleted)
            assertEquals(completedAt, progressRef.get(Source.SERVER).await().getTimestamp("completedAt"))
            assertEquals(0, auth.getUserProfile(uid)!!.xp)
        } finally {
            auth.logout()
        }
    }
}
