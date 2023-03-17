/*
 * Copyright (C) 2022 The Android Open Source Project
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
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import kotlin.random.Random

@Preview
@Composable
fun PreviewWithRenderErrors() {
  Column {
    val x = giveMeAList(10)
    Text(text = "Hello")
    Text(text = x[0].toString())
    Text(text = x[10].toString()) // Try to access an index that is out of bounds
  }
}

fun giveMeAList(size: Int): MutableList<Int> {
  val list = mutableListOf<Int>()
  for (i in 1..size) {
    list.add(Random.nextInt(100))
  }
  return list
}

@Preview
@Composable
fun PreviewWithoutRenderErrors() {
  Text("Something")
}

@Preview
@Composable
fun PreviewWithContrastError() {
  Text(text = "Contrast error", modifier = Modifier.background(color = Color.DarkGray))
}
