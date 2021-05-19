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
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.GradleApkProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.RunInstantAppTask
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP_WITH_DYNAMIC_FEATURES
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class DynamicFeatureInstantAppTest : AndroidGradleTestCase(){
  private lateinit var configSettings : RunnerAndConfigurationSettings
  private lateinit var configuration : AndroidRunConfiguration
  val runner = DefaultStudioProgramRunner()
  private lateinit var ex : Executor
  private lateinit var env : ExecutionEnvironment

  private lateinit var appIdProvider : GradleApplicationIdProvider

  private lateinit var apkProvider : GradleApkProvider

  private lateinit var launchOptionsBuilder : LaunchOptions.Builder

  private lateinit var device : IDevice
  @Mock
  private lateinit var launchStatus: LaunchStatus
  private val consolePrinter = StubConsolePrinter()

  private lateinit var instantAppSdks : InstantAppSdks

  override fun setUp() {
    super.setUp()

    MockitoAnnotations.initMocks(this)

    loadProject(INSTANT_APP_WITH_DYNAMIC_FEATURES, "app")

    // Run build task for main variant.
    val taskName = AndroidModuleModel.get(myAndroidFacet)!!.selectedVariant.mainArtifact.assembleTaskName
    invokeGradleTasks(project, taskName)

    configSettings = RunManager.getInstance(project).createRunConfiguration("debug", AndroidRunConfigurationType.getInstance().factory)
    configuration = configSettings.configuration as AndroidRunConfiguration
    ex = DefaultDebugExecutor.getDebugExecutorInstance()
    env = ExecutionEnvironment(ex, runner, configSettings, project)

    appIdProvider = GradleApplicationIdProvider(myAndroidFacet)

    apkProvider = GradleApkProvider(myAndroidFacet, appIdProvider, false)
    device = Mockito.mock(IDevice::class.java)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(26, null))

    launchOptionsBuilder = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setSkipNoopApkInstallations(true)
      .setForceStopRunningApp(true)

    instantAppSdks = IdeComponents(null, testRootDisposable).mockApplicationService(InstantAppSdks::class.java)
  }

  fun testDeployInstantAppAsInstantAPK() {
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures = ImmutableList.of("dynamicfeature")

    launchOptionsBuilder.setDeployAsInstant(true)

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      myAndroidFacet,
      appIdProvider,
      apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    val deployTask = launchTasks.stream().filter{ x -> x is RunInstantAppTask }.findFirst().orElse(null)

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

  fun testDeployInstantAppAsInstantBundle() {
    configuration.DEPLOY_APK_FROM_BUNDLE = true
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures = ImmutableList.of("dynamicfeature")

    launchOptionsBuilder.setDeployAsInstant(true)

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      myAndroidFacet,
      appIdProvider,
      apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    val deployTask = launchTasks.stream().filter{ x -> x is RunInstantAppTask }.findFirst().orElse(null)

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

  fun testDeployInstantAppWithoutInstantCheckbox() {
    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      myAndroidFacet,
      appIdProvider,
      apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    assertThat(launchTasks.stream().filter{ x -> x is RunInstantAppTask }.findFirst().orElse(null)).isNull()
  }

  class StubConsolePrinter : ConsolePrinter {
    override fun stdout(message: String) {
    }

    override fun stderr(message: String) {
    }
  }
}