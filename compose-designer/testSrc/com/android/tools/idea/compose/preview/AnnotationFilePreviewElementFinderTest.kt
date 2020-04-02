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

import com.android.tools.idea.compose.preview.util.ParametrizedPreviewElementTemplate
import com.android.tools.idea.compose.preview.util.UNDEFINED_API_LEVEL
import com.android.tools.idea.compose.preview.util.UNDEFINED_DIMENSION
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import org.junit.Assert

/**
 * Asserts that the given [methodName] body has the actual given [actualBodyRange]
 */
private fun assertMethodTextRange(file: UFile, methodName: String, actualBodyRange: TextRange) {
  val range = file
    .method(methodName)
    ?.uastBody
    ?.sourcePsi
    ?.textRange!!
  Assert.assertNotEquals(range, TextRange.EMPTY_RANGE)
  Assert.assertEquals(range, actualBodyRange)
}

private fun <T> computeOnBackground(computable: () -> T): T =
  AppExecutorUtil.getAppExecutorService().submit(computable).get()

class AnnotationFilePreviewElementFinderTest : ComposeLightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.override(true)
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.COMPOSE_PREVIEW_DATA_SOURCES.clearOverride()
  }

  fun testFindPreviewAnnotations() {
    val composeTest = myFixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        import androidx.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12, group = "groupA", showBackground = true)
        fun Preview2() {
        }

        @Composable
        @Preview(name = "preview3", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecoration = true)
        fun Preview3() {
        }

        // This preview element will be found but the ComposeViewAdapter won't be able to render it
        @Composable
        @Preview(name = "Preview with parameters")
        fun PreviewWithParametrs(i: Int) {
        }

        @Composable
        fun NoPreviewComposable() {

        }

        @Preview
        fun NoComposablePreview() {

        }

        @Composable
        @androidx.ui.tooling.preview.Preview(name = "FQN")
        fun FullyQualifiedAnnotationPreview() {

        }
      """.trimIndent()).toUElement() as UFile

    assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.sourcePsi.virtualFile))
    assertTrue(computeOnBackground { AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.sourcePsi.virtualFile) })

    val elements = computeOnBackground { AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest).toList() }
    assertEquals(5, elements.size)
    elements[1].let {
      assertEquals("preview2", it.displaySettings.name)
      assertEquals("groupA", it.displaySettings.group)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertEquals(1f, it.configuration.fontScale)
      assertTrue(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      assertMethodTextRange(composeTest, "Preview2", it.previewBodyPsi?.psiRange?.range!!)
      assertEquals("@Preview(name = \"preview2\", apiLevel = 12, group = \"groupA\", showBackground = true)",
                   it.previewElementDefinitionPsi?.element?.text)
    }

    elements[2].let {
      assertEquals("preview3", it.displaySettings.name)
      assertNull(it.displaySettings.group)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)
      assertEquals(0.2f, it.configuration.fontScale)
      assertFalse(it.displaySettings.showBackground)
      assertTrue(it.displaySettings.showDecoration)

      assertMethodTextRange(composeTest, "Preview3", it.previewBodyPsi?.psiRange?.range!!)
      assertEquals("@Preview(name = \"preview3\", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecoration = true)",
                   it.previewElementDefinitionPsi?.element?.text)
    }

    elements[0].let {
      assertEquals("Preview1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      assertMethodTextRange(composeTest, "Preview1", it.previewBodyPsi?.psiRange?.range!!)
      assertEquals("@Preview", it.previewElementDefinitionPsi?.element?.text)
    }

    elements[3].let {
      assertEquals("Preview with parameters", it.displaySettings.name)
    }

    elements[4].let {
      assertEquals("FQN", it.displaySettings.name)
    }
  }

  fun testFindPreviewAnnotationsWithoutImport() {
    val composeTest = myFixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        import androidx.compose.Composable

        @Composable
        @androidx.ui.tooling.preview.Preview
        fun Preview1() {
        }
      """.trimIndent()).toUElement() as UFile

    assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.sourcePsi.virtualFile))

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest)
    elements.single().run {
      assertEquals("TestKt.Preview1", composableMethodFqn)
    }
  }

  fun testNoDuplicatePreviewElements() {
    val composeTest = myFixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        import androidx.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12)
        fun Preview1() {
        }
      """.trimIndent()).toUElement() as UFile

    val element = AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest).single()
    // Check that we keep the first element
    assertEquals("Preview1", element.displaySettings.name)
  }

  fun testFindPreviewPackage() {
    myFixture.addFileToProject(
      "src/com/android/notpreview/Preview.kt",
      // language=kotlin
      """
        package com.android.notpreview

        annotation class Preview(val name: String = "",
                                 val apiLevel: Int = -1,
                                 val theme: String = "",
                                 val widthDp: Int = -1,
                                 val heightDp: Int = -1)
       """.trimIndent())

    val composeTest = myFixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        import com.android.notpreview.Preview
        import androidx.compose.Composable

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12)
        fun Preview2() {
        }

        @Composable
        @Preview(name = "preview3", width = 1, height = 2)
        fun Preview3() {
        }
      """.trimIndent())

    assertEquals(0, AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile).count())
  }

  /**
   * Ensures that calling findPreviewMethods returns an empty. Although the method is guaranteed to be called under smart mode,
   *
   */
  fun testDumbMode() {
    val composeTest = myFixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        import androidx.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12)
        fun Preview1() {
        }
      """.trimIndent()).toUElement() as UFile

    DumbServiceImpl.getInstance(myFixture.project).isDumb = true
    try {
      val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest)
      assertEquals(0, elements.count())
    }
    finally {
      DumbServiceImpl.getInstance(myFixture.project).isDumb = false
    }
  }

  fun testPreviewParameters() {
    val composeTest = myFixture.addFileToProject(
      "src/Test.kt",
      // language=kotlin
      """
        package test

        import androidx.ui.tooling.preview.Preview
        import androidx.ui.tooling.preview.PreviewParameter
        import androidx.ui.tooling.preview.PreviewParameterProvider
        import androidx.compose.Composable

        @Composable
        @Preview
        fun NoParameter() {
        }

        class TestStringProvider: PreviewParameterProvider<String> {
            override val values: Sequence<String> = sequenceOf("A", "B", "C")
        }

        class TestIntProvider: PreviewParameterProvider<Int> {
            override val values: Sequence<String> = sequenceOf(1, 2)
        }

        @Composable
        @Preview
        fun SingleParameter(@PreviewParameter(provider = TestStringProvider::class) aString: String) {
        }

        @Composable
        @Preview
        // Same as SingleParameter but without using "provider" in the annotation
        fun SingleParameterNoName(@PreviewParameter(TestStringProvider::class) aString: String) {
        }

        @Composable
        @Preview
        fun MultiParameter(@PreviewParameter(provider = TestStringProvider::class) aString: String,
                           @PreviewParameter(provider = TestIntProvider::class, limit = 2) aInt: Int) {
        }
      """.trimIndent()).toUElement() as UFile

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest).toList()
    elements[0].let {
      assertFalse(it is ParametrizedPreviewElementTemplate)
      assertEquals("NoParameter", it.displaySettings.name)
    }
    // The next two are the same just using the annotation parameter explicitly in one of them.
    // The resulting PreviewElement should be the same with different name.
    listOf("SingleParameter" to elements[1], "SingleParameterNoName" to elements[2])
      .map { (name, previewElement) -> name to previewElement as ParametrizedPreviewElementTemplate}
      .forEach { (name, previewElement) ->
        assertEquals(name, previewElement.displaySettings.name)
        assertEquals(1, previewElement.parameterProviders.size)
        previewElement.parameterProviders.single { param -> "aString" == param.name }.let { parameter ->
          assertEquals("test.TestStringProvider", parameter.providerClassFqn)
          assertEquals(0, parameter.index)
          assertEquals(Int.MAX_VALUE, parameter.limit)
        }
      }
    (elements[3] as ParametrizedPreviewElementTemplate).let {
      assertEquals("MultiParameter", it.displaySettings.name)
      assertEquals(2, it.parameterProviders.size)
      it.parameterProviders.single { param -> "aInt" == param.name }.let { parameter ->
        assertEquals("test.TestIntProvider", parameter.providerClassFqn)
        assertEquals(1, parameter.index)
        assertEquals(2, parameter.limit)
      }
    }
  }
}