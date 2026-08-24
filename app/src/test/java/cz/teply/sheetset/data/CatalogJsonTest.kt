package cz.teply.sheetset.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogJsonTest {
    @Test
    fun `literal catalog JSON is decoded`() {
        val json = """
            {
              "version": 1,
              "scores": [{
                "id": "score-1",
                "title": "Etude",
                "fileName": "score-1.pdf",
                "pageCount": 3,
                "importedAtEpochMs": 42
              }],
              "setlists": [{
                "id": "set-1",
                "name": "Concert",
                "scoreIds": ["score-1"]
              }]
            }
        """.trimIndent()

        assertEquals(
            LibraryCatalog(
                scores = listOf(Score("score-1", "Etude", "score-1.pdf", 3, 42L)),
                setlists = listOf(Setlist("set-1", "Concert", listOf("score-1"))),
            ),
            CatalogJson.decode(json),
        )
    }

    @Test
    fun `catalog survives JSON round trip`() {
        val catalog = LibraryCatalog(
            scores = listOf(Score("score-1", "Song", "score-1.pdf", 2, 7L)),
            setlists = listOf(Setlist("set-1", "Show", listOf("score-1", "score-1"))),
        )

        assertEquals(catalog, CatalogJson.decode(CatalogJson.encode(catalog)))
    }

    @Test
    fun `version two metadata survives literal JSON decode`() {
        val json = """
            {
              "version": 2,
              "scores": [{
                "id": "score-1",
                "title": "Etude",
                "fileName": "score-1.pdf",
                "pageCount": 4,
                "importedAtEpochMs": 42,
                "labels": ["Band", "Encore"],
                "bookmarks": [{"id":"bookmark-1","title":"Chorus","pageIndex":2}],
                "lastPageIndex": 3,
                "lastPagePart": 1,
                "lastViewedAtEpochMs": 99
              }],
              "setlists": [{
                "id": "set-1",
                "name": "Concert",
                "scoreIds": ["score-1"],
                "labels": ["Saturday"],
                "createdAtEpochMs": 77
              }]
            }
        """.trimIndent()

        assertEquals(
            LibraryCatalog(
                scores = listOf(
                    Score(
                        id = "score-1",
                        title = "Etude",
                        fileName = "score-1.pdf",
                        pageCount = 4,
                        importedAtEpochMs = 42L,
                        labels = listOf("Band", "Encore"),
                        bookmarks = listOf(Bookmark("bookmark-1", "Chorus", 2)),
                        lastPageIndex = 3,
                        lastPagePart = 1,
                        lastViewedAtEpochMs = 99L,
                    ),
                ),
                setlists = listOf(
                    Setlist(
                        id = "set-1",
                        name = "Concert",
                        scoreIds = listOf("score-1"),
                        labels = listOf("Saturday"),
                        createdAtEpochMs = 77L,
                    ),
                ),
            ),
            CatalogJson.decode(json),
        )
    }
}
