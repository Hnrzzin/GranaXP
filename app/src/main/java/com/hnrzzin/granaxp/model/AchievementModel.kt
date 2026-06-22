package com.hnrzzin.granaxp.model

import com.google.firebase.firestore.DocumentId

enum class CategoriaConquista {
    FINANCAS,
    EDUCACAO,
    GERAL
}

data class AchievementModel(
    @DocumentId
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var icon: String = "",
    var requirementValue: Int = 0,
    var category: CategoriaConquista = CategoriaConquista.GERAL
)
