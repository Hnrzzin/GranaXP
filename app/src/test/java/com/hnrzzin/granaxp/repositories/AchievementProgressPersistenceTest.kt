package com.hnrzzin.granaxp.repositories

import org.junit.Assert.assertFalse
import org.junit.Test

class AchievementProgressPersistenceTest {
    @Test
    fun `repository nao oferece escritores de conquistas ao cliente`() {
        val writers = setOf("createAchievementProgress", "updateAchievementProgress", "unlockAchievement", "deleteAchievementProgress")
        assertFalse(AchievementRepository::class.java.declaredMethods.any { it.name in writers })
    }
}
