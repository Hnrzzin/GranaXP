package com.hnrzzin.granaxp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.AuthRepository
import com.hnrzzin.granaxp.repositories.GoalRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SparkGoalIntegrationTest {
    @get:Rule val proof = InstrumentationExecutionProof()

    @Test fun ownerCanManageGoalWithoutMintingXp(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATORS)
        assertEquals("demo-granaxp", FirebaseApp.getInstance().options.projectId)
        val auth = AuthRepository()
        auth.logout()
        val email = "spark-goal-${System.nanoTime()}@example.test"
        val uid = auth.registerWithEmail(email, "local-test-password")
        auth.createUserProfile(UserModel(id = uid, name = "Meta Spark", email = email))
        val repository = GoalRepository(uid)
        val requestId = "spark-goal-${System.nanoTime()}"
        try {
            val created = repository.createGoal(requestId, "Reserva", 100.0, 0.0,
                GoalDeadline.CURTO, null)
            assertEquals(requestId, created.goalId)
            assertEquals(0, created.xpEarned)
            assertEquals(created.goalId, repository.createGoal(requestId, "Reserva", 100.0,
                0.0, GoalDeadline.CURTO, null).goalId)
            assertEquals(1, repository.getGoals().size)
            assertEquals(0, repository.updateGoalProgress(requestId, 40.0).xpEarned)
            assertEquals(0, repository.updateGoalProgress(requestId, 60.0).xpEarned)
            val completed = repository.getGoals().single()
            assertEquals(100.0, completed.currentAmount, 0.0)
            assertNotNull(completed.completedAt)
            assertEquals(0, repository.updateGoalDetails(requestId, "Reserva editada", 100.0,
                GoalDeadline.MEDIO, null).xpEarned)
            assertEquals("Reserva editada", repository.getGoals().single().title)
            assertEquals(0, auth.getUserProfile(uid)!!.xp)
            val profile = FirebaseFirestore.getInstance().collection("users").document(uid)
            try {
                profile.update("xp", 200).await()
                fail("O cliente não pode conceder XP persistido.")
            } catch (error: FirebaseFirestoreException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
            }
            assertEquals(0L, profile.get(Source.SERVER).await().getLong("xp"))
            repository.deleteGoal(repository.getGoals().single())
            assertTrue(repository.getGoals().isEmpty())
        } finally {
            auth.logout()
        }
    }
}
