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
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.npw.baselineprofiles.getBaselineProfilesMinSdk
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.RUN_CONFIGURATION_NAME
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.baselineProfileTaskName
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.chooseReleaseTargetApplicationId
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.getModuleNameForGradleTask
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.runConfigurationGradleTask
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.setupRunConfigurations
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BaselineProfilesModuleTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @get:Rule
  var tmpFolderRule = TemporaryFolder()

  private lateinit var testDisposable: Disposable

  @Before
  fun setup() {
    testDisposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun setupRunConfigurations() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)
    val appModule = projectRule.project.findAppModule()
    val runManager = RunManager.getInstance(appModule.project)

    setupRunConfigurations(appModule, runManager)

    assertConfigurationsSize(runManager.allConfigurationsList, 1)

    val generateRunConfiguration = runManager.allConfigurationsList.find {
      it.name == "$RUN_CONFIGURATION_NAME for ${appModule.getModuleNameForGradleTask()}"
    }
    assertThat(generateRunConfiguration).isNotNull()
    assertThat(runManager.selectedConfiguration?.name).isEqualTo(generateRunConfiguration?.name)
  }

  @Test
  fun runConfiguration() {
    val task = runConfigurationGradleTask("moduleName", "variantName", BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_BASELINE_PROFILE)
    assertThat(task).run {
      contains(":moduleName:${baselineProfileTaskName("variantName")}")
      contains(
        "-P${BaselineProfilesMacrobenchmarkCommon.FILTER_INSTR_ARG}=${BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_BASELINE_PROFILE}")
    }
  }

  @Test
  fun runConfigurationNoFilter() {
    val task = runConfigurationGradleTask("moduleName", "variantName", null)
    assertThat(task).run {
      contains(":moduleName:${baselineProfileTaskName("variantName")}")
      doesNotContain("-P${BaselineProfilesMacrobenchmarkCommon.FILTER_INSTR_ARG}")
    }
  }

  /**
   * We're checking +1 size, because of implicit app run configuration
   */
  private fun assertConfigurationsSize(configurationsList: List<RunConfiguration>, expectedSize: Int) =
    assertThat(configurationsList).hasSize(expectedSize + 1)

  @Test
  fun checkMinSdkUsesLowestPGOOrTargetModule() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

    val project = projectRule.project

    val appModule = project.findAppModule()

    val projectBuildModel = ProjectBuildModel.getOrLog(appModule.project)
    val targetModuleAndroidModel = projectBuildModel?.getModuleBuildModel(appModule)?.android()

    // Verify anything lower than LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION is not used
    val lowerThanPGO = SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION - 1
    targetModuleAndroidModel?.defaultConfig()?.minSdkVersion()?.setValue(lowerThanPGO)
    WriteCommandAction.runWriteCommandAction(project) { projectBuildModel?.applyChanges() }

    assertThat(getBaselineProfilesMinSdk(appModule)).isEqualTo(SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION)

    // Verify anything higher than LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION is preserved
    val higherThanPGO = SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION + 1
    targetModuleAndroidModel?.defaultConfig()?.minSdkVersion()?.setValue(higherThanPGO)
    WriteCommandAction.runWriteCommandAction(project) { projectBuildModel?.applyChanges() }

    assertThat(getBaselineProfilesMinSdk(appModule)).isEqualTo(higherThanPGO)
  }

  @Test
  fun testApplicationIdIsForFirstReleaseVariant() {

    fun mockIdeBasicVariant(name: String, applicationId: String): IdeBasicVariant {
      val mock = MockitoKt.mock<IdeBasicVariant>()
      whenever(mock.name).thenReturn(name)
      whenever(mock.applicationId).thenReturn(applicationId)
      return mock
    }

    // Basic case
    assertThat(chooseReleaseTargetApplicationId(
      basicVariants = listOf(
        mockIdeBasicVariant("debug", "com.test.debug"),
        mockIdeBasicVariant("release", "com.test.release")
      ),
      defaultValue = "com.test")).isEqualTo("com.test.release")

    // Release not found (this should never happen)
    assertThat(chooseReleaseTargetApplicationId(
      basicVariants = listOf(
        mockIdeBasicVariant("debug", "com.test.debug"),
        mockIdeBasicVariant("somethingElse", "com.test.release")
      ),
      defaultValue = "com.test")).isEqualTo("com.test")

    // Multiple flavors
    assertThat(chooseReleaseTargetApplicationId(
      basicVariants = listOf(
        mockIdeBasicVariant("freeDebug", "com.test.free.debug"),
        mockIdeBasicVariant("paidDebug", "com.test.paid.debug"),
        mockIdeBasicVariant("freeRelease", "com.test.free.release"),
        mockIdeBasicVariant("paidRelease", "com.test.paid.release"),
      ),
      defaultValue = "com.test")).isEqualTo("com.test.free.release")

    // Multidimensional flavors
    assertThat(chooseReleaseTargetApplicationId(
      basicVariants = listOf(
        mockIdeBasicVariant("freeOneDebug", "com.test.free.one.debug"),
        mockIdeBasicVariant("freeTwoDebug", "com.test.free.two.debug"),
        mockIdeBasicVariant("paidOneDebug", "com.test.paid.one.debug"),
        mockIdeBasicVariant("paidTwoDebug", "com.test.paid.two.debug"),
        mockIdeBasicVariant("freeOneRelease", "com.test.free.one.release"),
        mockIdeBasicVariant("freeTwoRelease", "com.test.free.two.release"),
        mockIdeBasicVariant("paidOneRelease", "com.test.paid.one.release"),
        mockIdeBasicVariant("paidTwoRelease", "com.test.paid.two.release"),
      ),
      defaultValue = "com.test")).isEqualTo("com.test.free.one.release")

    // Multidimensional flavors and multiple build types
    assertThat(chooseReleaseTargetApplicationId(
      basicVariants = listOf(
        mockIdeBasicVariant("freeOneDebug", "com.test.free.one.debug"),
        mockIdeBasicVariant("freeTwoDebug", "com.test.free.two.debug"),
        mockIdeBasicVariant("paidOneDebug", "com.test.paid.one.debug"),
        mockIdeBasicVariant("paidTwoDebug", "com.test.paid.two.debug"),
        mockIdeBasicVariant("freeOneRelease", "com.test.free.one.release"),
        mockIdeBasicVariant("freeTwoRelease", "com.test.free.two.release"),
        mockIdeBasicVariant("paidOneRelease", "com.test.paid.one.release"),
        mockIdeBasicVariant("paidTwoRelease", "com.test.paid.two.release"),
        mockIdeBasicVariant("freeOneStaging", "com.test.free.one.staging"),
        mockIdeBasicVariant("freeTwoStaging", "com.test.free.two.staging"),
        mockIdeBasicVariant("paidOneStaging", "com.test.paid.one.staging"),
        mockIdeBasicVariant("paidTwoStaging", "com.test.paid.two.staging"),
      ),
      defaultValue = "com.test")).isEqualTo("com.test.free.one.release")
  }
}
