package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class LessonProgressModel(
    @DocumentId
    var id: String = "",
    @Deprecated("Campo legado da V1; novos documentos identificam o usuário pelo caminho.")
    var userId: String = "",
    var lessonId: String = "",
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    var completedActivityIds: List<String> = emptyList(),
    var lastAccessed: Timestamp = Timestamp.now(),
    var completedAt: Timestamp? = null,
)
