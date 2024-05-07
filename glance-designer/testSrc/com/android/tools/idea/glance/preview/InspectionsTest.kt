/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InspectionsTest {
  @get:Rule val projectRule = GlanceProjectRule(AndroidProjectRule.inMemory().withKotlin())
  private val fixture
    get() = projectRule.fixture

  @Test
  fun testNeedsComposableInspection() {
    fixture.enableInspections(
      GlancePreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry
    )

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $GLANCE_PREVIEW_ANNOTATION_FQN
      import $COMPOSABLE_ANNOTATION_FQ_NAME

      @Composable
      @Preview
      fun Preview1() {
      }

      // Missing Composable annotation
      @Preview
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "9: Glance Preview only works with Composable functions",
      fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber(),
    )
  }

  @Test
  fun testInspectionsWithNoImport() {
    fixture.enableInspections(
      GlancePreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry
    )

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQ_NAME

      @Composable
      @$GLANCE_PREVIEW_ANNOTATION_FQN
      fun Preview1() {
      }

      // Missing Composable annotation
      @$GLANCE_PREVIEW_ANNOTATION_FQN
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "8: Glance Preview only works with Composable functions",
      fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber(),
    )
  }

  @Test
  fun testPreviewMustBeTopLevel() {
    fixture.enableInspections(GlancePreviewMustBeTopLevelFunction() as InspectionProfileEntry)

    @Suppress("TestFunctionName", "ClassName")
    @Language("kotlin")
    val fileContent =
      """
      import $GLANCE_PREVIEW_ANNOTATION_FQN
      import $COMPOSABLE_ANNOTATION_FQ_NAME

      annotation class MyEmptyAnnotation

      @Preview
      annotation class MyAnnotation

      @Composable
      @MyEmptyAnnotation
      @MyAnnotation
      @Preview
      fun TopLevelPreview() {
        @Composable
        @MyEmptyAnnotation
        @MyAnnotation // ERROR
        @Preview // ERROR
        fun NotTopLevelFunctionPreview() {
            @Composable
            @MyEmptyAnnotation
            @MyAnnotation // ERROR
            @Preview // ERROR
            fun SuperNestedPreview() {
            }
        }
      }

      class aClass {
        @Preview
        @MyEmptyAnnotation
        @MyAnnotation
        @Composable
        fun ClassMethodPreview() {
          @Composable
          @MyEmptyAnnotation
          @MyAnnotation // ERROR
          @Preview // ERROR
          fun NotTopLevelFunctionPreviewInAClass() {
          }
        }

        @Preview
        @MyEmptyAnnotation
        @MyAnnotation
        @Composable
        private fun PrivateClassMethodPreview() {
        }
      }

      private class privateClass {
        class NotTopLevelClass {
          @Preview // ERROR
          @MyEmptyAnnotation
          @MyAnnotation // ERROR
          @Composable
          fun ClassMethodPreview() {
          }
        }

        @Preview
        @MyEmptyAnnotation
        @MyAnnotation
        @Composable
        fun ClassMethodPreview() {
        }
      }

      class bClass(i: Int) {
        @Preview // ERROR
        @MyEmptyAnnotation
        @MyAnnotation // ERROR
        @Composable
        fun ClassMethodPreview() {
        }
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        .sortedByDescending { -it.startOffset }
        .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """15: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |16: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |20: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |21: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |35: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |36: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |51: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |53: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |68: Glance Preview must be a top level declaration or in a top level class with a default constructor.
                    |70: Glance Preview must be a top level declaration or in a top level class with a default constructor."""
        .trimMargin(),
      inspections,
    )
  }

  @Test
  fun testWidthShouldNotExceedLimit() {
    fixture.enableInspections(GlancePreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $GLANCE_PREVIEW_ANNOTATION_FQN
      import $COMPOSABLE_ANNOTATION_FQ_NAME

      private const val badWidth = 3000

      private const val goodWidth = 2000

      @Preview(widthDp = badWidth) // warning
      annotation class BadAnnotation

      @Preview(widthDp = goodWidth)
      annotation class GoodAnnotation(val widthDp: Int = 2001) // MultiPreview annotation parameters have no effect

      @Composable
      @GoodAnnotation
      @Preview(heightDp = 2001, widthDp = 2001) // Only one warning
      fun Preview1() {
      }

      @Composable
      @BadAnnotation
      @Preview(widthDp = goodWidth)
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.WARNING)
        .sortedByDescending { -it.startOffset }
        .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """7: Preview width and height are limited to be between 1 and 2,000, setting a lower or higher number will not change the preview dimension
        |15: Preview width and height are limited to be between 1 and 2,000, setting a lower or higher number will not change the preview dimension
      """
        .trimMargin(),
      inspections,
    )
  }

  @Test
  fun testHeightShouldNotExceedLimit() {
    fixture.enableInspections(GlancePreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $GLANCE_PREVIEW_ANNOTATION_FQN
      import $COMPOSABLE_ANNOTATION_FQ_NAME

      private const val badHeight = 3000

      private const val goodHeight = 2000

      @Preview(heightDp = badHeight) // warning
      annotation class BadAnnotation

      @Preview(heightDp = goodHeight)
      annotation class GoodAnnotation(val heightDp: Int = 2001) // MultiPreview annotation parameters have no effect

      @Composable
      @GoodAnnotation
      @Preview(heightDp = 2001, widthDp = 2001) // Only one warning
      fun Preview1() {
      }

      @Composable
      @BadAnnotation
      @Preview(heightDp = goodHeight)
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.WARNING)
        .sortedByDescending { -it.startOffset }
        .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """7: Preview width and height are limited to be between 1 and 2,000, setting a lower or higher number will not change the preview dimension
        |15: Preview width and height are limited to be between 1 and 2,000, setting a lower or higher number will not change the preview dimension
      """
        .trimMargin(),
      inspections,
    )
  }
}

/** Returns the [HighlightInfo] description adding the relative line number */
internal fun HighlightInfo.descriptionWithLineNumber() =
  ReadAction.compute<String, Throwable> {
    "${StringUtil.offsetToLineNumber(highlighter!!.document.text, startOffset)}: $description"
  }
