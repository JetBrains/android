/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.instantapp

import com.android.ddmlib.IDevice
import com.android.sdklib.devices.Abi
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.RunInstantAppTask
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.mockDeviceFor
import com.android.tools.idea.util.androidFacet
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class DynamicFeatureInstantAppTest  {

  private lateinit var configSettings: RunnerAndConfigurationSettings
  private lateinit var configuration: AndroidRunConfiguration
  val runner = DefaultStudioProgramRunner()
  private lateinit var ex: Executor
  private lateinit var env: ExecutionEnvironment
  private lateinit var androidFacet: AndroidFacet

  private val Project.apkProvider: ApkProvider get() = getProjectSystem().getApkProvider(configuration)!!
  private val Project.applicationIdProvider: ApplicationIdProvider get() = getProjectSystem().getApplicationIdProvider(configuration)!!
  private lateinit var launchOptionsBuilder: LaunchOptions.Builder

  private lateinit var device: IDevice

  @Mock
  private lateinit var launchStatus: LaunchStatus
  private val consolePrinter = StubConsolePrinter()

  private lateinit var instantAppSdks: InstantAppSdks

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  private fun runTest(test: (Project) -> Unit) {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.INSTANT_APP_WITH_DYNAMIC_FEATURES)
    preparedProject.open { project ->
      configSettings = RunManager.getInstance(project).allSettings.single { it.configuration is AndroidRunConfiguration }
      configuration = configSettings.configuration as AndroidRunConfiguration

      device = mockDeviceFor(26, listOf(Abi.X86_64, Abi.X86))

      ex = DefaultDebugExecutor.getDebugExecutorInstance()
      env = ExecutionEnvironment(ex, runner, configSettings, project)

      launchOptionsBuilder = LaunchOptions.builder()
        .setClearLogcatBeforeStart(false)

      instantAppSdks = IdeComponents(null, projectRule.fixture.testRootDisposable)
        .mockApplicationService(InstantAppSdks::class.java)

      androidFacet = project.gradleModule(":app")?.androidFacet!!

      test(project)
    }
  }

  @Test
  fun testDeployInstantAppAsInstantAPK() = runTest { project ->
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures = listOf(
      project.gradleModule(":dynamicfeature")!!.name
    )
    configuration.executeMakeBeforeRunStepInTest(device)

    launchOptionsBuilder.setDeployAsInstant(true)

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      androidFacet,
      project.applicationIdProvider,
      project.apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    val deployTask = launchTasks.stream().filter { x -> x is RunInstantAppTask }.findFirst().orElse(null)

    assertThat(deployTask).isNotNull()
    assertInstanceOf(deployTask, RunInstantAppTask::class.java)
    assertThat((deployTask as RunInstantAppTask).packages.size == 1)
    val apkInfo = deployTask.packages.iterator().next()
    assertThat(apkInfo.files.size == 2)
    assertThat(apkInfo.files[0].moduleName.equals("app"))
    assertThat(apkInfo.files[0].apkFile.name.equals("app-debug.apk"))
    assertThat(apkInfo.files[1].moduleName.equals("instantdynamicfeature"))
    assertThat(apkInfo.files[1].apkFile.name.equals("instantdynamicfeature-debug.apk"))
  }

  @Test
  fun testDeployInstantAppAsInstantBundle() = runTest { project ->
    configuration.DEPLOY_APK_FROM_BUNDLE = true
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures = ImmutableList.of(project.gradleModule(":dynamicfeature")!!.name)
    configuration.executeMakeBeforeRunStepInTest(device)

    launchOptionsBuilder.setDeployAsInstant(true)

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      androidFacet,
      project.applicationIdProvider,
      project.apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    val deployTask = launchTasks.stream().filter { x -> x is RunInstantAppTask }.findFirst().orElse(null)

    assertThat(deployTask).isNotNull()
    assertInstanceOf(deployTask, RunInstantAppTask::class.java)
    assertThat((deployTask as RunInstantAppTask).packages.size == 1)
    val apkInfo = deployTask.packages.iterator().next()
    assertThat(apkInfo.files.size == 2)
    assertThat(apkInfo.files[0].moduleName.equals("app"))
    assertThat(apkInfo.files[0].apkFile.name.equals("app-debug.apk"))
    assertThat(apkInfo.files[1].moduleName.equals("instantdynamicfeature"))
    assertThat(apkInfo.files[1].apkFile.name.equals("instantdynamicfeature-debug.apk"))
  }

  @Test
  fun testDeployInstantAppWithoutInstantCheckbox() = runTest { project ->
    configuration.executeMakeBeforeRunStepInTest(device)

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      androidFacet,
      project.applicationIdProvider,
      project.apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    assertThat(launchTasks.stream().filter { x -> x is RunInstantAppTask }.findFirst().orElse(null)).isNull()
  }

  class StubConsolePrinter : ConsolePrinter {
    override fun stdout(message: String) {
    }

    override fun stderr(message: String) {
    }
  }
}