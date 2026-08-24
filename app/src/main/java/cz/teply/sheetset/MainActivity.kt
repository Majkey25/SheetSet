package cz.teply.sheetset

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.teply.sheetset.settings.AppLanguages
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme
import cz.teply.sheetset.ui.WindowLayout

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<SheetSetViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguages.initialize(this)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SheetSetTheme {
                BoxWithConstraints {
                    SheetSetApp(
                        state = state,
                        actions = SheetSetActions(
                            importPdfs = viewModel::importPdfs,
                            createSetlist = viewModel::createSetlist,
                            openScore = viewModel::openScore,
                            openScoreAt = viewModel::openScoreAt,
                            openSetlistScore = viewModel::openSetlistScore,
                            closeReader = viewModel::closeReader,
                            previousPage = viewModel::previousPage,
                            nextPage = viewModel::nextPage,
                            jumpToPage = viewModel::jumpToPage,
                            addBookmark = viewModel::addBookmark,
                            renameBookmark = viewModel::renameBookmark,
                            deleteBookmark = viewModel::deleteBookmark,
                            saveAnnotations = viewModel::saveAnnotations,
                            exportPdf = viewModel::exportPdf,
                            renameScore = viewModel::renameScore,
                            deleteScore = viewModel::deleteScore,
                            renameSetlist = viewModel::renameSetlist,
                            deleteSetlist = viewModel::deleteSetlist,
                            updateScoreLabels = viewModel::updateScoreLabels,
                            updateSetlistLabels = viewModel::updateSetlistLabels,
                            addScores = viewModel::addScores,
                            removeScore = viewModel::removeScore,
                            reorderScores = viewModel::reorderScores,
                            updateSettings = viewModel::updateSettings,
                            selectLanguage = { languageTag ->
                                AppLanguages.select(this@MainActivity, languageTag)
                            },
                            createBackup = viewModel::createBackup,
                            shareBackup = {
                                viewModel.createSharedBackup(::openBackupShareSheet)
                            },
                            restoreBackup = viewModel::restoreBackup,
                        ),
                        windowLayout = WindowLayout.fromWidth(maxWidth),
                    )
                }
            }
        }
    }

    private fun openBackupShareSheet(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share.clipData = ClipData.newUri(contentResolver, "SheetSet backup", uri)
        startActivity(Intent.createChooser(share, getString(R.string.share_backup_chooser)))
    }
}
