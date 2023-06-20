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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum

/** Simple provider to test instantiation and default parameters. */
class TestProvider(defaultPrefix: String = "prefix") : PreviewParameterProvider<String> {

  override val values: Sequence<String> =
    sequenceOf("${defaultPrefix}A", "${defaultPrefix}B", "${defaultPrefix}C")
}

/** Provider that throws an Exception in the constructor */
class FailingProvider : PreviewParameterProvider<String> {

  init {
    throw Exception("Make it fail")
  }

  override val values: Sequence<String> = sequenceOf("hey")
}

/** Provider with 11 values */
class LargeProvider : PreviewParameterProvider<String> {
  override val values: Sequence<String> =
    sequenceOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
}

@Preview
@Composable
fun TestWithProvider(@PreviewParameter(provider = TestProvider::class) name: String) {}

@Preview
@Composable
fun TestWithProviderInExpression(@PreviewParameter(provider = TestProvider::class) name: String) =
  println(name)

@Preview @Composable fun TestLorem(@PreviewParameter(provider = LoremIpsum::class) lorem: String) {}

@Preview
@Composable
fun TestFailingProvider(@PreviewParameter(provider = FailingProvider::class) name: String) {}

@Preview
@Composable
fun TestLargeProvider(@PreviewParameter(provider = LargeProvider::class) name: String) {}
