/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.npw.baselineprofiles.NewBaselineProfilesModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddBaselineProfilesModuleTest(
  private val useGradleKtsParam: Boolean,
  private val useGmdParam: Boolean,
) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useGradleKts={0}, useGmdParam={1}")
    fun data(): List<Array<Any>> = listOf(
      arrayOf(true, true),
      arrayOf(true, false),
      arrayOf(false, true),
      arrayOf(false, false),
    )
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun addNewBaselineProfilesModule() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

    val project = projectRule.project
    val model = NewBaselineProfilesModuleModel(
      project = project,
      moduleParent = ":",
      projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker(),
    ).apply {
      androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
      targetModule.value = project.findAppModule()
      useGradleKts.set(useGradleKtsParam)
      useGmd.set(useGmdParam)
    }

    model.handleFinished() // Generate module files

    projectRule.invokeTasks("assembleDebug").apply {
      buildError?.printStackTrace()
      Assert.assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }
}