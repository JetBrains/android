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
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness

/**
 * Unit tests for [GradleAndroidTestApplicationLaunchTask].
 */
@RunWith(JUnit4::class)
class GradleAndroidTestApplicationLaunchTaskTest {
  @get:Rule
  val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock lateinit var mockProject: Project
  @Mock lateinit var mockExecutor: Executor
  @Mock lateinit var mockHandler: ProcessHandler
  @Mock lateinit var mockLaunchStatus: LaunchStatus
  @Mock lateinit var mockPrinter: ConsolePrinter
  @Mock lateinit var mockProcessHandler: ProcessHandler
  @Mock lateinit var mockAndroidTestResultListener:  AndroidTestResultListener
  @Mock lateinit var mockAndroidModuleModel: AndroidModuleModel
  @Mock lateinit var mockDevice: IDevice
  @Mock lateinit var mockGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker
  val retentionConfiguration = RetentionConfiguration()

  @Before
  fun setup() {
    `when`(mockAndroidModuleModel.modelVersion).thenReturn(GradleVersion(7, 0))
  }

  @Test
  fun testTaskReturnsSuccessForAllInModuleTest() {
    val launchTask = GradleAndroidTestApplicationLaunchTask.allInModuleTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration
    )

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isTrue()
    verify(mockGradleConnectedAndroidTestInvoker).schedule(
      eq(mockProject),
      eq("taskId"),
      eq(mockProcessHandler),
      eq(mockPrinter),
      eq(mockAndroidModuleModel),
      eq(false),
      eq(""),
      eq(""),
      eq(""),
      eq(mockDevice),
      eq(retentionConfiguration)
    )
  }

  @Test
  fun testTaskReturnsSuccessForAllInPackageTest() {
    val launchTask = GradleAndroidTestApplicationLaunchTask.allInPackageTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      "com.example.test",
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration)

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isTrue()
    verify(mockGradleConnectedAndroidTestInvoker).schedule(
      eq(mockProject),
      eq("taskId"),
      eq(mockProcessHandler),
      eq(mockPrinter),
      eq(mockAndroidModuleModel),
      eq(false),
      eq("com.example.test"),
      eq(""),
      eq(""),
      eq(mockDevice),
      eq(retentionConfiguration)
    )
  }

  @Test
  fun testTaskReturnsSuccessForClassTest() {
    val launchTask = GradleAndroidTestApplicationLaunchTask.classTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      "com.example.test.TestClass",
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration)

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isTrue()
    verify(mockGradleConnectedAndroidTestInvoker).schedule(
      eq(mockProject),
      eq("taskId"),
      eq(mockProcessHandler),
      eq(mockPrinter),
      eq(mockAndroidModuleModel),
      eq(false),
      eq(""),
      eq("com.example.test.TestClass"),
      eq(""),
      eq(mockDevice),
      eq(retentionConfiguration)
    )
  }

  @Test
  fun testTaskReturnsSuccessForMethodTest() {
    val launchTask = GradleAndroidTestApplicationLaunchTask.methodTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      "com.example.test.TestClass",
      "testMethod",
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration)

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isTrue()
    verify(mockGradleConnectedAndroidTestInvoker).schedule(
      eq(mockProject),
      eq("taskId"),
      eq(mockProcessHandler),
      eq(mockPrinter),
      eq(mockAndroidModuleModel),
      eq(false),
      eq(""),
      eq("com.example.test.TestClass"),
      eq("testMethod"),
      eq(mockDevice),
      eq(retentionConfiguration)
    )
  }

  @Test
  fun testTaskReturnsSuccessForAllInModuleTestWithRetention() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.YES, maxSnapshots = 5, compressSnapshots = true)
    val launchTask = GradleAndroidTestApplicationLaunchTask.allInModuleTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration
    )

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isTrue()
    verify(mockGradleConnectedAndroidTestInvoker).schedule(
      eq(mockProject),
      eq("taskId"),
      eq(mockProcessHandler),
      eq(mockPrinter),
      eq(mockAndroidModuleModel),
      eq(false),
      eq(""),
      eq(""),
      eq(""),
      eq(mockDevice),
      eq(retentionConfiguration)
    )
  }

  private fun testTaskReturnsSuccessForAllInPackageTestWithRetention(retentionConfiguration: RetentionConfiguration) {
    val launchTask = GradleAndroidTestApplicationLaunchTask.allInPackageTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      "com.example.test",
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration)

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isTrue()
    verify(mockGradleConnectedAndroidTestInvoker).schedule(
      eq(mockProject),
      eq("taskId"),
      eq(mockProcessHandler),
      eq(mockPrinter),
      eq(mockAndroidModuleModel),
      eq(false),
      eq("com.example.test"),
      eq(""),
      eq(""),
      eq(mockDevice),
      eq(retentionConfiguration)
    )
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

  @Test
  fun testTaskReturnsFailedIfAGPVersionIsTooOld() {
    `when`(mockAndroidModuleModel.modelVersion).thenReturn(GradleVersion(6, 0))

    val launchTask = GradleAndroidTestApplicationLaunchTask.allInModuleTest(
      mockProject,
      mockAndroidModuleModel,
      "taskId",
      /*waitForDebugger*/false,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      mockGradleConnectedAndroidTestInvoker,
      retentionConfiguration)

    val result = launchTask.run(LaunchContext(mockProject, mockExecutor, mockDevice, mockLaunchStatus, mockPrinter, mockHandler))

    assertThat(result.success).isFalse()
    assertThat(result.errorId).isEqualTo("ANDROID_TEST_AGP_VERSION_TOO_OLD")
  }
}