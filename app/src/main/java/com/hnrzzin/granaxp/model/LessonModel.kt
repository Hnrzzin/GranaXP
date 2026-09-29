package com.hnrzzin.granaxp.model

import com.google.firebase.firestore.DocumentId

data class LessonModel(
    @DocumentId
    var id: String = "",
    var moduleId: String = "",
    var title: String = "",
    var description: String = "",
    var duration: Int = 0,
    var xpReward: Int = 50,
    var order: Int = 0,
    @Deprecated("Campo legado: a organização nova usa moduleId.")
    var category: String = "",
    @Deprecated("Campo legado: vídeos passam a ser LessonBlocks.")
    var videoUrl: String? = null
)
