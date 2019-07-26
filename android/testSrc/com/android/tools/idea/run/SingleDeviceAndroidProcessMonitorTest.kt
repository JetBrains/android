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
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_DETACHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_FINISHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_IS_RUNNING
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [SingleDeviceAndroidProcessMonitor].
 */
class SingleDeviceAndroidProcessMonitorTest {
  companion object {
    const val TARGET_APP_NAME: String = "example.target.app"
    const val TEST_TIMEOUT_MILLIS: Long = 60000
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Mock
  lateinit var mockDevice: IDevice
  @Mock
  lateinit var mockDeploymentAppService: DeploymentApplicationService
  @Mock
  lateinit var mockLogcatCaptor: AndroidLogcatOutputCapture

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun processFoundThenProcessFinishes() {
    val latchForStart = CountDownLatch(1)
    val latchForEnd = CountDownLatch(1)

    SingleDeviceAndroidProcessMonitor(
      TARGET_APP_NAME,
      mockDevice,
      object : SingleDeviceAndroidProcessMonitorStateListener {
        override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
          if (newState == PROCESS_IS_RUNNING) {
            latchForStart.countDown()
          }
          if (newState == PROCESS_FINISHED) {
            latchForEnd.countDown()
          }
        }
      },
      mockDeploymentAppService,
      mockLogcatCaptor,
      1,
      TEST_TIMEOUT_MILLIS
    )

    val mockClients = listOf(createMockClient(123))
    `when`(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    assertThat(latchForStart.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

    assertThat(latchForEnd.count).isEqualTo(1)
    verify(mockLogcatCaptor).startCapture(eq(mockDevice) ?: mockDevice, eq(123), eq(TARGET_APP_NAME))

    // Now the target process finishes.
    `when`(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf())

    assertThat(latchForEnd.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor).stopCapture(eq(mockDevice) ?: mockDevice)
  }

  @Test
  fun processFoundThenKillProcessByMonitor() {
    val latchForStart = CountDownLatch(1)
    val latchForEnd = CountDownLatch(1)

    val monitor = SingleDeviceAndroidProcessMonitor(
      TARGET_APP_NAME,
      mockDevice,
      object : SingleDeviceAndroidProcessMonitorStateListener {
        override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
          if (newState == PROCESS_IS_RUNNING) {
            latchForStart.countDown()
          }
          if (newState == PROCESS_FINISHED) {
            latchForEnd.countDown()
          }
        }
      },
      mockDeploymentAppService,
      mockLogcatCaptor,
      1,
      TEST_TIMEOUT_MILLIS
    )

    val mockClient = createMockClient(123)
    `when`(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    assertThat(latchForStart.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

    assertThat(latchForEnd.count).isEqualTo(1)
    verify(mockLogcatCaptor).startCapture(eq(mockDevice) ?: mockDevice, eq(123), eq(TARGET_APP_NAME))

    // Now kill the target process by close.
    monitor.close()

    assertThat(latchForEnd.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor).stopCapture(eq(mockDevice) ?: mockDevice)
    verify(mockClient).kill()
  }

  @Test
  fun processFoundThenDetachProcess() {
    val latchForStart = CountDownLatch(1)
    val latchForEnd = CountDownLatch(1)

    val monitor = SingleDeviceAndroidProcessMonitor(
      TARGET_APP_NAME,
      mockDevice,
      object : SingleDeviceAndroidProcessMonitorStateListener {
        override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
          if (newState == PROCESS_IS_RUNNING) {
            latchForStart.countDown()
          }
          if (newState == PROCESS_DETACHED) {
            latchForEnd.countDown()
          }
        }
      },
      mockDeploymentAppService,
      mockLogcatCaptor,
      1,
      TEST_TIMEOUT_MILLIS
    )

    val mockClient = createMockClient(123)
    `when`(mockDeploymentAppService.findClient(eq(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    assertThat(latchForStart.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

    assertThat(latchForEnd.count).isEqualTo(1)
    verify(mockLogcatCaptor).startCapture(eq(mockDevice) ?: mockDevice, eq(123), eq(TARGET_APP_NAME))

    // Now detach the target process by close.
    monitor.detachAndClose()

    assertThat(latchForEnd.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor).stopCapture(eq(mockDevice) ?: mockDevice)
    verify(mockClient, never()).kill()
  }

  @Test
  fun monitorShouldTimeout() {
    val latch = CountDownLatch(1)
    SingleDeviceAndroidProcessMonitor(
      TARGET_APP_NAME,
      mockDevice,
      object : SingleDeviceAndroidProcessMonitorStateListener {
        override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
          if (newState == PROCESS_NOT_FOUND) {
            latch.countDown()
          }
        }
      },
      mockDeploymentAppService,
      mockLogcatCaptor,
      1,
      1
    )
    assertThat(latch.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor).stopCapture(eq(mockDevice) ?: mockDevice)
  }

  private fun createMockClient(pid: Int): Client {
    val mockClientData = mock(ClientData::class.java)
    `when`(mockClientData.pid).thenReturn(pid)
    val mockClient = mock(Client::class.java)
    `when`(mockClient.clientData).thenReturn(mockClientData)
    return mockClient
  }
}