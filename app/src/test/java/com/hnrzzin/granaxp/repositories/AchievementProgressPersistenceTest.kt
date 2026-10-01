package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AchievementProgressPersistenceTest {
    @Test
    fun `escrita legada nao leva unlockedAt nem campos do caminho novo`() {
        val updated = Timestamp(1_800_000_000, 0)

        val data = legacyAchievementProgressData(
            userId = "owner", achievementId = "badge-old", currentProgress = 2,
            isUnlocked = true, lastUpdated = updated,
        )

        assertEquals("owner", data["userId"])
        assertEquals("badge-old", data["achievementId"])
        assertEquals(2, data["currentProgress"])
        assertEquals(true, data["isUnlocked"])
        assertEquals(updated, data["lastUpdated"])
        assertFalse(data.containsKey("unlockedAt"))
        assertFalse(data.containsKey("id"))
    }
}
