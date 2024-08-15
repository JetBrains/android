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
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.preview.DisplayPositioning
import com.android.tools.preview.ParametrizedComposePreviewElementTemplate
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.UNDEFINED_API_LEVEL
import com.android.tools.preview.UNDEFINED_DIMENSION
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** Asserts that the given [methodName] body has the actual given [actualBodyRange] */
private fun assertMethodTextRange(file: PsiFile, methodName: String, actualBodyRange: TextRange) {
  val range =
    ReadAction.compute<TextRange, Throwable> {
      file.toUElementOfType<UFile>()?.method(methodName)?.uastBody?.sourcePsi?.textRange!!
    }
  assertNotEquals(range, TextRange.EMPTY_RANGE)
  assertEquals(range, actualBodyRange)
}

private fun <T> computeOnBackground(computable: () -> T): T =
  AppExecutorUtil.getAppExecutorService().submit(computable).get()

class AnnotationFileComposePreviewElementFinderTest {

  @get:Rule val projectRule = ComposeProjectRule()
  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Test
  fun testFindPreviewAnnotations() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
        @Preview(name = "preview2", apiLevel = 12, group = "groupA", showBackground = true, locale = "en-rUS")
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

        @[Composable Preview]
        fun Preview6() {
        }

        @Preview(name = "named multipreview")
        annotation class MySubAnnotation() {}

        @MySubAnnotation
        @Preview
        annotation class MyAnnotation() {}

        @MyAnnotation
        @Preview(name = "preview7")
        @Composable
        fun Preview7() {
        }

        // This preview element will be found but the ComposeViewAdapter won't be able to render it
        @Composable
        @Preview(name = "Preview with parameters")
        fun PreviewWithParameters(i: Int) {
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
      """
          .trimIndent(),
      )

    // Add secondary file with Previews that should not be found when looking into composeFile
    val otherFile =
      fixture.addFileToProjectAndInvalidate(
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

        @[Composable Preview]
        fun OtherFilePreview2() {
        }
      """
          .trimIndent(),
      )

    assertTrue(
      AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
    )
    assertTrue(
      AnnotationFilePreviewElementFinder.hasPreviewElements(project, otherFile.virtualFile)
    )
    assertTrue(
      computeOnBackground {
        runBlocking {
          AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
        }
      }
    )

    val elements =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()
    assertEquals(11, elements.size)
    elements[0].let {
      assertEquals("Preview1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@Preview", it.previewElementDefinition?.element?.text)
      }
    }

    elements[1].let {
      assertEquals("Preview2 - preview2", it.displaySettings.name)
      assertEquals("groupA", it.displaySettings.group)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertEquals("en-rUS", it.configuration.locale)
      assertEquals(1f, it.configuration.fontScale)
      assertTrue(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)
      assertEquals(0, it.configuration.uiMode)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview2",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals(
          "@Preview(name = \"preview2\", apiLevel = 12, group = \"groupA\", showBackground = true, locale = \"en-rUS\")",
          it.previewElementDefinition?.element?.text,
        )
      }
    }

    elements[2].let {
      assertEquals("Preview3 - preview3", it.displaySettings.name)
      assertNull(it.displaySettings.group)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)
      assertEquals("", it.configuration.locale)
      assertEquals(0.2f, it.configuration.fontScale)
      assertFalse(it.displaySettings.showBackground)
      assertTrue(it.displaySettings.showDecoration)
      assertEquals(0, it.configuration.uiMode)
      assertEquals("id:Nexus 7", it.configuration.deviceSpec)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview3",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals(
          "@Preview(name = \"preview3\", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecoration = true, device = Devices.NEXUS_7)",
          it.previewElementDefinition?.element?.text,
        )
      }
    }

    elements[3].let {
      assertEquals("Preview4 - preview4", it.displaySettings.name)
      assertEquals(3, it.configuration.uiMode)
      assertEquals("#baaaba", it.displaySettings.backgroundColor)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview4",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals(
          "@Preview(name = \"preview4\", uiMode = 3, backgroundColor = 0xBAAABA)",
          it.previewElementDefinition?.element?.text,
        )
      }
    }

    elements[4].let {
      assertEquals("Preview5 - preview5", it.displaySettings.name)
      assertEquals(3, it.configuration.uiMode)
      assertEquals("#ffbaaaba", it.displaySettings.backgroundColor)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview5",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals(
          "@Preview(name = \"preview5\", uiMode = 3, backgroundColor = 0xFFBAAABA)",
          it.previewElementDefinition?.element?.text,
        )
      }
    }

    elements[5].let {
      assertEquals("Preview6", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview6",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("Preview", it.previewElementDefinition?.element?.text)
      }
    }

    elements[6].let {
      assertEquals("Preview7 - named multipreview", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview7",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@MyAnnotation", it.previewElementDefinition?.element?.text)
      }
    }

    elements[7].let {
      assertEquals("Preview7 - MyAnnotation 1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview7",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@MyAnnotation", it.previewElementDefinition?.element?.text)
      }
    }

    elements[8].let {
      assertEquals("Preview7 - preview7", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview7",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@Preview(name = \"preview7\")", it.previewElementDefinition?.element?.text)
      }
    }

    elements[9].let {
      assertEquals("PreviewWithParameters - Preview with parameters", it.displaySettings.name)
    }

    elements[10].let {
      assertEquals("FullyQualifiedAnnotationPreview - FQN", it.displaySettings.name)
    }
  }

  @Test
  fun testFindMultiPreviewsInAndroidx() = runBlocking {
    // Add 3 files "simulating" them to be from androidx and containing a MultiPreview with a valid
    // package name.
    fixture.addFileToProjectAndInvalidate(
      "src/File1.kt",
      // language=kotlin
      """
        package androidx.preview.valid.pkg

        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        annotation class MyValidAnnotation1
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File2.kt",
      // language=kotlin
      """
        package androidx.valid.preview.pkg

        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        annotation class MyValidAnnotation2
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File3.kt",
      // language=kotlin
      """
        package androidx.valid.pkg.preview

        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        annotation class MyValidAnnotation3
        """
        .trimIndent(),
    )

    // Add 3 files "simulating" them to be from androidx and containing a MultiPreview with an
    // invalid package name.
    fixture.addFileToProjectAndInvalidate(
      "src/File4.kt",
      // language=kotlin
      """
        // Doesn't contain preview
        package androidx.invalid.pkg

        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        annotation class MyInvalidAnnotation1
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File5.kt",
      // language=kotlin
      """
        // 'mypreview' is not valid
        package androidx.invalid.mypreview.pkg

        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        annotation class MyInvalidAnnotation2
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File6.kt",
      // language=kotlin
      """
        // 'pre.view' is not valid
        package androidx.invalid.pre.view.pkg

        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Composable
        @Preview
        annotation class MyInvalidAnnotation3
        """
        .trimIndent(),
    )

    val composeTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        package com.example.test

        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN
        import androidx.preview.valid.pkg.MyValidAnnotation1
        import androidx.valid.preview.pkg.MyValidAnnotation2
        import androidx.valid.pkg.preview.MyValidAnnotation3
        import androidx.invalid.pkg.MyInvalidAnnotation1
        import androidx.invalid.mypreview.pkg.MyInvalidAnnotation2
        import androidx.invalid.pre.view.pkg.MyInvalidAnnotation3

        @Composable
        @Preview
        @MyValidAnnotation1
        @MyValidAnnotation2
        @MyValidAnnotation3
        @MyInvalidAnnotation1
        @MyInvalidAnnotation2
        @MyInvalidAnnotation3
        fun Preview1() {
        }
        """
          .trimIndent(),
      )

    assertTrue(
      AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
    )
    val elements =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()

    assertEquals(4, elements.size)

    elements[0].let {
      assertEquals("Preview1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@Preview", it.previewElementDefinition?.element?.text)
      }
    }

    elements[1].let {
      assertEquals("Preview1 - MyValidAnnotation1 1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@MyValidAnnotation1", it.previewElementDefinition?.element?.text)
      }
    }

    elements[2].let {
      assertEquals("Preview1 - MyValidAnnotation2 1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@MyValidAnnotation2", it.previewElementDefinition?.element?.text)
      }
    }

    elements[3].let {
      assertEquals("Preview1 - MyValidAnnotation3 1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecoration)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals("@MyValidAnnotation3", it.previewElementDefinition?.element?.text)
      }
    }
  }

  @Test
  fun testFindPreviewAnnotationsWithoutImport() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @$PREVIEW_TOOLING_PACKAGE.Preview
        fun Preview1() {
        }

        @[Composable $PREVIEW_TOOLING_PACKAGE.Preview]
        fun Preview2() {
        }
      """
          .trimIndent(),
      )

    assertTrue(
      AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
    )

    val elements =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()
    assertEquals(2, elements.size)

    elements[0].let { assertEquals("TestKt.Preview1", it.methodFqn) }

    elements[1].let { assertEquals("TestKt.Preview2", it.methodFqn) }
  }

  @Test
  fun testNoDuplicatePreviewElements() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
      """
          .trimIndent(),
      )

    val element =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .single()
    // Check that we keep the first element
    assertEquals("Preview1 - preview", element.displaySettings.name)
  }

  @Test
  fun testFindPreviewPackage() = runBlocking {
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
       """
        .trimIndent(),
    )

    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
      """
          .trimIndent(),
      )

    assertEquals(
      0,
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .count(),
    )
  }

  /**
   * Ensures that calling findPreviewMethods returns an empty. Although the method is guaranteed to
   * be called under smart mode,
   */
  @Test
  fun testDumbMode() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
    """
          .trimIndent(),
      )

    val result =
      DumbModeTestUtils.computeInDumbModeSynchronously(project) {
        val hasPreviewElements =
          GlobalScope.async {
            AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
          }
        try {
          runBlocking { withTimeout(2500) { hasPreviewElements.await() } }
          fail("The result should not have been returned in non-smart mode")
        } catch (_: TimeoutCancellationException) {}

        val previewElementsOnNonSmartMode =
          GlobalScope.async {
            AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
          }
        try {
          runBlocking { withTimeout(2500) { previewElementsOnNonSmartMode.await() } }
          fail("The result should not have been returned in non-smart mode")
        } catch (_: TimeoutCancellationException) {}
        previewElementsOnNonSmartMode
      }

    runInEdtAndWait {
      assertTrue(
        runBlocking {
          AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
        }
      )
    }
    assertEquals(2, result.await().size)
  }

  @Test
  fun testPreviewParameters() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
      """
          .trimIndent(),
      )

    val elements =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()
    elements[0].let {
      assertFalse(it is ParametrizedComposePreviewElementTemplate)
      assertEquals("NoParameter", it.displaySettings.name)
    }
    // The next two are the same just using the annotation parameter explicitly in one of them.
    // The resulting PreviewElement should be the same with different name.
    listOf("SingleParameter" to elements[1], "SingleParameterNoName" to elements[2])
      .map { (name, previewElement) ->
        name to previewElement as ParametrizedComposePreviewElementTemplate
      }
      .forEach { (name, previewElement) ->
        assertEquals(name, previewElement.displaySettings.name)
        assertEquals(1, previewElement.parameterProviders.size)
        previewElement.parameterProviders
          .single { param -> "aString" == param.name }
          .let { parameter ->
            assertEquals("test.TestStringProvider", parameter.providerClassFqn)
            assertEquals(0, parameter.index)
            assertEquals(Int.MAX_VALUE, parameter.limit)
          }
      }
    (elements[3] as ParametrizedComposePreviewElementTemplate).let {
      assertEquals("MultiParameter", it.displaySettings.name)
      assertEquals(2, it.parameterProviders.size)
      it.parameterProviders
        .single { param -> "aInt" == param.name }
        .let { parameter ->
          assertEquals("test.TestIntProvider", parameter.providerClassFqn)
          assertEquals(1, parameter.index)
          assertEquals(2, parameter.limit)
        }
    }
  }

  @Test
  fun testOrdering() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
      """
          .trimIndent(),
      )

    AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
      .toMutableList()
      .apply {
        // Randomize to make sure the ordering works
        shuffle()
      }
      .map {
        // Override positioning for testing for those preview starting with Top
        object : PsiComposePreviewElement by it {
          override val displaySettings: PreviewDisplaySettings =
            PreviewDisplaySettings(
              it.displaySettings.name,
              it.displaySettings.baseName,
              it.displaySettings.parameterName,
              it.displaySettings.group,
              it.displaySettings.showDecoration,
              it.displaySettings.showBackground,
              it.displaySettings.backgroundColor,
              if (it.displaySettings.name.startsWith("Top")) DisplayPositioning.TOP
              else it.displaySettings.displayPositioning,
            )
        }
      }
      .sortByDisplayAndSourcePosition()
      .map { it.methodFqn }
      .toTypedArray()
      .let {
        assertArrayEquals(
          arrayOf("TestKt.TopA", "TestKt.TopB", "TestKt.C", "TestKt.A", "TestKt.B"),
          it,
        )
      }
  }

  @Test
  fun testOrderingMultipreview() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN

        @Annot3
        @Preview
        annotation class Annot1(){}

        @Preview
        annotation class Annot2(){}

        @Composable
        @Annot1
        @Preview
        fun C() {
        }

        @Annot1
        @Preview
        annotation class Annot3(){}

        @Composable
        @Annot2
        @Preview
        @Annot3
        fun A() {
        }

        @Composable
        @Preview
        @Annot1
        fun TopA() {
        }

        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview // 10 previews, for testing lexicographic order with double-digit numbers in the names
        annotation class Many() {}

        @Composable
        @Many
        fun f(){
        }
      """
          .trimIndent(),
      )

    AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
      .toMutableList()
      .apply {
        // Randomize to make sure the ordering works
        shuffle()
      }
      .map {
        // Override positioning for testing for those preview starting with Top
        object : PsiComposePreviewElement by it {
          override val displaySettings: PreviewDisplaySettings =
            PreviewDisplaySettings(
              it.displaySettings.name,
              it.displaySettings.baseName,
              it.displaySettings.parameterName,
              it.displaySettings.group,
              it.displaySettings.showDecoration,
              it.displaySettings.showBackground,
              it.displaySettings.backgroundColor,
              if (it.displaySettings.name == "TopA") DisplayPositioning.TOP
              else it.displaySettings.displayPositioning,
            )
        }
      }
      .sortByDisplayAndSourcePosition()
      .map { it.displaySettings.name }
      .toTypedArray()
      .let {
        assertArrayEquals(
          arrayOf(
            "TopA", // Preview with top priority
            "C - Annot1 1",
            "C - Annot3 1",
            "C", // Previews of 'C'
            "A - Annot2 1",
            "A",
            "A - Annot1 1",
            "A - Annot3 1", // Previews of 'A'
            "TopA - Annot1 1",
            "TopA - Annot3 1", // Previews of 'TopA'
            "f - Many 01",
            "f - Many 02",
            "f - Many 03",
            "f - Many 04",
            "f - Many 05",
            "f - Many 06",
            "f - Many 07",
            "f - Many 08",
            "f - Many 09",
            "f - Many 10", // Previews of 'f'
          ),
          it,
        )
      }
  }

  @Test
  fun testRepeatedPreviewAnnotations() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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
      """
          .trimIndent(),
      )

    assertTrue(
      AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
    )
    assertTrue(
      computeOnBackground {
        runBlocking {
          AnnotationFilePreviewElementFinder.hasPreviewElements(project, composeTest.virtualFile)
        }
      }
    )

    val elements =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()
    assertEquals(2, elements.size)
    elements[0].let {
      assertEquals("Preview1 - preview1", it.displaySettings.name)
      assertEquals(2, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals(
          "@Preview(name = \"preview1\", widthDp = 2)",
          it.previewElementDefinition?.element?.text,
        )
      }
    }

    elements[1].let {
      assertEquals("Preview1 - preview2", it.displaySettings.name)
      assertEquals("groupA", it.displaySettings.group)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      ReadAction.run<Throwable> {
        assertMethodTextRange(
          composeTest,
          "Preview1",
          TextRange.create(it.previewBody!!.psiRange!!),
        )
        assertEquals(
          "@Preview(name = \"preview2\", group = \"groupA\")",
          it.previewElementDefinition?.element?.text,
        )
      }
    }
  }

  @Test
  fun testFindPreviewAnnotationsCache() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
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

      """
          .trimIndent(),
      )

    val otherFile =
      fixture.addFileToProjectAndInvalidate(
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

        @[Composable Preview]
        fun OtherFilePreview2() {
        }
      """
          .trimIndent(),
      )

    repeat(3) {
      val elements =
        AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
          .toList()
      assertEquals("Preview1", elements.single().displaySettings.name)

      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()

      val elementsInOtherFile =
        AnnotationFilePreviewElementFinder.findPreviewElements(project, otherFile.virtualFile)
          .toList()
      assertEquals("OtherFilePreview1", elementsInOtherFile[0].displaySettings.name)
      assertEquals("OtherFilePreview2", elementsInOtherFile[1].displaySettings.name)

      AnnotationFilePreviewElementFinder.findPreviewElements(project, otherFile.virtualFile)
        .toList()
    }
  }

  @Test
  fun testFindPreviewAnnotationsWithAlias() = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import $PREVIEW_TOOLING_PACKAGE.Devices
        import $PREVIEW_TOOLING_PACKAGE.Preview as MyPreview
        import $COMPOSABLE_ANNOTATION_FQN

        @Composable
        @MyPreview
        fun Preview1() {
        }

      """
          .trimIndent(),
      )

    val elements =
      AnnotationFilePreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toList()
    assertEquals("Preview1", elements.single().displaySettings.name)
  }
}
