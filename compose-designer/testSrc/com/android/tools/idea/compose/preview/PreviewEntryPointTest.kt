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

import com.intellij.codeInspection.InspectionProfileEntry
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.ComposeProjectRule
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection as K2UnusedSymbolInspection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PreviewEntryPointTest {

  @get:Rule val projectRule = ComposeProjectRule()
  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    val unusedSymbolInspection =
      if (KotlinPluginModeProvider.isK2Mode()) {
        K2UnusedSymbolInspection()
      } else {
        UnusedSymbolInspection()
      }
    fixture.enableInspections(unusedSymbolInspection as InspectionProfileEntry)
  }

  @Test
  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQN
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
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "Function \"NotUsed\" is never used",
      fixture
        .doHighlighting()
        .single { it?.description?.startsWith("Function") ?: false }
        .description,
    )
  }

  @Test
  fun testFindPreviewAnnotationsMultiPreview() {
    @Language("kotlin")
    val fileContent =
      """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Preview
      annotation class MyAnnotation

      annotation class MyEmptyAnnotation

      @Composable
      @MyAnnotation
      fun Preview1() {
      }

      @Composable
      @MyAnnotation
      @MyEmptyAnnotation
      fun Preview2() {
      }

      @Composable
      @MyEmptyAnnotation
      fun NotUsed() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(
      "Function \"NotUsed\" is never used",
      fixture
        .doHighlighting()
        .single { it?.description?.startsWith("Function") ?: false }
        .description,
    )
  }
}
