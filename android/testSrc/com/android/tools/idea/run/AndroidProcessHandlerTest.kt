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
import com.android.tools.idea.run.deployable.SwappableProcessHandler
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks

/**
 * Unit test for [AndroidProcessHandler].
 */
@RunWith(JUnit4::class)
class AndroidProcessHandlerTest {
  companion object {
    const val TARGET_APP_NAME: String = "example.target.app"
  }

  @Mock
  lateinit var mockProject: Project
  @Mock
  lateinit var mockDeploymentAppService: DeploymentApplicationService
  @Mock
  lateinit var mockMonitorManager: AndroidProcessMonitorManager
  @Mock
  lateinit var mockProcessListener: ProcessListener

  lateinit var handler: AndroidProcessHandler
  lateinit var textEmitter: TextEmitter
  lateinit var monitorManagerListener: AndroidProcessMonitorManagerListener

  @Before
  fun setUp() {
    initMocks(this)

    handler = AndroidProcessHandler(
      mockProject,
      TARGET_APP_NAME,
      mockDeploymentAppService
    ) { _, _, emitter, listener ->
      textEmitter = emitter
      monitorManagerListener = listener
      mockMonitorManager
    }.apply {
      addProcessListener(mockProcessListener)
      startNotify()
    }
  }

  @Test
  fun handlerIsRegisteredToCopyableUserData() {
    assertThat(handler.getCopyableUserData(SwappableProcessHandler.EXTENSION_KEY)).isSameAs(handler)
  }

  @Test
  fun runProcessOnOneDevice() {
    val inOrder = inOrder(mockProcessListener)
    inOrder.verify(mockProcessListener).startNotified(any())
    assertThat(handler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isNull()

    val mockDevice = createMockDevice(28)
    handler.addTargetDevice(mockDevice)
    assertThat(handler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(28))
    verify(mockMonitorManager).add(eq(mockDevice) ?: mockDevice)

    monitorManagerListener.onAllTargetProcessesTerminated()

    assertThat(handler.isProcessTerminated).isTrue()
    inOrder.verify(mockProcessListener).processWillTerminate(any(), eq(true))
    inOrder.verify(mockProcessListener).processTerminated(any())
    inOrder.verifyNoMoreInteractions()
  }

  @Test
  fun runProcessOnMultipleDevices() {
    val inOrder = inOrder(mockProcessListener)
    inOrder.verify(mockProcessListener).startNotified(any())
    assertThat(handler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isNull()

    handler.addTargetDevice(createMockDevice(28))
    assertThat(handler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(28))

    handler.addTargetDevice(createMockDevice(27))
    // AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL should return the lowest API level among all managed devices.
    assertThat(handler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(27))

    handler.addTargetDevice(createMockDevice(29))
    assertThat(handler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(27))

    monitorManagerListener.onAllTargetProcessesTerminated()

    assertThat(handler.isProcessTerminated).isTrue()
    inOrder.verify(mockProcessListener).processWillTerminate(any(), eq(true))
    inOrder.verify(mockProcessListener).processTerminated(any())
    inOrder.verifyNoMoreInteractions()
  }

  @Test
  fun textEmitterShouldRedirectToNotifyText() {
    textEmitter.emit("test emit message", ProcessOutputTypes.STDOUT)
    verify(mockProcessListener).onTextAvailable(argThat { event -> event.text == "test emit message" }, eq(ProcessOutputTypes.STDOUT))
  }

  @Test
  fun detachProcess() {
    val inOrder = inOrder(mockProcessListener)
    handler.detachProcess()
    assertThat(handler.isProcessTerminated).isTrue()
    inOrder.verify(mockProcessListener).startNotified(any())
    inOrder.verify(mockProcessListener).processWillTerminate(any(), eq(false))
    inOrder.verify(mockProcessListener).processTerminated(any())
    inOrder.verifyNoMoreInteractions()
  }

  private fun createMockDevice(apiVersion: Int): IDevice {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevice.version).thenReturn(AndroidVersion(apiVersion))
    return mockDevice
  }
}