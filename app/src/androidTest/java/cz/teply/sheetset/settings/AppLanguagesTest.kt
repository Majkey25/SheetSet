package cz.teply.sheetset.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
    @After
    fun cleanUp() {
        AppLanguages.select(context, null)
        preferences.edit().clear().commit()
    }

    @Test
    fun freshInstallSelectsEnglishOnlyOnce() {
        preferences.edit().clear().commit()
        AppLanguages.select(context, null)
        preferences.edit().clear().commit()

        AppLanguages.initialize(context)
        assertEquals("en", AppLanguages.currentTag(context))

        AppLanguages.select(context, null)
        AppLanguages.initialize(context)
        assertEquals(null, AppLanguages.currentTag(context))
        assertTrue(preferences.getBoolean("language_initialized", false))
    }

    @Test
    fun selectingLanguageSetsLocaleAndMarksInitialized() {
        preferences.edit().clear().commit()
        AppLanguages.select(context, null)
        preferences.edit().clear().commit()

        AppLanguages.select(context, "cs")

        assertEquals("cs", AppLanguages.currentTag(context))
        assertTrue(preferences.getBoolean("language_initialized", false))
    }

    @Test
    fun repeatedInitializePreservesFrameworkLocale() {
        preferences.edit().clear().commit()
        AppLanguages.select(context, null)
        preferences.edit().clear().commit()

        AppLanguages.initialize(context)
        AppLanguages.select(context, "cs")
        AppLanguages.initialize(context)

        assertEquals("cs", AppLanguages.currentTag(context))
    }

    @Test
    fun unsupportedLanguageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppLanguages.select(context, "fr")
        }
    }
}
