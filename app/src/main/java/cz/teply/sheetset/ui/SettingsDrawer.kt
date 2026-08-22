package cz.teply.sheetset.ui

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.teply.sheetset.R
import cz.teply.sheetset.settings.AnnotationTextSize
import cz.teply.sheetset.settings.AppSettings
import cz.teply.sheetset.settings.HighlightStrength
import cz.teply.sheetset.settings.PageFit
import cz.teply.sheetset.settings.ReaderDefaultTool
import cz.teply.sheetset.settings.ToolSize
import kotlinx.coroutines.launch

private enum class DrawerScreen { MENU, LANGUAGE, READER, ANNOTATIONS, ABOUT }

@Composable
fun SettingsDrawer(
    drawerState: DrawerState,
    destination: AppDestination,
    settings: AppSettings,
    onDestination: (AppDestination) -> Unit,
    onSettings: (AppSettings) -> Unit,
    onLanguage: (String?) -> Unit,
    content: @Composable () -> Unit,
) {
    var screen by remember { mutableStateOf(DrawerScreen.MENU) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) screen = DrawerScreen.MENU
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).then(
                    if (drawerState.currentValue == DrawerValue.Closed) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                ),
            ) {
                when (screen) {
                    DrawerScreen.MENU -> DrawerMenu(
                        destination = destination,
                        onDestination = {
                            onDestination(it)
                            scope.launch { drawerState.close() }
                        },
                        onScreen = { screen = it },
                    )
                    DrawerScreen.LANGUAGE -> LanguageSettings(
                        onBack = { screen = DrawerScreen.MENU },
                        onLanguage = onLanguage,
                    )
                    DrawerScreen.READER -> ReaderSettings(
                        settings = settings,
                        onBack = { screen = DrawerScreen.MENU },
                        onSettings = onSettings,
                    )
                    DrawerScreen.ANNOTATIONS -> AnnotationSettings(
                        settings = settings,
                        onBack = { screen = DrawerScreen.MENU },
                        onSettings = onSettings,
                    )
                    DrawerScreen.ABOUT -> AboutSettings(onBack = { screen = DrawerScreen.MENU })
                }
            }
        },
        content = content,
    )
}

@Composable
private fun DrawerMenu(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    onScreen: (DrawerScreen) -> Unit,
) {
    Column(
        Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.menu),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.tab_pdf)) },
            selected = destination == AppDestination.PDF,
            onClick = { onDestination(AppDestination.PDF) },
            icon = { Icon(painterResource(R.drawable.ic_pdf_24), contentDescription = null) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.tab_setlists)) },
            selected = destination == AppDestination.SETLISTS,
            onClick = { onDestination(AppDestination.SETLISTS) },
            icon = { Icon(painterResource(R.drawable.ic_setlist_24), contentDescription = null) },
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        DrawerSection(R.string.language, R.drawable.ic_language_24) {
            onScreen(DrawerScreen.LANGUAGE)
        }
        DrawerSection(R.string.reader_settings, R.drawable.ic_pdf_24) {
            onScreen(DrawerScreen.READER)
        }
        DrawerSection(R.string.annotation_defaults, R.drawable.ic_edit_24) {
            onScreen(DrawerScreen.ANNOTATIONS)
        }
        DrawerSection(R.string.about, R.drawable.ic_info_24) {
            onScreen(DrawerScreen.ABOUT)
        }
    }
}

@Composable
private fun DrawerSection(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        selected = false,
        onClick = onClick,
        icon = { Icon(painterResource(icon), contentDescription = null) },
    )
}

@Composable
private fun LanguageSettings(onBack: () -> Unit, onLanguage: (String?) -> Unit) {
    val context = LocalContext.current
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    val selected = if (locales.isEmpty) null else locales[0].language
    SettingsPage(R.string.language, onBack) {
        listOf(
            null to R.string.device_language,
            "en" to R.string.language_english,
            "cs" to R.string.language_czech,
            "sk" to R.string.language_slovak,
            "de" to R.string.language_german,
            "pl" to R.string.language_polish,
        ).forEach { (tag, label) ->
            ChoiceRow(
                label = label,
                selected = selected == tag,
                onClick = { onLanguage(tag) },
            )
        }
    }
}

@Composable
private fun ReaderSettings(
    settings: AppSettings,
    onBack: () -> Unit,
    onSettings: (AppSettings) -> Unit,
) {
    SettingsPage(R.string.reader_settings, onBack) {
        SwitchRow(R.string.keep_screen_awake, settings.keepScreenAwake) {
            onSettings(settings.copy(keepScreenAwake = it))
        }
        SwitchRow(R.string.page_turn_taps, settings.pageTurnTaps) {
            onSettings(settings.copy(pageTurnTaps = it))
        }
        SwitchRow(R.string.page_turn_swipes, settings.pageTurnSwipes) {
            onSettings(settings.copy(pageTurnSwipes = it))
        }
        SwitchRow(R.string.auto_hide_controls, settings.autoHideControls) {
            onSettings(settings.copy(autoHideControls = it))
        }
        ChoiceTitle(R.string.page_fit)
        ChoiceRow(R.string.fit_page, settings.pageFit == PageFit.PAGE) {
            onSettings(settings.copy(pageFit = PageFit.PAGE))
        }
        ChoiceRow(R.string.fit_width, settings.pageFit == PageFit.WIDTH) {
            onSettings(settings.copy(pageFit = PageFit.WIDTH))
        }
    }
}

@Composable
private fun AnnotationSettings(
    settings: AppSettings,
    onBack: () -> Unit,
    onSettings: (AppSettings) -> Unit,
) {
    SettingsPage(R.string.annotation_defaults, onBack) {
        ChoiceTitle(R.string.default_tool)
        listOf(
            ReaderDefaultTool.VIEW to R.string.view,
            ReaderDefaultTool.PEN to R.string.pen,
            ReaderDefaultTool.HIGHLIGHTER to R.string.highlighter,
        ).forEach { (value, label) ->
            ChoiceRow(label, settings.defaultTool == value) {
                onSettings(settings.copy(defaultTool = value))
            }
        }
        ChoiceTitle(R.string.pen_width)
        listOf(
            ToolSize.THIN to R.string.thin,
            ToolSize.MEDIUM to R.string.medium,
            ToolSize.THICK to R.string.thick,
        ).forEach { (value, label) ->
            ChoiceRow(label, settings.penWidth == value) {
                onSettings(settings.copy(penWidth = value))
            }
        }
        ChoiceTitle(R.string.highlighter_opacity)
        listOf(
            HighlightStrength.LIGHT to R.string.light,
            HighlightStrength.MEDIUM to R.string.medium,
            HighlightStrength.STRONG to R.string.strong,
        ).forEach { (value, label) ->
            ChoiceRow(label, settings.highlighterStrength == value) {
                onSettings(settings.copy(highlighterStrength = value))
            }
        }
        ChoiceTitle(R.string.text_size)
        listOf(
            AnnotationTextSize.SMALL to R.string.small,
            AnnotationTextSize.MEDIUM to R.string.medium,
            AnnotationTextSize.LARGE to R.string.large,
        ).forEach { (value, label) ->
            ChoiceRow(label, settings.textSize == value) {
                onSettings(settings.copy(textSize = value))
            }
        }
    }
}

@Composable
private fun AboutSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        ).versionName.orEmpty()
    }
    SettingsPage(R.string.about, onBack) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_version)) },
            supportingContent = { Text(version) },
        )
        ListItem(headlineContent = { Text(stringResource(R.string.android_requirement)) })
        ListItem(headlineContent = { Text(stringResource(R.string.privacy_offline)) })
        LinkRow(R.string.license, "$REPOSITORY_URL/blob/main/LICENSE", context)
        LinkRow(R.string.github_repository, REPOSITORY_URL, context)
        LinkRow(R.string.release_page, "$REPOSITORY_URL/releases", context)
    }
}

@Composable
private fun SettingsPage(
    @StringRes title: Int,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val back = stringResource(R.string.back)
    Column(
        Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier = Modifier.semantics { contentDescription = back },
                onClick = onBack,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left_24),
                    contentDescription = null,
                )
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChecked(!checked) },
        headlineContent = { Text(stringResource(label)) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChecked)
        },
    )
}

@Composable
private fun ChoiceTitle(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun ChoiceRow(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { RadioButton(selected = selected, onClick = onClick) },
    )
}

@Composable
private fun LinkRow(@StringRes label: Int, url: String, context: Context) {
    ListItem(
        modifier = Modifier.clickable { openUrl(context, url) },
        headlineContent = { Text(stringResource(label)) },
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private const val REPOSITORY_URL = "https://github.com/Majkey25/SheetSet"
