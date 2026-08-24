package cz.teply.sheetset.ui

import cz.teply.sheetset.settings.AutoScrollSpeed

enum class AutoScrollState { STOPPED, RUNNING, PAUSED }

fun autoScrollPixels(
    speed: AutoScrollSpeed,
    elapsedMillis: Long,
    density: Float,
): Float {
    require(elapsedMillis >= 0) { "Elapsed time must not be negative" }
    require(density.isFinite() && density > 0f) { "Density must be positive" }
    val dpPerSecond = when (speed) {
        AutoScrollSpeed.SLOW -> 24f
        AutoScrollSpeed.MEDIUM -> 48f
        AutoScrollSpeed.FAST -> 96f
    }
    val boundedMillis = elapsedMillis.coerceAtMost(1_000)
    return dpPerSecond * density * boundedMillis / 1_000f
}
