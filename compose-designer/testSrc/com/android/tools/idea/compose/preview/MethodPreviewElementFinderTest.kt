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
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertNotEquals

class MethodPreviewElementFinderTest : ComposeLightCodeInsightFixtureTestCase() {
  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import com.android.tools.preview.Configuration
      import androidx.compose.Composable

      @Composable
      fun Preview1() {
        Preview() {
        }
      }

      @Composable
      fun Preview2() {
        Preview(name = "preview2", configuration = Configuration(apiLevel = 12)) {
        }
      }

      @Composable
      fun Preview3() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
        }
      }

      @Composable
      fun NoPreviewComposable() {

      }

      fun PreviewMethodNotComposable() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
          Button {
          }
        }
      }
    """.trimIndent()).toUElement() as UFile

    val elements = MethodPreviewElementFinder.findPreviewMethods(composeTest)
    assertEquals(3, elements.size)
    elements.single { it.name == "preview2" }.let {
      assertEquals("preview2", it.name)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      assertMethodTextRange(composeTest, "Preview2", it.textRange)
    }

    elements.single { it.name == "preview3" }.let {
      assertEquals("preview3", it.name)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)

      assertMethodTextRange(composeTest, "Preview3", it.textRange)
    }

    elements.single { it.name.isEmpty() }.let {
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      assertMethodTextRange(composeTest, "Preview1", it.textRange)
    }
  }

  fun testElementBelongsToPreviewElement() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import androidx.compose.Composable

      @Composable
      fun Row(children: () -> Unit) {

      }

      @Composable
      fun Button() {
      }

      // Test comment
      @Composable
      fun PreviewMethod() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
          val i = 1

          Row {
            Button {
            }
          }
        }
      }
    """.trimIndent())

    var previewCall: UCallExpression? = null
    var previewMethod: UMethod? = null
    var localVariable: ULocalVariable? = null
    var configurationParameter: ULiteralExpression? = null
    composeTest.toUElement()?.accept(object: AbstractUastVisitor() {
      override fun visitMethod(node: UMethod): Boolean {
        if ("PreviewMethod" == node.name) {
          previewMethod = node
        }
        return super.visitMethod(node)
      }

      override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
        val intValue = node.evaluate() as? Int
        if (intValue == 2) {
          configurationParameter = node
        }

        return super.visitLiteralExpression(node)
      }

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if ("Preview" == node.methodName) {
          previewCall = node
        }

        return super.visitCallExpression(node)
      }

      override fun visitLocalVariable(node: ULocalVariable): Boolean {
        localVariable = node

        return super.visitLocalVariable(node)
      }
    })

    assertTrue(MethodPreviewElementFinder.elementBelongsToPreviewElement(previewCall!!.valueArguments[0].sourcePsi!!))
    assertTrue(MethodPreviewElementFinder.elementBelongsToPreviewElement(configurationParameter?.sourcePsi!!))
    assertFalse(MethodPreviewElementFinder.elementBelongsToPreviewElement(previewMethod?.sourcePsi!!))
    assertFalse(MethodPreviewElementFinder.elementBelongsToPreviewElement(localVariable?.sourcePsi!!))
  }

  fun testFindPreviewPackage() {
    @Language("kotlin")
    val notPreviewAnnotation = myFixture.addFileToProject("src/com/android/notpreview/Preview.kt", """
      package com.android.notpreview

      fun Preview(name: String? = null,
                  configuration: Configuration? = null,
                  children: () -> Unit) {
          children()
      }
    """.trimIndent())

    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.notpreview.Preview
      import androidx.compose.Composable

      @Composable
      fun Row(children: () -> Unit) {

      }

      @Composable
      fun Button() {
      }

      // Test comment
      @Composable
      fun PreviewMethod() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
          Row {
            Button {
            }
          }
        }
      }
    """.trimIndent())

    assertEquals(0, MethodPreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile).size)
  }
}