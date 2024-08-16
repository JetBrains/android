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

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InspectionsTest {

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.inMemory().withKotlin())
  private val fixture
    get() = projectRule.fixture

  @Test
  fun testNeedsComposableInspection() {
    fixture.enableInspections(
      ComposePreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry
    )

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      @Composable
      @Preview
      fun Preview1() {
      }

      // Missing Composable annotation
      @Preview(name = "preview2", apiLevel = 12)
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "9: Preview only works with Composable functions",
      fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber(),
    )
  }

  @Test
  fun testNoParametersInPreview() {
    fixture.enableInspections(
      PreviewAnnotationInFunctionWithParametersInspection() as InspectionProfileEntry
    )

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $PREVIEW_TOOLING_PACKAGE.PreviewParameter
      import $PREVIEW_TOOLING_PACKAGE.PreviewParameterProvider
      import $COMPOSABLE_ANNOTATION_FQN

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

      class IntProvider: PreviewParameterProvider<Int> {
          override val values: Sequence<Int> = sequenceOf(1, 2)
      }

      @Preview
      @Composable
      fun PreviewWithProvider(@PreviewParameter(IntProvider::class) a: Int) {
      }

      @Preview
      @Composable
      fun PreviewWithProviderAndDefault(@PreviewParameter(IntProvider::class) a: Int, b: String = "hello") {
      }

      @Preview
      @Composable
      fun PreviewWithProviderAndNoDefault(@PreviewParameter(IntProvider::class) a: Int, b: String) { // ERROR, no default in second parameter
      }

      annotation class MyEmptyAnnotation

      @MyEmptyAnnotation
      @Composable
      fun NotMultiPreview(a: Int){ // No error, because MyEmptyAnnotation is not a MultiPreview, as it doesn't provide Previews
      }

      @Preview
      annotation class MyAnnotation

      @MyAnnotation
      @Composable
      fun MultiPreviewWithProviderAndDefault(@PreviewParameter(IntProvider::class) a: Int, b: String = "hello") {
      }

      @MyAnnotation
      @Composable
      fun MultiPreviewWithProviderAndNoDefault(@PreviewParameter(IntProvider::class) a: Int, b: String) { // ERROR, no default in second parameter
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        // Filter out UNRESOLVED_REFERENCE caused by the standard library not being available.
        // sequence and sequenceOf are not available. We can safely ignore them.
        .filter { !it.description.contains("[UNRESOLVED_REFERENCE]") }
        .sortedByDescending { -it.startOffset }
        .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """5: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter
        |15: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter
        |34: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter
        |54: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter"""
        .trimMargin(),
      inspections,
    )
  }

  @Test
  fun testNoMultipleParameterProvider() {
    fixture.enableInspections(
      PreviewMultipleParameterProvidersInspection() as InspectionProfileEntry
    )

    @Suppress("TestFunctionName", "ClassName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $PREVIEW_TOOLING_PACKAGE.PreviewParameter
      import $PREVIEW_TOOLING_PACKAGE.PreviewParameterProvider
      import $COMPOSABLE_ANNOTATION_FQN

      class IntProvider: PreviewParameterProvider<Int> {
          override val values: Sequence<Int> = sequenceOf(1, 2)
      }

      @Preview
      @Composable
      fun PreviewWithMultipleProviders(@PreviewParameter(IntProvider::class) a: Int,
                                       @PreviewParameter(IntProvider::class) b: Int) { // ERROR, only one PreviewParameter is supported
      }

      @Preview
      annotation class MyAnnotation

      @MyAnnotation
      @Composable
      fun MultiPreviewWithMultipleProviders(@PreviewParameter(IntProvider::class) a: Int,
                                            @PreviewParameter(IntProvider::class) b: Int) { // ERROR, only one PreviewParameter is supported
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        // Filter out UNRESOLVED_REFERENCE caused by the standard library not being available.
        // sequence and sequenceOf are not available. We can safely ignore them.
        .filter { !it.description.contains("[UNRESOLVED_REFERENCE]") }
        .sortedByDescending { -it.startOffset }
        .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """12: Multiple @PreviewParameter are not allowed
        |21: Multiple @PreviewParameter are not allowed
      """
        .trimMargin(),
      inspections,
    )
  }

  @Test
  fun testPreviewMustBeTopLevel() {
    fixture.enableInspections(ComposePreviewMustBeTopLevelFunction() as InspectionProfileEntry)

    @Suppress("TestFunctionName", "ClassName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      annotation class MyEmptyAnnotation

      @Preview
      annotation class MyAnnotation

      @Composable
      @MyEmptyAnnotation
      @MyAnnotation
      @Preview(name = "top level preview")
      fun TopLevelPreview() {
        @Composable
        @MyEmptyAnnotation
        @MyAnnotation // ERROR
        @Preview(name = "not a top level preview") // ERROR
        fun NotTopLevelFunctionPreview() {
            @Composable
            @MyEmptyAnnotation
            @MyAnnotation // ERROR
            @Preview(name = "not a top level preview, with a lot of nesting") // ERROR
            fun SuperNestedPreview() {
            }
        }
      }

      class aClass {
        @Preview(name = "preview2", apiLevel = 12)
        @MyEmptyAnnotation
        @MyAnnotation
        @Composable
        fun ClassMethodPreview() {
          @Composable
          @MyEmptyAnnotation
          @MyAnnotation // ERROR
          @Preview(name = "not a top level preview in a class") // ERROR
          fun NotTopLevelFunctionPreviewInAClass() {
          }
        }

        @Preview(name = "preview in a class with default constructor")
        @MyEmptyAnnotation
        @MyAnnotation
        @Composable
        private fun PrivateClassMethodPreview() {
        }
      }

      private class privateClass {
        class NotTopLevelClass {
          @Preview("in a non top level class") // ERROR
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
        @Preview("in a class with parameters") // ERROR
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
      """15: Preview must be a top level declaration or in a top level class with a default constructor.
                    |16: Preview must be a top level declaration or in a top level class with a default constructor.
                    |20: Preview must be a top level declaration or in a top level class with a default constructor.
                    |21: Preview must be a top level declaration or in a top level class with a default constructor.
                    |35: Preview must be a top level declaration or in a top level class with a default constructor.
                    |36: Preview must be a top level declaration or in a top level class with a default constructor.
                    |51: Preview must be a top level declaration or in a top level class with a default constructor.
                    |53: Preview must be a top level declaration or in a top level class with a default constructor.
                    |68: Preview must be a top level declaration or in a top level class with a default constructor.
                    |70: Preview must be a top level declaration or in a top level class with a default constructor."""
        .trimMargin(),
      inspections,
    )
  }

  @Test
  fun testWidthShouldntExceedApiLimit() {
    fixture.enableInspections(ComposePreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      private const val badWidth = 3000

      private const val goodWidth = 2000

      @Preview(widthDp = badWidth) // warning
      annotation class BadAnnotation

      @Preview(widthDp = 2000)
      annotation class GoodAnnotation(val widthDp: Int = 2001) // MultiPreview annotation parameters have no effect

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 1", heightDp = 2001, widthDp = 2001) // Only one warning
      fun Preview1() {
      }

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 2", widthDp = goodWidth)
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
  fun testHeightShouldntExceedApiLimit() {
    fixture.enableInspections(ComposePreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      private const val badHeight = 3000

      private const val goodHeight = 2000

      @Preview(heightDp = badHeight) // warning
      annotation class BadAnnotation

      @Preview(heightDp = 2000)
      annotation class GoodAnnotation(val heightDp: Int = 2001) // MultiPreview annotation parameters have no effect

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 1", heightDp = 2001, widthDp = 2001) // Only one warning
      fun Preview1() {
      }

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 2", heightDp = goodHeight)
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
  fun testOnlyParametersAndValuesAreHighlighted() {
    fixture.enableInspections(ComposePreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      @Composable
      @Preview(name = "Preview 1", heightDp = 2001, widthDp = 2001)
      fun Preview1() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.WARNING)
        .sortedByDescending { -it.startOffset }
        .toTypedArray()

    // Verify the height inspection only highlights the height parameter and value, i.e. "heightDp =
    // 2001"
    val heightInspection = inspections[0]
    var highlightLength = heightInspection.actualEndOffset - heightInspection.actualStartOffset
    assertEquals("heightDp = 2001".length, highlightLength)
  }

  @Test
  fun testNonPositiveFontScale() {
    fixture.enableInspections(PreviewFontScaleMustBeGreaterThanZero() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      private const val badFontScale = 0f

      private const val goodFontScale = 2f

      @Preview(fontScale = badFontScale) // error
      annotation class BadAnnotation

      @Preview(fontScale = 1f)
      annotation class GoodAnnotation(val fontScale: Float = 0f) // MultiPreview annotation parameters have no effect

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 1", fontScale = goodFontScale)
      fun Preview1() {
      }

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 2", fontScale = -2f) // error
      fun Preview2() {
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
      """7: Preview fontScale value must be greater than zero
        |21: Preview fontScale value must be greater than zero
      """
        .trimMargin(),
      inspections,
    )
  }

  @Test
  fun testInvalidApiLevel() {
    fixture.enableInspections(PreviewApiLevelMustBeValid() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      private const val badApiLevel = 0

      private const val goodApiLevel = 30

      @Preview(apiLevel = badApiLevel) // error
      annotation class BadAnnotation

      @Preview(apiLevel = 30)
      annotation class GoodAnnotation(val apiLevel: Int = 0) // MultiPreview annotation parameters have no effect

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 1", apiLevel = goodApiLevel)
      fun Preview1() {
      }

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 2", apiLevel = -1) // error
      fun Preview2() {
      }

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 3", apiLevel = 1000000) // error
      fun Preview3() {
      }

      @Composable
      @Preview(name = "Preview 4", apiLevel = 30)
      fun Preview4() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        .sortedByDescending { -it.startOffset }
        .map { it.descriptionWithLineNumber() }

    val apiLevelErrorMessagePrefix = "Preview apiLevel must be set to an integer between "
    assertEquals(3, inspections.size)
    assertTrue(inspections[0].startsWith("7: $apiLevelErrorMessagePrefix")) // BadAnnotation error
    assertTrue(inspections[1].startsWith("21: $apiLevelErrorMessagePrefix")) // Preview 2 error
    assertTrue(inspections[2].startsWith("27: $apiLevelErrorMessagePrefix")) // Preview 3 error
  }

  @Test
  fun testInspectionsWithNoImport() {
    fixture.enableInspections(
      ComposePreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry
    )

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQN

      @Composable
      @$PREVIEW_TOOLING_PACKAGE.Preview
      fun Preview1() {
      }

      // Missing Composable annotation
      @$PREVIEW_TOOLING_PACKAGE.Preview(name = "preview2")
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "8: Preview only works with Composable functions",
      fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber(),
    )
  }

  @Test
  fun testPreviewCalledRecursively() {
    fixture.enableInspections(PreviewShouldNotBeCalledRecursively() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQN

      @Composable
      @$PREVIEW_TOOLING_PACKAGE.Preview
      fun Preview1() {
        ComposableWrapper {
          Preview1()
        }
      }

      // No preview annotation, i.e. regular composable
      @Composable
      fun Composable1() {
        ComposableWrapper {
          Composable1()
        }
      }

      @Composable
      fun ComposableWrapper(content: @Composable () -> Unit) {
        content()
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "6: Preview functions usually don't call themselves recursively," +
        " so please double-check you're calling the intended function",
      fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).single().descriptionWithLineNumber(),
    )
  }

  @Test
  fun testInvalidLegacyDeviceSpec() {
    fixture.enableInspections(PreviewDeviceShouldUseNewSpec() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      // Phone constant with old spec
      const val PHONE = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420"
      // Foldable with new spec
      const val FOLDABLE = "spec:width=673dp,height=841dp"

      @Composable
      @Preview(name = "Preview 1", device = PHONE)
      fun Preview1() {
      }

      @Composable
      @Preview(name = "Preview 2", device = FOLDABLE)
      fun Preview2() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections =
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        .sortedByDescending { -it.startOffset }
        .map { it.descriptionWithLineNumber() }

    assertEquals(1, inspections.size)
    assertEquals(
      "9: This constant uses a legacy device spec, which is no longer supported",
      inspections[0],
    ) // Preview 1 error

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContentAfterFix =
      """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      // Phone constant with old spec
      const val PHONE = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420"
      // Foldable with new spec
      const val FOLDABLE = "spec:width=673dp,height=841dp"

      @Composable
      @Preview(name = "Preview 1", device = "spec:width=411dp,height=891dp")
      fun Preview1() {
      }

      @Composable
      @Preview(name = "Preview 2", device = FOLDABLE)
      fun Preview2() {
      }
    """
        .trimIndent()
    val quickFix =
      QuickFixWrapper.unwrap(fixture.getAllQuickFixes().single()) as LocalQuickFixOnPsiElement
    ApplicationManager.getApplication().invokeAndWait {
      CommandProcessor.getInstance()
        .executeCommand(
          fixture.project,
          { runWriteAction { quickFix.applyFix() } },
          "Replace with new device spec",
          null,
        )
    }
    fixture.checkResult(fileContentAfterFix)
  }
}
