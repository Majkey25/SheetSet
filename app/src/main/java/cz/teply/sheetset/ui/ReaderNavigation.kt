package cz.teply.sheetset.ui

import cz.teply.sheetset.settings.ReaderLayout

data class ReaderPosition(
    val scoreIndex: Int,
    val pageIndex: Int,
    val pagePart: Int = 0,
)

fun nextPosition(
    current: ReaderPosition,
    pageCounts: List<Int>,
    layout: ReaderLayout,
): ReaderPosition? {
    validatePosition(current, pageCounts)
    val count = pageCounts[current.scoreIndex]
    return when (layout) {
        ReaderLayout.SINGLE -> when {
            current.pageIndex + 1 < count -> current.copy(pageIndex = current.pageIndex + 1, pagePart = 0)
            current.scoreIndex + 1 < pageCounts.size -> ReaderPosition(current.scoreIndex + 1, 0)
            else -> null
        }
        ReaderLayout.HALF -> when {
            current.pagePart == 0 -> current.copy(pagePart = 1)
            current.pageIndex + 1 < count -> current.copy(pageIndex = current.pageIndex + 1, pagePart = 0)
            current.scoreIndex + 1 < pageCounts.size -> ReaderPosition(current.scoreIndex + 1, 0)
            else -> null
        }
        ReaderLayout.TWO_PAGE -> {
            val start = spreadStart(current.pageIndex)
            when {
                start + 2 < count -> current.copy(pageIndex = start + 2, pagePart = 0)
                current.scoreIndex + 1 < pageCounts.size -> ReaderPosition(current.scoreIndex + 1, 0)
                else -> null
            }
        }
    }
}

fun previousPosition(
    current: ReaderPosition,
    pageCounts: List<Int>,
    layout: ReaderLayout,
): ReaderPosition? {
    validatePosition(current, pageCounts)
    return when (layout) {
        ReaderLayout.SINGLE -> when {
            current.pageIndex > 0 -> current.copy(pageIndex = current.pageIndex - 1, pagePart = 0)
            current.scoreIndex > 0 -> ReaderPosition(
                current.scoreIndex - 1,
                pageCounts[current.scoreIndex - 1] - 1,
            )
            else -> null
        }
        ReaderLayout.HALF -> when {
            current.pagePart == 1 -> current.copy(pagePart = 0)
            current.pageIndex > 0 -> current.copy(pageIndex = current.pageIndex - 1, pagePart = 1)
            current.scoreIndex > 0 -> ReaderPosition(
                current.scoreIndex - 1,
                pageCounts[current.scoreIndex - 1] - 1,
                1,
            )
            else -> null
        }
        ReaderLayout.TWO_PAGE -> {
            val start = spreadStart(current.pageIndex)
            when {
                start > 0 -> current.copy(pageIndex = (start - 2).coerceAtLeast(0), pagePart = 0)
                current.scoreIndex > 0 -> ReaderPosition(
                    current.scoreIndex - 1,
                    spreadStart(pageCounts[current.scoreIndex - 1] - 1),
                )
                else -> null
            }
        }
    }
}

fun spreadPages(position: ReaderPosition, pageCount: Int): List<Int> {
    require(pageCount > 0) { "Page count must be positive" }
    require(position.pageIndex in 0 until pageCount) { "Reader page is invalid" }
    val first = spreadStart(position.pageIndex)
    return listOfNotNull(first, (first + 1).takeIf { it < pageCount })
}

private fun spreadStart(pageIndex: Int): Int = pageIndex - pageIndex % 2

private fun validatePosition(position: ReaderPosition, pageCounts: List<Int>) {
    require(pageCounts.isNotEmpty() && pageCounts.all { it > 0 }) { "Page counts are invalid" }
    require(position.scoreIndex in pageCounts.indices) { "Reader score is invalid" }
    require(position.pageIndex in 0 until pageCounts[position.scoreIndex]) { "Reader page is invalid" }
    require(position.pagePart in 0..1) { "Reader page part is invalid" }
}
