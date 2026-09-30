package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.LessonBlock

enum class ActivityAnswerResult {
    IDLE,
    INCORRECT,
    CORRECT,
    ERROR,
}

data class ActivityAnswerState(
    val selectedAnswerIds: Set<String> = emptySet(),
    val result: ActivityAnswerResult = ActivityAnswerResult.IDLE,
    val isSubmitting: Boolean = false,
    val message: String? = null,
)

internal fun updateSelectedAnswers(
    block: LessonBlock,
    currentSelection: Set<String>,
    answerId: String,
): Set<String> {
    if (answerId !in block.alternatives) return currentSelection

    return if (block.correctAnswerIds.size > 1) {
        if (answerId in currentSelection) currentSelection - answerId
        else currentSelection + answerId
    } else {
        setOf(answerId)
    }
}

internal fun activityAnswerIsCorrect(
    block: LessonBlock,
    selectedAnswerIds: Set<String>,
): Boolean {
    val correctAnswerIds = block.correctAnswerIds.toSet()
    return selectedAnswerIds.isNotEmpty() && selectedAnswerIds == correctAnswerIds
}

internal fun validateLessonActivities(blocks: List<LessonBlock>) {
    blocks
        .filter { it.type.equals("ACTIVITY", ignoreCase = true) }
        .forEach { block ->
            check(block.activityType != null) {
                "A atividade ${block.id} não possui activityType."
            }
            check(block.alternatives.isNotEmpty()) {
                "A atividade ${block.id} não possui alternativas."
            }
            check(block.correctAnswerIds.isNotEmpty()) {
                "A atividade ${block.id} não possui resposta correta."
            }
            check(block.correctAnswerIds.toSet().size == block.correctAnswerIds.size) {
                "A atividade ${block.id} possui respostas corretas duplicadas."
            }
            check(block.correctAnswerIds.all { it in block.alternatives }) {
                "A atividade ${block.id} referencia uma alternativa inexistente."
            }
            check(block.feedback.isNotBlank()) {
                "A atividade ${block.id} não possui feedback."
            }
        }
}
