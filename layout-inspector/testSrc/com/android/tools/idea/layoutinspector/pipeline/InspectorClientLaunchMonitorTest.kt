/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.ListenerCollection
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private enum class DebuggerType {
  JAVA,
  HYBRID
}

private const val PORT = 53707

private abstract class XClientProvidingDebugProcess(session: XDebugSession) :
  XDebugProcess(session) {
  abstract val client: Client
}

@OptIn(ExperimentalCoroutinesApi::class)
class InspectorClientLaunchMonitorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun monitorOffersUserToStopsStuckConnection() {
    val project = projectRule.project
    val model = NotificationModel(project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val monitor =
      InspectorClientLaunchMonitor(
        project,
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope,
      )
    val client = mock<InspectorClient>()
    monitor.start(client)
    timeoutScope.testScheduler.advanceUntilIdle()
    assertThat(timeoutScope.testScheduler.currentTime)
      .isEqualTo(TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SECONDS))
    assertThat(model.notifications.single().message)
      .isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
    assertThat(stats.currentProgress).isEqualTo(AttachErrorState.NOT_STARTED)
    monitor.stop()
    model.clear()
  }

  @Test
  fun noTimeoutMessageIfConnectedBeforeTimeout() {
    val project = projectRule.project
    val model = NotificationModel(project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    run {
      val monitor =
        InspectorClientLaunchMonitor(
          project,
          model,
          ListenerCollection.createWithDirectExecutor(),
          stats,
          unused,
          timeoutScope,
          debuggerScope,
        )
      val client = mock<InspectorClient>()
      monitor.start(client)
      timeoutScope.advanceTimeBy(5.seconds)
      monitor.updateProgress(CONNECTED_STATE)
      timeoutScope.testScheduler.advanceUntilIdle()
      assertThat(model.notifications).isEmpty()
    }
  }

  @Test
  fun attachErrorStateListenersAreCalled() {
    val listeners = ListenerCollection.createWithDirectExecutor<(AttachErrorState) -> Unit>()
    val mockListener = mock<(AttachErrorState) -> Unit>()
    listeners.add(mockListener)

    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val scope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val monitor = InspectorClientLaunchMonitor(projectRule.project, model, listeners, stats, scope)
    monitor.updateProgress(AttachErrorState.ADB_PING)

    verify(mockListener).invoke(AttachErrorState.ADB_PING)
    assertThat(stats.currentProgress).isEqualTo(AttachErrorState.ADB_PING)
  }

  @Test
  fun slowAttachMessageWithLegacyClient() {
    val legacyClient = mock<InspectorClient>()
    whenever(legacyClient.clientType).thenReturn(ClientType.LEGACY_CLIENT)
    slowAttachMessage(legacyClient, "Disconnect")
  }

  @Test
  fun slowAttachMessageWithAppInspectionClient() = withEmbeddedLayoutInspector {
    enableEmbeddedLayoutInspector = false
    val appInspectionClient1 = mock<InspectorClient>()
    whenever(appInspectionClient1.clientType).thenReturn(ClientType.APP_INSPECTION_CLIENT)
    slowAttachMessage(appInspectionClient1, "Dump Views")

    enableEmbeddedLayoutInspector = true
    val appInspectionClient2 = mock<InspectorClient>()
    whenever(appInspectionClient2.clientType).thenReturn(ClientType.APP_INSPECTION_CLIENT)
    slowAttachMessage(appInspectionClient2, "Disconnect")
  }

  private fun slowAttachMessage(client: InspectorClient, expectedDisconnectMessage: String) {
    val project = projectRule.project
    val projectSystem = projectRule.project.getProjectSystem() as DefaultProjectSystem
    val moduleSystem = DefaultModuleSystem(projectRule.module)
    projectSystem.setModuleSystem(moduleSystem.module, moduleSystem)
    moduleSystem.usesCompose = true

    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val monitor =
      InspectorClientLaunchMonitor(
        project,
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope
      )
    monitor.start(client)
    timeoutScope.testScheduler.advanceUntilIdle()
    verify(client, never()).disconnect()
    val notification1 = model.notifications.single()
    assertThat(notification1.message)
      .isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
    assertThat(notification1.actions.first().name).isEqualTo("Continue Waiting")
    assertThat(notification1.actions.last().name).isEqualTo(expectedDisconnectMessage)

    // Continue waiting:
    notification1.actions.first().invoke(mock())
    assertThat(model.notifications).isEmpty()

    timeoutScope.testScheduler.advanceUntilIdle()
    verify(client, never()).disconnect()
    val notification2 = model.notifications.single()
    assertThat(notification2.message)
      .isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
    assertThat(notification2.actions.first().name).isEqualTo("Continue Waiting")
    assertThat(notification2.actions.last().name).isEqualTo(expectedDisconnectMessage)

    // Continue waiting:
    notification2.actions.first().invoke(mock())
    assertThat(model.notifications).isEmpty()

    timeoutScope.testScheduler.advanceUntilIdle()
    verify(client, never()).disconnect()
    val notification3 = model.notifications.single()
    assertThat(notification3.message)
      .isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
    assertThat(notification3.actions.first().name).isEqualTo("Continue Waiting")
    assertThat(notification3.actions.last().name).isEqualTo(expectedDisconnectMessage)

    // Disconnect:
    notification3.actions.last().invoke(mock())
    assertThat(model.notifications).isEmpty()
    verify(client).disconnect()
  }

  @Test
  fun slowAttachMessageRemovedWhenConnected() {
    val project = projectRule.project
    val projectSystem = projectRule.project.getProjectSystem() as DefaultProjectSystem
    val moduleSystem = DefaultModuleSystem(projectRule.module)
    projectSystem.setModuleSystem(moduleSystem.module, moduleSystem)
    moduleSystem.usesCompose = true

    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val monitor =
      InspectorClientLaunchMonitor(
        project,
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope
      )
    val client = mock<InspectorClient>()
    monitor.start(client)
    timeoutScope.testScheduler.advanceUntilIdle()
    verify(client, never()).disconnect()
    val notification1 = model.notifications.single()
    assertThat(notification1.message)
      .isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
    assertThat(notification1.actions.first().name).isEqualTo("Continue Waiting")
    assertThat(notification1.actions.last().name).isEqualTo("Disconnect")

    monitor.updateProgress(CONNECTED_STATE)
    timeoutScope.testScheduler.advanceUntilIdle()
    assertThat(model.notifications).isEmpty()
    assertThat(stats.currentProgress).isEqualTo(AttachErrorState.MODEL_UPDATED)
  }

  @Test
  fun slowAttachedMessageNotScheduledWhenClientIsClosed() {
    val project = projectRule.project
    val projectSystem = projectRule.project.getProjectSystem() as DefaultProjectSystem
    val moduleSystem = DefaultModuleSystem(projectRule.module)
    projectSystem.setModuleSystem(moduleSystem.module, moduleSystem)
    moduleSystem.usesCompose = true

    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val monitor =
      InspectorClientLaunchMonitor(
        project,
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope
      )
    val client = mock<InspectorClient>()
    monitor.start(client)
    monitor.stop()
    monitor.updateProgress(AttachErrorState.ADB_PING)
    timeoutScope.testScheduler.advanceUntilIdle()
    assertThat(model.notifications).isEmpty()
    assertThat(stats.currentProgress).isEqualTo(AttachErrorState.ADB_PING)
  }

  @Test
  fun debuggerPausedInJava() {
    val client = setupDebuggingProcess(DebuggerType.JAVA, pausedInJava = true)
    val project = projectRule.project
    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    run {
      val monitor =
        InspectorClientLaunchMonitor(
          project,
          model,
          ListenerCollection.createWithDirectExecutor(),
          stats,
          unused,
          timeoutScope,
          debuggerScope
        )
      monitor.start(client)
      debuggerScope.advanceTimeBy(DEBUGGER_CHECK_SECONDS.seconds)
      debuggerScope.testScheduler.runCurrent()
      assertThat(model.notifications.single().message)
        .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))

      // Check that the timeout warning is not shown when the debugger warning is shown
      timeoutScope.advanceUntilIdle()
      timeoutScope.testScheduler.runCurrent()
      assertThat(model.notifications.single().message)
        .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))
      assertThat(model.notifications.single().actions.first().name).isEqualTo("Resume Debugger")
      assertThat(model.notifications.single().actions.last().name).isEqualTo("Disconnect")

      // Resume the debugger:
      model.notifications.single().actions.first().invoke(mock())
      val manager = XDebuggerManager.getInstance(projectRule.project)
      verify(manager.debugSessions.single()).resume()
      verify(client, never()).disconnect()

      val data = DynamicLayoutInspectorSession.newBuilder()
      client.stats.save(data)
      val savedStats = data.build()
      assertThat(savedStats.attach.debuggerAttached).isTrue()
      assertThat(savedStats.attach.debuggerPausedDuringAttach).isTrue()
    }
  }

  @Test
  fun debuggerPausedInNative() {
    val client = setupDebuggingProcess(DebuggerType.HYBRID, pausedInJava = false)
    val project = projectRule.project
    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    run {
      val monitor =
        InspectorClientLaunchMonitor(
          project,
          model,
          ListenerCollection.createWithDirectExecutor(),
          stats,
          unused,
          timeoutScope,
          debuggerScope
        )
      monitor.start(client)
      debuggerScope.advanceTimeBy(DEBUGGER_CHECK_SECONDS.seconds)
      debuggerScope.testScheduler.runCurrent()
      assertThat(model.notifications.single().message)
        .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))

      // Check that the timeout warning is not shown when the debugger warning is shown
      timeoutScope.advanceUntilIdle()
      timeoutScope.testScheduler.runCurrent()
      assertThat(model.notifications.single().message)
        .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))

      // Resume the debugger:
      model.notifications.single().actions.first().invoke(mock())
      val manager = XDebuggerManager.getInstance(projectRule.project)
      verify(manager.debugSessions.filter { it.isPaused }.single()).resume()
      verify(manager.debugSessions.filter { !it.isPaused }.single(), never()).resume()
      verify(client, never()).disconnect()
    }
  }

  private fun setupDebuggingProcess(
    debuggerType: DebuggerType,
    pausedInJava: Boolean
  ): InspectorClient {
    val client: InspectorClient = mock()
    whenever(client.process).thenReturn(processDescriptor)
    whenever(client.stats)
      .thenReturn(
        SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT, areMultipleProjectsOpen = { false })
      )
    val adbFileProvider = projectRule.mockProjectService(AdbFileProvider::class.java)
    whenever(adbFileProvider.get()).thenReturn(mock())
    val adbService = projectRule.mockService(AdbService::class.java)
    val bridge: AndroidDebugBridge = mock()
    val future = Futures.immediateFuture(bridge)
    whenever(adbService.getDebugBridge(any<File>())).thenReturn(future)
    val device: IDevice = mock()
    val adbClient: Client = mock()
    val clientData: ClientData = mock()
    whenever(bridge.devices).thenReturn(arrayOf(device))
    whenever(device.serialNumber).thenReturn(processDescriptor.device.serial)
    whenever(device.clients).thenReturn(arrayOf(adbClient))
    whenever(adbClient.clientData).thenReturn(clientData)
    whenever(adbClient.isDebuggerAttached).thenReturn(true)
    whenever(adbClient.debuggerListenPort).thenReturn(PORT)
    whenever(clientData.pid).thenReturn(processDescriptor.pid)
    setUpHybridDebugger(adbClient, debuggerType, pausedInJava)
    return client
  }

  private fun setUpHybridDebugger(
    client: Client,
    debuggerType: DebuggerType,
    pausedInJava: Boolean
  ) {
    val manager = projectRule.mockProjectService(XDebuggerManager::class.java)
    val session: DebuggerSession = mock()
    val process: DebugProcessImpl = mock()
    val connection: RemoteConnection = mock()
    val javaSession: XDebugSession = mock()
    val nativeSession: XDebugSession = mock()
    val javaProcess: JavaDebugProcess = mock()
    val nativeProcess: XClientProvidingDebugProcess = mock()
    val sessions =
      when (debuggerType) {
        DebuggerType.JAVA -> arrayOf(javaSession)
        DebuggerType.HYBRID -> arrayOf(javaSession, nativeSession)
      }
    whenever(manager.debugSessions).thenReturn(sessions)
    whenever(javaSession.debugProcess).thenReturn(javaProcess)
    whenever(javaSession.isPaused).thenReturn(pausedInJava)
    whenever(javaProcess.debuggerSession).thenReturn(session)
    whenever(session.process).thenReturn(process)
    whenever(nativeSession.debugProcess).thenReturn(nativeProcess)
    whenever(nativeSession.isPaused).thenReturn(!pausedInJava)
    whenever(nativeProcess.client).thenReturn(client)
    whenever(session.process).thenReturn(process)
    whenever(process.connection).thenReturn(connection)
    whenever(connection.debuggerAddress).thenReturn(PORT.toString())
  }

  private val processDescriptor =
    object : ProcessDescriptor {
      override val device =
        object : DeviceDescriptor {
          override val manufacturer = "mfg"
          override val model = "model"
          override val serial = "emulator-33"
          override val isEmulator = true
          override val apiLevel = 33
          override val version = "10.0.0"
          override val codename: String? = null
        }
      override val abiCpuArch = "x86_64"
      override val name = "my name"
      override val packageName = "my package name"
      override val isRunning = true
      override val pid = 1234
      override val streamId = 4321L
    }

  private fun TestScope.advanceTimeBy(duration: Duration) =
    testScheduler.advanceTimeBy(duration.inWholeMilliseconds)
}
