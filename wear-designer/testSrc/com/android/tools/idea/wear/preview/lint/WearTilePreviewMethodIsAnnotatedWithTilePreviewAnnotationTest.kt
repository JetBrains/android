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

import com.android.tools.idea.projectsystem.isUnitTestModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotationTest {

  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    fixture.enableInspections(WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotation())

    val unitTestModule = fixture.project.modules.single { it.isUnitTestModule() }
    val unitTestRoot = fixture.tempDirFixture.findOrCreateDir("src/test")
    runInEdt {
      ApplicationManager.getApplication().runWriteAction<SourceFolder> {
        PsiTestUtil.addSourceRoot(unitTestModule, unitTestRoot, true)
      }
    }

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
  fun isAvailableForKotlinAndJavaFiles() {
    val inspection = WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotation()

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
    val inspection = WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotation()
    val kotlinUnitTestFile = fixture.addFileToProject("src/test/test.kt", "")
    val javaUnitTestFile = fixture.addFileToProject("src/test/Test.java", "")

    assertFalse(inspection.isAvailableForFile(kotlinUnitTestFile))
    assertFalse(inspection.isAvailableForFile(javaUnitTestFile))
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
        it.description ==
          message("inspection.preview.annotation.not.from.tile.package")
      }
    assertEquals(
      "@Preview" to "tilePreviewWithInvalidPreviewAnnotation",
      directPreviewError.text to file.containingMethodName(directPreviewError),
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

private fun PsiFile.containingMethodName(highlightInfo: HighlightInfo) =
  runReadAction {
      elementsInRange(TextRange.create(highlightInfo.startOffset, highlightInfo.endOffset))
    }
    .mapNotNull {
      runReadAction { (it.parent as? PsiMethod)?.name ?: (it.parent as? KtNamedFunction)?.name }
    }
    .single()
