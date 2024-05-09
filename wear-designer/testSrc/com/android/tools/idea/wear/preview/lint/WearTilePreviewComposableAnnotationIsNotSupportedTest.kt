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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewComposableAnnotationIsNotSupportedTest {
  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  @get:Rule val wearTilePreviewFlagRule = FlagRule(StudioFlags.WEAR_TILE_PREVIEW, true)

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewComposableAnnotationIsNotSupported()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)
    fixture.addUnitTestSourceRoot()
    fixture.stubComposableAnnotation()
  }

  @Test
  fun isAvailableForKotlinAndJavaFiles() {
    // supported types
    val kotlinFile = fixture.configureByText(KotlinFileType.INSTANCE, "")
    val javaFile = fixture.configureByText(JavaFileType.INSTANCE, "")
    assertTrue(inspection.isAvailableForFile(kotlinFile))
    assertTrue(inspection.isAvailableForFile(javaFile))

    // unsupported types
    val xmlFile = fixture.configureByText(XmlFileType.INSTANCE, "")
    val htmlFile = fixture.configureByText(HtmlFileType.INSTANCE, "")
    assertFalse(inspection.isAvailableForFile(xmlFile))
    assertFalse(inspection.isAvailableForFile(htmlFile))
  }

  @Test
  fun isNotAvailableForUnitTestFiles() {
    val kotlinUnitTestFile = fixture.addFileToProject("src/test/test.kt", "")
    val javaUnitTestFile = fixture.addFileToProject("src/test/Test.java", "")

    assertFalse(inspection.isAvailableForFile(kotlinUnitTestFile))
    assertFalse(inspection.isAvailableForFile(javaUnitTestFile))
  }

  @Test
  fun canBeDisabled() {
    val kotlinFile = fixture.configureByText(KotlinFileType.INSTANCE, "")
    val javaFile = fixture.configureByText(JavaFileType.INSTANCE, "")

    StudioFlags.WEAR_TILE_PREVIEW.override(false)

    assertFalse(inspection.isAvailableForFile(kotlinFile))
    assertFalse(inspection.isAvailableForFile(javaFile))
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
