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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection as K2UnusedSymbolInspection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewEntryPointTest {
  @get:Rule val projectRule = WearTileProjectRule()

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
    fixture.enableInspections(UnusedDeclarationInspection(true))
  }

  @Test
  fun testFindPreviewAnnotationsKotlin() {
    @Language("kotlin")
    val fileContent =
      """
      import androidx.wear.tiles.tooling.preview.Preview
      import androidx.wear.tiles.tooling.preview.TilePreviewData

      @Preview
      fun Preview1() = TilePreviewData()

      fun NotUsedWithTilePreviewSignature() = TilePreviewData()

      @Preview
      fun Preview2() = TilePreviewData(0)

      @Preview
      fun NotATilePreviewSignature() {
      }

      fun NotUsed() {
      }
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)

    assertEquals(
      listOf(
        "Function \"NotUsedWithTilePreviewSignature\" is never used",
        "Function \"NotATilePreviewSignature\" is never used",
        "Function \"NotUsed\" is never used",
      ),
      fixture
        .doHighlighting()
        .filter { it?.description?.startsWith("Function") ?: false }
        .map { it.description },
    )
  }

  @Test
  fun testFindPreviewAnnotationsJava() {
    @Language("java")
    val fileContent =
      """
      import androidx.wear.tiles.tooling.preview.Preview;
      import androidx.wear.tiles.tooling.preview.TilePreviewData;

      class JavaPreview {
        @Preview
        private TilePreviewData PrivatePreview() {
          return new TilePreviewData();
        }

        @Preview
        public static TilePreviewData StaticPreview() {
          return new TilePreviewData();
        }

        public TilePreviewData NotUsedWithTilePreviewSignature() {
          return new TilePreviewData();
        }

        @Preview
        public void NotATilePreviewSignature() {
        }

        public void NotUsed() {
        }
      }
    """
        .trimIndent()

    fixture.configureByText("Test.java", fileContent)

    assertEquals(
      listOf(
        "Method 'NotUsedWithTilePreviewSignature()' is never used",
        "Method 'NotATilePreviewSignature()' is never used",
        "Method 'NotUsed()' is never used",
      ),
      fixture
        .doHighlighting()
        .filter { it?.description?.startsWith("Method") ?: false }
        .map { it.description },
    )
  }

  @Test
  fun testFindPreviewAnnotationsMultiPreview() {
    @Language("kotlin")
    val fileContent =
      """
      import androidx.wear.tiles.tooling.preview.Preview
      import androidx.wear.tiles.tooling.preview.TilePreviewData

      @Preview
      annotation class MyAnnotation

      annotation class MyEmptyAnnotation

      @MyAnnotation
      fun Preview1() = TilePreviewData()

      @MyAnnotation
      @MyEmptyAnnotation
      fun Preview2() = TilePreviewData()

      @MyEmptyAnnotation
      fun NotUsed() = TilePreviewData()
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
  fun testPreviewAnnotationFromDifferentPackageDoesNotMarkAsEntryPoint() {
    fixture.addFileToProjectAndInvalidate(
      "com/android/test/Preview.kt",
      // language=kotlin
      """
        package com.android.test

        annotation class Preview
      """,
    )

    @Language("kotlin")
    val fileContent =
      """
      import com.android.test.Preview
      import androidx.wear.tiles.tooling.preview.TilePreviewData

      @Preview
      fun PreviewAnnotationFromDifferentPackage() = TilePreviewData()
    """
        .trimIndent()

    fixture.configureByText("Test.kt", fileContent)

    assertEquals(
      "Function \"PreviewAnnotationFromDifferentPackage\" is never used",
      fixture
        .doHighlighting()
        .single { it?.description?.startsWith("Function") ?: false }
        .description,
    )
  }
}
