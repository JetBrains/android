/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing

import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import java.io.File
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.util.AndroidBundle.message
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class SourceToGradleModuleStepTest {
  private lateinit var page: SourceToGradleModuleStep

  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion())

  @Before
  fun setup() {
    page =
      SourceToGradleModuleStep(
        SourceToGradleModuleModel(projectRule.project, DefaultProjectSyncInvoker())
      )
    page.onWizardStarting(mock<ModelWizard.Facade>())
    Disposer.register(projectRule.fixture.testRootDisposable, page)
  }

  @Test
  fun testCheckPathValidInput() {
    val path = File(AndroidTestBase.getTestDataPath(), TestProjectPaths.IMPORTING).path
    assertThat(page.checkPath(path).severity).isEqualTo(Validator.Severity.OK)
  }

  @Test
  fun testUpdateForwardStatusValidInput() {
    val path = File(AndroidTestBase.getTestDataPath(), TestProjectPaths.IMPORTING).path
    page.updateForwardStatus(path)
    assertThat(page.canGoForward().get()).isTrue()
  }

  @Test
  fun testCheckPathDoesNotExist() {
    val path = File(AndroidTestBase.getTestDataPath(), "path_that_does_not_exist").path
    assertThat(page.checkPath(path).message)
      .isEqualTo(message("android.wizard.module.import.source.browse.invalid.location"))
  }

  @Test
  fun testCheckPathEmptyPath() {
    // Don't validate default empty input: jetbrains.github.io/ui/principles/validation_errors/#23
    assertThat(page.updateForwardStatus("").severity).isEqualTo(Validator.Severity.OK)
  }

  @Test
  fun testCheckDirectoryWithNoModules() {
    val noModulesDirectory =
      File(AndroidTestBase.getTestDataPath(), TestProjectPaths.IMPORTING + "/simple/lib/")
    assertThat(noModulesDirectory).exists()
    assertThat(noModulesDirectory).isDirectory()
    assertThat(page.updateForwardStatus(noModulesDirectory.path).severity)
      .isEqualTo(Validator.Severity.ERROR)
  }

  @Test
  fun testCheckSelectFile() {
    val jarFile =
      File(
        AndroidTestBase.getTestDataPath(),
        TestProjectPaths.IMPORTING + "/simple/lib/library.jar",
      )
    assertThat(jarFile).exists()
    assertThat(jarFile).isFile()
    assertThat(page.updateForwardStatus(jarFile.path).severity).isEqualTo(Validator.Severity.ERROR)
  }

  @Test
  fun testCheckPathNotAProject() {
    val path = AndroidTestBase.getTestDataPath()
    assertThat(page.checkPath(path).message)
      .isEqualTo(message("android.wizard.module.import.source.browse.cant.import"))
  }

  @Test
  fun testCheckPathInProject() {
    projectRule.loadProject(TestProjectPaths.IMPORTING)
    val path = projectRule.project.basePath!!
    assertThat(page.checkPath(path).message)
      .isEqualTo(message("android.wizard.module.import.source.browse.taken.location"))
  }
}
