package cz.teply.sheetset

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches() {
        composeRule.onNodeWithContentDescription("Import PDF").assertIsDisplayed()
    }

    @Test
    fun rootBackDoesNotFinishApp() {
        Espresso.pressBack()

        composeRule.onNodeWithContentDescription("Import PDF").assertIsDisplayed()
    }
}
