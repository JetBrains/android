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
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.android.tools.idea.wear.preview.withTilePreviewDependency
import com.intellij.openapi.application.readAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearTilePreviewInspectionBaseTest(private val isUnitTestInspection: Boolean) {

  companion object {
    @JvmStatic @Parameterized.Parameters fun data() = listOf(false, true)
  }

  private val moduleWithTilesToolingPreviewDependency =
    AndroidModuleModelBuilder(
      gradlePath = ":with-dependency",
      selectedBuildVariant = "debug",
      AndroidProjectBuilder().withTilePreviewDependency(),
    )
  private val moduleWithoutDependency =
    AndroidModuleModelBuilder(gradlePath = ":without-dependency", selectedBuildVariant = "debug", AndroidProjectBuilder())

  @get:Rule
  val projectRule =
    WearTileProjectRule(
      AndroidProjectRule.withAndroidModels(
        JavaModuleModelBuilder.rootModuleBuilder,
        moduleWithTilesToolingPreviewDependency,
        moduleWithoutDependency,
      )
    )

  @get:Rule val wearTilePreviewFlagRule = FlagRule(StudioFlags.WEAR_TILE_PREVIEW, true)

  private val fixture
    get() = projectRule.fixture

  private val inspection = object : WearTilePreviewInspectionBase(isUnitTestInspection = isUnitTestInspection) {}

  @Test
  fun isAvailableForKotlinAndJavaFiles() = runTest {
    val kotlinFile = fixture.addFileToProject("with-dependency/src/main/kotlin/Test.kt", "")
    val javaFile = fixture.addFileToProject("with-dependency/src/main/java/Test.java", "")

    readAction {
      assertEquals(!isUnitTestInspection, inspection.isAvailableForFile(kotlinFile))
      assertEquals(!isUnitTestInspection, inspection.isAvailableForFile(javaFile))
    }
  }

  @Test
  fun isAvailableForUnitTestFiles() = runTest {
    val kotlinUnitTestFile = fixture.addFileToProject("with-dependency/src/test/kotlin/Test.kt", "")
    val javaUnitTestFile = fixture.addFileToProject("with-dependency/src/test/java/Test.java", "")

    readAction {
      assertEquals(isUnitTestInspection, inspection.isAvailableForFile(kotlinUnitTestFile))
      assertEquals(isUnitTestInspection, inspection.isAvailableForFile(javaUnitTestFile))
    }
  }

  @Test
  fun isUnavailableForUnSupportedTypes() = runTest {
    val xmlFile = fixture.addFileToProject("with-dependency/src/main/Test.xml", "")
    val xmlUnitTestFile = fixture.addFileToProject("with-dependency/src/test/Test.xml", "")
    val htmlFile = fixture.addFileToProject("with-dependency/src/main/Test.html", "")
    val htmlUnitTestFile = fixture.addFileToProject("with-dependency/src/test/Test.html", "")

    readAction {
      assertFalse(inspection.isAvailableForFile(xmlFile))
      assertFalse(inspection.isAvailableForFile(xmlUnitTestFile))
      assertFalse(inspection.isAvailableForFile(htmlFile))
      assertFalse(inspection.isAvailableForFile(htmlUnitTestFile))
    }
  }

  @Test
  fun canBeDisabled() = runTest {
    val kotlinFile = fixture.addFileToProject("with-dependency/src/main/kotlin/Test.kt", "")
    val javaFile = fixture.addFileToProject("with-dependency/src/main/java/Test.java", "")

    StudioFlags.WEAR_TILE_PREVIEW.override(false)

    readAction {
      assertFalse(inspection.isAvailableForFile(kotlinFile))
      assertFalse(inspection.isAvailableForFile(javaFile))
    }
  }

  @Test
  // Regression test for b/487624989
  fun isNotAvailableIfModuleDoesNotDependOnTilePreviewAndroidxLibrary() = runTest {
    val kotlinFile = fixture.addFileToProject("without-dependency/src/main/kotlin/Test.kt", "")

    readAction { assertFalse(inspection.isAvailableForFile(kotlinFile)) }
  }
}
