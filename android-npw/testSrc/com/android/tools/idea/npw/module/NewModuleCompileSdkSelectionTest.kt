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
package com.android.tools.idea.npw.module

import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate.createDefaultModuleTemplate
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.withCompileSdk
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.io.File
import org.junit.Rule
import org.junit.Test

class NewModuleCompileSdkSelectionTest {
  private val agpVersionToTest =
    AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT.withCompileSdk("33")

  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = agpVersionToTest)

  private val emptyProjectSyncInvoker =
    object : ProjectSyncInvoker {
      override fun syncProject(project: Project) {}
    }

  @Test
  fun `new module has compile SDK of highest of existing modules`() {
    projectRule.load(
      projectPath = TestProjectPaths.SIMPLE_APPLICATION,
      agpVersion = agpVersionToTest,
    )
    generateTestLibraryModuleFiles()
    assertThat(libraryBuildGradleKts).contains("compileSdk = 33")
  }

  private val libraryBuildGradleKts: String
    get() = File(projectRule.project.basePath, "$TEST_LIBRARY_NAME/$FN_BUILD_GRADLE_KTS").readText()

  private fun generateTestLibraryModuleFiles() {
    val model =
      NewAndroidModuleModel.fromExistingProject(
        project = projectRule.project,
        moduleParent = ":",
        projectSyncInvoker = emptyProjectSyncInvoker,
        formFactor = FormFactor.Mobile,
        category = Category.Activity,
        isLibrary = true,
      )
    model.androidSdkInfo.value =
      AndroidVersionsInfo.VersionItem.fromStableVersion(HIGHEST_KNOWN_STABLE_API)
    model.moduleName.set(TEST_LIBRARY_NAME)
    model.template.set(createDefaultModuleTemplate(projectRule.project, TEST_LIBRARY_NAME))
    model.packageName.set("com.example")
    model.useGradleKts.set(true)
    model.handleFinished()
  }

  companion object {
    private const val TEST_LIBRARY_NAME = "test_library"
  }
}
