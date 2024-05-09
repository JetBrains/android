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
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewNotSupportedInUnitTestFilesTest {
  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  @get:Rule val wearTilePreviewFlagRule = FlagRule(StudioFlags.WEAR_TILE_PREVIEW, true)

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewNotSupportedInUnitTestFiles()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)

    fixture.addUnitTestSourceRoot()
    fixture.addAndroidTestSourceRoot()

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
  fun isAvailableForKotlinAndJavaUnitTestFiles() {
    // supported types
    val kotlinUnitTestFile = fixture.addFileToProject("src/test/test.kt", "")
    val javaUnitTestFile = fixture.addFileToProject("src/test/Test.java", "")
    assertTrue(inspection.isAvailableForFile(kotlinUnitTestFile))
    assertTrue(inspection.isAvailableForFile(javaUnitTestFile))

    // unsupported types
    val xmlUnitTestFile = fixture.configureByText("src/test/test.xml", "")
    val htmlUnitTestFile = fixture.configureByText("src/test/test.html", "")
    assertFalse(inspection.isAvailableForFile(xmlUnitTestFile))
    assertFalse(inspection.isAvailableForFile(htmlUnitTestFile))
  }

  @Test
  fun canBeDisabled() {
    val kotlinUnitTestFile = fixture.addFileToProject("src/test/Test.kt", "")
    val javaUnitTestFile = fixture.configureByText("src/test/Test.java", "")

    StudioFlags.WEAR_TILE_PREVIEW.override(false)

    assertFalse(inspection.isAvailableForFile(kotlinUnitTestFile))
    assertFalse(inspection.isAvailableForFile(javaUnitTestFile))
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
