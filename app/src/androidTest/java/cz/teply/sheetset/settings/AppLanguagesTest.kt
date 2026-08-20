package cz.teply.sheetset.settings

import android.content.Context
import android.app.LocaleManager
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
    private val localeManager = context.getSystemService(LocaleManager::class.java)

    @After
    fun cleanUp() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        preferences.edit().clear().commit()
    }

    @Test
    fun freshInstallSelectsEnglishOnlyOnce() {
        preferences.edit().clear().commit()
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()

        AppLanguages.initialize(context)
        assertEquals("en", localeManager.applicationLocales.toLanguageTags())

        AppLanguages.select(context, null)
        AppLanguages.initialize(context)
        assertTrue(localeManager.applicationLocales.isEmpty)
    }

    @Test
    fun repeatedInitializePreservesFrameworkLocale() {
        preferences.edit().clear().commit()
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()

        AppLanguages.initialize(context)
        localeManager.applicationLocales = LocaleList.forLanguageTags("cs")
        AppLanguages.initialize(context)

        assertEquals("cs", localeManager.applicationLocales.toLanguageTags())
    }

    @Test
    fun unsupportedLanguageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppLanguages.select(context, "fr")
        }
    }
}
