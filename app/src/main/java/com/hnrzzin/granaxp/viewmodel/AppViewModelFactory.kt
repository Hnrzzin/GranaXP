package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppViewModelFactory(private val userId: String? = null) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            // AuthViewModel não precisa de userId para ser criado
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel() as T
            }

            // Para as outras ViewModels, o userId é obrigatório
            userId.isNullOrEmpty() -> {
                throw IllegalArgumentException("Um userId válido é obrigatório para as ViewModels de dados.")
            }

            modelClass.isAssignableFrom(AchievementViewModel::class.java) -> {
                AchievementViewModel(userId) as T
            }
            modelClass.isAssignableFrom(BudgetViewModel::class.java) -> {
                BudgetViewModel(userId) as T
            }
            modelClass.isAssignableFrom(GoalViewModel::class.java) -> {
                GoalViewModel(userId) as T
            }
            modelClass.isAssignableFrom(LessonViewModel::class.java) -> {
                LessonViewModel(userId) as T
            }
            modelClass.isAssignableFrom(ReminderViewModel::class.java) -> {
                ReminderViewModel(userId) as T
            }
            modelClass.isAssignableFrom(TransactionViewModel::class.java) -> {
                TransactionViewModel(userId) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(userId) as T
            }

            else -> throw IllegalArgumentException("ViewModel não reconhecida: ${modelClass.name}")
        }
    }
}