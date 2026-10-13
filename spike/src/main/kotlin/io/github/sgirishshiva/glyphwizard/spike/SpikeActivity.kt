// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.PatternName
import kotlin.math.roundToInt

/** Thin control panel for the spike; every button sends a [SpikeCommand] to [SpikePatternService]. */
class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val requestNotifications =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                SpikeControls(
                    onSend = ::send,
                    onOpenListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onAllowNotifications = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    }

    private fun send(
        action: String,
        extras: Bundle,
    ) {
        startForegroundService(Intent(this, SpikePatternService::class.java).setAction(action).putExtras(extras))
    }
}

@Composable
private fun SpikeControls(
    onSend: (String, Bundle) -> Unit,
    onOpenListenerSettings: () -> Unit,
    onAllowNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pattern by rememberSaveable { mutableStateOf(PatternName.Checker) }
    var fps by rememberSaveable { mutableFloatStateOf(30f) }
    var square by rememberSaveable { mutableStateOf(true) }
    var max2047 by rememberSaveable { mutableStateOf(false) }
    Column(modifier.padding(16.dp)) {
        PatternName.entries.chunked(CHIPS_PER_ROW).forEach { row ->
            Row {
                row.forEach { name ->
                    FilterChip(selected = pattern == name, onClick = { pattern = name }, label = { Text(name.key) })
                }
            }
        }
        Text("fps ${fps.roundToInt()}")
        Slider(value = fps, onValueChange = { fps = it }, valueRange = 5f..120f)
        Row {
            FilterChip(
                selected = square,
                onClick = { square = !square },
                label = { Text(if (square) SpikeCommand.LAYOUT_SQUARE else SpikeCommand.LAYOUT_LEDS) },
            )
            FilterChip(
                selected = max2047,
                onClick = { max2047 = !max2047 },
                label = { Text("max ${if (max2047) SpikeCommand.PROBE_MAX else SpikeCommand.DEFAULT_MAX}") },
            )
        }
        Row {
            Button(onClick = {
                val extras =
                    Bundle().apply {
                        putString(SpikeCommand.EXTRA_PATTERN, pattern.key)
                        putInt(SpikeCommand.EXTRA_FPS, fps.roundToInt())
                        putString(
                            SpikeCommand.EXTRA_LAYOUT,
                            if (square) SpikeCommand.LAYOUT_SQUARE else SpikeCommand.LAYOUT_LEDS,
                        )
                        putInt(
                            SpikeCommand.EXTRA_MAX,
                            if (max2047) SpikeCommand.PROBE_MAX else SpikeCommand.DEFAULT_MAX,
                        )
                    }
                onSend(SpikeCommand.ACTION_SHOW, extras)
            }) { Text("Start") }
            Button(onClick = { onSend(SpikeCommand.ACTION_STOP, Bundle.EMPTY) }) { Text("Stop") }
        }
        Button(onClick = onOpenListenerSettings) { Text("Grant notification access") }
        Button(onClick = onAllowNotifications) { Text("Allow notifications") }
    }
}

private const val CHIPS_PER_ROW = 4
