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

import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MACROBENCHMARK
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MICROBENCHMARK
import com.android.tools.idea.npw.benchmark.NewBenchmarkModuleModel
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

@RunWith(Parameterized::class)
class BenchmarkModuleTest(private val useGradleKts: Boolean) {
  companion object {
    @JvmStatic @Parameterized.Parameters(name = "useGradleKts={0}") fun data() = listOf(false, true)
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion())

  @Test
  fun addNewMicrobenchmarkModule() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION, agpVersion = getAgpVersion())

    val project = projectRule.project
    val model =
      NewBenchmarkModuleModel(
          project = project,
          moduleParent = ":",
          projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker(),
        )
        .apply {
          // SimpleApplication app minSdkVersion
          androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(21)
          packageName.set("template.test.pkg")
          benchmarkModuleType.set(MICROBENCHMARK)
          useGradleKts.set(this@BenchmarkModuleTest.useGradleKts)
        }

    model.handleFinished() // Generate module files

    projectRule.invokeTasks(10000, "assembleDebug").apply {
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }

  @Test
  fun addNewMacrobenchmarkModule() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION, agpVersion = getAgpVersion())

    val project = projectRule.project
    val model =
      NewBenchmarkModuleModel(
          project = project,
          moduleParent = ":",
          projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker(),
        )
        .apply {
          // Lowest supported min sdk for macrobenchmark
          androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(23)
          benchmarkModuleType.set(MACROBENCHMARK)
          targetModule.value = project.findAppModule()
          useGradleKts.set(this@BenchmarkModuleTest.useGradleKts)
        }

    model.handleFinished() // Generate module files

    projectRule.invokeTasks(10000, "assembleDebug").apply {
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }
}
