package io.github.sgirishshiva.glyphwizard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.sgirishshiva.glyphwizard.designsystem.GlyphTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GlyphTheme { Text(stringResource(R.string.app_name)) } }
    }
}
