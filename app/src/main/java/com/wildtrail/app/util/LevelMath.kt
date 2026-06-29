package com.wildtrail.app.util

import kotlin.math.sqrt

object LevelMath {

    fun xpForLevel(level: Int): Int = 50 * level * (level - 1)

    fun levelForXp(xp: Int): Int {
        if (xp <= 0) return 1
        val n = ((1.0 + sqrt(1.0 + 0.08 * xp)) / 2.0).toInt()
        return n.coerceAtLeast(1)
    }

    fun xpInCurrentLevel(xp: Int): Int = xp - xpForLevel(levelForXp(xp))

    fun xpForNextLevel(level: Int): Int = xpForLevel(level + 1) - xpForLevel(level)

    fun progressInCurrentLevel(xp: Int): Float {
        val level = levelForXp(xp)
        val span = xpForNextLevel(level).coerceAtLeast(1)
        return (xpInCurrentLevel(xp).toFloat() / span).coerceIn(0f, 1f)
    }
}
