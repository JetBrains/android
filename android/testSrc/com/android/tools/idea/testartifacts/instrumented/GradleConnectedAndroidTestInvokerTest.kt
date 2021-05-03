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
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.testFramework.ProjectRule
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.nullable
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

/**
 * Unit tests for [GradleConnectedAndroidTestInvoker].
 */
@RunWith(JUnit4::class)
class GradleConnectedAndroidTestInvokerTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock lateinit var mockExecutor: Executor
  @Mock lateinit var mockHandler: ProcessHandler
  @Mock lateinit var mockLaunchStatus: LaunchStatus
  @Mock lateinit var mockPrinter: ConsolePrinter
  @Mock lateinit var mockProcessHandler: ProcessHandler
  @Mock lateinit var mockAndroidTestResultListener: AndroidTestResultListener
  @Mock lateinit var mockAndroidModuleModel: AndroidModuleModel
  @Mock lateinit var mockGradleTaskManager: GradleTaskManager
  private lateinit var mockDevices: List<IDevice>

  private val directExecutor: ExecutorService = MoreExecutors.newDirectExecutorService()

  @Before
  fun setup() {
    `when`(mockProcessHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)).thenReturn(mockAndroidTestResultListener)
    `when`(mockAndroidModuleModel.selectedVariantName).thenReturn("debug")
  }

  private fun createGradleConnectedAndroidTestInvoker(
    numDevices: Int = 1
  ) : GradleConnectedAndroidTestInvoker {
    mockDevices = (1..numDevices).map { deviceIndex ->
      mock<IDevice>().apply {
        `when`(serialNumber).thenReturn("DEVICE_SERIAL_NUMBER_${deviceIndex}")
        `when`(version).thenReturn(AndroidVersion(29))
      }
    }.toList()
    return  GradleConnectedAndroidTestInvoker(
      numDevices,
      backgroundTaskExecutor = directExecutor::submit,
      gradleTaskManagerFactory = { mockGradleTaskManager })
  }

  @Test
  fun executeTaskSingleDevice() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = false, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      any(),
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun doNotExecuteUntilAllDevicesAreScheduled() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker(numDevices = 2)

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = false, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager, never()).executeTasks(
      any(),
      anyList(),
      anyString(),
      any(),
      nullable(String::class.java),
      any()
    )

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = false, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[1], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      any(),
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForAllInPackageTestWithSingleDevice() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = false, testPackageName = "packageName", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.testInstrumentationRunnerArguments.package=packageName") &&
          env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_1"
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForClassTestWithMultipleDevices() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker(numDevices = 2)

    mockDevices.forEach { device ->
      gradleConnectedTestInvoker.schedule(
        projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
        waitForDebugger = false, testPackageName = "", testClassName = "testClassName", testMethodName = "",
        device, RetentionConfiguration())
    }

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.testInstrumentationRunnerArguments.class=testClassName") &&
          env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_1,DEVICE_SERIAL_NUMBER_2"
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTest() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = false, testPackageName = "", testClassName = "testClassName", testMethodName = "testMethodName",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.testInstrumentationRunnerArguments.class=testClassName#testMethodName")
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTestWithDebugger() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = true, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.testInstrumentationRunnerArguments.debug=true")
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun useUnifiedTestPlatformFlagShouldBeEnabledInGradleExecutionSettings() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = true, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.experimental.androidTest.useUnifiedTestPlatform=true") &&
          arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures=0")
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun retentionEnabledTest() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.YES, maxSnapshots = 5, compressSnapshots = true)
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = true, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], retentionConfiguration)

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.experimental.androidTest.useUnifiedTestPlatform=true") &&
          arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures=5") &&
          arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.compressSnapshots=true")
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun retentionUseGradleTest() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.USE_GRADLE, maxSnapshots = 5, compressSnapshots = true)
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = true, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], retentionConfiguration)

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pandroid.experimental.androidTest.useUnifiedTestPlatform=true") &&
          !arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures")
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun utpTestResultsReportShouldBeEnabled() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = true, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      anyList(),
      anyString(),
      argThat {
        it?.run {
          arguments.contains("-Pcom.android.tools.utp.GradleAndroidProjectResolverExtension.enable=true")
        } ?: false
      },
      nullable(String::class.java),
      any()
    )
  }

  @Test
  fun testTaskNamesMatchSelectedBuildVariant() {
    `when`(mockAndroidModuleModel.selectedVariantName).thenReturn("nonDefaultBuildVariant")

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.schedule(
      projectRule.project, "taskId", mockProcessHandler, mockPrinter, mockAndroidModuleModel,
      waitForDebugger = true, testPackageName = "", testClassName = "", testMethodName = "",
      mockDevices[0], RetentionConfiguration())

    verify(mockGradleTaskManager).executeTasks(
      any(),
      eq(listOf("connectedNonDefaultBuildVariantAndroidTest")),
      anyString(),
      any(),
      nullable(String::class.java),
      any()
    )
  }
}