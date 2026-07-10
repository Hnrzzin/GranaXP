package com.hnrzzin.granaxp.model

import com.google.firebase.firestore.DocumentId

enum class CategoriaConquista {
    FINANCAS,
    EDUCACAO,
}

enum class RequirementType {
    TRANSACTION_COUNT,   // quantidade de transações registradas
    LESSON_COUNT,        // quantidade de lições concluídas
    GOAL_COUNT,          // quantidade de metas criadas
    MONTHLY_SAVINGS      // valor poupado (receita - despesa) no mês corrente
}

data class AchievementModel(
    @DocumentId
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var icon: String = "",
    var requirementValue: Int = 0,
    var requirementType: RequirementType = RequirementType.TRANSACTION_COUNT,
    var category: CategoriaConquista = CategoriaConquista.FINANCAS // era GERAL, que não existe mais
)