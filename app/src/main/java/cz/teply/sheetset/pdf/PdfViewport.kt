package cz.teply.sheetset.pdf

data class ViewportScroll(
    val panY: Float,
    val reachedEnd: Boolean,
)

fun halfPagePan(maxPanY: Float, part: Int): Float {
    require(maxPanY.isFinite() && maxPanY >= 0f) { "Invalid page overflow" }
    require(part in 0..1) { "Invalid half-page part" }
    return if (part == 0) maxPanY else -maxPanY
}

fun scrollPan(currentPanY: Float, maxPanY: Float, pixels: Float): ViewportScroll {
    require(currentPanY.isFinite()) { "Invalid page pan" }
    require(maxPanY.isFinite() && maxPanY >= 0f) { "Invalid page overflow" }
    require(pixels.isFinite() && pixels >= 0f) { "Invalid scroll distance" }
    if (maxPanY == 0f) return ViewportScroll(0f, true)
    val pan = (currentPanY - pixels).coerceIn(-maxPanY, maxPanY)
    return ViewportScroll(pan, pan <= -maxPanY)
}
