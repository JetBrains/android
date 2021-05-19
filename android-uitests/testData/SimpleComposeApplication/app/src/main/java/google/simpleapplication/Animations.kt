package google.simpleapplication

import androidx.compose.animation.Transition
import androidx.compose.animation.core.FloatPropKey
import androidx.compose.animation.core.transitionDefinition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.ui.tooling.preview.Preview

private const val halfSize = 200f

private val scale = FloatPropKey()

private val transDef = transitionDefinition<String> {
  state("start") {
    this[scale] = 1f
  }
  state("end") {
    this[scale] = 3f
  }
  transition {
    scale using tween(
      durationMillis = 10000
    )
  }
}

@Preview
@Composable
fun AnimationDemo() {
  Transition(definition = transDef, initState = "start", toState = "end") { state ->
    ScaledColorRect(scale = state[scale])
  }
}

@Preview
@Composable
fun NotAnAnimation() {
  MaterialTheme {
    Text(text = "Not an animation!")
  }
}

@Composable
private fun ScaledColorRect(scale: Float) {
  Canvas(Modifier.fillMaxSize()) {
    drawRect(
      Color.Red,
      topLeft = Offset(center.x - halfSize * scale, center.y - halfSize * scale),
      size = Size(halfSize * 2 * scale, halfSize * 2 * scale)
    )
  }
}