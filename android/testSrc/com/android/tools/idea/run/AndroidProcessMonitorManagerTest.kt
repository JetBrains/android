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

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_DETACHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_FINISHED
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.util.Ref
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.lang.Boolean.FALSE
import java.util.concurrent.CountDownLatch

@RunWith(JUnit4::class)
class AndroidProcessMonitorManagerTest {
  companion object {
    const val TARGET_APP_NAME: String = "example.target.app"
  }

  @Mock
  lateinit var mockDeploymentAppService: DeploymentApplicationService
  @Mock
  lateinit var mockTextEmitter: TextEmitter
  @Mock
  lateinit var mockMonitorManagerListener: AndroidProcessMonitorManagerListener

  lateinit var monitorManager: AndroidProcessMonitorManager
  lateinit var stateChangeListener: SingleDeviceAndroidProcessMonitorStateListener
  val mockSingleDeviceAndroidProcessMonitors = mutableMapOf<IDevice, SingleDeviceAndroidProcessMonitor>()

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    monitorManager =  AndroidProcessMonitorManager(TARGET_APP_NAME,
                                                   mockDeploymentAppService,
                                                   mockTextEmitter,
                                                   /*captureLogcat=*/true,
                                                   mockMonitorManagerListener) {_, device, listener, _, _ ->
      if (::stateChangeListener.isInitialized) {
        assertThat(listener).isSameAs(stateChangeListener)
      }
      assertThat(mockSingleDeviceAndroidProcessMonitors).doesNotContainKey(device)

      stateChangeListener = listener

      val monitor = mock(SingleDeviceAndroidProcessMonitor::class.java)
      `when`(monitor.targetDevice).thenReturn(device)
      mockSingleDeviceAndroidProcessMonitors[device] = monitor

      monitor
    }
  }

  @Test
  fun processFinishesOnSingleDevice() {
    val device = createMockDevice(28)
    monitorManager.add(device)

    assertThat(monitorManager.getMonitor(device)).isNotNull()

    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device)
    assertThat(mockSingleDeviceAndroidProcessMonitors).hasSize(1)

    stateChangeListener.onStateChanged(mockSingleDeviceAndroidProcessMonitors.getValue(device), PROCESS_FINISHED)

    assertThat(monitorManager.getMonitor(device)).isNull()
    verify(mockMonitorManagerListener).onAllTargetProcessesTerminated()
  }

  @Test
  fun isAssociated() {
    val nonAssociatedDevice = createMockDevice(28)
    val associatedDevice = createMockDevice(29)

    monitorManager.add(associatedDevice)

    assertThat(monitorManager.isAssociated(nonAssociatedDevice)).isFalse()
    assertThat(monitorManager.isAssociated(associatedDevice)).isTrue()
  }

  @Test
  fun processNotFoundOnSingleDevice() {
    val device = createMockDevice(28)
    monitorManager.add(device)

    assertThat(monitorManager.getMonitor(device)).isNotNull()

    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device)
    assertThat(mockSingleDeviceAndroidProcessMonitors).hasSize(1)

    stateChangeListener.onStateChanged(mockSingleDeviceAndroidProcessMonitors.getValue(device), PROCESS_NOT_FOUND)

    assertThat(monitorManager.getMonitor(device)).isNull()
    verify(mockMonitorManagerListener).onAllTargetProcessesTerminated()
  }

  @Test
  fun processDetachedOnSingleDevice() {
    val device = createMockDevice(28)
    monitorManager.add(device)

    assertThat(monitorManager.getMonitor(device)).isNotNull()

    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device)
    assertThat(mockSingleDeviceAndroidProcessMonitors).hasSize(1)

    stateChangeListener.onStateChanged(mockSingleDeviceAndroidProcessMonitors.getValue(device), PROCESS_DETACHED)

    assertThat(monitorManager.getMonitor(device)).isNull()
    verify(mockMonitorManagerListener).onAllTargetProcessesTerminated()
  }

  @Test
  fun processFinishesOnTwoDevices() {
    val device1 = createMockDevice(28)
    val device2 = createMockDevice(29)
    monitorManager.add(device1)
    monitorManager.add(device2)

    assertThat(monitorManager.getMonitor(device1)).isNotNull()
    assertThat(monitorManager.getMonitor(device2)).isNotNull()

    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device1)
    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device2)
    assertThat(mockSingleDeviceAndroidProcessMonitors).hasSize(2)

    stateChangeListener.onStateChanged(mockSingleDeviceAndroidProcessMonitors.getValue(device1), PROCESS_FINISHED)
    assertThat(monitorManager.getMonitor(device1)).isNull()

    // The process is still running on device2 so the callback should not be called yet.
    verify(mockMonitorManagerListener, never()).onAllTargetProcessesTerminated()

    stateChangeListener.onStateChanged(mockSingleDeviceAndroidProcessMonitors.getValue(device2), PROCESS_FINISHED)
    assertThat(monitorManager.getMonitor(device2)).isNull()

    verify(mockMonitorManagerListener).onAllTargetProcessesTerminated()
  }

  @Test
  fun testGetMonitor() {
    val device1 = createMockDevice(27)
    val device2 = createMockDevice(28)
    val device3NotAdded = createMockDevice(29)
    monitorManager.add(device1)
    monitorManager.add(device2)

    assertThat(monitorManager.getMonitor(device1)).isNotNull()
    assertThat(monitorManager.getMonitor(device2)).isNotNull()
    assertThat(monitorManager.getMonitor(device3NotAdded)).isNull()
  }

  @Test
  fun testClose() {
    val device = createMockDevice(28)
    monitorManager.add(device)

    assertThat(monitorManager.getMonitor(device)).isNotNull()

    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device)
    assertThat(mockSingleDeviceAndroidProcessMonitors).hasSize(1)

    monitorManager.close()

    verify(mockSingleDeviceAndroidProcessMonitors.getValue(device)).close()
  }

  @Test
  fun testDetachAndClose() {
    val device = createMockDevice(28)
    monitorManager.add(device)

    assertThat(monitorManager.getMonitor(device)).isNotNull()

    assertThat(mockSingleDeviceAndroidProcessMonitors).containsKey(device)
    assertThat(mockSingleDeviceAndroidProcessMonitors).hasSize(1)

    monitorManager.detachAndClose()

    verify(mockSingleDeviceAndroidProcessMonitors.getValue(device)).detachAndClose()
  }

  @Test
  fun replaceDevice() {
    val terminatedLatch = CountDownLatch(1)
    val managerListener = object : AndroidProcessMonitorManagerListener {
      override fun onAllTargetProcessesTerminated() {
        terminatedLatch.countDown()
      }
    }
    val isReplacing = Ref(false)

    val targetDevice = createMockDevice(28)
    monitorManager = AndroidProcessMonitorManager(TARGET_APP_NAME,
                                                   mockDeploymentAppService,
                                                   mockTextEmitter,
                                                   /*captureLogcat=*/true,
                                                   managerListener) {_, device, listener, deployService, logcat ->
      if (::stateChangeListener.isInitialized) {
        assertThat(listener).isSameAs(stateChangeListener)
      }

      if (isReplacing.get() == FALSE) {
        assertThat(mockSingleDeviceAndroidProcessMonitors).doesNotContainKey(targetDevice)
      }

      stateChangeListener = listener

      val textEmitter = mock(TextEmitter::class.java)
      val monitor = SingleDeviceAndroidProcessMonitor(TARGET_APP_NAME, device, listener, deployService, logcat, textEmitter,
                                                      SingleDeviceAndroidProcessMonitor.POLLING_INTERVAL_MILLIS,
                                                      SingleDeviceAndroidProcessMonitor.APP_PROCESS_DISCOVERY_TIMEOUT_MILLIS,
                                                      MoreExecutors.directExecutor())
      mockSingleDeviceAndroidProcessMonitors[device] = monitor

      monitor
    }
    monitorManager.add(targetDevice)
    isReplacing.set(true)
    monitorManager.closeAndReplace(targetDevice)
    assertThat(terminatedLatch.count).isEqualTo(1)
  }

  private fun createMockDevice(apiVersion: Int): IDevice {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevice.version).thenReturn(AndroidVersion(apiVersion))
    return mockDevice
  }
}