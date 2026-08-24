package cz.teply.sheetset.settings

import android.content.SharedPreferences

class SettingsStore(private val preferences: SharedPreferences) {
    fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            keepScreenAwake = preferences.getBoolean("keep_screen_awake", defaults.keepScreenAwake),
            pageFit = preferences.enum("page_fit", defaults.pageFit),
            pageTurnTaps = preferences.getBoolean("page_turn_taps", defaults.pageTurnTaps),
            pageTurnSwipes = preferences.getBoolean("page_turn_swipes", defaults.pageTurnSwipes),
            autoHideControls = preferences.getBoolean("auto_hide_controls", defaults.autoHideControls),
            defaultTool = preferences.enum("default_tool", defaults.defaultTool),
            penWidth = preferences.enum("pen_width", defaults.penWidth),
            highlighterStrength = preferences.enum("highlighter_strength", defaults.highlighterStrength),
            textSize = preferences.enum("text_size", defaults.textSize),
            readerLayout = preferences.enum("reader_layout", defaults.readerLayout),
        )
    }

    fun save(settings: AppSettings) {
        preferences.edit()
            .putBoolean("keep_screen_awake", settings.keepScreenAwake)
            .putString("page_fit", settings.pageFit.name)
            .putBoolean("page_turn_taps", settings.pageTurnTaps)
            .putBoolean("page_turn_swipes", settings.pageTurnSwipes)
            .putBoolean("auto_hide_controls", settings.autoHideControls)
            .putString("default_tool", settings.defaultTool.name)
            .putString("pen_width", settings.penWidth.name)
            .putString("highlighter_strength", settings.highlighterStrength.name)
            .putString("text_size", settings.textSize.name)
            .putString("reader_layout", settings.readerLayout.name)
            .apply()
    }
}

private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, fallback: T): T =
    getString(key, null)?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: fallback
