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
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import org.junit.Assert.assertArrayEquals

class MultiPreviewElementFinderTest : ComposeLightCodeInsightFixtureTestCase() {
  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import androidx.compose.Composable

      @Composable
      fun Button() {
      }

      @Composable
      @Preview
      fun Preview1() {
      }

      @Composable
      fun Preview2() {
            Preview(name = "preview2", apiLevel = 12) {
              Button() {
              }
            }
      }

      @Composable
      @Preview(name = "preview3", width = 1, height = 2)
      fun Preview3() {
      }
      
      // This checks that no duplicates are returned
      @Composable
      @Preview(name = "preview4")
      fun Preview4() {
        Preview(name = "preview4") {
          Button() {}
        }
      }

      @Composable
      fun NoPreviewComposable() {

      }

      @Preview
      fun NoComposablePreview() {

      }

    """.trimIndent()).toUElement() as UFile

    val elements = MultiPreviewElementFinder(listOf(AnnotationPreviewElementFinder, MethodPreviewElementFinder))
      .findPreviewMethods(composeTest)

    assertArrayEquals(arrayOf("", "preview2", "preview3", "preview4"), elements.map { it.displayName }.toTypedArray())
  }
}