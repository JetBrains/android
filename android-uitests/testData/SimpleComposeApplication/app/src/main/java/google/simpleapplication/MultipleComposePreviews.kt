package google.simpleapplication

import androidx.compose.runtime.Composable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun Preview1() {
    MaterialTheme {
        Text(text = "Preview1")
    }
}

@Preview
@Composable
fun Preview2() {
    MaterialTheme {
        Text(text = "Preview2")
    }
}

@Preview()
@Composable
fun Preview3() {
    MaterialTheme {
        Text(text = "Preview3")
    }
}
