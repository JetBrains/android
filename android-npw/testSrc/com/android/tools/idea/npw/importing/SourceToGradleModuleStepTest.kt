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
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.util.AndroidBundle.message
import org.mockito.Mockito.mock
import java.io.File

class SourceToGradleModuleStepTest : AndroidGradleTestCase() {
  private lateinit var page: SourceToGradleModuleStep
  override fun setUp() {
    super.setUp()
    page = SourceToGradleModuleStep(SourceToGradleModuleModel(project, DefaultProjectSyncInvoker()))
    page.onWizardStarting(mock(ModelWizard.Facade::class.java))
    Disposer.register(testRootDisposable, page)
  }

  fun testCheckPathValidInput() {
    val path = File(AndroidTestBase.getTestDataPath(), TestProjectPaths.IMPORTING).path
    assertEquals(Validator.Severity.OK, page.checkPath(path).severity)
  }

  fun testCheckPathDoesNotExist() {
    val path = File(AndroidTestBase.getTestDataPath(), "path_that_does_not_exist").path
    assertEquals(message("android.wizard.module.import.source.browse.invalid.location"), page.checkPath(path).message)
  }

  fun testCheckPathEmptyPath() {
    // Don't validate default empty input: jetbrains.github.io/ui/principles/validation_errors/#23
    assertEquals(Validator.Severity.OK, page.updateForwardStatus("").severity)
  }

  fun testCheckDirectoryWithNoModules() {
    val noModulesDirectory = File(AndroidTestBase.getTestDataPath(), TestProjectPaths.IMPORTING + "/simple/lib/")
    assertThat(noModulesDirectory).exists()
    assertThat(noModulesDirectory).isDirectory()
    assertEquals(Validator.Severity.ERROR, page.updateForwardStatus(noModulesDirectory.path).severity)
  }

  fun testCheckSelectFile() {
    val jarFile = File(AndroidTestBase.getTestDataPath(), TestProjectPaths.IMPORTING + "/simple/lib/library.jar")
    assertThat(jarFile).exists()
    assertThat(jarFile).isFile()
    assertEquals(Validator.Severity.ERROR, page.updateForwardStatus(jarFile.path).severity)
  }

  fun testCheckPathNotAProject() {
    val path = AndroidTestBase.getTestDataPath()
    assertEquals(message("android.wizard.module.import.source.browse.cant.import"), page.checkPath(path).message)
  }

  fun testCheckPathInProject() {
    loadProject(TestProjectPaths.IMPORTING)
    val path = projectFolderPath.path
    assertEquals(message("android.wizard.module.import.source.browse.taken.location"), page.checkPath(path).message)
  }
}