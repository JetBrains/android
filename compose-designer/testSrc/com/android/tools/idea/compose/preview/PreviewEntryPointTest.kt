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
import com.intellij.codeInspection.InspectionProfileEntry
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PreviewEntryPointTest(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val PREVIEW_TOOLING_PACKAGE = previewAnnotationPackage

  @get:Rule
  val projectRule = ComposeProjectRule(previewAnnotationPackage = previewAnnotationPackage,
                                       composableAnnotationPackage = composableAnnotationPackage)
  private val fixture get() = projectRule.fixture

  @Before
  fun setUp() {
    fixture.enableInspections(UnusedSymbolInspection() as InspectionProfileEntry)
  }

  @Test
  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val fileContent = """
      import androidx.compose.Composable
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun Preview1() {
      }

      fun NotUsed() {
      }

      @Composable
      @Preview
      fun Preview2() {
      }

      @Preview
      fun NotAComposable() {
      }
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals("Function \"NotUsed\" is never used",
                 fixture.doHighlighting().single { it?.description?.startsWith("Function") ?: false }.description)
  }
}