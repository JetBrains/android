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
import com.android.tools.idea.compose.preview.util.DisplayPositioning
import com.android.tools.idea.compose.preview.util.ParametrizedPreviewElementTemplate
import com.android.tools.idea.compose.preview.util.PreviewDisplaySettings
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.UNDEFINED_API_LEVEL
import com.android.tools.idea.compose.preview.util.UNDEFINED_DIMENSION
import com.android.tools.idea.compose.preview.util.sortByDisplayAndSourcePosition
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Asserts that the given [methodName] body has the actual given [actualBodyRange]
 */
private fun assertMethodTextRange(file: PsiFile, methodName: String, actualBodyRange: TextRange) {
  val range = ReadAction.compute<TextRange, Throwable> {
    file
      .toUElementOfType<UFile>()
      ?.method(methodName)
      ?.uastBody
      ?.sourcePsi
      ?.textRange!!
  }
  assertNotEquals(range, TextRange.EMPTY_RANGE)
  assertEquals(range, actualBodyRange)
}

private fun <T> computeOnBackground(computable: () -> T): T =
  AppExecutorUtil.getAppExecutorService().submit(computable).get()

@RunWith(Parameterized::class)
class AnnotationFilePreviewElementFinderTest(previewAnnotationPackage: String, composableAnnotationPackage: String) {
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
  private val project get() = projectRule.project
  private val fixture get() = projectRule.fixture

  @Test
  fun testFindPreviewAnnotations() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import $PREVIEW_TOOLING_PACKAGE.Devices
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12, group = "groupA", showBackground = true)
        fun Preview2() {
        }

        @Composable
        @Preview(name = "preview3", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecoration = true, device = Devices.NEXUS_7)
        fun Preview3() {
        }

        @Composable
        @Preview(name = "preview4", uiMode = 3, backgroundColor = 0xBAAABA)
        fun Preview4() {
        }

        @Composable
        @Preview(name = "preview5", uiMode = 3, backgroundColor = 0xFFBAAABA)
        fun Preview5() {
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
        @$PREVIEW_TOOLING_PACKAGE.Preview(name = "FQN")
        fun FullyQualifiedAnnotationPreview() {

        }
      """.trimIndent())

    // Add secondary file with Previews that should not be found when looking into composeFile
    val otherFile = fixture.addFileToProjectAndInvalidate(
      "src/OtherFile.kt",
      // language=kotlin
      """
        import $PREVIEW_TOOLING_PACKAGE.Devices
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview
        fun OtherFilePreview1() {
        }

        @Composable
        @Preview
        fun OtherFilePreview2() {
        }
      """.trimIndent())

    assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.virtualFile))
    assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, otherFile.virtualFile))
    assertTrue(computeOnBackground { AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.virtualFile) })

    val elements = computeOnBackground { AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile).toList() }
    assertEquals(7, elements.size)
    elements[0].let {
      assertEquals("Preview1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview1", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals("@Preview", it.previewElementDefinitionPsi?.element?.text)
      }
    }

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
      assertEquals(0, it.configuration.uiMode)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview2", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals("@Preview(name = \"preview2\", apiLevel = 12, group = \"groupA\", showBackground = true)",
                     it.previewElementDefinitionPsi?.element?.text)
      }
    }

    elements[2].let {
      assertEquals("preview3", it.displaySettings.name)
      assertNull(it.displaySettings.group)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)
      assertEquals(0.2f, it.configuration.fontScale)
      assertFalse(it.displaySettings.showBackground)
      assertTrue(it.displaySettings.showDecoration)
      assertEquals(0, it.configuration.uiMode)
      assertEquals("id:Nexus 7", it.configuration.deviceSpec)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview3", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals(
          "@Preview(name = \"preview3\", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecoration = true, device = Devices.NEXUS_7)",
          it.previewElementDefinitionPsi?.element?.text)
      }
    }

    elements[3].let {
      assertEquals("preview4", it.displaySettings.name)
      assertEquals(3, it.configuration.uiMode)
      assertEquals("#baaaba", it.displaySettings.backgroundColor)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview4", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals("@Preview(name = \"preview4\", uiMode = 3, backgroundColor = 0xBAAABA)",
                     it.previewElementDefinitionPsi?.element?.text)
      }
    }

    elements[4].let {
      assertEquals("preview5", it.displaySettings.name)
      assertEquals(3, it.configuration.uiMode)
      assertEquals("#ffbaaaba", it.displaySettings.backgroundColor)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview5", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals("@Preview(name = \"preview5\", uiMode = 3, backgroundColor = 0xFFBAAABA)",
                     it.previewElementDefinitionPsi?.element?.text)
      }
    }

    elements[5].let {
      assertEquals("Preview with parameters", it.displaySettings.name)
    }

    elements[6].let {
      assertEquals("FQN", it.displaySettings.name)
    }
  }

  @Test
  fun testFindPreviewAnnotationsWithoutImport() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @$PREVIEW_TOOLING_PACKAGE.Preview
        fun Preview1() {
        }
      """.trimIndent())

    assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.virtualFile))

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile)
    elements.single().run {
      assertEquals("TestKt.Preview1", composableMethodFqn)
    }
  }

  @Test
  fun testNoDuplicatePreviewElements() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview(name = "preview", apiLevel = 12)
        @Preview(name = "preview", apiLevel = 12)
        fun Preview1() {
        }
      """.trimIndent())

    val element = AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile).single()
    // Check that we keep the first element
    assertEquals("preview", element.displaySettings.name)
  }

  @Test
  fun testFindPreviewPackage() {
    fixture.addFileToProjectAndInvalidate(
      "com/android/notpreview/Preview.kt",
      // language=kotlin
      """
        package com.android.notpreview

        annotation class Preview(val name: String = "",
                                 val apiLevel: Int = -1,
                                 val theme: String = "",
                                 val widthDp: Int = -1,
                                 val heightDp: Int = -1)
       """.trimIndent())

    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import com.android.notpreview.Preview
        import $COMPOSABLE_ANNOTATION_FQN

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

    assertEquals(0, AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile).count())
  }

  /**
   * Ensures that calling findPreviewMethods returns an empty. Although the method is guaranteed to be called under smart mode,
   *
   */
  @Test
  fun testDumbMode() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import $COMPOSABLE_ANNOTATION_FQN
        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12)
        fun Preview1() {
        }
      """.trimIndent())

    runInEdtAndWait {
      DumbServiceImpl.getInstance(project).isDumb = true
      try {
        val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile)
        assertEquals(0, elements.count())
        assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.virtualFile))
      }
      finally {
        DumbServiceImpl.getInstance(project).isDumb = false
      }
    }
  }

  @Test
  fun testPreviewParameters() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        package test

        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $PREVIEW_TOOLING_PACKAGE.PreviewParameter
        import $PREVIEW_TOOLING_PACKAGE.PreviewParameterProvider
        import $COMPOSABLE_ANNOTATION_FQN

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
      """.trimIndent())

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile).toList()
    elements[0].let {
      assertFalse(it is ParametrizedPreviewElementTemplate)
      assertEquals("NoParameter", it.displaySettings.name)
    }
    // The next two are the same just using the annotation parameter explicitly in one of them.
    // The resulting PreviewElement should be the same with different name.
    listOf("SingleParameter" to elements[1], "SingleParameterNoName" to elements[2])
      .map { (name, previewElement) -> name to previewElement as ParametrizedPreviewElementTemplate }
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

  @Test
  fun testOrdering() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview
        fun C() {
        }

        @Composable
        @Preview
        fun A() {
        }

        @Composable
        @Preview
        fun TopA() {
        }

        @Composable
        @Preview
        fun B() {
        }

        @Composable
        @Preview
        fun TopB() {
        }
      """.trimIndent())

    ReadAction.run<Throwable> {
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile)
        .toMutableList().apply {
          // Randomize to make sure the ordering works
          shuffle()
        }
        .map {
          // Override positioning for testing for those preview starting with Top
          object : PreviewElement by it {
            override val displaySettings: PreviewDisplaySettings =
              PreviewDisplaySettings(
                it.displaySettings.name,
                it.displaySettings.group,
                it.displaySettings.showDecoration,
                it.displaySettings.showBackground,
                it.displaySettings.backgroundColor,
                if (it.displaySettings.name.startsWith("Top")) DisplayPositioning.TOP else it.displaySettings.displayPositioning)
          }
        }
        .sortByDisplayAndSourcePosition()
        .map { it.composableMethodFqn }
        .toTypedArray()
        .let {
          assertArrayEquals(arrayOf("TestKt.TopA", "TestKt.TopB", "TestKt.C", "TestKt.A", "TestKt.B"), it)
        }
    }
  }

  @Test
  fun testRepeatedPreviewAnnotations() {
    val composeTest = fixture.addFileToProjectAndInvalidate(
      "src/Test.kt",
      // language=kotlin
      """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @Preview(name = "preview1", widthDp = 2)
        @Preview(name = "preview2", group = "groupA")
        fun Preview1() {
        }
      """.trimIndent())

    assertTrue(AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.virtualFile))
    assertTrue(computeOnBackground { AnnotationFilePreviewElementFinder.hasPreviewMethods(project, composeTest.virtualFile) })

    val elements = computeOnBackground { AnnotationFilePreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile).toList() }
    assertEquals(2, elements.size)
    elements[0].let {
      assertEquals("preview1", it.displaySettings.name)
      assertEquals(2, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview1", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals("@Preview(name = \"preview1\", widthDp = 2)",
                     it.previewElementDefinitionPsi?.element?.text)
      }
    }

    elements[1].let {
      assertEquals("preview2", it.displaySettings.name)
      assertEquals("groupA", it.displaySettings.group)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      ReadAction.run<Throwable> {
        assertMethodTextRange(composeTest, "Preview1", it.previewBodyPsi?.psiRange?.range!!)
        assertEquals("@Preview(name = \"preview2\", group = \"groupA\")",
                     it.previewElementDefinitionPsi?.element?.text)
      }
    }
  }
}