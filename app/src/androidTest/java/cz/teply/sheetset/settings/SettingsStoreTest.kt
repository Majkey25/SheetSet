package cz.teply.sheetset.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun invalidValuesFallBackToDefaults() {
        preferences.edit()
            .putString("page_fit", "BROKEN")
            .putString("pen_width", "MISSING")
            .putString("reader_layout", "UNKNOWN")
            .putString("auto_scroll_speed", "IMPOSSIBLE")
            .putBoolean("keep_screen_awake", false)
            .commit()

        assertEquals(
            AppSettings(keepScreenAwake = false),
            SettingsStore(preferences).load(),
        )
    }

    @Test
    fun settingsRoundTrip() {
        val expected = AppSettings(
            pageFit = PageFit.WIDTH,
            pageTurnTaps = false,
            pageTurnSwipes = false,
            autoHideControls = false,
            defaultTool = ReaderDefaultTool.PEN,
            penWidth = ToolSize.THICK,
            highlighterStrength = HighlightStrength.STRONG,
            textSize = AnnotationTextSize.LARGE,
            readerLayout = ReaderLayout.HALF,
            autoScrollSpeed = AutoScrollSpeed.FAST,
        )

        val store = SettingsStore(preferences)
        store.save(expected)

        assertEquals(expected, store.load())
    }
}
