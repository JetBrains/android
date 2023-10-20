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

import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.SdkVersionInfo
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.npw.baselineprofiles.getBaselineProfilesMinSdk
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BENCHMARKS_CLASS_NAME
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.GENERATOR_CLASS_NAME
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.RUN_CONFIGURATION_NAME
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.baselineProfileTaskName
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.createTestClasses
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.getModuleNameForGradleTask
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.runConfigurationFilterArgument
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.runConfigurationGradleTask
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.setupRunConfigurations
import com.android.tools.idea.run.configuration.BP_PLUGIN_FILTERING_SUPPORTED
import com.android.tools.idea.run.configuration.BP_PLUGIN_MIN_SUPPORTED
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.mock.MockModule
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.io.File

class BaselineProfilesModuleTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

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
  fun createTestClasses_kotlin() = createTestClasses(Language.Kotlin)

  @Test
  fun createTestClasses_java() = createTestClasses(Language.Java)

  private fun createTestClasses(language: Language) {
    val targetApplicationId = "target_app_id"

    val projectTemplateDataMock = MockitoKt.mock<ProjectTemplateData>()
    whenever(projectTemplateDataMock.language).thenReturn(language)

    val newModule = MockitoKt.mock<ModuleTemplateData>()
    whenever(newModule.packageName).thenReturn("package_name")
    whenever(newModule.projectTemplateData).thenReturn(projectTemplateDataMock)
    whenever(newModule.srcDir).thenReturn(File(""))
    whenever(newModule.name).thenReturn("newModule")

    val targetModule = MockitoKt.mock<Module>()
    whenever(targetModule.name).thenReturn("targetModule")
    whenever(targetModule.project).thenReturn(projectRule.project)

    val mockExecutor = MockitoKt.mock<RecipeExecutor>()

    mockExecutor.createTestClasses(
      targetModule,
      newModule,
      targetApplicationId
    )

    verify(mockExecutor).run {
      val generatorFile = newModule.srcDir.resolve("$GENERATOR_CLASS_NAME.${language.extension}")
      save(MockitoKt.any(), eq(generatorFile))
      open(generatorFile)

      val benchmarksFile = newModule.srcDir.resolve("$BENCHMARKS_CLASS_NAME.${language.extension}")
      save(MockitoKt.any(), eq(benchmarksFile))
      open(benchmarksFile)
    }
  }

  @Test
  fun setupRunConfigurations_emptyVariants() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)
    val appModule = projectRule.project.findAppModule()
    val runManager = RunManager.getInstance(appModule.project)

    setupRunConfigurations(emptyList(), appModule, runManager)

    assertConfigurationsSize(runManager.allConfigurationsList, 1)

    val generateRunConfiguration = runManager.allConfigurationsList.find { it.name == RUN_CONFIGURATION_NAME }
    assertThat(generateRunConfiguration).isNotNull()
    assertThat(runManager.selectedConfiguration?.name).isEqualTo(generateRunConfiguration?.name)
  }

  @Test
  fun setupRunConfigurations_oneVariant() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)
    val appModule = projectRule.project.findAppModule()

    val runManager = RunManager.getInstance(appModule.project)
    val variants = listOf("release")

    setupRunConfigurations(variants, appModule, runManager)

    assertConfigurationsSize(runManager.allConfigurationsList, variants.size)

    val generateRunConfiguration = runManager.allConfigurationsList.find { it.name == RUN_CONFIGURATION_NAME }
    assertThat(generateRunConfiguration).isNotNull()
    assertThat(runManager.selectedConfiguration?.name).isEqualTo(generateRunConfiguration?.name)
  }

  @Test
  fun setupRunConfigurations_moreVariants() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)
    val appModule = projectRule.project.findAppModule()
    val runManager = RunManager.getInstance(appModule.project)
    val variants = listOf("demoFree", "demoPaid", "prodFree", "prodPaid")

    setupRunConfigurations(variants, appModule, runManager)

    assertConfigurationsSize(runManager.allConfigurationsList, variants.size)

    val configNames = runManager.allConfigurationsList.map { it.name }
    variants.map { "$RUN_CONFIGURATION_NAME [$it]" }.forEach {
      assertThat(configNames).contains(it)
    }
  }

  @Test
  fun getModuleNameForGradleTask() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)
    val project = projectRule.project

    val appModule = project.findAppModule()

    assertThat(appModule.getModuleNameForGradleTask()).isEqualTo("app")
  }


  private class MockProjectWithName(
    private val myName: String,
    parentDisposable: Disposable
  ) : MockProjectEx(
    parentDisposable) {
    override fun getName(): String {
      return myName
    }
  }

  @Test
  fun getModuleNameForGradleTaskWithSpaces() {
    val projectWithName = MockProjectWithName("My Application is great", testDisposable)
    val module = MockModule(projectWithName, testDisposable)
    module.setName("My_Application_is_great.app")
    assertThat(module.getModuleNameForGradleTask()).isEqualTo("app")
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
  fun runConfigurationFilterArgumentNotSupportedByPlugin() {
    listOf(
      BP_PLUGIN_MIN_SUPPORTED,
      AgpVersion(BP_PLUGIN_FILTERING_SUPPORTED.major, BP_PLUGIN_FILTERING_SUPPORTED.minor - 1, 0),
    ).forEach {
      val argument = runConfigurationFilterArgument(it)

      assertThat(argument).contains(BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_BASELINE_PROFILE)
    }
  }

  @Test
  fun runConfigurationFilterArgumentSupportedByPlugin() {
    // Test version when plugin adds support and some arbitrary higher version
    listOf(
      BP_PLUGIN_FILTERING_SUPPORTED,
      AgpVersion(BP_PLUGIN_FILTERING_SUPPORTED.major, BP_PLUGIN_FILTERING_SUPPORTED.minor + 1, 0),
    ).forEach {
      val argument = runConfigurationFilterArgument(it)

      assertThat(argument).isNull()
    }
  }
}