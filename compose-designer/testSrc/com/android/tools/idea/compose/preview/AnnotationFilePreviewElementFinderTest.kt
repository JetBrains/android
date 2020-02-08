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

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
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

class AnnotationFilePreviewElementFinderTest : ComposeLightJavaCodeInsightFixtureTestCase() {
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
        @Preview(name = "preview3", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecorations = true)
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
      """.trimIndent()).toUElement() as UFile

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest)
    assertEquals(4, elements.size)
    elements[1].let {
      assertEquals("preview2", it.displaySettings.name)
      assertEquals("groupA", it.displaySettings.group)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertEquals(1f, it.configuration.fontScale)
      assertTrue(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecorations)

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
      assertTrue(it.displaySettings.showDecorations)

      assertMethodTextRange(composeTest, "Preview3", it.previewBodyPsi?.psiRange?.range!!)
      assertEquals("@Preview(name = \"preview3\", widthDp = 1, heightDp = 2, fontScale = 0.2f, showDecorations = true)",
                   it.previewElementDefinitionPsi?.element?.text)
    }

    elements[0].let {
      assertEquals("Preview1", it.displaySettings.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertFalse(it.displaySettings.showBackground)
      assertFalse(it.displaySettings.showDecorations)

      assertMethodTextRange(composeTest, "Preview1", it.previewBodyPsi?.psiRange?.range!!)
      assertEquals("@Preview", it.previewElementDefinitionPsi?.element?.text)
    }

    elements[3].let {
      assertEquals("Preview with parameters", it.displaySettings.name)
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

    val elements = AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest)
    assertEquals(1, elements.size)
    // Check that we keep the first element
    assertEquals("Preview1", elements[0].displaySettings.name)
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

    assertEquals(0, AnnotationFilePreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile).size)
  }
}