package com.wildtrail.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMathTest {

    @Test
    fun `xpForLevel matches the documented curve`() {
        assertEquals(0, LevelMath.xpForLevel(1))
        assertEquals(100, LevelMath.xpForLevel(2))
        assertEquals(300, LevelMath.xpForLevel(3))
        assertEquals(600, LevelMath.xpForLevel(4))
        assertEquals(1000, LevelMath.xpForLevel(5))
        assertEquals(4500, LevelMath.xpForLevel(10))
    }

    @Test
    fun `levelForXp inverts the curve at boundaries`() {
        assertEquals(1, LevelMath.levelForXp(0))
        assertEquals(2, LevelMath.levelForXp(100))
        assertEquals(2, LevelMath.levelForXp(150))
        assertEquals(3, LevelMath.levelForXp(300))
        assertEquals(5, LevelMath.levelForXp(1000))
        assertEquals(5, LevelMath.levelForXp(1499))
        assertEquals(6, LevelMath.levelForXp(1500))
    }

    @Test
    fun `progressInCurrentLevel is 0 at boundary and 1 just before next`() {
        assertEquals(0f, LevelMath.progressInCurrentLevel(100), 0.001f)
        assertEquals(0.5f, LevelMath.progressInCurrentLevel(200), 0.001f)
        assertTrue(LevelMath.progressInCurrentLevel(299) > 0.99f)
    }

    @Test
    fun `levelForXp clamps to at least 1`() {
        assertEquals(1, LevelMath.levelForXp(-100))
        assertEquals(1, LevelMath.levelForXp(0))
    }
}
