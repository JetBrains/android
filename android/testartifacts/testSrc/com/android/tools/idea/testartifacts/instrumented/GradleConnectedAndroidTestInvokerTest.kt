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
import com.android.resources.Density
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.task.ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.GradleTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import java.util.concurrent.ExecutorService

/**
 * Unit tests for [GradleConnectedAndroidTestInvoker].
 */
@RunWith(JUnit4::class)
class GradleConnectedAndroidTestInvokerTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val mockitoJunitRule = MockitoJUnit.rule()

  @Mock
  lateinit var mockExecutionEnvironment: ExecutionEnvironment

  @Mock
  lateinit var mockAndroidTestSuiteView: AndroidTestSuiteView

  @Mock lateinit var mockAndroidModuleModel: GradleAndroidModel
  @Mock lateinit var mockGradleTaskManager: GradleTaskManager
  @Mock lateinit var mockModuleData: ModuleData
  @Mock lateinit var mockBuildToolWindow: ToolWindow
  private lateinit var mockDevices: List<IDevice>
  private lateinit var mockGradleTestResultAdapters: List<GradleTestResultAdapter>

  private val directExecutor: ExecutorService = MoreExecutors.newDirectExecutorService()

  @Before
  fun setup() {
    val mockAndroidProject = Mockito.mock(IdeAndroidProject::class.java).also {
      whenever(it.projectType).thenReturn(IdeAndroidProjectType.PROJECT_TYPE_APP)
    }
    whenever(mockAndroidModuleModel.selectedVariantName).thenReturn("debug")
    whenever(mockAndroidModuleModel.androidProject).thenReturn(mockAndroidProject)
    whenever(mockAndroidModuleModel.selectedVariantCore).thenReturn(Mockito.mock(IdeVariantCore::class.java))
    whenever(mockAndroidModuleModel.getGradleConnectedTestTaskNameForSelectedVariant()).thenCallRealMethod()
    whenever(mockModuleData.id).thenReturn(":app")
    whenever(mockModuleData.getProperty(eq("gradleIdentityPath"))).thenReturn(":app")
    whenever(mockBuildToolWindow.isAvailable).thenReturn(true)
    whenever(mockBuildToolWindow.isVisible).thenReturn(false)
  }

  private fun createGradleConnectedAndroidTestInvoker(
    numDevices: Int = 1
  ) : GradleConnectedAndroidTestInvoker {
    mockDevices = (1..numDevices).map { deviceIndex ->
      mock<IDevice>().apply {
        whenever(serialNumber).thenReturn("DEVICE_SERIAL_NUMBER_${deviceIndex}")
        whenever(version).thenReturn(AndroidVersion(29))
      }
    }.toList()
    mockGradleTestResultAdapters = (1..numDevices).map { deviceIndex ->
      mock<GradleTestResultAdapter>().apply {
        whenever(device).thenReturn(AndroidDevice(
          id = "DEVICE_SERIAL_NUMBER_${deviceIndex}",
          deviceName = "DEVICE_SERIAL_NUMBER_${deviceIndex}",
          avdName = "avdName",
          deviceType = AndroidDeviceType.LOCAL_PHYSICAL_DEVICE,
          version = AndroidVersion(29)
        ))
        whenever(iDevice).thenReturn(mockDevices[deviceIndex - 1])
        whenever(needRerunWithUninstallIncompatibleApkOption()).thenReturn(GradleTestResultAdapter.UtpInstallResult())
      }
    }
    return  GradleConnectedAndroidTestInvoker(
      mockExecutionEnvironment,
      mockModuleData,
      backgroundTaskExecutor = directExecutor::submit,
      gradleTaskManagerFactory = { mockGradleTaskManager },
      gradleTestResultAdapterFactory = { iDevice, _, _, _ ->
        mockGradleTestResultAdapters[mockDevices.indexOf(iDevice)]
      },
      buildToolWindowProvider = { mockBuildToolWindow },
    )
  }

  @Test
  fun executeTaskSingleDevice() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      any(),
      any()
    )
  }


  @Test
  fun checkGradleExecutionSettingsForAllInPackageTestWithSingleDevice() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "packageName", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        @Suppress("DEPRECATION")
        it.arguments.contains("-Pandroid.testInstrumentationRunnerArguments.package=packageName") &&
        it.env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_1" &&
        it.getUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE) == true
      },
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForRegexTestWithSingleDevice() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "regex",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        @Suppress("DEPRECATION")
        it.arguments.contains("-Pandroid.testInstrumentationRunnerArguments.tests_regex=regex") &&
        it.env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_1" &&
        it.getUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE) == true
      },
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForClassTestWithMultipleDevices() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker(numDevices = 2)

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "testClassName", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")


    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pandroid.testInstrumentationRunnerArguments.class=testClassName") &&
        it.env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_1,DEVICE_SERIAL_NUMBER_2"
      },
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTest() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "testClassName", testMethodName = "testMethodName", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pandroid.testInstrumentationRunnerArguments.class=testClassName#testMethodName")
      },
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTestWithDebugger() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pandroid.testInstrumentationRunnerArguments.debug=true")
      },
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTestWithExtraInstrumentationOptions() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "-e name1 true")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pandroid.testInstrumentationRunnerArguments.name1=true")
      },
      any()
    )
  }

  @Test
  fun checkGradleExecutionSettingsForMethodTestWithMultipleExtraInstrumentationOptions() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "-e name1 true -e name2 false")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.containsAll(listOf("-Pandroid.testInstrumentationRunnerArguments.name1=true",
                                "-Pandroid.testInstrumentationRunnerArguments.name2=false"))
      },
      any()
    )
  }

  @Test
  fun retentionEnabledTest() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.YES, maxSnapshots = 5, compressSnapshots = true)
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      retentionConfiguration, extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures=5") &&
        it.arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.compressSnapshots=true")
      },
      any()
    )
  }

  @Test
  fun retentionUseGradleTest() {
    val retentionConfiguration = RetentionConfiguration(enabled = EnableRetention.USE_GRADLE, maxSnapshots = 5, compressSnapshots = true)
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      retentionConfiguration, extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        !it.arguments.contains("-Pandroid.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures")
      },
      any()
    )
  }

  @Test
  fun utpTestResultsReportShouldBeEnabled() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pcom.android.tools.utp.GradleAndroidProjectResolverExtension.enable=true")
      },
      any()
    )
  }

  @Test
  fun testTaskNamesMatchSelectedBuildVariant() {
    whenever(mockAndroidModuleModel.selectedVariantName).thenReturn("nonDefaultBuildVariant")

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.tasks == listOf(":app:connectedNonDefaultBuildVariantAndroidTest")
      },
      any()
    )
  }

  @Test
  fun testTaskNamesMatchSelectedModule() {
    whenever(mockModuleData.getProperty(eq("gradleIdentityPath"))).thenReturn(":app:testModule")

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.tasks == listOf(":app:testModule:connectedDebugAndroidTest")
      },
      any()
    )
  }

  @Test
  fun testTaskNamesCanHandleTheRootModuleOnlyProject() {
    // This is a regression test for b/219164389.
    whenever(mockModuleData.getProperty(eq("gradleIdentityPath"))).thenReturn(":")


    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.tasks == listOf(":connectedDebugAndroidTest")
      },
      any()
    )
  }

  @Test
  fun testTaskNamesCanHandleCompositeBuild() {
    // This is a regression test for b/117064606.
    whenever(mockModuleData.gradleIdentityPath).thenReturn(":ModulesSDK:includedModule")

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.tasks == listOf(":ModulesSDK:includedModule:connectedDebugAndroidTest")
      },
      any()
    )
  }

  @Test
  fun retryExecuteTaskAfterInstallationFailure() {
    whenever(mockGradleTaskManager.executeTasks(
      anyString(),
      any(),
      any(),
      any()
    )).then {
      val externalTaskId: ExternalSystemTaskId = it.getArgument(0)
      val projectPath: String = it.getArgument(2)
      val listener: ExternalSystemTaskNotificationListener = it.getArgument(5)
      listener.onEnd(projectPath, externalTaskId)
      null
    }

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker(numDevices = 2)

    var attempt = 0
    whenever(mockGradleTestResultAdapters[1].needRerunWithUninstallIncompatibleApkOption()).then {
      attempt++
      GradleTestResultAdapter.UtpInstallResult(attempt == 1)
    }
    whenever(mockGradleTestResultAdapters[1].showRerunWithUninstallIncompatibleApkOptionDialog(any(), any())).thenReturn(true)

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    inOrder(mockGradleTaskManager, mockAndroidTestSuiteView).apply {
      verify(mockGradleTaskManager).executeTasks(
        anyString(),
        any(),
        argThat {
          !it.arguments.contains("-Pandroid.experimental.testOptions.uninstallIncompatibleApks=true") &&
          it.env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_1,DEVICE_SERIAL_NUMBER_2"
        },
        any()
      )
      verify(mockAndroidTestSuiteView).onRerunScheduled(argThat { it.id == "DEVICE_SERIAL_NUMBER_2" })
      verify(mockGradleTaskManager).executeTasks(
        anyString(),
        any(),
        argThat {
          it.arguments.contains("-Pandroid.experimental.testOptions.uninstallIncompatibleApks=true") &&
          it.env["ANDROID_SERIAL"] == "DEVICE_SERIAL_NUMBER_2"
        },
        any()
      )
      verifyNoMoreInteractions()
    }
  }

  @Test
  fun deviceSpecificGradleProperties() {
    val mockDevice = mock<com.android.tools.idea.run.AndroidDevice>()
    whenever(mockDevice.version).thenReturn(AndroidVersion(30))
    whenever(mockDevice.density).thenReturn(Density.XXHIGH.dpiValue)
    whenever(mockDevice.abis).thenReturn(listOf(Abi.X86, Abi.X86_64))
    whenever(mockDevice.appPreferredAbi).thenReturn(null)
    whenever(mockExecutionEnvironment.getCopyableUserData(eq(DeviceFutures.KEY))).thenReturn(DeviceFutures(listOf(mockDevice)))

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        it.arguments.contains("-Pandroid.injected.build.api=30") &&
        it.arguments.contains("-Pandroid.injected.build.abi=x86,x86_64")
      },
      any()
    )
  }

  @Test
  fun `test device api level when injection is disabled`() {
    StudioFlags.API_OPTIMIZATION_ENABLE.override(false)
    val mockDevice = mock<com.android.tools.idea.run.AndroidDevice>()
    whenever(mockDevice.version).thenReturn(AndroidVersion(30))
    whenever(mockDevice.density).thenReturn(Density.XXHIGH.dpiValue)
    whenever(mockDevice.abis).thenReturn(listOf(Abi.X86, Abi.X86_64))
    whenever(mockDevice.appPreferredAbi).thenReturn(null)
    whenever(mockExecutionEnvironment.getCopyableUserData(eq(DeviceFutures.KEY))).thenReturn(DeviceFutures(listOf(mockDevice)))

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = true,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      any(),
      argThat {
        !it.arguments.contains("-Pandroid.injected.build.api=30") &&
        it.arguments.contains("-Pandroid.injected.build.abi=x86,x86_64")
      },
      any()
    )
    StudioFlags.API_OPTIMIZATION_ENABLE.clearOverride()
  }

  @Test
  fun projectSystemIdMustBeGradle() {
    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    verify(mockGradleTaskManager).executeTasks(
      anyString(),
      argThat {
        it.projectSystemId == GradleConstants.SYSTEM_ID
      },
      any(),
      any()
    )
  }

  @Test
  fun buildToolWindowShouldBeDisplayedWhenTaskFailedBeforeTestSuiteStarted() {
    whenever(mockGradleTaskManager.executeTasks(
      anyString(),
      any(),
      any(),
      any()
    )).then {
      val externalTaskId: ExternalSystemTaskId = it.getArgument(0)
      val projectPath: String = it.getArgument(2)
      val listener: ExternalSystemTaskNotificationListener = it.getArgument(5)
      listener.onEnd(projectPath, externalTaskId)
      null
    }

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    verify(mockBuildToolWindow).show()
  }

  @Test
  fun buildToolWindowShouldNotBeDisplayedWhenTaskFailedAfterTestSuiteStarted() {
    whenever(mockGradleTaskManager.executeTasks(
      anyString(),
      any(),
      any(),
      any()
    )).then {
      val externalTaskId: ExternalSystemTaskId = it.getArgument(0)
      val projectPath: String = it.getArgument(2)
      val listener: ExternalSystemTaskNotificationListener = it.getArgument(5)
      listener.onEnd(projectPath, externalTaskId)
      null
    }

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    whenever(mockGradleTestResultAdapters[0].testSuiteStarted).thenReturn(true)

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    verify(mockBuildToolWindow, never()).show()
  }

  @Test
  fun buildToolWindowShouldNotBeDisplayedWhenTaskIsCancelled() {
    whenever(mockGradleTaskManager.executeTasks(
      anyString(),
      any(),
      any(),
      any()
    )).then {
      val projectPath: String = it.getArgument(0)
      val externalTaskId: ExternalSystemTaskId = it.getArgument(1)
      val listener: ExternalSystemTaskNotificationListener = it.getArgument(3)
      listener.onCancel(projectPath, externalTaskId)
      listener.onEnd(projectPath, externalTaskId)
      null
    }

    val gradleConnectedTestInvoker = createGradleConnectedAndroidTestInvoker()

    gradleConnectedTestInvoker.runGradleTask(
      projectRule.project, mockDevices, "taskId", mockAndroidTestSuiteView, mockAndroidModuleModel, waitForDebugger = false,
      testPackageName = "", testClassName = "", testMethodName = "", testRegex = "",
      RetentionConfiguration(), extraInstrumentationOptions = "")

    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    verify(mockBuildToolWindow, never()).show()
  }
}