/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package google.simpleapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun VisualLintErrorPreview() {
  val textState = remember { mutableStateOf(TextFieldValue("This is a wide text field")) }
  Column {
    TextField(
      value = textState.value,
      modifier = Modifier.fillMaxWidth(),
      onValueChange = { textState.value = it },
    )
    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
      Text(text = "This is a wide button")
    }
    Text(
      text =
        "This is a very very very very\n\nvery very very very very very very very very very very very very very very very very long text that has a line with more than 120 characters."
    )
    Box {
      Text(text = "This is a background text")
      Button(onClick = {}) { Text(text = "Button") }
    }
  }
}

@Preview
@Composable
fun NoVisualLintErrorPreview() {
  val textState = remember { mutableStateOf(TextFieldValue("This is a narrow text field")) }
  Column {
    TextField(value = textState.value, onValueChange = { textState.value = it })
    Button(onClick = {}) { Text(text = "This is a narrow button") }
    Text(text = "This is a short text")
  }
}

@Preview
@Composable
fun ColorContrastIssuePreview() {
  val veryDarkGrayColor = Color(0xFF00000F)
  val blackColor = Color(0xFF000000)
  Text(
    color = veryDarkGrayColor,
    text = "Hello Android!",
    modifier = Modifier.background(blackColor),
  )
}

@Preview
@Composable
fun OneColorBlindErrorPreview() {
  val backgroundColor = Color(0xFF833901)
  val foregroundColor = Color(0xFFFDE300)
  Text(
    color = foregroundColor,
    text = "Hello Android",
    modifier = Modifier.background(backgroundColor),
  )
}

@Preview
@Composable
fun TwoColorBlindErrorsPreview() {
  val backgroundColor = Color(0xFF994200)
  val foregroundColor = Color(0xFF06FF61)
  Text(
    color = foregroundColor,
    text = "Hello Android",
    modifier = Modifier.background(backgroundColor),
  )
}

@Preview
@Composable
fun ThreeColorBlindErrorPreview() {
  val purpleColor = Color(0xFFB3003D)
  val veryLightColor = Color(0xFFBCD5E2)
  Text(color = purpleColor, text = "Hello Android!", modifier = Modifier.background(veryLightColor))
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
fun WearPreview() {
  Column { Text("Hello world", modifier = Modifier.padding(10.dp)) }
}
