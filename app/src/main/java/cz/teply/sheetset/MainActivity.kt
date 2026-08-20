package cz.teply.sheetset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.teply.sheetset.ui.SheetSetActions
import cz.teply.sheetset.ui.SheetSetApp
import cz.teply.sheetset.ui.SheetSetTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<SheetSetViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SheetSetTheme {
                SheetSetApp(
                    state,
                    SheetSetActions(
                        importPdfs = viewModel::importPdfs,
                        createSetlist = viewModel::createSetlist,
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
