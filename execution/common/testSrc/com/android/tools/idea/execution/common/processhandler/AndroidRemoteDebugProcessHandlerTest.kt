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
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.engine.DebugProcess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AndroidRemoteDebugProcessHandlerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  lateinit var debugProcess: DebugProcess
  lateinit var client: Client
  lateinit var device: IDevice

  @Before
  fun setUpDebugSession() {
    client = mock<Client>()
    device = createDevice()
    whenever(client.device).thenReturn(device)
    val clientData = object : ClientData(client, 111) {
      override fun getPackageName() = "MyApp"
      override fun getProcessName() = "MyApp"
    }
    whenever(client.clientData).thenReturn(clientData)
    debugProcess = mock<DebugProcess>()
    val debugManager = projectRule.mockProjectService(DebuggerManager::class.java)
    whenever(debugManager.getDebugProcess(any<AndroidRemoteDebugProcessHandler>())).thenReturn(debugProcess)
  }

  private fun createDevice(): IDevice {
    val mockDevice = mock<IDevice>()
    whenever(mockDevice.version).thenReturn(AndroidVersion(26))
    whenever(mockDevice.isOnline).thenReturn(true)
    return mockDevice
  }

  @Test
  fun restoreConnectionOnDetach() {
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.detachProcess()
    verify(client).notifyVmMirrorExited()
  }

  @Test
  fun `don'tRestoreConnectionOnDestroy`() {
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.destroyProcess()
    verify(client, never()).notifyVmMirrorExited()
  }

  @Test
  fun `don'tTerminateTargetVMOnDestroy`() {
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.destroyProcess()
    processHandler.waitFor()
    verify(debugProcess).stop(false)
  }
}