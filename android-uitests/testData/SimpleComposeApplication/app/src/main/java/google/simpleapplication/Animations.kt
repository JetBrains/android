package google.simpleapplication

import androidx.animation.FloatPropKey
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.ui.animation.Transition
import androidx.ui.core.Modifier
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.Text
import androidx.ui.geometry.Offset
import androidx.ui.geometry.Size
import androidx.ui.graphics.Color
import androidx.ui.layout.fillMaxSize
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview

private const val halfSize = 200f

private val scale = FloatPropKey()

private val transDef = transitionDefinition {
  state("start") {
    this[scale] = 1f
  }
  state("end") {
    this[scale] = 3f
  }
  transition {
    scale using tween<Float> {
      duration = 10000
    }
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
      topLeft = Offset(center.dx - halfSize * scale, center.dy - halfSize * scale),
      size = Size(halfSize * 2 * scale, halfSize * 2 * scale)
    )
  }
}