package cz.teply.sheetset.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguages {
    val supportedTags = setOf("en", "cs", "sk", "de", "pl")

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("language_initialized", false)) {
            preferences.edit().putBoolean("language_initialized", true).commit()
            setLocales(context, "en")
        }
    }

    fun select(context: Context, languageTag: String?) {
        require(languageTag == null || languageTag in supportedTags) { "Unsupported language" }
        context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
            .edit().putBoolean("language_initialized", true).commit()
        setLocales(context, languageTag)
    }

    fun currentTag(context: Context): String? = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales[0]?.language
    } else {
        AppCompatDelegate.getApplicationLocales()[0]?.language
    }

    private fun setLocales(context: Context, languageTag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            AppCompatDelegate.setApplicationLocales(
                languageTag?.let(LocaleListCompat::forLanguageTags)
                    ?: LocaleListCompat.getEmptyLocaleList(),
            )
        }
    }
}
