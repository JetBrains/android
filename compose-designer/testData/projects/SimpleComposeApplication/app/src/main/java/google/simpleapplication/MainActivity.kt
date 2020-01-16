package google.simpleapplication

import android.os.Bundle
import androidx.ui.material.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.ui.core.Text
import androidx.ui.core.setContent
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import androidx.ui.layout.*

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
            Button("Hello World")
        }
    }
}
