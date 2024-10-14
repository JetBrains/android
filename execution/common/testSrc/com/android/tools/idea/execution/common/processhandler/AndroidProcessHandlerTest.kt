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

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.testFramework.ProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for [AndroidProcessHandler].
 */
@RunWith(JUnit4::class)
class AndroidProcessHandlerTest {
  companion object {
    const val TARGET_APP_NAME: String = "example.target.app"
  }

  @get:Rule
  val projectRule = ProjectRule()

  val project
    get() = projectRule.project

  @Mock
  lateinit var mockDeploymentAppService: DeploymentApplicationService

  @Mock
  lateinit var mockMonitorManager: AndroidProcessMonitorManager

  @Mock
  lateinit var mockProcessListener: ProcessListener

  @Mock
  lateinit var mockAnsiEscapeDecoder: AnsiEscapeDecoder
  private var autoTerminate: Boolean = true

  val handler: AndroidProcessHandler by lazy {
    AndroidProcessHandler(
      TARGET_APP_NAME,
      { device -> device.forceStop(TARGET_APP_NAME) },
      autoTerminate,
      mockAnsiEscapeDecoder,
      mockDeploymentAppService
    ) { emitter, listener ->
      textEmitter = emitter
      monitorManagerListener = listener
      mockMonitorManager
    }.apply {
      addProcessListener(mockProcessListener)
      startNotify()
    }
  }
  private lateinit var textEmitter: TextEmitter
  private lateinit var monitorManagerListener: AndroidProcessMonitorManagerListener

  @Before
  fun setUp() {
    initMocks(this)

    whenever(mockAnsiEscapeDecoder.escapeText(any(), any(), any())).then { invocation ->
      val (text, attributes, textAcceptor) = invocation.arguments
      text as String
      attributes as Key<*>
      textAcceptor as AnsiEscapeDecoder.ColoredTextAcceptor
      textAcceptor.coloredTextAvailable(text, attributes)
    }
  }

  @After
  fun tearDown() {
    handler.destroyProcess()
  }

  @Test
  fun runProcessOnOneDevice() {
    handler

    val inOrder = inOrder(mockProcessListener)
    inOrder.verify(mockProcessListener).startNotified(any())

    val mockDevice = createMockDevice(28)
    handler.addTargetDevice(mockDevice)
    verify(mockMonitorManager).add(eq(mockDevice))

    monitorManagerListener.onAllTargetProcessesTerminated()
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()

    inOrder.verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(true))
    inOrder.verify(mockProcessListener, timeout(1000)).processTerminated(any())
    inOrder.verifyNoMoreInteractions()

    assertThat(handler.isProcessTerminated).isTrue()
  }

  @Test
  fun runProcessOnMultipleDevices() {
    handler

    val inOrder = inOrder(mockProcessListener)
    inOrder.verify(mockProcessListener).startNotified(any())

    handler.addTargetDevice(createMockDevice(28))
    handler.addTargetDevice(createMockDevice(27))
    handler.addTargetDevice(createMockDevice(29))

    monitorManagerListener.onAllTargetProcessesTerminated()
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()

    inOrder.verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(true))
    inOrder.verify(mockProcessListener, timeout(1000)).processTerminated(any())
    inOrder.verifyNoMoreInteractions()

    assertThat(handler.isProcessTerminated).isTrue()
  }

  @Test
  fun textEmitterShouldRedirectToNotifyText() {
    handler
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
  fun callCloseForMonitorManager() {
    val mockDevice = createMockDevice(28)
    handler.addTargetDevice(mockDevice)

    handler.destroyProcess()
    handler.waitFor()

    verify(mockMonitorManager).close()
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
  fun processHandlerShouldAutoTerminateWhenAutoTerminateIsEnabled() {
    autoTerminate = true

    handler.addTargetDevice(createMockDevice(28))
    monitorManagerListener.onAllTargetProcessesTerminated()

    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()
    inOrder(mockProcessListener).apply {
      verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(true))
      verify(mockProcessListener, timeout(1000)).processTerminated(any())
      verifyNoMoreInteractions()
    }

    assertThat(handler.isProcessTerminated).isTrue()
  }

  @Test
  fun processHandlerShouldNotAutoTerminateWhenAutoTerminateIsOff() {
    autoTerminate = false

    handler.addTargetDevice(createMockDevice(28))
    monitorManagerListener.onAllTargetProcessesTerminated()
    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isFalse()

    verify(mockProcessListener, never()).processWillTerminate(any(), anyBoolean())
    assertThat(handler.isProcessTerminated).isFalse()
  }

  @Test
  fun processHandlerShouldBeDetachedAfterAllTargetDeviceIsDetached() {
    val targetDevice = createMockDevice(28)

    handler.addTargetDevice(targetDevice)
    whenever(mockMonitorManager.isEmpty()).thenReturn(true)
    handler.detachDevice(targetDevice)

    assertThat(handler.isProcessTerminating || handler.isProcessTerminated).isTrue()
    inOrder(mockProcessListener).apply {
      verify(mockProcessListener).processWillTerminate(any(), /*willBeDestroyed=*/eq(false))
      verify(mockProcessListener, timeout(1000)).processTerminated(any())
      verifyNoMoreInteractions()
    }

    assertThat(handler.isProcessTerminated).isTrue()
  }

  private fun createMockDevice(apiVersion: Int): IDevice {
    val mockDevice = mock<IDevice>()
    whenever(mockDevice.version).thenReturn(AndroidVersion(apiVersion))
    return mockDevice
  }
}