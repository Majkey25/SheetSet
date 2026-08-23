package cz.teply.sheetset

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.teply.sheetset.data.LibraryRepository
import cz.teply.sheetset.data.PdfImport
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.pdf.PageAnnotation
import cz.teply.sheetset.pdf.PdfExporter
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.AppLanguages
import cz.teply.sheetset.settings.SettingsStore
import cz.teply.sheetset.settings.HighlightStrength
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
    private val settingsStore = SettingsStore(
        application.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE),
    )
    private val annotationSaveMutex = Mutex()
    private val mutableState = MutableStateFlow(
        LibraryUiState(loading = true, settings = settingsStore.load()),
    )
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

    fun reorderScores(setlistId: String, scoreIds: List<String>) = launchAction {
        repository.reorderScores(setlistId, scoreIds)
    }

    fun updateSettings(settings: AppSettings) {
        settingsStore.save(settings)
        mutableState.update { it.copy(settings = settings) }
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

    fun saveAnnotations(pageAnnotations: List<PageAnnotation>) {
        val reader = state.value.reader ?: return
        val annotations = reader.annotations.withPage(reader.pageIndex, pageAnnotations)
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
                        PdfExporter.export(
                            reader.file,
                            output,
                            reader.annotations,
                            state.value.settings.highlighterStrength.alpha(),
                        )
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

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val application = getApplication<Application>()
                val locales = application.getSystemService(LocaleManager::class.java)
                    .applicationLocales
                val languageTag = if (locales.isEmpty) null else locales[0].language
                application.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    repository.createBackup(output, state.value.settings, languageTag)
                } ?: throw FileNotFoundException(uri.toString())
                mutableState.update { it.copy(loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false, reader = null) }
            try {
                val application = getApplication<Application>()
                val metadata = application.contentResolver.openInputStream(uri)?.use { input ->
                    repository.restoreBackup(input)
                } ?: throw FileNotFoundException(uri.toString())
                settingsStore.save(metadata.settings)
                mutableState.update {
                    it.copy(
                        catalog = repository.load(),
                        loading = false,
                        settings = metadata.settings,
                    )
                }
                AppLanguages.select(application, metadata.languageTag)
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
                mutableState.update {
                    it.copy(catalog = repository.load(), loading = false, error = false)
                }
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
        val fallbackName = getApplication<Application>().getString(R.string.untitled_pdf_file)
        var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { fallbackName }
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
        if (name.isBlank()) name = fallbackName
        PdfImport(name, resolver.getType(uri), size) {
            resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
    }
}

private fun HighlightStrength.alpha(): Int = when (this) {
    HighlightStrength.LIGHT -> 70
    HighlightStrength.MEDIUM -> 105
    HighlightStrength.STRONG -> 150
}
