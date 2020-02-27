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

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.toArray
import org.intellij.lang.annotations.Language

/**
 * Returns the [HighlightInfo] description adding the relative line number
 */
private fun HighlightInfo.descriptionWithLineNumber() =
  "${StringUtil.offsetToLineNumber(highlighter.document.text, startOffset)}: ${description}"

class InspectionsTest : ComposeLightJavaCodeInsightFixtureTestCase() {
  fun testNeedsComposableInspection() {
    myFixture.enableInspections(PreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
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
    assertEquals("9: Preview only works with Composable functions.",
                 myFixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber())
  }

  fun testNoParametersInPreview() {
    myFixture.enableInspections(PreviewAnnotationInFunctionWithParametersInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Preview
      @Composable
      fun Preview1(a: Int) { // ERROR, no defaults
      }

      @Preview(name = "preview2", apiLevel = 12)
      @Composable
      fun Preview2(b: String = "hello") {
      }

      @Preview
      @Composable
      fun Preview3(a: Int, b: String = "hello") { // ERROR, first parameter has not default
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    val inspections = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }


    assertEquals("""3: Composable functions with non-default parameters are not supported in Preview.
                    |13: Composable functions with non-default parameters are not supported in Preview.""".trimMargin(),
                 inspections)
  }

  fun testPreviewMustBeTopLevel() {
    myFixture.enableInspections(PreviewMustBeTopLevelFunction() as InspectionProfileEntry)

    @Suppress("TestFunctionName", "ClassName")
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview(name = "top level preview")
      fun TopLevelPreview() {
        @Composable
        @Preview(name = "not a top level preview") // ERROR
        fun NotTopLevelFunctionPreview() {
            @Composable
            @Preview(name = "not a top level preview, with a lot of nesting") // ERROR
            fun SuperNestedPreview() {
            }
        }
      }

      class aClass {
        @Preview(name = "preview2", apiLevel = 12)
        @Composable
        fun ClassMethodPreview() {
          @Composable
          @Preview(name = "not a top level preview in a class") // ERROR
          fun NotTopLevelFunctionPreviewInAClass() {
          }
        }

        @Preview(name = "preview in a class with default constructor")
        @Composable
        private fun PrivateClassMethodPreview() {
        }
      }

      private class privateClass {
        class NotTopLevelClass {
          @Preview("in a non top level class") // ERROR
          @Composable
          fun ClassMethodPreview() {
          }
        }

        @Preview
        @Composable
        fun ClassMethodPreview() {
        }
      }

      class bClass(i: Int) {
        @Preview("in a class with parameters") // ERROR
        @Composable
        fun ClassMethodPreview() {
        }
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    val inspections = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals("""7: Preview must be a top level declarations or in a top level class with a default constructor.
                    |10: Preview must be a top level declarations or in a top level class with a default constructor.
                    |21: Preview must be a top level declarations or in a top level class with a default constructor.
                    |34: Preview must be a top level declarations or in a top level class with a default constructor.
                    |47: Preview must be a top level declarations or in a top level class with a default constructor.""".trimMargin(),
                 inspections)
  }

  fun testWidthShouldntExceedApiLimit() {
    myFixture.enableInspections(PreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview(name = "Preview 1", widthDp = 2001)
      fun Preview1() {
      }

      @Composable
      @Preview(name = "Preview 2", widthDp = 2000)
      fun Preview2() {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    val inspections = myFixture.doHighlighting(HighlightSeverity.WARNING)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }


    assertEquals("4: Preview width is limited to 2,000. Setting a higher number will not increase the preview width.", inspections)
  }

  fun testHeightShouldntExceedApiLimit() {
    myFixture.enableInspections(PreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview(name = "Preview 1", heightDp = 2001)
      fun Preview1() {
      }

      @Composable
      @Preview(name = "Preview 2", heightDp = 2000)
      fun Preview2() {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    val inspections = myFixture.doHighlighting(HighlightSeverity.WARNING)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }


    assertEquals("4: Preview height is limited to 2,000. Setting a higher number will not increase the preview height.", inspections)
  }

  fun testOnlyParametersAndValuesAreHighlighted() {
    myFixture.enableInspections(PreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview(name = "Preview 1", heightDp = 2001, widthDp = 2001)
      fun Preview1() {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    val inspections = myFixture.doHighlighting(HighlightSeverity.WARNING)
      .sortedByDescending { -it.startOffset }
      .toArray(emptyArray())

    // Verify the height inspection only highlights the height parameter and value, i.e. "heightDp = 2001"
    val heightInspection = inspections[0]
    var highlightLength = heightInspection.actualEndOffset - heightInspection.actualStartOffset
    assertEquals("heightDp = 2001".length, highlightLength)

    // Verify the width inspection only highlights the width parameter and value, i.e. "widthDp = 2001"
    val widthInspection = inspections[1]
    highlightLength = widthInspection.actualEndOffset - widthInspection.actualStartOffset
    assertEquals("widthDp = 2001".length, highlightLength)
  }

  fun testInspectionsWithNoImport() {
    myFixture.enableInspections(PreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import androidx.compose.Composable

      @Composable
      @androidx.ui.tooling.preview.Preview
      fun Preview1() {
      }

      // Missing Composable annotation
      @androidx.ui.tooling.preview.Preview(name = "preview2")
      fun Preview2() {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    assertEquals("8: Preview only works with Composable functions.",
                 myFixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber())
  }
}