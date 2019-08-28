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
import com.android.tools.idea.run.*
import com.android.tools.idea.run.tasks.DynamicAppDeployTaskContext
import com.android.tools.idea.run.tasks.RunInstantAppTask
import com.android.tools.idea.run.tasks.SplitApkDeployTask
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
import org.mockito.Mockito

class DynamicFeatureInstantAppTest : AndroidGradleTestCase(){
  private lateinit var configSettings : RunnerAndConfigurationSettings
  private lateinit var configuration : AndroidAppRunConfigurationBase
  val runner = AndroidProgramRunner()
  private lateinit var ex : Executor
  private lateinit var env : ExecutionEnvironment

  private lateinit var appIdProvider : GradleApplicationIdProvider

  private lateinit var apkProvider : GradleApkProvider

  private lateinit var launchOptionsBuilder : LaunchOptions.Builder

  private val device = Mockito.mock(IDevice::class.java)
  private val launchStatus = StubLaunchStatus()
  private val consolePrinter = StubConsolePrinter()

  private lateinit var instantAppSdks : InstantAppSdks

  override fun setUp() {
    super.setUp()

    loadProject(INSTANT_APP_WITH_DYNAMIC_FEATURES, "app")

    configSettings = RunManager.getInstance(project).createRunConfiguration("debug", AndroidRunConfigurationType.getInstance().configurationFactories[0])
    configuration = configSettings.configuration as AndroidAppRunConfigurationBase
    ex = DefaultDebugExecutor.getDebugExecutorInstance()
    env = ExecutionEnvironment(ex, runner, configSettings, project)

    appIdProvider = GradleApplicationIdProvider(myAndroidFacet)

    apkProvider = GradleApkProvider(myAndroidFacet, appIdProvider, false)

    launchOptionsBuilder = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setSkipNoopApkInstallations(true)
      .setForceStopRunningApp(true)

    instantAppSdks = IdeComponents.mockApplicationService(InstantAppSdks::class.java, testRootDisposable)
  }

  fun testDeployInstantAppAsInstantAPK() {
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures = ImmutableList.of("dynamicfeature")

    launchOptionsBuilder.setDeployAsInstant(true)

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      myAndroidFacet,
      null,
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
      null,
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
      null,
      appIdProvider,
      apkProvider,
      launchOptionsBuilder.build()
    )

    val launchTasks = launchTaskProvider.getTasks(device, launchStatus, consolePrinter)

    assertThat(launchTasks.stream().filter{ x -> x is RunInstantAppTask }.findFirst().orElse(null)).isNull()
    val deployTask = launchTasks.stream().filter{ x -> x is SplitApkDeployTask }.findFirst().orElse(null)

    assertThat(deployTask).isNotNull()
    assertInstanceOf(deployTask, SplitApkDeployTask::class.java)
    assertInstanceOf((deployTask as SplitApkDeployTask).context, DynamicAppDeployTaskContext::class.java)

    val context = deployTask.context
    assertThat(context.artifacts.size).isEqualTo(3)
    assertThat(context.artifacts[0].name).isEqualTo("app-debug.apk")
    assertThat(context.artifacts[1].name).isEqualTo("dynamicfeature-debug.apk")
    assertThat(context.artifacts[2].name).isEqualTo("instantdynamicfeature-debug.apk")
  }

  class StubConsolePrinter : ConsolePrinter {
    override fun stdout(message: String) {
    }

    override fun stderr(message: String) {
    }
  }

  class StubLaunchStatus : LaunchStatus {
    override fun isLaunchTerminated(): Boolean {
      return false
    }

    override fun terminateLaunch(reason: String?) {
    }
  }
}