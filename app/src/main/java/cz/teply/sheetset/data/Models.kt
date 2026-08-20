package cz.teply.sheetset.data

const val MAX_TITLE_LENGTH = 120

data class Score(
    val id: String,
    val title: String,
    val fileName: String,
    val pageCount: Int,
    val importedAtEpochMs: Long,
)

data class Setlist(
    val id: String,
    val name: String,
    val scoreIds: List<String> = emptyList(),
)

data class LibraryCatalog(
    val scores: List<Score> = emptyList(),
    val setlists: List<Setlist> = emptyList(),
) {
    fun createSetlist(name: String, id: String): LibraryCatalog {
        val cleanName = name.trim().take(MAX_TITLE_LENGTH)
        require(cleanName.isNotEmpty()) { "Setlist name must not be blank" }
        require(id.isNotBlank()) { "Setlist ID must not be blank" }
        return copy(setlists = setlists + Setlist(id, cleanName))
    }

    fun deleteScore(scoreId: String): LibraryCatalog = copy(
        scores = scores.filterNot { it.id == scoreId },
        setlists = setlists.map { setlist ->
            setlist.copy(scoreIds = setlist.scoreIds.filterNot { it == scoreId })
        },
    )

    fun moveScore(setlistId: String, fromIndex: Int, toIndex: Int): LibraryCatalog {
        val setlist = setlists.firstOrNull { it.id == setlistId } ?: return this
        if (fromIndex !in setlist.scoreIds.indices || toIndex !in setlist.scoreIds.indices) {
            return this
        }
        val reordered = setlist.scoreIds.toMutableList()
        reordered.add(toIndex, reordered.removeAt(fromIndex))
        return copy(setlists = setlists.map { current ->
            if (current.id == setlistId) current.copy(scoreIds = reordered) else current
        })
    }
}
