/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ConsoleProvider
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

private const val TEST_APP_PACKAGE_NAME = "my.example.application.test"

/**
 * Unit tests for [ReattachingConnectDebuggerTask].
 */
class ReattachingConnectDebuggerTaskTest : AndroidTestCase() {

  lateinit var mockLaunchInfo: LaunchInfo

  @Mock
  lateinit var mockDevice: IDevice

  @Mock
  lateinit var mockStatus: ProcessHandlerLaunchStatus

  @Mock
  lateinit var mockProcessHandler: ProcessHandler

  @Mock
  lateinit var mockAndroidSessionInfo: AndroidSessionInfo

  @Mock
  lateinit var mockClient: Client

  @Mock
  lateinit var mockClientData: ClientData

  @Mock
  lateinit var mockApplicationIdProvider: ApplicationIdProvider

  lateinit var baseConnector: TestConnectDebuggerTask
  lateinit var printer: ProcessHandlerConsolePrinter

  override fun setUp() {
    super.setUp()

    MockitoAnnotations.initMocks(this)
    whenever(mockApplicationIdProvider.packageName).thenReturn(TEST_APP_PACKAGE_NAME)
    whenever(mockApplicationIdProvider.testPackageName).thenThrow(ApkProvisionException("no test package"))
    baseConnector = TestConnectDebuggerTask(project, mockApplicationIdProvider)
    printer = ProcessHandlerConsolePrinter(mockProcessHandler)

    whenever(mockStatus.processHandler).thenReturn(mockProcessHandler)
    whenever(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockAndroidSessionInfo)
    whenever(mockClient.clientData).thenReturn(mockClientData)
    whenever(mockClientData.clientDescription).thenReturn(TEST_APP_PACKAGE_NAME)
    whenever(mockClientData.debuggerConnectionStatus).thenReturn(ClientData.DebuggerStatus.WAITING)

    mockLaunchInfo = LaunchInfo(
      DefaultDebugExecutor.getDebugExecutorInstance(),
      mock(ProgramRunner::class.java),
      mock(ExecutionEnvironment::class.java),
      mock(ConsoleProvider::class.java)
    )
  }

  @Test
  fun testPerform() {
    val mockRunContentManager = mock(RunContentManager::class.java)
    whenever(mockRunContentManager.findContentDescriptor(any(), any())).thenReturn(mock(RunContentDescriptor::class.java))
    project.replaceService(RunContentManager::class.java, mockRunContentManager, testRootDisposable)

    val listener = TestListener()
    val debugger = ReattachingConnectDebuggerTask(baseConnector, masterAndroidProcessName = "", listener)

    // Verify that the base connector is not launched yet.
    assertThat(baseConnector.launchInvocations).isEqualTo(0)

    // Start debug connector task.
    debugger.perform(mockLaunchInfo, mockDevice, mockStatus, printer)

    // Make sure callback methods are invoked.
    assertThat(listener.isStarted).isTrue()
    assertThat(listener.launchInfo).isEqualTo(mockLaunchInfo)
    assertThat(listener.device).isEqualTo(mockDevice)
    assertThat(debugger.getReattachingListenerForTesting()).isNotNull()

    // When the test application is ready for debugger, the callback will be
    // invoked by ddmlib. Here, we call the callback manually for testing.
    debugger.getReattachingListenerForTesting()!!.clientChanged(mockClient, Client.CHANGE_DEBUGGER_STATUS)
    dispatchAllEventsInIdeEventQueue()

    assertThat(baseConnector.launchInvocations).isEqualTo(1)

    // Stop the reattaching task from the listener.
    listener.controller.stop()

    assertThat(debugger.getReattachingListenerForTesting()).isNull()
  }
}

class TestConnectDebuggerTask(project: Project, applicationIdProvider: ApplicationIdProvider)
  : ConnectDebuggerTaskBase(applicationIdProvider, project, false) {
  var launchInvocations = 0

  override fun launchDebugger(currentLaunchInfo: LaunchInfo,
                              client: Client,
                              state: ProcessHandlerLaunchStatus,
                              printer: ProcessHandlerConsolePrinter) {
    launchInvocations++
  }
}

/**
 * A [ReattachingConnectDebuggerTaskListener] implementation for testing.
 */
class TestListener : ReattachingConnectDebuggerTaskListener {
  var isStarted = false
  lateinit var launchInfo: LaunchInfo
  lateinit var device: IDevice
  lateinit var controller: ReattachingConnectDebuggerController

  override fun onStart(launchInfo: LaunchInfo, device: IDevice, controller: ReattachingConnectDebuggerController) {
    isStarted = true
    this.launchInfo = launchInfo
    this.device = device
    this.controller = controller
  }
}