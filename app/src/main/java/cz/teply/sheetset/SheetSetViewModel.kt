package cz.teply.sheetset

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.teply.sheetset.data.LibraryRepository
import cz.teply.sheetset.data.PdfImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class SheetSetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(File(application.filesDir, "library"))
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
