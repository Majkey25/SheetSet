package cz.teply.sheetset

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.teply.sheetset.data.LibraryRepository
import cz.teply.sheetset.data.Bookmark
import cz.teply.sheetset.data.PdfImport
import cz.teply.sheetset.data.Score
import cz.teply.sheetset.pdf.PageAnnotation
import cz.teply.sheetset.pdf.PdfExporter
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.AppLanguages
import cz.teply.sheetset.settings.SettingsStore
import cz.teply.sheetset.settings.HighlightStrength
import cz.teply.sheetset.settings.ReaderLayout
import cz.teply.sheetset.ui.ReaderPosition
import cz.teply.sheetset.ui.nextPosition
import cz.teply.sheetset.ui.previousPosition
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
import java.io.FileOutputStream
import java.util.UUID

class SheetSetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(File(application.filesDir, "library"))
    private val settingsStore = SettingsStore(
        application.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE),
    )
    private val annotationSaveMutex = Mutex()
    private val readerPositionMutex = Mutex()
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

    fun updateScoreLabels(scoreId: String, labels: List<String>) = launchAction {
        repository.updateScoreLabels(scoreId, labels)
    }

    fun renameSetlist(setlistId: String, name: String) = launchAction {
        repository.renameSetlist(setlistId, name)
    }

    fun deleteSetlist(setlistId: String) = launchAction { repository.deleteSetlist(setlistId) }

    fun updateSetlistLabels(setlistId: String, labels: List<String>) = launchAction {
        repository.updateSetlistLabels(setlistId, labels)
    }

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
        openReader(
            listOf(score.id),
            scoreIndex = 0,
            pageIndex = score.lastPageIndex,
            pagePart = score.lastPagePart,
        )
    }

    fun openScoreAt(score: Score, pageIndex: Int) {
        openReader(listOf(score.id), scoreIndex = 0, pageIndex = pageIndex, pagePart = 0)
    }

    fun jumpToPage(pageIndex: Int) {
        val reader = state.value.reader ?: return
        if (pageIndex !in 0 until reader.score.pageCount) return
        applyReaderPosition(reader, ReaderPosition(reader.scoreIndex, pageIndex, 0))
    }

    fun addBookmark(title: String) {
        val reader = state.value.reader ?: return
        launchAction {
            repository.addBookmark(
                reader.score.id,
                Bookmark(UUID.randomUUID().toString(), title, reader.pageIndex),
            )
        }
    }

    fun renameBookmark(bookmarkId: String, title: String) {
        val scoreId = state.value.reader?.score?.id ?: return
        launchAction { repository.renameBookmark(scoreId, bookmarkId, title) }
    }

    fun deleteBookmark(bookmarkId: String) {
        val scoreId = state.value.reader?.score?.id ?: return
        launchAction { repository.deleteBookmark(scoreId, bookmarkId) }
    }

    fun openSetlistScore(setlistId: String, scoreIndex: Int) {
        val ids = state.value.catalog.setlists.firstOrNull { it.id == setlistId }?.scoreIds ?: return
        openReader(ids, scoreIndex, pageIndex = 0, pagePart = 0)
    }

    fun closeReader() {
        mutableState.update { it.copy(reader = null) }
    }

    fun nextPage(layout: ReaderLayout) {
        movePage(layout, forward = true)
    }

    fun previousPage(layout: ReaderLayout) {
        movePage(layout, forward = false)
    }

    private fun movePage(layout: ReaderLayout, forward: Boolean) {
        val reader = state.value.reader ?: return
        val catalog = state.value.catalog
        val scores = catalog.scores.associateBy(Score::id)
        val pageCounts = reader.scoreIds.map { id -> scores[id]?.pageCount ?: return }
        val current = ReaderPosition(reader.scoreIndex, reader.pageIndex, reader.pagePart)
        val target = if (forward) {
            nextPosition(current, pageCounts, layout)
        } else {
            previousPosition(current, pageCounts, layout)
        } ?: return
        applyReaderPosition(reader, target)
    }

    private fun applyReaderPosition(reader: ReaderUiState, target: ReaderPosition) {
        if (target.scoreIndex == reader.scoreIndex) {
            val viewedAt = System.currentTimeMillis()
            val updatedCatalog = state.value.catalog.saveReaderPosition(
                reader.score.id,
                target.pageIndex,
                target.pagePart,
                viewedAt,
            )
            val updatedScore = updatedCatalog.scores.first { it.id == reader.score.id }
            mutableState.update {
                it.copy(
                    catalog = updatedCatalog,
                    reader = reader.copy(
                        score = updatedScore,
                        pageIndex = target.pageIndex,
                        pagePart = target.pagePart,
                    ),
                )
            }
            viewModelScope.launch {
                readerPositionMutex.withLock {
                    try {
                        repository.saveReaderPosition(
                            reader.score.id,
                            target.pageIndex,
                            target.pagePart,
                            viewedAt,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        mutableState.update { it.copy(error = true) }
                    }
                }
            }
            return
        }
        openReader(
            reader.scoreIds,
            target.scoreIndex,
            target.pageIndex,
            target.pagePart,
        )
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

    fun createSharedBackup(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            val application = getApplication<Application>()
            val directory = File(application.cacheDir, "shared-backups")
            val temporary = File(directory, ".SheetSet-Backup.tmp")
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Could not create share directory")
                }
                temporary.delete()
                val locales = application.getSystemService(LocaleManager::class.java)
                    .applicationLocales
                val languageTag = if (locales.isEmpty) null else locales[0].language
                FileOutputStream(temporary).use { output ->
                    repository.createBackup(output, state.value.settings, languageTag)
                }
                val destination = File(directory, "SheetSet-Backup.zip")
                if (destination.exists() && !destination.delete()) {
                    throw IllegalStateException("Could not replace shared backup")
                }
                if (!temporary.renameTo(destination)) {
                    throw IllegalStateException("Could not prepare shared backup")
                }
                val uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.files",
                    destination,
                )
                mutableState.update { it.copy(loading = false) }
                onReady(uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            } finally {
                temporary.delete()
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false, reader = null) }
            try {
                val application = getApplication<Application>()
                val input = application.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                val metadata = input.use { backup -> repository.restoreBackup(backup) }
                mutableState.update {
                    it.copy(
                        catalog = repository.load(),
                        loading = false,
                        settings = metadata?.settings ?: it.settings,
                    )
                }
                metadata?.let {
                    settingsStore.save(it.settings)
                    AppLanguages.select(application, it.languageTag)
                }
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
                val catalog = repository.load()
                mutableState.update {
                    val reader = it.reader?.let { current ->
                        catalog.scores.firstOrNull { score -> score.id == current.score.id }
                            ?.let { score -> current.copy(score = score) }
                    }
                    it.copy(
                        catalog = catalog,
                        reader = reader,
                        loading = false,
                        error = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private fun openReader(
        scoreIds: List<String>,
        scoreIndex: Int,
        pageIndex: Int,
        pagePart: Int,
    ) {
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
                repository.saveReaderPosition(
                    score.id,
                    page = pageIndex,
                    part = pagePart,
                    viewedAt = System.currentTimeMillis(),
                )
                val catalog = repository.load()
                val savedScore = catalog.scores.first { it.id == score.id }
                mutableState.update {
                    it.copy(
                        catalog = catalog,
                        loading = false,
                        reader = ReaderUiState(
                            score = savedScore,
                            file = file,
                            scoreIds = scoreIds,
                            scoreIndex = scoreIndex,
                            pageIndex = pageIndex,
                            pagePart = pagePart,
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
