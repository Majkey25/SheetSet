package cz.teply.sheetset

import cz.teply.sheetset.data.LibraryCatalog

data class LibraryUiState(
    val catalog: LibraryCatalog = LibraryCatalog(),
    val loading: Boolean = false,
    val error: Boolean = false,
)
