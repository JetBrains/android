package google.simpleapplication

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import google.simpleapplication.R

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
        Greeting(stringResource(R.string.greeting))
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

@Preview
@Composable
fun MyPreviewWithInline() {
    MyInline {
        Text("Only a text")
        Text("Only a text")
    }
}
