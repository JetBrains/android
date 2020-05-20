package google.simpleapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.ui.core.setContent
import androidx.ui.foundation.Text
import androidx.ui.layout.*
import androidx.ui.material.Button
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview

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

@Preview
@Composable
fun OnlyATextNavigation() {
  Text("Only a text")
}
