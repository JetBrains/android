package google.simpleapplication

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AmbientTextStyle
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun ComplexPreview() {
  val shortText = "Click me"
  val longText = "Very long text\nthat spans across\nmultiple lines"
  var short by remember { mutableStateOf(true) }
  MaterialTheme {
    Column {
      Row {
        Button(onClick = {}) {
          Text("Button1")
        }
        Button(onClick = {}) {
          Text("Button2")
        }
      }
      Text("Text1")
      Box(
        modifier = Modifier
          .background(
            Color.Blue,
            RoundedCornerShape(15.dp)
          )
          .clickable { short = !short }
          .padding(20.dp)
          .wrapContentSize()
          .animateContentSize { startSize, endSize -> println("$startSize -> $endSize") }
      ) {
        Text(
          if (short) {
            shortText
          }
          else {
            longText
          },
          style = AmbientTextStyle.current.copy(color = Color.White)
        )
      }
    }
  }
}