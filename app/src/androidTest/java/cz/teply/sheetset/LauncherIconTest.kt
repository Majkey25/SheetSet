package cz.teply.sheetset

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class LauncherIconTest {
    @Test
    fun launcherUsesAdaptiveIcon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        context.resources.getXml(context.applicationInfo.icon).use { icon ->
            while (icon.eventType != XmlPullParser.START_TAG) icon.next()

            assertEquals("adaptive-icon", icon.name)
        }
    }
}
