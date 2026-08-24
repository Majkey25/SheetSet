package cz.teply.sheetset.pdf

fun halfPagePan(maxPanY: Float, part: Int): Float {
    require(maxPanY.isFinite() && maxPanY >= 0f) { "Invalid page overflow" }
    require(part in 0..1) { "Invalid half-page part" }
    return if (part == 0) maxPanY else -maxPanY
}
