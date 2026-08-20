package cz.teply.sheetset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.teply.sheetset.settings.AppLanguages
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<SheetSetViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguages.initialize(this)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SheetSetTheme {
                SheetSetApp(
                    state,
                    SheetSetActions(
                        importPdfs = viewModel::importPdfs,
                        createSetlist = viewModel::createSetlist,
                        openScore = viewModel::openScore,
                        openSetlistScore = viewModel::openSetlistScore,
                        closeReader = viewModel::closeReader,
                        previousPage = viewModel::previousPage,
                        nextPage = viewModel::nextPage,
                        saveStrokes = viewModel::saveStrokes,
                        exportPdf = viewModel::exportPdf,
                        renameScore = viewModel::renameScore,
                        deleteScore = viewModel::deleteScore,
                        renameSetlist = viewModel::renameSetlist,
                        deleteSetlist = viewModel::deleteSetlist,
                        addScores = viewModel::addScores,
                        removeScore = viewModel::removeScore,
                        moveScore = viewModel::moveScore,
                    ),
                )
            }
        }
    }
}
