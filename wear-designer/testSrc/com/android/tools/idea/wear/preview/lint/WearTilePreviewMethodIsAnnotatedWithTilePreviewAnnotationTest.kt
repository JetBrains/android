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

import com.android.tools.idea.preview.quickfixes.ReplacePreviewAnnotationFix
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotationTest {

  @get:Rule val projectRule = WearTileProjectRule()

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotation()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)

    fixture.addFileToProject(
      "src/main/test/Preview.kt",
      // language=kotlin
      """
      package test

      annotation class Preview
     """
        .trimIndent(),
    )
    fixture.addFileToProject(
      "src/main/test/AnInvalidMultiPreviewAnnotation.kt",
      // language=kotlin
      """
      package test

      @Preview
      annotation class AnInvalidMultiPreviewAnnotation
     """
        .trimIndent(),
    )
  }

  @Test
  fun previewAnnotationFromADifferentPackageResultsInAnErrorKotlin() {
    val kotlinFile =
      fixture.addFileToProject(
        "src/main/test/Test.kt",
        // language=kotlin
        """
      import androidx.wear.tiles.tooling.preview.TilePreviewData
      import test.Preview
      import test.AnInvalidMultiPreviewAnnotation

      @Preview
      fun tilePreviewWithInvalidPreviewAnnotation() = TilePreviewData()

      fun someMethodWithTilePreviewSignatureButNotAnnotated() = TilePreviewData()

      @Preview
      @AnInvalidMultiPreviewAnnotation
      fun someAnnotatedMethodButWithoutTilePreviewSignature() = Unit

      @AnInvalidMultiPreviewAnnotation
      fun tilePreviewWithInvalidMultiPreviewAnnotation() = TilePreviewData()
      """
          .trimIndent(),
      )

    previewAnnotationFromADifferentPackageResultsInAnError(kotlinFile)
  }

  @Test
  fun previewAnnotationFromADifferentPackageResultsInAnErrorJava() {
    val javaFile =
      fixture.addFileToProject(
        "src/main/test/Test.java",
        // language=java
        """
      import androidx.wear.tiles.tooling.preview.TilePreviewData;
      import test.Preview;
      import test.AnInvalidMultiPreviewAnnotation;

      class Test {
        @Preview
        TilePreviewData tilePreviewWithInvalidPreviewAnnotation() {
          return new TilePreviewData();
        }

        TilePreviewData someMethodWithTilePreviewSignatureButNotAnnotated() {
          return new TilePreviewData();
        }

        @Preview
        @AnInvalidMultiPreviewAnnotation
        void someAnnotatedMethodButWithoutTilePreviewSignature() {}

        @AnInvalidMultiPreviewAnnotation
        TilePreviewData tilePreviewWithInvalidMultiPreviewAnnotation() {
          return new TilePreviewData();
        }
      }
      """
          .trimIndent(),
      )

    previewAnnotationFromADifferentPackageResultsInAnError(javaFile)
  }

  private fun previewAnnotationFromADifferentPackageResultsInAnError(file: PsiFile) {
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
    assertEquals(2, errors.size)

    val directPreviewError =
      errors.single {
        it.description == message("inspection.preview.annotation.not.from.tile.package")
      }
    assertEquals(
      "@Preview" to "tilePreviewWithInvalidPreviewAnnotation",
      directPreviewError.text to file.containingMethodName(directPreviewError),
    )
    assertTrue(
      directPreviewError.findRegisteredQuickFix { desc, _ ->
        QuickFixWrapper.unwrap(desc.action) is ReplacePreviewAnnotationFix
      }
    )

    val multiPreviewError =
      errors.single {
        it.description ==
          message("inspection.preview.annotation.not.from.tile.package.multipreview")
      }
    assertEquals(
      "@AnInvalidMultiPreviewAnnotation" to "tilePreviewWithInvalidMultiPreviewAnnotation",
      multiPreviewError.text to file.containingMethodName(multiPreviewError),
    )
  }
}
