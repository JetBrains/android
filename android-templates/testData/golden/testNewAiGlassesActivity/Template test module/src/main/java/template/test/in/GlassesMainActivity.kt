// This file should not be edited manually! See go/template-diff-tests
package template.test.`in`

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.ActionCard
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.googlefonts.createGoogleSansFlexTypography

class GlassesMainActivity : ComponentActivity() {
    private lateinit var audioInterface: AudioInterface

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = true
        audioInterface = AudioInterface(
            this,
            getString(R.string.hello_ai_glasses)
        )
        lifecycle.addObserver(audioInterface)
        setContent {
            GlimmerTheme(
                typography = createGoogleSansFlexTypography()
            ) {
                HomeScreen(onClose = {
                    audioInterface.speak("Goodbye!")
                    finish()
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Do things to make the user aware that this activity is active (for
        // example, play audio), when the display state is off
    }

    override fun onStop() {
        super.onStop()
        // Release high-drain resources (camera, mic, sensors)
        // to prevent battery drain
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onClose: () -> Unit) {
    Box(
        modifier = modifier
            .background(GlimmerTheme.colors.background)
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        ActionCard(
            title = {
                Text(stringResource(id = R.string.app_name))
            },
            action = {
                Button(onClick = {
                    onClose()
                }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        ) {
            Text(stringResource(id = R.string.hello_ai_glasses))
        }
    }
}

@Preview(device = "id:ai_glasses_device", backgroundColor = 0x00FF00)
@Composable
fun DefaultPreview() {
    GlimmerTheme {
        HomeScreen(onClose = {})
    }
}