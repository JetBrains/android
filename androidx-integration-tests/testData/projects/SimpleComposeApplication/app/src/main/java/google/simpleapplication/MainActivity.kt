package google.simpleapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Greeting("Android")
            }
        }
    }
}

/**
 * Greeting element.
 *
 * @sample DefaultPreview
 */
@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview
@Composable
fun DefaultPreview() {
    MaterialTheme {
        Greeting("Android")
    }
}

@Preview
@Composable
fun TwoElementsPreview() {
    MaterialTheme {
        Column {
            Text("Hello 2")
            Button(onClick = {}) {
                Text("Hello World")
            }
        }
    }
}

@Preview
@Composable
fun NavigatablePreview() {
  PreviewInOtherFile()
}
