package io.github.sgirishshiva.glyphwizard.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlyphThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeRendersItsContent() {
        compose.setContent { GlyphTheme { Text("x") } }
        compose.onNodeWithText("x").assertIsDisplayed()
    }
}
