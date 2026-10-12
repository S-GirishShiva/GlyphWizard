package io.github.sgirishshiva.glyphwizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityTest {
    // MainActivity rather than createComposeRule(): release unit tests lack ui-test-manifest's ComponentActivity
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsTheAppName() {
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertIsDisplayed()
    }
}
