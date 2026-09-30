package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonSelectionMode

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

    return if (block.selectionMode == LessonSelectionMode.MULTIPLE) {
        if (answerId in currentSelection) currentSelection - answerId
        else currentSelection + answerId
    } else {
        setOf(answerId)
    }
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
            check(block.selectionMode != null) {
                "A atividade ${block.id} não possui modo de seleção."
            }
        }
}
