package com.android.tools.idea.appinspection.ide

import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessNotifier
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.TestActionEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch

private const val FAKE_MANUFACTURER_NAME = "FakeManufacturer"

class SelectProcessActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    projectRule.mockService(ActionManager::class.java)
  }

  private fun createFakeStream(serial: String = UUID.randomUUID().toString(), isEmulator: Boolean = true): Common.Stream {
    val device = FakeTransportService.FAKE_DEVICE.toBuilder()
      .setSerial(serial)
      .setManufacturer(FAKE_MANUFACTURER_NAME)
      .setIsEmulator(isEmulator)
      .build()

    return Common.Stream.newBuilder()
      .setDevice(device)
      .build()
  }
  private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
    return TransportProcessDescriptor(this, FakeTransportService.FAKE_PROCESS.toBuilder()
      .setName(name ?: FakeTransportService.FAKE_PROCESS_NAME)
      .setPid(pid)
      .build())
  }

  private fun createFakeEvent(): AnActionEvent = AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT)

  @Test
  fun testNoProcesses() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf() }
    val selectProcessAction = SelectProcessAction(model)
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children.size).isEqualTo(1)
    assertThat(children[0].templateText).isEqualTo("No devices detected")
  }

  @Test
  fun displayTextForDevicesSetAsExpected() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf() }
    val selectProcessAction = SelectProcessAction(model)

    val physicalStream = createFakeStream(isEmulator = false)
    val emulatorStream = createFakeStream(isEmulator = true)

    val physicalProcess = physicalStream.createFakeProcess("A", 100)
    val emulatorProcess = emulatorStream.createFakeProcess("A", 100)

    testNotifier.fireConnected(physicalProcess)
    testNotifier.fireConnected(emulatorProcess)

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val devices = selectProcessAction.getChildren(null)
    assertThat(devices.size).isEqualTo(2)
    // Physical devices prepend the manufacturer
    assertThat(devices[0].templateText).isEqualTo("$FAKE_MANUFACTURER_NAME ${FakeTransportService.FAKE_DEVICE_NAME}")
    // Virtual devices hide the manufacturer
    assertThat(devices[1].templateText).isEqualTo(FakeTransportService.FAKE_DEVICE_NAME)
  }

  @Test
  fun addsNonPreferredAndPreferredProcess_orderEnsured() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("B") }
    val selectProcessAction = SelectProcessAction(model)

    val fakeStream = createFakeStream()
    val processA = fakeStream.createFakeProcess("A", 100)
    val processB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(processA) // Not preferred
    testNotifier.fireConnected(processB) // Preferred

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val devices = selectProcessAction.getChildren(null)
    assertThat(devices.size).isEqualTo(2)
    val device = devices[0]
    assertThat(device.templateText).isEqualTo("FakeDevice")
    assertThat(devices[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

    val processes = (device as ActionGroup).getChildren(null)
    processes.forEach { it.update(createFakeEvent()) }
    assertThat(processes.size).isEqualTo(3)

    // Preferred process B should be ahead of Non-preferred process A
    assertThat(processes[0].templateText).isEqualTo("B")
    assertThat(processes[1]).isInstanceOf(Separator::class.java)
    assertThat(processes[2].templateText).isEqualTo("A")
  }

  @Test
  fun deadProcessesShowUpInProcessList() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("A") }
    val selectProcessAction = SelectProcessAction(model)

    val fakeStream = createFakeStream()
    val process = fakeStream.createFakeProcess("A", 100)

    testNotifier.fireConnected(process)
    run {
      selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
      assertThat(selectProcessAction.childrenCount).isEqualTo(2)
      val deviceAction = selectProcessAction.getChildren(null)[0]
      val processAction = (deviceAction as ActionGroup).getChildren(null)[0]
      assertThat(processAction.templateText).isEqualTo("A")
    }

    testNotifier.fireDisconnected(process)
    run {
      selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
      assertThat(selectProcessAction.childrenCount).isEqualTo(1)
      val deviceAction = selectProcessAction.getChildren(null)[0]
      val processAction = (deviceAction as ActionGroup).getChildren(null)[0]
      assertThat(processAction.templateText).isEqualTo("A [DEAD]")
    }
  }

  @Test
  fun selectStopInspection_firesCallbackAndRetainsProcess() {
    val testNotifier = TestProcessNotifier()
    val model = AppInspectionProcessModel(testNotifier) { listOf("B") }
    val fakeStream = createFakeStream()
    val processB = fakeStream.createFakeProcess("B", 101)
    val callbackFiredLatch = CountDownLatch(1)
    val selectProcessAction = SelectProcessAction(model) {
      model.stopInspection(processB)
      callbackFiredLatch.countDown()
    }

    testNotifier.fireConnected(processB) // Preferred

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val devices = selectProcessAction.getChildren(null)
    assertThat(devices.size).isEqualTo(2)
    val device = devices[0]
    assertThat(device.templateText).isEqualTo("FakeDevice")
    assertThat((device as ActionGroup).getChildren(null).map { it.templateText }).containsExactly("B")
    assertThat(devices[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

    devices[1].actionPerformed(TestActionEvent())
    callbackFiredLatch.await()

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val refreshedDevices = selectProcessAction.getChildren(null)
    assertThat(refreshedDevices).hasLength(1)
    val processes = (refreshedDevices[0] as ActionGroup).getChildren(null)
    assertThat(processes).hasLength(2)
    assertThat(processes.map { it.templateText }).containsExactly("B", "B [DEAD]")
  }
}
