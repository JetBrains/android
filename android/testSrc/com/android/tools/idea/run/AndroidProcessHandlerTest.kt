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
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.run.deployable.SwappableProcessHandler
import com.android.tools.idea.run.deployment.AndroidExecutionTarget
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
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

  @Mock lateinit var mockProject: Project
  @Mock lateinit var mockExecutionTargetManager: ExecutionTargetManager
  @Mock lateinit var mockExecutionTarget: AndroidExecutionTarget
  @Mock lateinit var mockDeploymentAppService: DeploymentApplicationService
  @Mock lateinit var mockMonitorManager: AndroidProcessMonitorManager
  @Mock lateinit var mockProcessListener: ProcessListener
  @Mock lateinit var mockAnsiEscapeDecoder: AnsiEscapeDecoder

  lateinit var handler: AndroidProcessHandler
  lateinit var textEmitter: TextEmitter
  lateinit var monitorManagerListener: AndroidProcessMonitorManagerListener

  @Before
  fun setUp() {
    initMocks(this)

    `when`(mockProject.getService(eq(ExecutionTargetManager::class.java)))
      .thenReturn(mockExecutionTargetManager)
    `when`(mockExecutionTargetManager.activeTarget).thenReturn(mockExecutionTarget)
    `when`(mockAnsiEscapeDecoder.escapeText(any(), any(), any())).then { invocation ->
      val (text, attributes, textAcceptor) = invocation.arguments
      text as String
      attributes as Key<*>
      textAcceptor as AnsiEscapeDecoder.ColoredTextAcceptor
      textAcceptor.coloredTextAvailable(text, attributes)
    }

    handler = AndroidProcessHandler(
      mockProject,
      TARGET_APP_NAME,
      /*captureLogcat=*/true,
      mockAnsiEscapeDecoder,
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
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()

    inOrder.verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(true))
    inOrder.verify(mockProcessListener, timeout(1000)).processTerminated(any())
    inOrder.verifyNoMoreInteractions()

    assertThat(handler.isProcessTerminated).isTrue()
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
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()

    inOrder.verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(true))
    inOrder.verify(mockProcessListener, timeout(1000)).processTerminated(any())
    inOrder.verifyNoMoreInteractions()

    assertThat(handler.isProcessTerminated).isTrue()
  }

  @Test
  fun textEmitterShouldRedirectToNotifyText() {
    textEmitter.emit("test emit message", ProcessOutputTypes.STDOUT)
    verify(mockProcessListener).onTextAvailable(argThat { event -> event.text == "test emit message" }, eq(ProcessOutputTypes.STDOUT))
  }

  @Test
  fun destroyProcess() {
    val inOrder = inOrder(mockProcessListener)

    handler.destroyProcess()
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()

    inOrder.verify(mockProcessListener).startNotified(any())
    inOrder.verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(true))
    inOrder.verify(mockProcessListener, timeout(1000)).processTerminated(any())
    inOrder.verifyNoMoreInteractions()

    assertThat(handler.isProcessTerminated).isTrue()
  }

  @Test
  fun detachProcess() {
    val inOrder = inOrder(mockProcessListener)

    handler.detachProcess()
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()

    inOrder.verify(mockProcessListener).startNotified(any())
    inOrder.verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(false))
    inOrder.verify(mockProcessListener, timeout(1000)).processTerminated(any())
    inOrder.verifyNoMoreInteractions()

    assertThat(handler.isProcessTerminated).isTrue()
  }

  @Test
  fun canKillProcess_returnsFalseWhenNoAssociatedDevices() {
    assertThat(handler.canKillProcess()).isFalse()
  }

  @Test
  fun canKillProcess_returnsTrueWhenThereIsAnyAssociatedDevice() {
    val nonAssociatedDevice = mock(IDevice::class.java)
    val associatedDevice = mock(IDevice::class.java)

    `when`(mockExecutionTarget.runningDevices).thenReturn(listOf(nonAssociatedDevice, associatedDevice))
    `when`(mockMonitorManager.isAssociated(associatedDevice)).thenReturn(true)

    assertThat(handler.canKillProcess()).isTrue()
  }

  @Test
  fun canKillProcess_returnsFalseWhenThereAreNoAssociatedDevices() {
    val nonAssociatedDevice1 = mock(IDevice::class.java)
    val nonAssociatedDevice2 = mock(IDevice::class.java)

    `when`(mockExecutionTarget.runningDevices).thenReturn(listOf(nonAssociatedDevice1, nonAssociatedDevice2))

    assertThat(handler.canKillProcess()).isFalse()
  }

  @Test
  fun canKillProcess_returnsFalseWhenActiveTargetIsNotAndroidTarget() {
    `when`(mockExecutionTargetManager.activeTarget).thenReturn(mock(ExecutionTarget::class.java))

    assertThat(handler.canKillProcess()).isFalse()
  }

  private fun createMockDevice(apiVersion: Int): IDevice {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevice.version).thenReturn(AndroidVersion(apiVersion))
    return mockDevice
  }
}