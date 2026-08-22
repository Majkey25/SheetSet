package cz.teply.sheetset

import cz.teply.sheetset.data.LibraryCatalog
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.pdf.DocumentAnnotations
import cz.teply.sheetset.settings.AppSettings
import java.io.File

data class ReaderUiState(
    val score: Score,
    val file: File,
    val scoreIds: List<String>,
    val scoreIndex: Int,
    val pageIndex: Int,
    val annotations: DocumentAnnotations,
)

data class LibraryUiState(
    val catalog: LibraryCatalog = LibraryCatalog(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val reader: ReaderUiState? = null,
    val settings: AppSettings = AppSettings(),
)
