package cz.teply.sheetset

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.teply.sheetset.data.LibraryRepository
import cz.teply.sheetset.data.PdfImport
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.pdf.Stroke
import cz.teply.sheetset.pdf.PdfExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class SheetSetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(File(application.filesDir, "library"))
    private val annotationSaveMutex = Mutex()
    private val mutableState = MutableStateFlow(LibraryUiState(loading = true))
    val state = mutableState.asStateFlow()

    init {
        launchAction { }
    }

    fun importPdfs(uris: List<Uri>) = launchAction {
        uris.forEach { uri -> repository.importPdf(pdfImport(uri)) }
    }

    fun createSetlist(name: String) = launchAction { repository.createSetlist(name) }

    fun renameScore(scoreId: String, title: String) = launchAction {
        repository.renameScore(scoreId, title)
    }

    fun deleteScore(scoreId: String) = launchAction { repository.deleteScore(scoreId) }

    fun renameSetlist(setlistId: String, name: String) = launchAction {
        repository.renameSetlist(setlistId, name)
    }

    fun deleteSetlist(setlistId: String) = launchAction { repository.deleteSetlist(setlistId) }

    fun addScores(setlistId: String, scoreIds: List<String>) = launchAction {
        scoreIds.forEach { scoreId -> repository.addScoreToSetlist(setlistId, scoreId) }
    }

    fun removeScore(setlistId: String, index: Int) = launchAction {
        repository.removeScoreFromSetlist(setlistId, index)
    }

    fun moveScore(setlistId: String, fromIndex: Int, toIndex: Int) = launchAction {
        repository.moveScore(setlistId, fromIndex, toIndex)
    }

    fun openScore(score: Score) {
        openReader(listOf(score.id), scoreIndex = 0, pageIndex = 0)
    }

    fun openSetlistScore(setlistId: String, scoreIndex: Int) {
        val ids = state.value.catalog.setlists.firstOrNull { it.id == setlistId }?.scoreIds ?: return
        openReader(ids, scoreIndex, pageIndex = 0)
    }

    fun closeReader() {
        mutableState.update { it.copy(reader = null) }
    }

    fun nextPage() {
        val reader = state.value.reader ?: return
        if (reader.pageIndex < reader.score.pageCount - 1) {
            mutableState.update { it.copy(reader = reader.copy(pageIndex = reader.pageIndex + 1)) }
        } else if (reader.scoreIndex < reader.scoreIds.lastIndex) {
            openReader(reader.scoreIds, reader.scoreIndex + 1, pageIndex = 0)
        }
    }

    fun previousPage() {
        val reader = state.value.reader ?: return
        if (reader.pageIndex > 0) {
            mutableState.update { it.copy(reader = reader.copy(pageIndex = reader.pageIndex - 1)) }
        } else if (reader.scoreIndex > 0) {
            val previousIndex = reader.scoreIndex - 1
            val previous = state.value.catalog.scores.firstOrNull {
                it.id == reader.scoreIds[previousIndex]
            } ?: return
            openReader(reader.scoreIds, previousIndex, previous.pageCount - 1)
        }
    }

    fun saveStrokes(strokes: List<Stroke>) {
        val reader = state.value.reader ?: return
        val annotations = reader.annotations.withPage(reader.pageIndex, strokes)
        mutableState.update { it.copy(reader = reader.copy(annotations = annotations)) }
        viewModelScope.launch {
            annotationSaveMutex.withLock {
                try {
                    repository.saveAnnotations(reader.score.id, annotations)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableState.update { it.copy(error = true) }
                }
            }
        }
    }

    fun exportPdf(uri: Uri) {
        val reader = state.value.reader ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        PdfExporter.export(reader.file, output, reader.annotations)
                    } ?: throw FileNotFoundException(uri.toString())
                }
                mutableState.update { it.copy(loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                action()
                mutableState.value = LibraryUiState(catalog = repository.load())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private fun openReader(scoreIds: List<String>, scoreIndex: Int, pageIndex: Int) {
        val scoreId = scoreIds.getOrNull(scoreIndex) ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val score = state.value.catalog.scores.firstOrNull { it.id == scoreId }
                    ?: throw IllegalArgumentException("Score does not exist")
                val file = withContext(Dispatchers.IO) { repository.pdfFile(score) }
                val annotations = annotationSaveMutex.withLock {
                    repository.loadAnnotations(score.id)
                }
                mutableState.update {
                    it.copy(
                        loading = false,
                        reader = ReaderUiState(
                            score = score,
                            file = file,
                            scoreIds = scoreIds,
                            scoreIndex = scoreIndex,
                            pageIndex = pageIndex,
                            annotations = annotations,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private suspend fun pdfImport(uri: Uri): PdfImport = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled.pdf"
        var size: Long? = null
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
        PdfImport(name, resolver.getType(uri), size) {
            resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
    }
}
