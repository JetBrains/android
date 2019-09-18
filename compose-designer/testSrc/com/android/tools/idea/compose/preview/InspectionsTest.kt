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
package com.android.tools.idea.compose.preview

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.containers.toArray
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertArrayEquals

class InspectionsTest : ComposeLightJavaCodeInsightFixtureTestCase() {
  fun testNeedsComposableInspection() {
    myFixture.enableInspections(PreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry)

    @Language("kotlin")
    val fileContent = """
      import com.android.tools.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview
      fun Preview1() {
      }

      // Missing Composable annotation
      @Preview(name = "preview2", apiLevel = 12)
      fun Preview2() {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    assertEquals("Preview methods must be @Composable.",
                 myFixture.doHighlighting(HighlightSeverity.ERROR).single().description)
  }

  fun testNoParametersInPreview() {
    myFixture.enableInspections(PreviewAnnotationInFunctionWithParametersInspection() as InspectionProfileEntry)

    @Language("kotlin")
    val fileContent = """
      import com.android.tools.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview
      fun Preview1(a: Int) {
      }

      @Preview(name = "preview2", apiLevel = 12)
      @Composable
      fun Preview2(b: String = "hello") {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    val inspections = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .sortedByDescending { -it.startOffset }
      .map { it.description }
      .toArray(emptyArray())

    assertArrayEquals(arrayOf("Preview methods must not have parameters.",
                              "Preview methods must not have parameters."),
                      inspections)
  }
}