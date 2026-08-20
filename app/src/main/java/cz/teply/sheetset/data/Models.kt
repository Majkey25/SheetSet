package cz.teply.sheetset.data

import org.json.JSONArray
import org.json.JSONObject

const val MAX_TITLE_LENGTH = 120

private fun cleanTitle(value: String): String = value.trim().take(MAX_TITLE_LENGTH).also {
    require(it.isNotEmpty()) { "Title must not be blank" }
}

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
        require(id.isNotBlank()) { "Setlist ID must not be blank" }
        return copy(setlists = setlists + Setlist(id, cleanTitle(name)))
    }

    fun renameScore(scoreId: String, title: String): LibraryCatalog = copy(
        scores = scores.map { score ->
            if (score.id == scoreId) score.copy(title = cleanTitle(title)) else score
        },
    )

    fun renameSetlist(setlistId: String, name: String): LibraryCatalog = copy(
        setlists = setlists.map { setlist ->
            if (setlist.id == setlistId) setlist.copy(name = cleanTitle(name)) else setlist
        },
    )

    fun deleteSetlist(setlistId: String): LibraryCatalog = copy(
        setlists = setlists.filterNot { it.id == setlistId },
    )

    fun addScoreToSetlist(setlistId: String, scoreId: String): LibraryCatalog {
        require(scores.any { it.id == scoreId }) { "Score does not exist" }
        require(setlists.any { it.id == setlistId }) { "Setlist does not exist" }
        return copy(setlists = setlists.map { setlist ->
            if (setlist.id == setlistId) setlist.copy(scoreIds = setlist.scoreIds + scoreId)
            else setlist
        })
    }

    fun removeScoreFromSetlist(setlistId: String, index: Int): LibraryCatalog {
        val setlist = setlists.firstOrNull { it.id == setlistId } ?: return this
        if (index !in setlist.scoreIds.indices) return this
        val remaining = setlist.scoreIds.toMutableList().apply { removeAt(index) }
        return copy(setlists = setlists.map { current ->
            if (current.id == setlistId) current.copy(scoreIds = remaining) else current
        })
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

object CatalogJson {
    private const val VERSION = 1

    fun encode(catalog: LibraryCatalog): String = JSONObject()
        .put("version", VERSION)
        .put(
            "scores",
            JSONArray().apply {
                catalog.scores.forEach { score ->
                    put(
                        JSONObject()
                            .put("id", score.id)
                            .put("title", score.title)
                            .put("fileName", score.fileName)
                            .put("pageCount", score.pageCount)
                            .put("importedAtEpochMs", score.importedAtEpochMs),
                    )
                }
            },
        )
        .put(
            "setlists",
            JSONArray().apply {
                catalog.setlists.forEach { setlist ->
                    put(
                        JSONObject()
                            .put("id", setlist.id)
                            .put("name", setlist.name)
                            .put("scoreIds", JSONArray(setlist.scoreIds)),
                    )
                }
            },
        )
        .toString()

    fun decode(raw: String): LibraryCatalog {
        val root = JSONObject(raw)
        require(root.getInt("version") == VERSION) { "Unsupported catalog version" }
        val scoresJson = root.getJSONArray("scores")
        val scores = List(scoresJson.length()) { index ->
            scoresJson.getJSONObject(index).toScore()
        }
        require(scores.map(Score::id).distinct().size == scores.size) {
            "Duplicate score ID"
        }
        val setlistsJson = root.getJSONArray("setlists")
        val setlists = List(setlistsJson.length()) { index ->
            setlistsJson.getJSONObject(index).toSetlist()
        }
        require(setlists.map(Setlist::id).distinct().size == setlists.size) {
            "Duplicate setlist ID"
        }
        val scoreIds = scores.mapTo(mutableSetOf(), Score::id)
        require(setlists.all { setlist -> setlist.scoreIds.all(scoreIds::contains) }) {
            "Setlist references a missing score"
        }
        return LibraryCatalog(scores, setlists)
    }

    private fun JSONObject.toScore(): Score {
        val id = getString("id")
        val title = getString("title")
        val fileName = getString("fileName")
        val pageCount = getInt("pageCount")
        val importedAt = getLong("importedAtEpochMs")
        require(id.isNotBlank()) { "Score ID must not be blank" }
        require(title.isNotBlank() && title.length <= MAX_TITLE_LENGTH) { "Invalid score title" }
        require(fileName.matches(Regex("[A-Za-z0-9-]+\\.pdf"))) { "Invalid score file name" }
        require(pageCount > 0) { "Page count must be positive" }
        require(importedAt >= 0) { "Import time must not be negative" }
        return Score(id, title, fileName, pageCount, importedAt)
    }

    private fun JSONObject.toSetlist(): Setlist {
        val id = getString("id")
        val name = getString("name")
        val scoreIdsJson = getJSONArray("scoreIds")
        require(id.isNotBlank()) { "Setlist ID must not be blank" }
        require(name.isNotBlank() && name.length <= MAX_TITLE_LENGTH) { "Invalid setlist name" }
        return Setlist(
            id = id,
            name = name,
            scoreIds = List(scoreIdsJson.length(), scoreIdsJson::getString),
        )
    }
}
