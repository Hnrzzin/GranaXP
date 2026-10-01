package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class AchievementProgressModel(
    @DocumentId
    var id: String = "",
    @Deprecated("Preservado somente para ler e criar progresso legado da V1")
    val userId: String = "",
    val achievementId: String = "",
    val currentProgress: Int = 0,
    @get:PropertyName("isUnlocked") @set:PropertyName("isUnlocked")
    var isUnlocked: Boolean = false,
    val unlockedAt: Timestamp? = null,
    @Deprecated("Timestamp de atualização legado; não indica o primeiro desbloqueio")
    val lastUpdated: Timestamp? = null
)
