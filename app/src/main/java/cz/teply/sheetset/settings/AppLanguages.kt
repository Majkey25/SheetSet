package cz.teply.sheetset.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

object AppLanguages {
    val supportedTags = setOf("en", "cs", "sk", "de", "pl")

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("language_initialized", false)) {
            preferences.edit().putBoolean("language_initialized", true).commit()
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags("en")
        }
    }

    fun select(context: Context, languageTag: String?) {
        require(languageTag == null || languageTag in supportedTags) { "Unsupported language" }
        context.getSharedPreferences("sheetset-settings", Context.MODE_PRIVATE)
            .edit().putBoolean("language_initialized", true).commit()
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (languageTag == null) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(languageTag)
    }
}
