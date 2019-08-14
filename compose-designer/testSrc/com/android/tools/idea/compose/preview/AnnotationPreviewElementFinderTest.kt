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

import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

class AnnotationPreviewElementFinderTest : ComposeLightCodeInsightFixtureTestCase() {
  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import androidx.compose.Compose

      @Compose
      @Preview
      fun Preview1() {
      }

      @Compose
      @Preview(name = "preview2", apiLevel = 12)
      fun Preview2() {
      }

      @Compose
      @Preview(name = "preview3", width = 1, height = 2)
      fun Preview3() {
      }

      @Compose
      fun NoPreviewCompose() {

      }
    """.trimIndent())

    val elements = AnnotationPreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile)
    assertEquals(3, elements.size)
    elements.single { it.name == "preview2" }.let {
      assertEquals("preview2", it.name)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
    }

    elements.single { it.name == "preview3" }.let {
      assertEquals("preview3", it.name)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)
    }

    elements.single { it.name.isEmpty() }.let {
      assertEquals("", it.name)
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
    }
  }

  fun testElementBelongsToPreviewElement() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import androidx.compose.Compose

      @Compose
      @Preview(name = "preview3", width = 1, height = 2)
      fun Preview3() {
      }
    """.trimIndent())

    var previewAnnotation: UAnnotation? = null
    var previewMethod: UMethod? = null
    composeTest.toUElement()?.accept(object: AbstractUastVisitor() {
      override fun visitAnnotation(node: UAnnotation): Boolean {
        if ("com.android.tools.preview.Preview" == node.qualifiedName) {
          previewAnnotation = node
        }
        return super.visitAnnotation(node)
      }

      override fun visitMethod(node: UMethod): Boolean {
        previewMethod = node
        return super.visitMethod(node)
      }
    })

    assertTrue(AnnotationPreviewElementFinder.elementBelongsToPreviewElement(previewAnnotation!!))
    assertFalse(AnnotationPreviewElementFinder.elementBelongsToPreviewElement(previewMethod!!))
  }

  fun testFindPreviewPackage() {
    @Language("kotlin")
    val notPreviewAnnotation = myFixture.addFileToProject("src/com/android/notpreview/Preview.kt", """
      package com.android.notpreview

      annotation class Preview(val name: String = "",
                               val apiLevel: Int = -1,
                               val theme: String = "",
                               val width: Int = -1,
                               val height: Int = -1)
    """.trimIndent())

    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.notpreview.Preview
      import androidx.compose.Compose

      @Compose
      @Preview
      fun Preview1() {
      }

      @Compose
      @Preview(name = "preview2", apiLevel = 12)
      fun Preview2() {
      }

      @Compose
      @Preview(name = "preview3", width = 1, height = 2)
      fun Preview3() {
      }
    """.trimIndent())

    assertEquals(0, AnnotationPreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile).size)
  }
}