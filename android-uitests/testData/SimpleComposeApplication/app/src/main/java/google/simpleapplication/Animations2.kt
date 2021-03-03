package google.simpleapplication

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Preview
@Composable
fun VerySimpleAnimation() {
  var toState by remember { mutableStateOf(false) }

  val transition: Transition<Boolean> = updateTransition(targetState = toState)

  val scale: Float by transition.animateFloat(
    label = "Scale",
    transitionSpec = { spring(stiffness = 50f) }
  ) { state ->
    if (state) 3f else 1f
  }

  Box(
    Modifier
      .fillMaxSize()
      .wrapContentSize(Alignment.CenterStart)
      .size((100 * scale).dp)
      .background(Color(0xFFFF0000))
  )
}
