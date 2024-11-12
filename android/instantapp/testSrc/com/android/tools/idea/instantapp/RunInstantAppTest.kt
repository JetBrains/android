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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoCleanerRule
import com.android.tools.deployer.Deployer
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.projectsystem.applicationProjectContextForTests
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationExecutor
import com.android.tools.idea.run.FakeAndroidDevice
import com.android.tools.idea.run.configuration.execution.createApp
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.mockDeviceFor
import com.google.android.instantapps.sdk.api.ExtendedSdk
import com.google.android.instantapps.sdk.api.RunHandler
import com.google.android.instantapps.sdk.api.StatusCode
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.replaceService
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RunInstantAppTest {
  private val device =
    mockDeviceFor(AndroidVersion(AndroidVersion.VersionCodes.O), listOf(Abi.X86_64, Abi.X86))
  private val applicationDeployer = mock<ApplicationDeployer>()

  private lateinit var runHandler: RunHandler
  private lateinit var configSettings: RunnerAndConfigurationSettings
  private val configuration
    get() = configSettings.configuration as AndroidRunConfiguration

  @get:Rule
  val projectRule =
    AndroidProjectRule.testProject(AndroidCoreTestProject.INSTANT_APP_WITH_DYNAMIC_FEATURES)

  @get:Rule val cleaner = MockitoCleanerRule()

  @Before
  fun setUp() {
    runHandler = mock<RunHandler>()

    val extendedSdk = mock<ExtendedSdk>()
    whenever(extendedSdk.runHandler).thenReturn(runHandler)

    val instantAppSdks =
      object : InstantAppSdks() {
        override fun loadLibrary(attemptUpgrades: Boolean): ExtendedSdk {
          return extendedSdk
        }
      }
    projectRule.replaceService(InstantAppSdks::class.java, instantAppSdks)
    configSettings =
      RunManager.getInstance(projectRule.project).allSettings.single {
        it.configuration is AndroidRunConfiguration
      }
  }

  private fun getExecutor(): AndroidRunConfigurationExecutor {
    val runner =
      ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configSettings.configuration)!!
    val env =
      ExecutionEnvironment(
        DefaultRunExecutor.getRunExecutorInstance(),
        runner,
        configSettings,
        projectRule.project,
      )

    return AndroidRunConfigurationExecutor(
      (configSettings.configuration as ModuleBasedConfiguration<*, *>)
        .applicationProjectContextForTests,
      env,
      FakeAndroidDevice.forDevices(listOf(device)),
      projectRule.project.getProjectSystem().getApkProvider(configSettings.configuration)!!,
      applicationDeployer = applicationDeployer,
    )
  }

  @Test
  fun testDeployInstantAppAsInstantAPK() {

    // PREPARE
    configuration.DEPLOY_AS_INSTANT = true
    configuration.executeMakeBeforeRunStepInTest(device)

    whenever(
        runHandler.runApks(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
      )
      .thenReturn(StatusCode.SUCCESS)

    // RUN
    ProgressManager.getInstance()
      .runProcess(
        Computable { getExecutor().run(ProgressManager.getInstance().progressIndicator) },
        EmptyProgressIndicator(),
      )

    val captor = argumentCaptor<ImmutableList<File>>()
    val serialNumber = device.serialNumber

    verify(runHandler, times(1))
      .runApks(captor.capture(), isNull(), anyOrNull(), eq(serialNumber), anyOrNull(), any(), any())

    val apks = (captor.firstValue as List<File>).map { it.name.substringAfterLast('/') }
    assertThat(apks)
      .containsExactly(
        "app-debug.apk",
        "instantdynamicfeature-debug.apk",
        "dynamicfeature-debug.apk",
      )
  }

  @Test
  fun testDeployInstantAppAsInstantAPKWithDisabledFeature() {

    // PREPARE
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures =
      listOf(projectRule.project.gradleModule(":dynamicfeature")!!.name)
    configuration.executeMakeBeforeRunStepInTest(device)

    whenever(
        runHandler.runApks(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
      )
      .thenReturn(StatusCode.SUCCESS)

    // RUN
    ProgressManager.getInstance()
      .runProcess(
        Computable { getExecutor().run(ProgressManager.getInstance().progressIndicator) },
        EmptyProgressIndicator(),
      )

    val captor = argumentCaptor<ImmutableList<File>>()
    val serialNumber = device.serialNumber

    verify(runHandler, times(1))
      .runApks(captor.capture(), isNull(), anyOrNull(), eq(serialNumber), anyOrNull(), any(), any())

    val apks = (captor.firstValue as List<File>).map { it.name.substringAfterLast('/') }
    assertThat(apks).containsExactly("app-debug.apk", "instantdynamicfeature-debug.apk")
  }

  @Test
  fun testDeployInstantAppAsInstantBundle() {

    // PREPARE
    configuration.DEPLOY_APK_FROM_BUNDLE = true
    configuration.DEPLOY_AS_INSTANT = true
    configuration.disabledDynamicFeatures =
      ImmutableList.of(projectRule.project.gradleModule(":dynamicfeature")!!.name)
    configuration.executeMakeBeforeRunStepInTest(device)

    whenever(
        runHandler.runApks(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
      )
      .thenReturn(StatusCode.SUCCESS)

    // RUN
    ProgressManager.getInstance()
      .runProcess(
        Computable { getExecutor().run(ProgressManager.getInstance().progressIndicator) },
        EmptyProgressIndicator(),
      )

    val captor = argumentCaptor<ImmutableList<File>>()
    val serialNumber = device.serialNumber

    verify(runHandler, times(1))
      .runApks(captor.capture(), isNull(), anyOrNull(), eq(serialNumber), anyOrNull(), any(), any())

    val apks = (captor.firstValue as List<File>).map { it.name.substringAfterLast('/') }
    assertThat(apks)
      .containsExactly(
        "instant-base-master.apk",
        "instant-instantdynamicfeature-master.apk",
        "instant-base-mdpi.apk",
      )
  }

  @Test
  fun testDeployInstantAppWithoutInstantCheckbox() {
    configuration.executeMakeBeforeRunStepInTest(device)
    configuration.MODE = AndroidRunConfiguration.DO_NOTHING

    whenever(applicationDeployer.fullDeploy(eq(device), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(
        Deployer.Result(
          false,
          false,
          false,
          createApp(device, "google.simpleapplication", emptyList(), emptyList()),
        )
      )

    // RUN
    ProgressManager.getInstance()
      .runProcess(
        Computable { getExecutor().run(ProgressManager.getInstance().progressIndicator) },
        EmptyProgressIndicator(),
      )

    verify(runHandler, never())
      .runZip(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
    verify(runHandler, never())
      .runApks(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
  }
}
