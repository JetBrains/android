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
package com.android.tools.idea.run

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_DETACHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_FINISHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_IS_RUNNING
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.WAITING_FOR_PROCESS
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.same
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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

  @get:Rule
  var mockitoJunit = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
  @Mock
  lateinit var mockDevice: IDevice
  @Mock
  lateinit var mockDeploymentAppService: DeploymentApplicationService
  @Mock
  lateinit var mockLogcatCaptor: AndroidLogcatOutputCapture
  @Mock
  lateinit var mockTextEmitter: TextEmitter
  @Mock
  lateinit var mockListener: SingleDeviceAndroidProcessMonitorStateListener
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
    `when`(mockScheduledExecutor.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any())).then {
      capturedUpdateStateRunnable = it.getArgument(0)
      capturedUpdateInitialDelay = it.getArgument(1)
      capturedUpdateInterval = it.getArgument(2)
      capturedUpdateIntervalTimeUnit = it.getArgument(3)
      mockStateUpdaterScheduledFuture
    }

    `when`(mockScheduledExecutor.schedule(any(), anyLong(), any())).then {
      capturedTimeoutRunnable = it.getArgument(0)
      capturedTimeout = it.getArgument(1)
      capturedTimeoutTimeUnit = it.getArgument(2)
      mockTimeoutScheduledFuture
    }
  }

  private fun startMonitor(
    logcatCaptor: AndroidLogcatOutputCapture? = mockLogcatCaptor
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
      logcatCaptor,
      mockTextEmitter,
      listenerExecutor = MoreExecutors.directExecutor(),
      stateUpdaterExecutor = mockScheduledExecutor
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
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)
    verify(mockLogcatCaptor).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now the target process finishes.
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf())
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_DETACHED)
    verify(mockLogcatCaptor).stopCapture(same(mockDevice))
    verify(mockDevice, never()).kill(TARGET_APP_NAME)
  }

  @Test
  fun processFoundThenKillProcessByMonitor() {
    val monitor = startMonitor()

    val mockClient = createMockClient(123)
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)
    verify(mockLogcatCaptor).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now kill the target process by close.
    monitor.close()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_FINISHED)
    verify(mockLogcatCaptor).stopCapture(same(mockDevice))
    verify(mockClient).kill()
  }

  @Test
  fun processFoundThenDetachProcess() {
    val monitor = startMonitor()

    val mockClient = createMockClient(123)
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)
    verify(mockLogcatCaptor).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now detach the target process by close.
    monitor.detachAndClose()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_DETACHED)
    verify(mockLogcatCaptor).stopCapture(same(mockDevice))
    verify(mockClient, never()).kill()
  }

  @Test
  fun monitorShouldTimeout() {
    startMonitor()

    timeoutMonitor()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_NOT_FOUND)
    verify(mockLogcatCaptor).stopCapture(same(mockDevice))
  }

  @Test
  fun monitorShouldWorkWithoutLogcatCaptor() {
    val monitor = startMonitor(logcatCaptor = null)

    val mockClients = listOf(createMockClient(123))
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)

    monitor.close()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_FINISHED)
  }

  @Test
  fun processFoundThenStopListeningAndClose() {
    val monitor = startMonitor()

    val mockClients = listOf(createMockClient(123))
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    updateMonitorState()

    assertThat(capturedCurrentState).isEqualTo(PROCESS_IS_RUNNING)
    verify(mockLogcatCaptor).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now replace the listener and detach the target process by replaceListenerAndClose.
    var isListenerReplacedAndDetached = false
    monitor.replaceListenerAndClose(object : SingleDeviceAndroidProcessMonitorStateListener {
      override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
        isListenerReplacedAndDetached = newState == PROCESS_FINISHED
      }
    })

    assertThat(isListenerReplacedAndDetached).isTrue()
    verify(mockLogcatCaptor).stopCapture(same(mockDevice))
    verify(mockClients[0]).kill()
  }

  private fun createMockClient(pid: Int): Client {
    val mockClientData = mock(ClientData::class.java)
    `when`(mockClientData.pid).thenReturn(pid)
    val mockClient = mock(Client::class.java)
    `when`(mockClient.clientData).thenReturn(mockClientData)
    return mockClient
  }
}