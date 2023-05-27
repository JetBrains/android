/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness

/**
 * Unit tests for [GradleAndroidTestRunConfigurationExecutor].
 */
@RunWith(JUnit4::class)
class GradleAndroidTestRunConfigurationExecutorTest {
  @get:Rule
  val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  @Mock
  lateinit var mockGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker

  private val device = DeviceImpl(null, "serial_number", IDevice.DeviceState.ONLINE)

  private fun getEnv(executor: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(projectRule.project).createConfiguration("run test",
                                                                                         AndroidTestRunConfigurationType.getInstance().configurationFactories.single())
    val androidTestRunConfiguration = configSettings.configuration as AndroidTestRunConfiguration
    androidTestRunConfiguration.setModule(projectRule.module)

    androidTestRunConfiguration.TEST_NAME_REGEX = "testRegex"
    androidTestRunConfiguration.METHOD_NAME = "testMethod"
    androidTestRunConfiguration.CLASS_NAME = "com.example.test.TestClass"
    androidTestRunConfiguration.PACKAGE_NAME = "com.example.test"

    return ExecutionEnvironment(executor, DefaultStudioProgramRunner(), configSettings, projectRule.project)
  }

  @Test
  fun testTaskReturnsSuccessForAllInModuleTest() {
    val env = getEnv(DefaultRunExecutor.getRunExecutorInstance())
    val androidTestRunConfiguration = env.runProfile as AndroidTestRunConfiguration
    androidTestRunConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_MODULE
    val executor = object : GradleAndroidTestRunConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device))) {
      override fun gradleConnectedAndroidTestInvoker() = mockGradleConnectedAndroidTestInvoker
    }

    executor.run(EmptyProgressIndicator())

    verify(mockGradleConnectedAndroidTestInvoker).runGradleTask(eq(projectRule.project), eq(listOf(device)), eq("applicationId"), any(),
                                                                any(),/*waitForDebugger*/ eq(false), eq(""), eq(""), eq(""),
                                                                eq("testRegex"), any(), any())
  }

  @Test
  fun testTaskReturnsSuccessForAllInPackageTest() {
    val env = getEnv(DefaultRunExecutor.getRunExecutorInstance())
    val androidTestRunConfiguration = env.runProfile as AndroidTestRunConfiguration
    androidTestRunConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE
    val executor = object : GradleAndroidTestRunConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device))) {
      override fun gradleConnectedAndroidTestInvoker() = mockGradleConnectedAndroidTestInvoker
    }
    executor.run(EmptyProgressIndicator())

    verify(mockGradleConnectedAndroidTestInvoker).runGradleTask(eq(projectRule.project), eq(listOf(device)), eq("applicationId"), any(),
                                                                any(),/*waitForDebugger*/ eq(false), eq("com.example.test"), eq(""), eq(""),
                                                                eq(""), any(), any())
  }

  @Test
  fun testTaskReturnsSuccessForClassTest() {
    val env = getEnv(DefaultRunExecutor.getRunExecutorInstance())
    val androidTestRunConfiguration = env.runProfile as AndroidTestRunConfiguration
    androidTestRunConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_CLASS
    val executor = object : GradleAndroidTestRunConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device))) {
      override fun gradleConnectedAndroidTestInvoker() = mockGradleConnectedAndroidTestInvoker
    }
    executor.run(EmptyProgressIndicator())

    verify(mockGradleConnectedAndroidTestInvoker).runGradleTask(eq(projectRule.project), eq(listOf(device)), eq("applicationId"), any(),
                                                                any(),/*waitForDebugger*/ eq(false), eq(""),
                                                                eq("com.example.test.TestClass"), eq(""), eq(""), any(), any())
  }

  @Test
  fun testTaskReturnsSuccessForMethodTest() {
    val env = getEnv(DefaultRunExecutor.getRunExecutorInstance())
    val androidTestRunConfiguration = env.runProfile as AndroidTestRunConfiguration
    androidTestRunConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_METHOD
    val executor = object : GradleAndroidTestRunConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device))) {
      override fun gradleConnectedAndroidTestInvoker() = mockGradleConnectedAndroidTestInvoker
    }
    executor.run(EmptyProgressIndicator())

    verify(mockGradleConnectedAndroidTestInvoker).runGradleTask(eq(projectRule.project), eq(listOf(device)), eq("applicationId"), any(),
                                                                any(),/*waitForDebugger*/ eq(false), eq(""),
                                                                eq("com.example.test.TestClass"), eq("testMethod"), eq(""), any(), any())
  }

  @Test
  fun testTaskReturnsSuccessForAllInModuleTestWithRetention() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.YES, maxSnapshots = 5, compressSnapshots = true)
    val env = getEnv(DefaultRunExecutor.getRunExecutorInstance())
    val androidTestRunConfiguration = env.runProfile as AndroidTestRunConfiguration
    androidTestRunConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_MODULE
    androidTestRunConfiguration.RETENTION_ENABLED = EnableRetention.YES
    androidTestRunConfiguration.RETENTION_MAX_SNAPSHOTS = 5
    androidTestRunConfiguration.RETENTION_COMPRESS_SNAPSHOTS = true
    val executor = object : GradleAndroidTestRunConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device))) {
      override fun gradleConnectedAndroidTestInvoker() = mockGradleConnectedAndroidTestInvoker
    }
    executor.run(EmptyProgressIndicator())

    verify(mockGradleConnectedAndroidTestInvoker).runGradleTask(eq(projectRule.project), eq(listOf(device)), eq("applicationId"), any(),
                                                                any(),/*waitForDebugger*/ eq(false), eq(""), eq(""), eq(""),
                                                                eq("testRegex"), eq(retentionConfiguration), any())
  }

  private fun testTaskReturnsSuccessForAllInPackageTestWithRetention(retentionConfiguration: RetentionConfiguration) {
    val env = getEnv(DefaultRunExecutor.getRunExecutorInstance())
    val androidTestRunConfiguration = env.runProfile as AndroidTestRunConfiguration
    androidTestRunConfiguration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE

    androidTestRunConfiguration.RETENTION_ENABLED = retentionConfiguration.enabled
    androidTestRunConfiguration.RETENTION_MAX_SNAPSHOTS = retentionConfiguration.maxSnapshots
    androidTestRunConfiguration.RETENTION_COMPRESS_SNAPSHOTS = retentionConfiguration.compressSnapshots

    val executor = object : GradleAndroidTestRunConfigurationExecutor(env, DeviceFutures.forDevices(listOf(device))) {
      override fun gradleConnectedAndroidTestInvoker() = mockGradleConnectedAndroidTestInvoker
    }
    executor.run(EmptyProgressIndicator())

    verify(mockGradleConnectedAndroidTestInvoker).runGradleTask(eq(projectRule.project), eq(listOf(device)), eq("applicationId"), any(),
                                                                any(),/*waitForDebugger*/ eq(false), eq("com.example.test"), eq(""), eq(""),
                                                                eq(""), eq(retentionConfiguration), any())
  }

  @Test
  fun testTaskReturnsSuccessForAllInPackageTestWithRetentionEnabled() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.YES, maxSnapshots = 5, compressSnapshots = true)
    testTaskReturnsSuccessForAllInPackageTestWithRetention(retentionConfiguration)
  }

  @Test
  fun testTaskReturnsSuccessForAllInPackageTestWithRetentionDisabled() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.NO, maxSnapshots = 5, compressSnapshots = true)
    testTaskReturnsSuccessForAllInPackageTestWithRetention(retentionConfiguration)
  }

  @Test
  fun testTaskReturnsSuccessForAllInPackageTestWithRetentionUseGradle() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.USE_GRADLE, maxSnapshots = 5, compressSnapshots = true)
    testTaskReturnsSuccessForAllInPackageTestWithRetention(retentionConfiguration)
  }
}