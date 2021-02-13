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
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_DETACHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_FINISHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_IS_RUNNING
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.same
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

/**
 * Unit tests for [SingleDeviceAndroidProcessMonitor].
 */
class SingleDeviceAndroidProcessMonitorTest {
  companion object {
    const val TARGET_APP_NAME: String = "example.target.app"
    const val TEST_TIMEOUT_MILLIS: Long = 10000
    const val TEST_POLLING_INTERVAL_MILLIS: Long = 10
  }

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
          when (newState) {
            PROCESS_IS_RUNNING -> latchForStart.countDown()
            PROCESS_DETACHED -> latchForEnd.countDown()
            PROCESS_FINISHED ->
              fail("Process should not have finished, and should have detached instead if it terminates on the device end")
            else -> {
            }
          }
        }
      },
      mockDeploymentAppService,
      mockLogcatCaptor,
      mockTextEmitter,
      TEST_POLLING_INTERVAL_MILLIS,
      TEST_TIMEOUT_MILLIS
    )

    val mockClients = listOf(createMockClient(123))
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)
    assertThat(latchForStart.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

    assertThat(latchForEnd.count).isEqualTo(1)
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now the target process finishes.
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf())

    assertThat(latchForEnd.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).stopCapture(same(mockDevice))
    verify(mockDevice, never()).kill(TARGET_APP_NAME)
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
      mockTextEmitter,
      TEST_POLLING_INTERVAL_MILLIS,
      TEST_TIMEOUT_MILLIS
    )

    val mockClient = createMockClient(123)
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    assertThat(latchForStart.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

    assertThat(latchForEnd.count).isEqualTo(1)
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now kill the target process by close.
    monitor.close()

    assertThat(latchForEnd.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).stopCapture(same(mockDevice))
    verify(mockClient, timeout(TEST_TIMEOUT_MILLIS)).kill()
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
      mockTextEmitter,
      TEST_POLLING_INTERVAL_MILLIS,
      TEST_TIMEOUT_MILLIS
    )

    val mockClient = createMockClient(123)
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(listOf(mockClient))
    assertThat(latchForStart.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

    assertThat(latchForEnd.count).isEqualTo(1)
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).startCapture(same(mockDevice), eq(123), eq(TARGET_APP_NAME))

    // Now detach the target process by close.
    monitor.detachAndClose()

    assertThat(latchForEnd.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).stopCapture(same(mockDevice))
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
      mockTextEmitter,
      TEST_POLLING_INTERVAL_MILLIS,
      appProcessDiscoveryTimeoutMillis = 1
    )
    assertThat(latch.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    verify(mockLogcatCaptor, timeout(TEST_TIMEOUT_MILLIS)).stopCapture(same(mockDevice))
  }

  @Test
  fun monitorShouldWorkWithoutLogcatCaptor() {
    val monitor = SingleDeviceAndroidProcessMonitor(
      TARGET_APP_NAME,
      mockDevice,
      mockListener,
      mockDeploymentAppService,
      /*logcatCaptor=*/null,
      mockTextEmitter,
      TEST_POLLING_INTERVAL_MILLIS,
      TEST_TIMEOUT_MILLIS
    )
    val mockClients = listOf(createMockClient(123))
    `when`(mockDeploymentAppService.findClient(same(mockDevice), eq(TARGET_APP_NAME))).thenReturn(mockClients)

    verify(mockListener, timeout(TEST_TIMEOUT_MILLIS)).onStateChanged(eq(monitor), eq(PROCESS_IS_RUNNING))

    monitor.close()

    verify(mockListener, timeout(TEST_TIMEOUT_MILLIS)).onStateChanged(eq(monitor), eq(PROCESS_FINISHED))
  }

  private fun createMockClient(pid: Int): Client {
    val mockClientData = mock(ClientData::class.java)
    `when`(mockClientData.pid).thenReturn(pid)
    val mockClient = mock(Client::class.java)
    `when`(mockClient.clientData).thenReturn(mockClientData)
    return mockClient
  }
}