package com.android.tools.idea.logcat.actions

import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceProperties
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.appProcessTracker
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.fakeadbserver.services.Service
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.logcat.LogcatPresenter.Companion.CONNECTED_DEVICE
import com.android.tools.idea.logcat.LogcatPresenter.Companion.EDITOR
import com.android.tools.idea.logcat.actions.TerminateAppActions.CrashAppAction
import com.android.tools.idea.logcat.actions.TerminateAppActions.ForceStopAppAction
import com.android.tools.idea.logcat.actions.TerminateAppActions.KillAppAction
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Tests for [TerminateAppActions] */
@RunsInEdt
class TerminateAppActionsTest {
  private val projectRule = ProjectRule()
  private val editorRule = LogcatEditorRule(projectRule)
  private val fakeAdbRule = FakeAdbServerProviderRule()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      editorRule,
      fakeAdbRule,
      ProjectServiceRule(projectRule, AdbLibService::class.java) {
        TestAdbLibService(fakeAdbRule.adbSession)
      },
      EdtRule(),
    )

  private val project
    get() = projectRule.project

  private val editor
    get() = editorRule.editor

  private val fakeAdb
    get() = fakeAdbRule.fakeAdb

  private val adbSession
    get() = fakeAdbRule.adbSession

  private val device30 = Device.createPhysical("device", true, "10", 30, "Google", "Pixel")

  private val device25 = Device.createPhysical("device", true, "10", 25, "Google", "Pixel")

  private val activityManagerService = ActivityManagerService()

  @Test
  fun forceStopAppAction_processExists_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      ForceStopAppAction().update(event)

      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun forceStopAppAction_processDoesNotExists_isNotEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      ForceStopAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun forceStopAppAction_systemProcess_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "system_process"))

      ForceStopAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun forceStopAppAction_unknownAppId_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "pid-101"))

      ForceStopAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun killAppAction_processExists_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      KillAppAction().update(event)

      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun killAppAction_processDoesNotExists_isNotEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      KillAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun killAppAction_systemProcess_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      KillAppAction().update(event)

      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun crashAppAction_processExists_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      CrashAppAction().update(event)

      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun crashAppAction_oldDevice_notVisible(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device25)
      val event = createEvent(device25)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      CrashAppAction().update(event)

      assertThat(event.presentation.isVisible).isFalse()
    }

  @Test
  fun crashAppAction_processDoesNotExists_isNotEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      editorRule.putLogcatMessages(logcatMessage(pid = 101))

      CrashAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun crashAppAction_systemProcess_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "system_process"))

      CrashAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  @Test
  fun crashAppAction_unknownAppId_isEnabled(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "pid-101"))

      CrashAppAction().update(event)

      assertThat(event.presentation.isEnabled).isFalse()
      assertThat(event.presentation.isVisible).isTrue()
    }

  /**
   * This runs with runBlockingWithTimeout instead of runTest because the action launches a
   * coroutine in another scope
   */
  @Test
  fun forceStopAppAction_actionPerformed(): Unit =
    runBlockingWithTimeout(timeout = Duration.ofSeconds(5)) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      device.serviceManager.setActivityManager(activityManagerService)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "com.app"))

      ForceStopAppAction().actionPerformed(event)

      assertThat(activityManagerService.commands.receive()).isEqualTo("force-stop com.app")
    }

  @Test
  fun killAction_actionPerformed(): Unit =
    runTest(timeout = 5.seconds) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      device.serviceManager.setActivityManager(activityManagerService)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "com.app"))

      KillAppAction().actionPerformed(event)

      waitForCondition { device.getClient(101) == null }
    }

  /**
   * This runs with runBlockingWithTimeout instead of runTest because the action launches a
   * coroutine in another scope
   */
  @Test
  fun crashAppAction_actionPerformed(): Unit =
    runBlockingWithTimeout(timeout = Duration.ofSeconds(5)) {
      val device = fakeAdb.connectDevice(device30)
      val event = createEvent(device30)
      device.startClient(101)
      device.serviceManager.setActivityManager(activityManagerService)
      editorRule.putLogcatMessages(logcatMessage(pid = 101, appId = "com.app"))

      CrashAppAction().actionPerformed(event)

      assertThat(activityManagerService.commands.receive()).isEqualTo("crash com.app")
    }

  private fun createEvent(device: Device) =
    TestActionEvent.createTestEvent(
      SimpleDataContext.builder()
        .add(PROJECT, project)
        .add(EDITOR, editor)
        .add(CONNECTED_DEVICE, device)
        .build()
    )

  /** Connect a device and wait for AdbSession to see it */
  private suspend fun FakeAdbServerProvider.connectDevice(device: Device): DeviceState {
    val deviceState =
      connectDevice(
        device.serialNumber,
        "manufacturer",
        device.model,
        device.release,
        device.sdk.toString(),
        USB,
      )
    adbSession.connectedDevicesTracker.connectedDevices.waitFor {
      it.serialNumber == device.serialNumber
    }
    return deviceState
  }

  /** Start a client and wait for AdbSession to see it */
  private suspend fun DeviceState.startClient(pid: Int) {
    startClient(pid, 0, "packageName", "processName", false)
    val device =
      adbSession.connectedDevicesTracker.connectedDevices.value.find { it.serialNumber == deviceId }
        ?: throw IllegalStateException("Device $deviceId not found")
    val flow =
      when (device.deviceProperties().api() >= 31) {
        true -> device.appProcessTracker.appProcessFlow.asJdwpProcessFlow()
        false -> device.jdwpProcessTracker.processesFlow
      }
    flow.waitFor { it.pid == pid }
  }

  private class ActivityManagerService : Service {
    val commands = Channel<String?>()

    override fun process(args: List<String>, shellCommandOutput: ShellCommandOutput) {
      runBlocking { commands.send(args.joinToString(" ") { it }) }
      shellCommandOutput.writeExitCode(0)
    }
  }
}

private fun Flow<List<AppProcess>>.asJdwpProcessFlow() = transform {
  emit(it.mapNotNull { process -> process.jdwpProcess })
}

private suspend fun <T> Flow<List<T>>.waitFor(predicate: (T) -> Boolean) {
  first { list -> list.find { predicate(it) } != null }
}
