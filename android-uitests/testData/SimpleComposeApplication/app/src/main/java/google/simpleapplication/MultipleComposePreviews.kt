package google.simpleapplication

import androidx.compose.Composable
import androidx.ui.core.Text
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview

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
