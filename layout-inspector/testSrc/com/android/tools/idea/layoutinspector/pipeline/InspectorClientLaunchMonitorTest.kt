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

import com.android.adblib.tools.debugging.getOrNull
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.adblib.tools.debugging.jdwpProxySocketServer
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.layoutinspector.DeviceProvisionerServiceCleanUpRule
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.debugger.DEBUGGER_CHECK_DELAY
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.ListenerCollection
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RuleChain
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

private enum class DebuggerType {
  JAVA,
  HYBRID,
}

@OptIn(ExperimentalCoroutinesApi::class)
class InspectorClientLaunchMonitorTest {
  private val provisionerRule = DeviceProvisionerRule()
  private val projectRule = AndroidProjectRule.inMemory()
  private val provisionerServiceRule = DeviceProvisionerServiceCleanUpRule { projectRule.project }

  @get:Rule val chain = RuleChain(projectRule, provisionerRule, provisionerServiceRule)

  @Before
  fun before() {
    val manager = XDebuggerManager.getInstance(projectRule.project)
    projectRule.replaceProjectService(XDebuggerManager::class.java, spy(manager))
  }

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
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope,
      )
    val client = setupSimpleClient()
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
          model,
          ListenerCollection.createWithDirectExecutor(),
          stats,
          unused,
          timeoutScope,
          debuggerScope,
        )
      val client = setupSimpleClient()
      monitor.start(client)
      timeoutScope.advanceTimeBy(5.seconds)
      monitor.updateProgress(CONNECTED_STATE)
      timeoutScope.testScheduler.advanceUntilIdle()
      assertThat(model.notifications).isEmpty()
    }
  }

  @Test
  fun attachErrorStateListenersAreCalled() = runTest {
    val listeners = ListenerCollection.createWithDirectExecutor<(AttachErrorState) -> Unit>()
    val mockListener = mock<(AttachErrorState) -> Unit>()
    listeners.add(mockListener)

    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val monitor = InspectorClientLaunchMonitor(model, listeners, stats, this)
    monitor.updateProgress(AttachErrorState.ADB_PING)

    verify(mockListener).invoke(AttachErrorState.ADB_PING)
    assertThat(stats.currentProgress).isEqualTo(AttachErrorState.ADB_PING)
  }

  @Test
  fun slowAttachMessageWithLegacyClient() {
    val legacyClient = setupSimpleClient()
    whenever(legacyClient.clientType).thenReturn(ClientType.LEGACY_CLIENT)
    slowAttachMessage(legacyClient, "Disconnect")
  }

  @Test
  fun slowAttachMessageWithAppInspectionClient() = withEmbeddedLayoutInspector {
    enableEmbeddedLayoutInspector = false
    val appInspectionClient1 = setupSimpleClient()
    whenever(appInspectionClient1.clientType).thenReturn(ClientType.APP_INSPECTION_CLIENT)
    slowAttachMessage(appInspectionClient1, "Dump Views")

    enableEmbeddedLayoutInspector = true
    val appInspectionClient2 = setupSimpleClient()
    whenever(appInspectionClient2.clientType).thenReturn(ClientType.APP_INSPECTION_CLIENT)
    slowAttachMessage(appInspectionClient2, "Disconnect")
  }

  private fun slowAttachMessage(
    client: AbstractInspectorClient,
    expectedDisconnectMessage: String,
  ) {
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
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope,
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
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope,
      )
    val client = setupSimpleClient()
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
        model,
        ListenerCollection.createWithDirectExecutor(),
        stats,
        unused,
        timeoutScope,
        debuggerScope,
      )
    val client = setupSimpleClient()
    monitor.start(client)
    monitor.stop()
    monitor.updateProgress(AttachErrorState.ADB_PING)
    timeoutScope.testScheduler.advanceUntilIdle()
    assertThat(model.notifications).isEmpty()
    assertThat(stats.currentProgress).isEqualTo(AttachErrorState.ADB_PING)
  }

  @Test
  fun debuggerPausedInJava() =
    withEmbeddedLayoutInspector(false) {
      runTest {
        val client = setupDebuggingProcess(DebuggerType.JAVA, pausedInJava = true)
        val model = NotificationModel(projectRule.project)
        val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
        val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
        val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
        val monitor =
          InspectorClientLaunchMonitor(
            model,
            ListenerCollection.createWithDirectExecutor(),
            stats,
            this,
            timeoutScope,
            debuggerScope,
          )
        monitor.start(client)
        debuggerScope.testScheduler.advanceTimeBy(DEBUGGER_CHECK_DELAY)
        assertThat(model.notifications.single().message)
          .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))

        // Check that the timeout warning is not shown when the debugger warning is shown
        timeoutScope.advanceUntilIdle()
        timeoutScope.testScheduler.runCurrent()

        assertThat(model.notifications.single().message)
          .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))
        assertThat(model.notifications.single().actions.size).isEqualTo(2)
        assertThat(model.notifications.single().actions.first().name).isEqualTo("Resume Debugger")
        assertThat(model.notifications.single().actions.last().name).isEqualTo("Disconnect")

        // Resume the debugger:
        model.notifications.single().actions.first().invoke(mock())
        val manager = XDebuggerManager.getInstance(projectRule.project)
        verify((manager.debugSessions as Array<XDebugSession>).single()).resume()

        val data = DynamicLayoutInspectorSession.newBuilder()
        client.stats.save(data)
        val savedStats = data.build()
        assertThat(savedStats.attach.debuggerAttached).isTrue()
        assertThat(savedStats.attach.debuggerPausedDuringAttach).isTrue()
        debuggerScope.cancel()
      }
    }

  @Test
  fun debuggerPausedInJavaEmbeddedLi() =
    withEmbeddedLayoutInspector(true) {
      runTest {
        val client = setupDebuggingProcess(DebuggerType.JAVA, pausedInJava = true)
        val model = NotificationModel(projectRule.project)
        val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
        val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
        val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
        val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
        run {
          val monitor =
            InspectorClientLaunchMonitor(
              model,
              ListenerCollection.createWithDirectExecutor(),
              stats,
              unused,
              timeoutScope,
              debuggerScope,
            )
          monitor.start(client)
          debuggerScope.advanceTimeBy(DEBUGGER_CHECK_DELAY)
          debuggerScope.testScheduler.runCurrent()
          assertThat(model.notifications.single().message)
            .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))

          // Check that the timeout warning is not shown when the debugger warning is shown
          timeoutScope.advanceUntilIdle()
          timeoutScope.testScheduler.runCurrent()
          assertThat(model.notifications.single().message)
            .isEqualTo(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))
          assertThat(model.notifications.single().actions.size).isEqualTo(1)
          assertThat(model.notifications.single().actions.first().name).isEqualTo("Resume Debugger")

          // Resume the debugger:
          model.notifications.single().actions.first().invoke(mock())
          val manager = XDebuggerManager.getInstance(projectRule.project)
          verify(manager.debugSessions.single()).resume()

          val data = DynamicLayoutInspectorSession.newBuilder()
          client.stats.save(data)
          val savedStats = data.build()
          assertThat(savedStats.attach.debuggerAttached).isTrue()
          assertThat(savedStats.attach.debuggerPausedDuringAttach).isTrue()
        }
      }
    }

  @Test
  fun debuggerPausedInNative() = runTest {
    val client = setupDebuggingProcess(DebuggerType.HYBRID, pausedInJava = false)
    val model = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    run {
      val monitor =
        InspectorClientLaunchMonitor(
          model,
          ListenerCollection.createWithDirectExecutor(),
          stats,
          unused,
          timeoutScope,
          debuggerScope,
        )
      monitor.start(client)
      debuggerScope.advanceTimeBy(DEBUGGER_CHECK_DELAY)
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
      verify(manager.debugSessions.single { it.isPaused }).resume()
      verify(manager.debugSessions.single { !it.isPaused }, never()).resume()
    }
  }

  private fun setupSimpleClient(): AbstractInspectorClient {
    val client: AbstractInspectorClient = mock()
    whenever(client.project).thenReturn(projectRule.project)
    return client
  }

  private suspend fun setupDebuggingProcess(
    debuggerType: DebuggerType,
    pausedInJava: Boolean,
  ): AbstractInspectorClient {
    val device =
      provisionerRule.deviceProvisionerPlugin.addNewDevice(processDescriptor.device.serial)
    val provisionerService: DeviceProvisionerService = mock()
    whenever(provisionerService.deviceProvisioner).thenReturn(provisionerRule.deviceProvisioner)
    projectRule.replaceProjectService(DeviceProvisionerService::class.java, provisionerService)
    device.activationAction.activate()
    val connectedDevice = waitNonNull { device.state.connectedDevice }
    device.fakeAdbDevice!!.startClient(
      processDescriptor.pid,
      userId = 0,
      processDescriptor.name,
      processDescriptor.packageName,
      isWaiting = false,
    )
    val jdwpProcess = waitNonNull {
      connectedDevice.jdwpProcessTracker.processesFlow.value.singleOrNull()
    }
    val debuggingPort = waitNonNull {
      jdwpProcess.jdwpProxySocketServer.proxyStatusFlow.value.socketAddress.getOrNull()?.port
    }
    setUpHybridDebugger(debuggingPort, debuggerType, pausedInJava)

    val notificationModel = NotificationModel(projectRule.project)
    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT) { false }
    val scope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
    val client =
      FakeInspector(
        projectRule.project,
        notificationModel,
        processDescriptor,
        stats,
        scope,
        projectRule.testRootDisposable,
      )
    scope.testScheduler.advanceUntilIdle()
    return client
  }

  private fun <T> waitNonNull(timeout: Duration = 30.seconds, provider: suspend () -> T?): T {
    var value: T? = null
    waitForCondition(timeout) {
      value = runBlocking { provider() }
      value != null
    }
    return value!!
  }

  private fun setUpHybridDebugger(
    debuggingPort: Int,
    debuggerType: DebuggerType,
    pausedInJava: Boolean,
  ) {
    val manager = XDebuggerManager.getInstance(projectRule.project)
    val session: DebuggerSession = mock()
    val process: DebugProcessImpl = mock()
    val connection: RemoteConnection = mock()
    val javaSession: XDebugSession = mock()
    val nativeSession: XDebugSession = mock()
    val javaProcess: JavaDebugProcess = mock()
    val nativeProcess: FakeAndroidNativeHybridDebugProcess = mock()
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
    whenever(nativeProcess.javaSession).thenReturn(javaSession)
    whenever(session.process).thenReturn(process)
    whenever(process.connection).thenReturn(connection)
    whenever(connection.debuggerAddress).thenReturn(debuggingPort.toString())
  }

  private val processDescriptor =
    object : ProcessDescriptor {
      override val device =
        object : DeviceDescriptor {
          override val manufacturer = "mfg"
          override val model = "model"
          override val serial = "emulator-1234"
          override val isEmulator = true
          override val apiLevel = AndroidApiLevel(33)
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

  private class FakeInspector(
    project: Project,
    notificationModel: NotificationModel,
    process: ProcessDescriptor,
    stats: SessionStatistics,
    scope: CoroutineScope,
    parentDisposable: Disposable,
  ) :
    AbstractInspectorClient(
      ClientType.APP_INSPECTION_CLIENT,
      project,
      notificationModel,
      process,
      stats,
      scope,
      parentDisposable,
    ) {
    override suspend fun startFetching() = throw NotImplementedError()

    override suspend fun stopFetching() = throw NotImplementedError()

    override fun refresh() = throw NotImplementedError()

    override suspend fun saveSnapshot(
      path: Path,
      screenshotType: LayoutInspectorViewProtocol.Screenshot.Type,
    ) = throw NotImplementedError()

    override suspend fun doConnect() {}

    override suspend fun doDisconnect() {}

    override val capabilities
      get() = throw NotImplementedError()

    override val treeLoader: TreeLoader
      get() = throw NotImplementedError()

    override val inLiveMode: Boolean
      get() = false

    override val provider: PropertiesProvider
      get() = throw NotImplementedError()
  }
}
