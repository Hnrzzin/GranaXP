package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AchievementCatalogMappingTest {
    @Test
    fun `consulta bem sucedida com catalogo vazio retorna lista vazia`() = runBlocking {
        val result = readAchievementCatalog(
            fetch = { emptyList<Pair<String, Map<String, Any?>>>() },
            fields = { it.second },
            decode = { AchievementModel(id = it.first) },
        )

        assertEquals(emptyList<AchievementModel>(), result)
    }

    @Test
    fun `falha global da consulta e propagada`() = runBlocking {
        val failure = IllegalStateException("Firestore indisponivel")
        var decoded = false
        try {
            readAchievementCatalog(
                fetch = { throw failure },
                fields = { _: Pair<String, Map<String, Any?>> -> emptyMap() },
                decode = { _: Pair<String, Map<String, Any?>> ->
                    decoded = true
                    AchievementModel()
                },
            )
            fail("A falha global foi convertida em catalogo vazio")
        } catch (caught: IllegalStateException) {
            assertSame(failure, caught)
        }
        assertEquals(false, decoded)
    }

    @Test
    fun `definicao STREAK invalida e falha individual nao ocultam conquistas validas`() {
        val validFields = mapOf<String, Any?>(
            "title" to "Sequência", "description" to "Missões", "icon" to "fire",
            "category" to "EDUCACAO", "requirementType" to "STREAK", "requirementValue" to 7,
        )
        val documents = listOf(
            "lesson" to mapOf<String, Any?>("requirementType" to "LESSON_COUNT"),
            "bad-streak" to validFields.plus("requirementValue" to "7"),
            "bad-mapping" to mapOf<String, Any?>("requirementType" to "LESSON_COUNT"),
            "good-streak" to validFields,
        )

        val result = mapAchievementDocuments(documents, { it.second }) { (id, fields) ->
            if (id == "bad-mapping") error("Falha de desserialização")
            AchievementModel(
                id = id,
                requirementType = RequirementType.valueOf(fields["requirementType"] as String),
                requirementValue = (fields["requirementValue"] as? Int) ?: 0,
            )
        }

        assertEquals(listOf("lesson", "good-streak"), result.map { it.id })
    }

    @Test
    fun `tipos V1 permanecem disponiveis com STREAK invalida`() = runBlocking {
        val validFields = mapOf<String, Any?>(
            "title" to "Sequencia", "description" to "Missoes", "icon" to "fire",
            "category" to "EDUCACAO", "requirementType" to "STREAK", "requirementValue" to "7",
        )
        val documents = listOf(
            "lesson" to mapOf<String, Any?>("requirementType" to "LESSON_COUNT"),
            "goal" to mapOf<String, Any?>("requirementType" to "GOAL_COUNT"),
            "transaction" to mapOf<String, Any?>("requirementType" to "TRANSACTION_COUNT"),
            "bad-streak" to validFields,
        )
        val result = readAchievementCatalog(
            fetch = { documents },
            fields = { it.second },
            decode = { (id, fields) -> AchievementModel(
                id = id,
                requirementType = RequirementType.valueOf(fields["requirementType"] as String),
            ) },
        )

        assertEquals(listOf("lesson", "goal", "transaction"), result.map { it.id })
    }
}
