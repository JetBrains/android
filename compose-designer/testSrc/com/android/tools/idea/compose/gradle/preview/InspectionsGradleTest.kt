/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.preview.PreviewNotSupportedInUnitTestFiles
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.compose.preview.descriptionWithLineNumber
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InspectionsGradleTest {

  @get:Rule val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)
  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
  }

  @Test
  fun testPreviewNotSupportedInUnitTestFiles_unitTest() {
    fixture.enableInspections(PreviewNotSupportedInUnitTestFiles() as InspectionProfileEntry)
    val vFile =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PREVIEWS_UNIT_TEST.path,
        ProjectRootManager.getInstance(projectRule.project).contentRoots[0]
      )!!
    fixture.configureFromExistingVirtualFile(vFile)
    assertEquals(
      "21: Preview is not supported in unit test files.",
      fixture.doHighlighting(HighlightSeverity.ERROR).single().descriptionWithLineNumber()
    )
  }

  @Test
  fun testPreviewNotSupportedInUnitTestFiles_androidTest() {
    fixture.enableInspections(PreviewNotSupportedInUnitTestFiles() as InspectionProfileEntry)
    val vFile =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PREVIEWS_ANDROID_TEST.path,
        ProjectRootManager.getInstance(projectRule.project).contentRoots[0]
      )!!
    fixture.configureFromExistingVirtualFile(vFile)
    assertTrue(fixture.doHighlighting(HighlightSeverity.ERROR).isEmpty())
  }
}
