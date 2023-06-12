/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview

@Preview @Composable fun EmptyPreview() {}

@Preview
@Composable
fun PreviewWithUnsignedTypes() {
  var myInt: Int = -2147483648
  var myUInt: UInt = 4294967295u
  var myLong: Long = -9223372036854775807L
  var myULong: ULong = 18446744073709551615uL
  MaterialTheme {
    Column {
      Text("$myInt")
      Text("$myUInt")
      Text("$myLong")
      Text("$myULong")
    }
  }
}

@Preview
@Composable
fun PreviewInOtherFile() {
  MaterialTheme {
    Column {
      Text("Line1")
      Text("Line2")
      Text("Line3")
    }
  }
}

@Preview
@Composable
fun TextFieldPreview() {
  val textState = remember { mutableStateOf(TextFieldValue()) }
  MaterialTheme { TextField(value = textState.value, onValueChange = { textState.value = it }) }
}
