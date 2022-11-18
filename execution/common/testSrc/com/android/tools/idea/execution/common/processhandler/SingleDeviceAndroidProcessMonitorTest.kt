/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.processhandler

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_DETACHED
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_FINISHED
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_IS_RUNNING
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.WAITING_FOR_PROCESS
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [SingleDeviceAndroidProcessMonitor].
 */
class SingleDeviceAndroidProcessMonitorTest {
  companion object {
    const val TARGET_APP_NAME: String = "example.target.app"
  }

  private fun createDevice(): IDevice {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevice.version).thenReturn(AndroidVersion(26))
    whenever(mockDevice.isOnline).thenReturn(true)
    return mockDevice
  }

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  var mockitoJunit = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
  var mockDevice = createDevice()

  @Mock
  lateinit var mockDeploymentAppService: DeploymentApplicationService

  @Mock
  lateinit var mockTextEmitter: TextEmitter

  @Mock(answer = Answers.RETURNS_MOCKS)
  lateinit var mockScheduledExecutor: ScheduledExecutorService
  @Mock
  lateinit var mockStateUpdaterScheduledFuture: ScheduledFuture<*>
  @Mock
  lateinit var mockTimeoutScheduledFuture: ScheduledFuture<*>

  var capturedCurrentState: SingleDeviceAndroidProcessMonitorState = WAITING_FOR_PROCESS

  lateinit var capturedUpdateStateRunnable: Runnable
  var capturedUpdateInitialDelay: Long = -1
  var capturedUpdateInterval: Long = -1
  lateinit var capturedUpdateIntervalTimeUnit: TimeUnit

  lateinit var capturedTimeoutRunnable: Runnable
  var capturedTimeout: Long = -1
  lateinit var capturedTimeoutTimeUnit: TimeUnit

  @Before
  fun setUp() {
    whenever(mockScheduledExecutor.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any())).then {
      capturedUpdateStateRunnable = it.getArgument(0)
      capturedUpdateInitialDelay = it.getArgument(1)
      capturedUpdateInterval = it.getArgument(2)
      capturedUpdateIntervalTimeUnit = it.getArgument(3)
      mockStateUpdaterScheduledFuture
    }

    whenever(mockScheduledExecutor.schedule(any(), anyLong(), any())).then {
      capturedTimeoutRunnable = it.getArgument(0)
      capturedTimeout = it.getArgument(1)
      capturedTimeoutTimeUnit = it.getArgument(2)
      mockTimeoutScheduledFuture
    }
    ApplicationManager.getApplication()
      .replaceService(DeploymentApplicationService::class.java, mockDeploymentAppService, projectRule.project)
  }

  private fun startMonitor(
    finishAndroidProcessCallback: (IDevice) -> Unit = { it.forceStop(AndroidProcessMonitorManagerTest.TARGET_APP_NAME) }
  ): SingleDeviceAndroidProcessMonitor {
    return SingleDeviceAndroidProcessMonitor(
      TARGET_APP_NAME,
      mockDevice,
      object : SingleDeviceAndroidProcessMonitorStateListener {
        override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
          capturedCurrentState = newState
        }
      },
      mockDeploymentAppService,
      mockTextEmitter,
      finishAndroidProcessCallback,
      listenerExecutor = MoreExecutors.directExecutor(),
      stateUpdaterExecutor = mockScheduledExecutor,
    )
  }

  private fun updateMonitorState() {
    capturedUpdateStateRunnable.run()
  }

  private fun timeoutMonitor() {
    capturedTimeoutRunnable.run()
  }

  @Test
  fun processFoundThenProcessFinishes() {
    startMonitor()

    val mockClients = listOf(createMockClient(123))
    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)

    // Now the target process finishes.
    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf())
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_DETACHED)
    verify(mockDevice, never()).kill(TARGET_APP_NAME)
  }

  @Test
  fun callCustomStopAndroidProcessCallback() {
    var callBackIsCalled = false
    val monitor = startMonitor(finishAndroidProcessCallback = { callBackIsCalled = true })

    val mockClients = listOf(createMockClient(123))
    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    updateMonitorState()

    // Now kill the target process by close.
    monitor.close()

    assertThat(callBackIsCalled).isTrue()
  }

  @Test
  fun processFoundThenKillProcessByMonitor() {
    val monitor = startMonitor()

    whenever(mockDevice.isOnline).thenReturn(true)
    val mockClient = createMockClient(123)

    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)

    // Now kill the target process by close.
    monitor.close()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_FINISHED)
    verify(mockDevice, times(2)).forceStop(TARGET_APP_NAME)
  }

  @Test
  fun processFoundThenDetachProcess() {
    val monitor = startMonitor()

    val mockClient = createMockClient(123)
    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)

    // Now detach the target process by close.
    monitor.detachAndClose()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_DETACHED)
    verify(mockDevice, never()).forceStop(TARGET_APP_NAME)
  }

  @Test
  fun monitorShouldTimeout() {
    startMonitor()

    timeoutMonitor()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_NOT_FOUND)
  }

  @Test
  fun monitorShouldWorkWithoutLogcatCaptor() {
    val monitor = startMonitor()

    val mockClients = listOf(createMockClient(123))
    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)

    monitor.close()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_FINISHED)
  }

  @Test
  fun processFoundThenStopListeningAndClose() {
    whenever(mockDevice.isOnline).thenReturn(true)
    val monitor = startMonitor()

    val mockClient = createMockClient(123)
    whenever(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)

    // Now replace the listener and detach the target process by replaceListenerAndClose.
    var isListenerReplacedAndDetached = false
    monitor.replaceListenerAndClose(object : SingleDeviceAndroidProcessMonitorStateListener {
      override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
        isListenerReplacedAndDetached = newState == PROCESS_FINISHED
      }
    })

    assertThat(isListenerReplacedAndDetached).isTrue()
    verify(mockDevice, times(2)).forceStop(TARGET_APP_NAME)
  }

  private fun createMockClient(pid: Int): Client {
    val mockClientData = mock(ClientData::class.java)
    whenever(mockClientData.pid).thenReturn(pid)
    val mockClient = mock(Client::class.java)
    whenever(mockClient.clientData).thenReturn(mockClientData)
    return mockClient
  }
}