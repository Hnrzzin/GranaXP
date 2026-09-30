package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonSelectionMode

data class LessonOpenResult(
    val blocks: List<LessonBlock>,
    val completedActivityIds: Set<String>,
)

data class ActivitySubmissionResult(
    val correct: Boolean,
    val feedback: String?,
    val completedActivityIds: Set<String>,
)

data class XpMutationResult(
    val xpEarned: Int,
    val leveledUp: Boolean,
)

data class LessonCompletionResult(
    val xpEarned: Int,
    val leveledUp: Boolean,
    val alreadyCompleted: Boolean,
)

data class GoalCreationResult(
    val goalId: String,
    val xpEarned: Int,
    val leveledUp: Boolean,
)

internal fun parseLessonOpenResult(value: Any?): LessonOpenResult {
    val data = value.asCallableMap()
    val blocks = (data["blocks"] as? List<*>)
        ?: error("Blocos ausentes na resposta.")
    return LessonOpenResult(
        blocks = blocks.map { raw ->
            val block = raw.asCallableMap()
            val type = block.requiredString("type")
            val isActivity = type.equals("ACTIVITY", ignoreCase = true)
            val alternatives = block["alternatives"] as? Map<*, *>
                ?: error("Alternativas ausentes na resposta.")
            check(alternatives.keys.all { it is String } && alternatives.values.all { it is String }) {
                "Alternativas inválidas na resposta."
            }
            val activityType = (block["activityType"] as? String)?.let(ActivityType::valueOf)
            val selectionMode = (block["selectionMode"] as? String)?.let(LessonSelectionMode::valueOf)
            if (isActivity) {
                check(activityType != null && selectionMode != null) {
                    "Atividade sem tipo ou modo de seleção."
                }
            }
            LessonBlock(
                id = block.requiredString("id"),
                type = type,
                order = (block["order"] as? Number)?.toInt() ?: 0,
                title = block["title"] as? String ?: "",
                content = block["content"] as? String ?: "",
                activityType = activityType,
                alternatives = alternatives.entries.associate { it.key as String to it.value as String },
                selectionMode = selectionMode,
                feedback = block["feedback"] as? String ?: "",
                chartImageUrl = block["chartImageUrl"] as? String,
            )
        },
        completedActivityIds = data.requiredStringSet("completedActivityIds"),
    )
}

internal fun parseActivitySubmissionResult(value: Any?): ActivitySubmissionResult {
    val data = value.asCallableMap()
    val correct = data.requiredBoolean("correct")
    return ActivitySubmissionResult(
        correct = correct,
        feedback = if (correct) null else data.requiredString("feedback"),
        completedActivityIds = if (correct) data.requiredStringSet("completedActivityIds") else emptySet(),
    )
}

internal fun parseXpMutationResult(value: Any?): XpMutationResult {
    val data = value.asCallableMap()
    return XpMutationResult(
        xpEarned = data.requiredInt("xpEarned"),
        leveledUp = data.requiredBoolean("leveledUp"),
    )
}

internal fun parseLessonCompletionResult(value: Any?): LessonCompletionResult {
    val data = value.asCallableMap()
    return LessonCompletionResult(
        xpEarned = data.requiredInt("xpEarned"),
        leveledUp = data.requiredBoolean("leveledUp"),
        alreadyCompleted = data.requiredBoolean("alreadyCompleted"),
    )
}

internal fun parseGoalCreationResult(value: Any?): GoalCreationResult {
    val data = value.asCallableMap()
    return GoalCreationResult(
        goalId = data.requiredString("goalId"),
        xpEarned = data.requiredInt("xpEarned"),
        leveledUp = data.requiredBoolean("leveledUp"),
    )
}

private fun Any?.asCallableMap(): Map<*, *> = this as? Map<*, *>
    ?: error("Resposta inválida da Cloud Function.")

private fun Map<*, *>.requiredInt(field: String): Int {
    val number = this[field] as? Number ?: error("Campo $field ausente na resposta.")
    return number.toInt()
}

private fun Map<*, *>.requiredBoolean(field: String): Boolean = this[field] as? Boolean
    ?: error("Campo $field ausente na resposta.")

private fun Map<*, *>.requiredString(field: String): String = this[field] as? String
    ?: error("Campo $field ausente na resposta.")

private fun Map<*, *>.requiredStringSet(field: String): Set<String> {
    val values = this[field] as? List<*> ?: error("Campo $field ausente na resposta.")
    check(values.all { it is String }) { "Campo $field inválido na resposta." }
    return values.map { it as String }.toSet()
}
