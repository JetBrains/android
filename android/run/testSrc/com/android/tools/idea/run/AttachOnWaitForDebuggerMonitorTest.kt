/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import org.junit.Test
import kotlin.jvm.internal.Ref.BooleanRef

class AttachOnWaitForDebuggerMonitorTest {
  open class FakeDebuggerHost(
    private val enabled: Boolean,
    private val canDebugRun: Boolean,
    private val anyActiveDebugSession: Boolean,
    private val attach: (project: Project,
                         debugger: AndroidDebugger<out AndroidDebuggerState>,
                         client: Client,
                         config: AndroidRunConfigurationBase) -> Unit
    ): AttachOnWaitForDebuggerMonitor.DebuggerHost(project = mock()) {

    private val applicationIdProvider = mock<ApplicationIdProvider>().also {
      whenever(it.packageName).thenReturn("Foo")
    }
    private val config: AndroidRunConfigurationBase = mock<AndroidRunConfigurationBase>().also {
      whenever(it.applicationIdProvider).thenReturn(applicationIdProvider)
    }
    private val debuggerState: AndroidDebugger<AndroidDebuggerState> = mock()
    private val device: IDevice = mock()
    internal val clientData: ClientData = mock<ClientData>().also {
      whenever(it.debuggerConnectionStatus).thenReturn(ClientData.DebuggerStatus.WAITING)
    }
    internal val client: Client = mock<Client>().also {
      whenever(it.clientData).thenReturn(clientData)
      whenever(it.device).thenReturn(device)
    }

    override val runConfig: AndroidRunConfigurationBase?
      get() = config

    override fun debugger(config: AndroidRunConfigurationBase): AndroidDebugger<out AndroidDebuggerState>? {
      return debuggerState
    }

    override fun enabled(config: AndroidRunConfigurationBase, debugger: AndroidDebugger<out AndroidDebuggerState>): Boolean {
      return enabled
    }

    override fun canDebugRun(project: Project, config: AndroidRunConfigurationBase): Boolean {
      return canDebugRun
    }

    override fun anyActiveDebugSessions(project: Project, device: IDevice, applicationId: String): Boolean {
      return anyActiveDebugSession
    }

    override fun attachAction(project: Project,
                              debugger: AndroidDebugger<out AndroidDebuggerState>,
                              client: Client,
                              config: AndroidRunConfigurationBase) {
      this.attach(project, debugger, client, config)
    }
  }

  @Test
  fun testAttach() {
    val attachCalled = BooleanRef()
    val debuggerHost = object: FakeDebuggerHost(true, true, false, { _, _, _, _ -> attachCalled.element = true }) {}
    val auto = AttachOnWaitForDebuggerMonitor(debuggerHost)

    auto.listener.clientChanged(debuggerHost.client, Client.CHANGE_DEBUGGER_STATUS)
    Truth.assertThat(attachCalled.element).isTrue()
  }

  @Test
  fun testNotWaiting() {
    val debuggerHost = object: FakeDebuggerHost(true, true, false, { _, _, _, _ -> throw RuntimeException() }) {}
    val auto = AttachOnWaitForDebuggerMonitor(debuggerHost)

    whenever(debuggerHost.clientData.debuggerConnectionStatus).thenReturn(ClientData.DebuggerStatus.ATTACHED)
    auto.listener.clientChanged(debuggerHost.client, Client.CHANGE_DEBUGGER_STATUS)
  }

  @Test
  fun testDisabled() {
    val debuggerHost = object: FakeDebuggerHost(false, true, false, { _, _, _, _ -> throw RuntimeException() }) {}
    val auto = AttachOnWaitForDebuggerMonitor(debuggerHost)

    auto.listener.clientChanged(debuggerHost.client, Client.CHANGE_DEBUGGER_STATUS)
  }

  @Test
  fun testDebuggerCannotStart() {
    val debuggerHost = object: FakeDebuggerHost(true, false, false, { _, _, _, _ -> throw RuntimeException() }) {}
    val auto = AttachOnWaitForDebuggerMonitor(debuggerHost)

    auto.listener.clientChanged(debuggerHost.client, Client.CHANGE_DEBUGGER_STATUS)
  }

  @Test
  fun testDebuggerAlreadyAttached() {
    val debuggerHost = object: FakeDebuggerHost(true, true, true, { _, _, _, _ -> throw RuntimeException() }) {}
    val auto = AttachOnWaitForDebuggerMonitor(debuggerHost)

    auto.listener.clientChanged(debuggerHost.client, Client.CHANGE_DEBUGGER_STATUS)
  }

  @Test
  fun testDebuggerAlreadyAttachedAndCannotRunt() {
    val debuggerHost = object: FakeDebuggerHost(true, false, true, { _, _, _, _ -> throw RuntimeException() }) {}
    val auto = AttachOnWaitForDebuggerMonitor(debuggerHost)

    auto.listener.clientChanged(debuggerHost.client, Client.CHANGE_DEBUGGER_STATUS)
  }
}