package com.hnrzzin.granaxp.utils

import com.hnrzzin.granaxp.repositories.UserRepository

/**
 * Fonte única da lógica de concessão de XP e subida de nível.
 *
 * Antes essa lógica estava duplicada em HomeViewModel.earnXp() (nunca chamado)
 * e LessonViewModel.grantXp(). Qualquer regra futura que conceda XP (lição
 * concluída, meta concluída, etc.) deve chamar este utilitário, para não
 * correr o risco de duas cópias divergirem se a curva de XP mudar um dia.
 *
 * @return true se o usuário subiu de nível ao menos uma vez.
 */
object XpUtils {
    suspend fun grantXp(userRepository: UserRepository, amount: Int): Boolean {
        val user = userRepository.getUser() ?: return false

        var newXp = user.xp + amount
        var newLevel = user.level
        var newNextLevelXp = user.nextLevelXp
        var leveledUp = false

        while (newXp >= newNextLevelXp) {
            newXp -= newNextLevelXp
            newLevel += 1
            newNextLevelXp = (newNextLevelXp * 1.2).toInt()
            leveledUp = true
        }

        userRepository.updateUser(
            user.copy(xp = newXp, level = newLevel, nextLevelXp = newNextLevelXp)
        )
        return leveledUp
    }
}