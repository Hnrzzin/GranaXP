package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.orderedAlternatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CallableResultsTest {
    @Test
    fun `parseia conteúdo público sem gabarito`() {
        val result = parseLessonOpenResult(mapOf(
            "blocks" to listOf(mapOf(
                "id" to "activity-1", "type" to "ACTIVITY", "order" to 2L,
                "title" to "Pergunta", "content" to "Escolha",
                "activityType" to "MULTIPLE_CHOICE",
                "alternatives" to mapOf("b" to "B", "a" to "A"),
                "selectionMode" to "MULTIPLE", "feedback" to "Tente de novo",
                "correctAnswerIds" to listOf("b"),
            )),
            "completedActivityIds" to listOf("activity-1"),
        ))

        assertEquals(LessonSelectionMode.MULTIPLE, result.blocks.single().selectionMode)
        assertEquals(listOf("a", "b"), result.blocks.single().orderedAlternatives().map { it.key })
        assertEquals(setOf("activity-1"), result.completedActivityIds)
    }

    @Test
    fun `parseia resultado autoritativo da resposta`() {
        assertEquals(
            ActivitySubmissionResult(false, "Revise", emptySet()),
            parseActivitySubmissionResult(mapOf("correct" to false, "feedback" to "Revise")),
        )
        assertEquals(
            ActivitySubmissionResult(true, null, setOf("a")),
            parseActivitySubmissionResult(mapOf("correct" to true, "completedActivityIds" to listOf("a"))),
        )
    }

    @Test
    fun `rejeita conteúdo de atividade sem modo de seleção`() {
        assertThrows(IllegalStateException::class.java) {
            parseLessonOpenResult(mapOf(
                "blocks" to listOf(mapOf("id" to "a", "type" to "ACTIVITY")),
                "completedActivityIds" to emptyList<String>(),
            ))
        }
    }
    @Test
    fun `parseia conclusão de lição sem confiar em valores locais`() {
        val result = parseLessonCompletionResult(
            mapOf("xpEarned" to 50L, "leveledUp" to true, "alreadyCompleted" to false),
        )

        assertEquals(50, result.xpEarned)
        assertTrue(result.leveledUp)
        assertFalse(result.alreadyCompleted)
    }

    @Test
    fun `parseia criação de meta com id emitido pelo servidor`() {
        val result = parseGoalCreationResult(
            mapOf("goalId" to "goal-1", "xpEarned" to 200L, "leveledUp" to true),
        )

        assertEquals("goal-1", result.goalId)
        assertEquals(200, result.xpEarned)
        assertTrue(result.leveledUp)
    }

    @Test
    fun `rejeita resposta incompleta da callable`() {
        assertThrows(IllegalStateException::class.java) {
            parseXpMutationResult(mapOf("xpEarned" to 200L))
        }
    }

    @Test
    fun `converte Timestamp para estrutura JSON aceita pela callable Android`() {
        assertEquals(
            mapOf("seconds" to 1_800_000_000.0, "nanoseconds" to 123_000_000.0),
            Timestamp(1_800_000_000, 123_000_000).toCallableTimestamp(),
        )
        assertEquals(null, (null as Timestamp?).toCallableTimestamp())
    }
}
