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

import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isUnitTestModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.SourceFolder
import com.intellij.testFramework.PsiTestUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewNotSupportedInUnitTestFilesTest {
  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    val inspections = LocalInspectionEP.LOCAL_INSPECTION.extensions
      .filter { e -> e.implementationClass == WearTilePreviewNotSupportedInUnitTestFiles::class.java.name }
      .map(InspectionEP::instantiateTool)
    fixture.enableInspections(*inspections.toTypedArray())


    val androidTestModule = projectRule.project.modules.single { it.isAndroidTestModule() }
    val androidTestSourceRoot = fixture.tempDirFixture.findOrCreateDir("src/androidTest")
    val unitTestModule = fixture.project.modules.single { it.isUnitTestModule() }
    val unitTestRoot = fixture.tempDirFixture.findOrCreateDir("src/test")
    runInEdt {
      ApplicationManager.getApplication().runWriteAction<SourceFolder> {
        PsiTestUtil.addSourceRoot(androidTestModule, androidTestSourceRoot, true)
        PsiTestUtil.addSourceRoot(unitTestModule, unitTestRoot, true)
      }
    }
    fixture.addFileToProject(
      "src/main/test/multipreview.kt",
      // language=kotlin
      """
      package test

      import androidx.wear.tiles.tooling.preview.Preview

      @Preview
      annotation class AMultiPreviewAnnotation
     """
        .trimIndent(),
    )
  }

  @Test
  fun previewAnnotationsAreNotSupportedInUnitTestFilesKotlin() {
    val unitTestFile =
      fixture.addFileToProject(
        "src/test/Test.kt",
        // language=kotlin
        """
      import androidx.wear.tiles.tooling.preview.Preview
      import androidx.wear.tiles.tooling.preview.TilePreviewData
      import test.AMultiPreviewAnnotation

      @Preview
      fun tilePreviewInUnitTest() = TilePreviewData()

      fun someMethodWithTilePreviewSignatureButNotAnnotated() = TilePreviewData()

      @Preview
      fun someAnnotatedMethodButWithoutTilePreviewSignature() = Unit

      @AMultiPreviewAnnotation
      fun tilePreviewInUnitTestsWithMultiPreview() = TilePreviewData()
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(unitTestFile.virtualFile)

    assertEquals(
      listOf(
        // language=kotlin
        """
          @Preview
          fun tilePreviewInUnitTest() = TilePreviewData()
        """
          .trimIndent(),
        // language=kotlin
        """
          @Preview
          fun someAnnotatedMethodButWithoutTilePreviewSignature() = Unit
        """
          .trimIndent(),
        // language=kotlin
        """
          @AMultiPreviewAnnotation
          fun tilePreviewInUnitTestsWithMultiPreview() = TilePreviewData()
        """
          .trimIndent(),
      ),
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        .filter { it.description == "Preview is not supported in unit test files" }
        .map { it.text },
    )
  }

  @Test
  fun previewAnnotationsAreNotSupportedInUnitTestFilesJava() {
    val unitTestFile =
      fixture.addFileToProject(
        "src/test/Test.java",
        // language=java
        """
      import androidx.wear.tiles.tooling.preview.Preview;
      import androidx.wear.tiles.tooling.preview.TilePreviewData;
      import test.AMultiPreviewAnnotation;

      class Test {
        @Preview
        TilePreviewData tilePreviewInUnitTest() {
          return new TilePreviewData();
        }

        TilePreviewData someMethodWithTilePreviewSignatureButNotAnnotated() {
          return new TilePreviewData();
        }

        @Preview
        void someAnnotatedMethodButWithoutTilePreviewSignature() {
        }

        @AMultiPreviewAnnotation
        TilePreviewData tilePreviewInUnitTestsWithMultiPreview() {
          return new TilePreviewData();
        }
      }
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(unitTestFile.virtualFile)

    assertEquals(
      listOf(
        // language=java
        """
          @Preview
            TilePreviewData tilePreviewInUnitTest() {
              return new TilePreviewData();
            }
        """
          .trimIndent(),
        // language=java
        """
          @Preview
            void someAnnotatedMethodButWithoutTilePreviewSignature() {
            }
        """
          .trimIndent(),
        // language=java
        """
          @AMultiPreviewAnnotation
            TilePreviewData tilePreviewInUnitTestsWithMultiPreview() {
              return new TilePreviewData();
            }
        """
          .trimIndent(),
      ),
      fixture
        .doHighlighting(HighlightSeverity.ERROR)
        .filter { it.description == "Preview is not supported in unit test files" }
        .map { it.text },
    )
  }

  @Test
  fun previewsAnnotationsAreSupportedInMainAndAndroidTestFiles() {
    val kotlinFileContent =
      // language=kotlin
      """
      import androidx.wear.tiles.tooling.preview.Preview
      import androidx.wear.tiles.tooling.preview.TilePreviewData
      import test.AMultiPreviewAnnotation

      @Preview
      fun tilePreview() = TilePreviewData()

      @AMultiPreviewAnnotation
      fun tilePreviewWithMultiPreview() = TilePreviewData()
      """
        .trimIndent()

    val javaFileContent =
      // language=java
      """
      import androidx.wear.tiles.tooling.preview.Preview;
      import androidx.wear.tiles.tooling.preview.TilePreviewData;
      import test.AMultiPreviewAnnotation;

      class Test {
        @Preview
        TilePreviewData tilePreview() {
          return new TilePreviewData();
        }

        @AMultiPreviewAnnotation
        TilePreviewData tilePreviewWithMultiPreview() {
          return new TilePreviewData();
        }
      }
      """
        .trimIndent()

    val mainFiles =
      listOf(
        fixture.addFileToProject("src/main/test.kt", kotlinFileContent),
        fixture.addFileToProject("src/main/Test.java", javaFileContent),
      )
    val androidTestFiles =
      listOf(
        fixture.addFileToProject("src/androidTest/test.kt", kotlinFileContent),
        fixture.addFileToProject("src/androidTest/Test.java", javaFileContent),
      )

    for (file in mainFiles + androidTestFiles) {
      fixture.configureFromExistingVirtualFile(file.virtualFile)

      assertTrue(fixture.doHighlighting(HighlightSeverity.ERROR).isEmpty())
    }
  }
}
