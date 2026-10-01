package com.hnrzzin.granaxp.model

fun DailyMissionModel.orderedAlternatives(): List<Map.Entry<String, String>> {
    val preferredOrder = if (activityType == ActivityType.TRUE_FALSE) {
        mapOf("true" to 0, "false" to 1)
    } else {
        emptyMap()
    }
    return alternatives.entries.sortedWith(
        compareBy<Map.Entry<String, String>> { preferredOrder[it.key] ?: preferredOrder.size }
            .thenBy { it.key.lowercase() }
            .thenBy { it.key },
    )
}
