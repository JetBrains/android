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
import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.containers.toArray
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class InspectionsTest(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val COMPOSABLE_ANNOTATION_FQN = "$composableAnnotationPackage.Composable"
  private val PREVIEW_TOOLING_PACKAGE = previewAnnotationPackage

  @get:Rule
  val projectRule = ComposeProjectRule(previewAnnotationPackage = previewAnnotationPackage,
                                       composableAnnotationPackage = composableAnnotationPackage)
  private val fixture get() = projectRule.fixture

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_MULTIPREVIEW.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_MULTIPREVIEW.clearOverride()
  }

  @Test
  fun testNeedsComposableInspection() {
    fixture.enableInspections(PreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
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
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals("9: Preview only works with Composable functions.",
                 fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber())
  }

  @Test
  fun testNoParametersInPreview() {
    fixture.enableInspections(PreviewAnnotationInFunctionWithParametersInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
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
          override val values: Sequence<String> = sequenceOf(1, 2)
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
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.ERROR)
      // Filter out UNRESOLVED_REFERENCE caused by the standard library not being available.
      // sequence and sequenceOf are not available. We can safely ignore them.
      .filter { !it.description.contains("[UNRESOLVED_REFERENCE]") }
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }


    assertEquals(
      """5: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter.
        |15: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter.
        |34: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter.
        |54: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter.""".trimMargin(),
      inspections)
  }

  @Test
  fun testNoMultipleParameterProvider() {
    fixture.enableInspections(PreviewMultipleParameterProvidersInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName", "ClassName")
    @Language("kotlin")
    val fileContent = """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $PREVIEW_TOOLING_PACKAGE.PreviewParameter
      import $PREVIEW_TOOLING_PACKAGE.PreviewParameterProvider
      import $COMPOSABLE_ANNOTATION_FQN

      class IntProvider: PreviewParameterProvider<Int> {
          override val values: Sequence<String> = sequenceOf(1, 2)
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
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.ERROR)
      // Filter out UNRESOLVED_REFERENCE caused by the standard library not being available.
      // sequence and sequenceOf are not available. We can safely ignore them.
      .filter { !it.description.contains("[UNRESOLVED_REFERENCE]") }
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }


    assertEquals(
      """12: Multiple @PreviewParameter are not allowed.
        |21: Multiple @PreviewParameter are not allowed.
      """.trimMargin(), inspections)
  }

  @Test
  fun testPreviewMustBeTopLevel() {
    fixture.enableInspections(PreviewMustBeTopLevelFunction() as InspectionProfileEntry)

    @Suppress("TestFunctionName", "ClassName")
    @Language("kotlin")
    val fileContent = """
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
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.ERROR)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals("""15: Preview must be a top level declarations or in a top level class with a default constructor.
                    |16: Preview must be a top level declarations or in a top level class with a default constructor.
                    |20: Preview must be a top level declarations or in a top level class with a default constructor.
                    |21: Preview must be a top level declarations or in a top level class with a default constructor.
                    |35: Preview must be a top level declarations or in a top level class with a default constructor.
                    |36: Preview must be a top level declarations or in a top level class with a default constructor.
                    |51: Preview must be a top level declarations or in a top level class with a default constructor.
                    |53: Preview must be a top level declarations or in a top level class with a default constructor.
                    |68: Preview must be a top level declarations or in a top level class with a default constructor.
                    |70: Preview must be a top level declarations or in a top level class with a default constructor.""".trimMargin(),
                 inspections)
  }

  @Test
  fun testWidthShouldntExceedApiLimit() {
    fixture.enableInspections(PreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      @Preview(widthDp = 2001) // warning
      annotation class BadAnnotation

      @Preview(widthDp = 2000)
      annotation class GoodAnnotation(val widthDp: Int = 2001) // MultiPreview annotation parameters have no effect

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 1", widthDp = 2001) // warning
      fun Preview1() {
      }

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 2", widthDp = 2000)
      fun Preview2() {
      }
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.WARNING)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """3: Preview width is limited to 2,000. Setting a higher number will not increase the preview width.
        |11: Preview width is limited to 2,000. Setting a higher number will not increase the preview width.
      """.trimMargin(), inspections)
  }

  @Test
  fun testHeightShouldntExceedApiLimit() {
    fixture.enableInspections(PreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      @Preview(heightDp = 2001) // warning
      annotation class BadAnnotation

      @Preview(heightDp = 2000)
      annotation class GoodAnnotation(val heightDp: Int = 2001) // MultiPreview annotation parameters have no effect

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 1", heightDp = 2001) // Warning
      fun Preview1() {
      }

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 2", heightDp = 2000)
      fun Preview2() {
      }
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.WARNING)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """3: Preview height is limited to 2,000. Setting a higher number will not increase the preview height.
        |11: Preview height is limited to 2,000. Setting a higher number will not increase the preview height.
      """.trimMargin(), inspections)
  }

  @Test
  fun testOnlyParametersAndValuesAreHighlighted() {
    fixture.enableInspections(PreviewDimensionRespectsLimit() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      @Composable
      @Preview(name = "Preview 1", heightDp = 2001, widthDp = 2001)
      fun Preview1() {
      }
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.WARNING)
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

  @Test
  fun testNegativeFontScale() {
    fixture.enableInspections(PreviewFontScaleMustBeGreaterThanZero() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN

      @Preview(fontScale = 0f) // error
      annotation class BadAnnotation

      @Preview(fontScale = 1f)
      annotation class GoodAnnotation(val fontScale: Float = 0f) // MultiPreview annotation parameters have no effect

      @Composable
      @BadAnnotation
      @Preview(name = "Preview 1", fontScale = 2f)
      fun Preview1() {
      }

      @Composable
      @GoodAnnotation
      @Preview(name = "Preview 2", fontScale = -2f) // error
      fun Preview2() {
      }
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    val inspections = fixture.doHighlighting(HighlightSeverity.ERROR)
      .sortedByDescending { -it.startOffset }
      .joinToString("\n") { it.descriptionWithLineNumber() }

    assertEquals(
      """3: Preview fontScale value must be greater than zero.
        |17: Preview fontScale value must be greater than zero.
      """.trimMargin(), inspections)
  }

  @Test
  fun testInspectionsWithNoImport() {
    fixture.enableInspections(PreviewNeedsComposableAnnotationInspection() as InspectionProfileEntry)

    @Suppress("TestFunctionName")
    @Language("kotlin")
    val fileContent = """
      import $COMPOSABLE_ANNOTATION_FQN

      @Composable
      @$PREVIEW_TOOLING_PACKAGE.Preview
      fun Preview1() {
      }

      // Missing Composable annotation
      @$PREVIEW_TOOLING_PACKAGE.Preview(name = "preview2")
      fun Preview2() {
      }
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals("8: Preview only works with Composable functions.",
                 fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber())
  }
}