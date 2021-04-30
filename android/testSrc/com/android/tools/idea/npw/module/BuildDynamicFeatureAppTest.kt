/*
 * Copyright (C) 2021 The Android Open Source Project
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


import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.tools.idea.npw.dynamicapp.DynamicFeatureModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito


@RunWith(Parameterized::class)
class BuildDynamicFeatureAppTest(private val useGradleKts: Boolean) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useGradleKts={0}")
    fun data() = listOf(false, true)
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun addNewDynamicFeatureModule() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    val project = projectRule.project
    val model = DynamicFeatureModel(
      project = project, moduleParent = ":", projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker(),
      isInstant = false, templateName = "Dynamic Feature", templateDescription = "Dynamic Feature description"
    )

    model.androidSdkInfo.value = createAndroidVersionItem()
    model.baseApplication.value = project.findAppModule() // Dynamic Feature base module
    model.useGradleKts.set(useGradleKts)

    model.handleFinished() // Generate module files

    projectRule.invokeTasks("assembleDebug").apply {
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }

  private fun createAndroidVersionItem(): AndroidVersionsInfo.VersionItem {
    val apiLevel = HIGHEST_KNOWN_STABLE_API
    return Mockito.mock(AndroidVersionsInfo::class.java).VersionItem(object : MockPlatformTarget(apiLevel, 0) {
      override fun getVersion(): AndroidVersion = AndroidVersion(apiLevel)
    })
  }
}