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
package com.android.tools.idea.preview

import com.android.tools.idea.preview.annotations.findAllAnnotationsInGraph
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.runInEdtAndWait
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.toUElement
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnnotationPreviewNameHelperTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    val file =
      fixture.addFileToProject(
        "src/Test.kt",
        // language=kotlin
        """
        annotation class Preview()

        @Annot3
        @Preview
        annotation class Annot1()

        @Preview
        annotation class Annot2()

        @Annot1
        @Preview
        fun c() {}

        @Annot1
        @Preview
        annotation class Annot3()

        @Annot2
        @Preview
        @Annot3
        fun a() {}

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
        annotation class Many()

        @Many
        fun f() {}
      """
          .trimIndent(),
      )
    runInEdtAndWait { fixture.openFileInEditor(file.virtualFile) }
  }

  @Test
  fun `nameParameter is used if available`() =
    runBlocking<Unit> {
      val method = readAction {
        fixture.findElementByText<KtFunction>("fun f() {}", KtFunction::class.java).toUElement()!!
      }
      val nodeInfo = method.findAllAnnotationsInGraph { it.isPreviewAnnotation() }.take(1).single()
      val helper = AnnotationPreviewNameHelper.create(nodeInfo, "f") { isPreviewAnnotation() }

      assertEquals(
        "f - someParameterName",
        helper.buildPreviewName(nameParameter = "someParameterName"),
      )
      assertEquals(
        "someParameterName",
        helper.buildParameterName(nameParameter = "someParameterName"),
      )
    }

  @Test
  fun `nodeInfo is used when available and nameParameter is not passed`() =
    runBlocking<Unit> {
      val method = readAction {
        fixture.findElementByText<KtFunction>("fun f() {}", KtFunction::class.java).toUElement()!!
      }
      val nodeInfo = method.findAllAnnotationsInGraph { it.isPreviewAnnotation() }.take(1).single()
      val helper = AnnotationPreviewNameHelper.create(nodeInfo, "f") { isPreviewAnnotation() }

      assertEquals("f - Many 01", helper.buildPreviewName(nameParameter = null))
      assertEquals("Many 01", helper.buildParameterName(nameParameter = null))
    }

  @Test
  fun `method name and null is used when there is no nodeInfo and nameParameter`() =
    runBlocking<Unit> {
      val helper = AnnotationPreviewNameHelper.create(null, "f") { isPreviewAnnotation() }

      assertEquals("f", helper.buildPreviewName(nameParameter = null))
      assertEquals(null, helper.buildParameterName(nameParameter = null))
    }

  @Test
  fun `test method 'a' builds names based on MultiPreviews 'Annot1', 'Annot2', 'Annot3' and a direct preview`() =
    runBlocking<Unit> {
      val a = readAction {
        fixture.findElementByText<KtFunction>("fun a() {}", KtFunction::class.java).toUElement()!!
      }
      val aPreviewNames = mutableListOf<String>()
      val aParameterNames = mutableListOf<String?>()
      a.findAllAnnotationsInGraph { it.isPreviewAnnotation() }
        .map { nodeInfo ->
          aPreviewNames +=
            AnnotationPreviewNameHelper.create(nodeInfo, "a") { isPreviewAnnotation() }
              .buildPreviewName(null)
          aParameterNames +=
            AnnotationPreviewNameHelper.create(nodeInfo, "a") { isPreviewAnnotation() }
              .buildParameterName(null)
        }
        .collect()

      assertContentEquals(
        listOf("a - Annot2 1", "a", "a - Annot1 1", "a - Annot3 1"),
        aPreviewNames,
      )

      assertContentEquals(listOf("Annot2 1", null, "Annot1 1", "Annot3 1"), aParameterNames)
    }

  @Test
  fun `test method 'c' builds names based on MultiPreview 'Annot1' and a direct preview`() =
    runBlocking<Unit> {
      val c = readAction {
        fixture.findElementByText<KtFunction>("fun c() {}", KtFunction::class.java).toUElement()!!
      }
      val cPreviewNames = mutableListOf<String>()
      val cParameterNames = mutableListOf<String?>()
      c.findAllAnnotationsInGraph { it.isPreviewAnnotation() }
        .map { nodeInfo ->
          cPreviewNames +=
            AnnotationPreviewNameHelper.create(nodeInfo, "c") { isPreviewAnnotation() }
              .buildPreviewName(null)
          cParameterNames +=
            AnnotationPreviewNameHelper.create(nodeInfo, "c") { isPreviewAnnotation() }
              .buildParameterName(null)
        }
        .collect()

      assertContentEquals(listOf("c - Annot3 1", "c - Annot1 1", "c"), cPreviewNames)

      assertContentEquals(listOf("Annot3 1", "Annot1 1", null), cParameterNames)
    }

  @Test
  fun `test method 'f' builds padded preview names based on a MultiPreview declaring 10 previews`() =
    runBlocking<Unit> {
      val f = readAction {
        fixture.findElementByText<KtFunction>("fun f() {}", KtFunction::class.java).toUElement()!!
      }
      val fPreviewNames = mutableListOf<String>()
      val fParameterNames = mutableListOf<String?>()
      f.findAllAnnotationsInGraph { it.isPreviewAnnotation() }
        .map { nodeInfo ->
          fPreviewNames +=
            AnnotationPreviewNameHelper.create(nodeInfo, "f") { isPreviewAnnotation() }
              .buildPreviewName(null)
          fParameterNames +=
            AnnotationPreviewNameHelper.create(nodeInfo, "f") { isPreviewAnnotation() }
              .buildParameterName(null)
        }
        .collect()

      assertContentEquals(
        listOf(
          "f - Many 01",
          "f - Many 02",
          "f - Many 03",
          "f - Many 04",
          "f - Many 05",
          "f - Many 06",
          "f - Many 07",
          "f - Many 08",
          "f - Many 09",
          "f - Many 10",
        ),
        fPreviewNames,
      )

      assertContentEquals(
        listOf(
          "Many 01",
          "Many 02",
          "Many 03",
          "Many 04",
          "Many 05",
          "Many 06",
          "Many 07",
          "Many 08",
          "Many 09",
          "Many 10",
        ),
        fParameterNames,
      )
    }

  private fun UElement?.isPreviewAnnotation() = runReadAction {
    (this as? UAnnotation)?.qualifiedName == "Preview"
  }
}
