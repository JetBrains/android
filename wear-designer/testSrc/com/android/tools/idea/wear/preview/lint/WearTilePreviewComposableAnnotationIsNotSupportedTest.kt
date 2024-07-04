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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewComposableAnnotationIsNotSupportedTest {
  @get:Rule val projectRule = WearTileProjectRule()

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewComposableAnnotationIsNotSupported()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)
    fixture.stubComposableAnnotation()
  }

  @Test
  fun composableAnnotationOnATilePreviewResultsInAnErrorKotlin() {
    composableAnnotationOnATilePreviewResultsInAnError(
      fixture.addFileToProject(
        "src/main/test.kt",
        // language=kotlin
        """
        import androidx.compose.runtime.Composable
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @Preview
        @Composable
        fun invalidPreviewWithComposableAnnotation() = TilePreviewData()

        @Composable
        fun validMethodWithComposableAnnotationButWithoutPreviewAnnotation() = TilePreviewData()

        @Preview
        fun validPreview() = TilePreviewData()

        @Composable
        fun validMethodWithComposableAnnotation() {}
      """
          .trimIndent(),
      )
    )
  }

  @Test
  fun composableAnnotationOnATilePreviewResultsInAnErrorJava() {
    composableAnnotationOnATilePreviewResultsInAnError(
      fixture.addFileToProject(
        "src/main/Test.java",
        // language=java
        """
        import androidx.compose.runtime.Composable;
        import androidx.wear.tiles.tooling.preview.Preview;
        import androidx.wear.tiles.tooling.preview.TilePreviewData;

        class Test {
          @Preview
          @Composable
          TilePreviewData invalidPreviewWithComposableAnnotation() {
            return new TilePreviewData();
          }

          @Composable
          TilePreviewData validMethodWithComposableAnnotationButWithoutPreviewAnnotation() {
            return new TilePreviewData();
          }

          @Preview
          TilePreviewData validPreview() {
            return new TilePreviewData();
          }

          @Composable
          void validMethodWithComposableAnnotation() {}
        }
      """
          .trimIndent(),
      )
    )
  }

  private fun composableAnnotationOnATilePreviewResultsInAnError(file: PsiFile) {
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
    assertEquals(1, errors.size)

    val error = errors.single()
    assertEquals("@Composable", error.text)
    assertEquals(
      message("inspection.preview.annotation.composable.not.supported"),
      error.description,
    )
    assertEquals("invalidPreviewWithComposableAnnotation", file.containingMethodName(error))
  }
}
