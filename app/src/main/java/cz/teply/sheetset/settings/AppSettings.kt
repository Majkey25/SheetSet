package cz.teply.sheetset.settings

import cz.teply.sheetset.pdf.AnnotationEditorSettings

enum class PageFit { PAGE, WIDTH }
enum class ReaderDefaultTool { VIEW, PEN, HIGHLIGHTER }
enum class ToolSize { THIN, MEDIUM, THICK }
enum class HighlightStrength { LIGHT, MEDIUM, STRONG }
enum class AnnotationTextSize { SMALL, MEDIUM, LARGE }
enum class ReaderLayout { SINGLE, HALF, TWO_PAGE }
enum class ThemeMode { LIGHT, DARK }

data class AppSettings(
    val keepScreenAwake: Boolean = true,
    val pageFit: PageFit = PageFit.PAGE,
    val pageTurnTaps: Boolean = true,
    val pageTurnSwipes: Boolean = true,
    val autoHideControls: Boolean = true,
    val defaultTool: ReaderDefaultTool = ReaderDefaultTool.VIEW,
    val textSize: AnnotationTextSize = AnnotationTextSize.MEDIUM,
    val readerLayout: ReaderLayout = ReaderLayout.SINGLE,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val editor: AnnotationEditorSettings = AnnotationEditorSettings.defaults(),
)
