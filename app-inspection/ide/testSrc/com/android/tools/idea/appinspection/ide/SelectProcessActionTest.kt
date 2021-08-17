package com.android.tools.idea.appinspection.ide

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
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
    val model = ProcessesModel(testNotifier) { listOf() }
    val selectProcessAction = SelectProcessAction(model)
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children).hasLength(2)
    assertThat(children[0].templateText).isEqualTo(AppInspectionBundle.message("action.no.devices"))
    assertThat(children[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))
  }

  @Test
  fun processLabelPresentationCanBeOverridden() {
    val testNotifier = TestProcessNotifier()
    val physicalStream = createFakeStream(isEmulator = false)
    val physicalProcess = physicalStream.createFakeProcess("A", 100)
    val model = ProcessesModel(testNotifier) { listOf(physicalProcess.name) }

    val selectProcessAction = SelectProcessAction(model, createProcessLabel = { process -> "Test: " + process.name })
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    assertThat(selectProcessAction.templateText).isEqualTo(AppInspectionBundle.message("action.select.process"))

    testNotifier.fireConnected(physicalProcess)
    val selectProcessEvent = createFakeEvent()
    selectProcessAction.update(selectProcessEvent)
    assertThat(selectProcessEvent.presentation.text).isEqualTo("Test: A")
  }

  @Test
  fun stopPresentationCanBeOverridden() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf() }
    val selectProcessAction = SelectProcessAction(model, stopPresentation = SelectProcessAction.StopPresentation("Test stop label", "Test stop description"))
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children).hasLength(2)
    assertThat(children[1].templatePresentation.text).isEqualTo("Test stop label")
    assertThat(children[1].templatePresentation.description).isEqualTo("Test stop description")
  }

  @Test
  fun displayTextForDevicesSetAsExpected() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf() }
    val selectProcessAction = SelectProcessAction(model)

    val physicalStream = createFakeStream(isEmulator = false)
    val emulatorStream = createFakeStream(isEmulator = true)

    val physicalProcess = physicalStream.createFakeProcess("A", 100)
    val emulatorProcess = emulatorStream.createFakeProcess("A", 100)

    testNotifier.fireConnected(physicalProcess)
    testNotifier.fireConnected(emulatorProcess)

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children).hasLength(3)
    // Physical devices prepend the manufacturer
    assertThat(children[0].templateText).isEqualTo("$FAKE_MANUFACTURER_NAME ${FakeTransportService.FAKE_DEVICE_NAME}")
    // Virtual devices hide the manufacturer
    assertThat(children[1].templateText).isEqualTo(FakeTransportService.FAKE_DEVICE_NAME)
    // Stop button
    assertThat(children[2].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))
  }

  @Test
  fun addsNonPreferredAndPreferredProcess_orderEnsured() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf("B") }
    val selectProcessAction = SelectProcessAction(model)

    val fakeStream = createFakeStream()
    val processA = fakeStream.createFakeProcess("A", 100)
    val processB = fakeStream.createFakeProcess("B", 101)

    testNotifier.fireConnected(processA) // Not preferred
    testNotifier.fireConnected(processB) // Preferred

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children).hasLength(2)
    val device = children[0]
    assertThat(device.templateText).isEqualTo("FakeDevice")
    assertThat(children[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

    val processes = (device as ActionGroup).getChildren(null)
    processes.forEach { it.update(createFakeEvent()) }
    assertThat(processes).hasLength(3)

    // Preferred process B should be ahead of Non-preferred process A
    assertThat(processes[0].templateText).isEqualTo("B")
    assertThat(processes[1]).isInstanceOf(Separator::class.java)
    assertThat(processes[2].templateText).isEqualTo("A")
  }

  @Test
  fun listsProcessesInSortedOrder() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf("X", "Y", "Z") }
    val selectProcessAction = SelectProcessAction(model)

    val fakeStream = createFakeStream()
    for (name in listOf("C", "B", "A", "Z", "Y", "X")) {
      fakeStream.createFakeProcess(name, name.hashCode())
        .also { testNotifier.fireConnected(it) }
    }

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    val device = children[0]
    val processes = (device as ActionGroup).getChildren(null)
    processes.forEach { it.update(createFakeEvent()) }
    assertThat(processes).hasLength(7)

    // Preferred processes first, then non-preferred, but everything sorted
    assertThat(processes[0].templateText).isEqualTo("X")
    assertThat(processes[1].templateText).isEqualTo("Y")
    assertThat(processes[2].templateText).isEqualTo("Z")
    assertThat(processes[3]).isInstanceOf(Separator::class.java)
    assertThat(processes[4].templateText).isEqualTo("A")
    assertThat(processes[5].templateText).isEqualTo("B")
    assertThat(processes[6].templateText).isEqualTo("C")
  }

  @Test
  fun deadProcessesShowUpInProcessList() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf("A") }
    val selectProcessAction = SelectProcessAction(model)

    val fakeStream = createFakeStream()
    val process = fakeStream.createFakeProcess("A", 100)

    testNotifier.fireConnected(process)
    run {
      selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
      assertThat(selectProcessAction.childrenCount).isEqualTo(2)
      val children = selectProcessAction.getChildren(null)
      val deviceAction = children[0]
      assertThat(children[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

      val processAction = (deviceAction as ActionGroup).getChildren(null)[0]
      assertThat(processAction.templateText).isEqualTo("A")
    }

    testNotifier.fireDisconnected(process)
    run {
      selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
      assertThat(selectProcessAction.childrenCount).isEqualTo(2)
      val children = selectProcessAction.getChildren(null)
      val deviceAction = children[0]
      assertThat(children[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

      val processAction = (deviceAction as ActionGroup).getChildren(null)[0]
      assertThat(processAction.templateText).isEqualTo("A [DETACHED]")
    }
  }

  @Test
  fun deadProcessesFilteredOutIfOfflineNotSupported() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf("A") }
    val selectProcessAction = SelectProcessAction(model, supportsOffline = false)

    val fakeStream = createFakeStream()
    val process = fakeStream.createFakeProcess("A", 100)

    testNotifier.fireConnected(process)
    run {
      selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
      assertThat(selectProcessAction.childrenCount).isEqualTo(2)
      val children = selectProcessAction.getChildren(null)
      val deviceAction = children[0]
      assertThat(children[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

      val processAction = (deviceAction as ActionGroup).getChildren(null)[0]
      assertThat(processAction.templateText).isEqualTo("A")
    }

    testNotifier.fireDisconnected(process)
    run {
      selectProcessAction.update(createFakeEvent())
      selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
      assertThat(selectProcessAction.childrenCount).isEqualTo(2)
      val children = selectProcessAction.getChildren(null)
      assertThat(children[0].templateText).isEqualTo(AppInspectionBundle.message("action.no.devices"))
      assertThat(children[1].templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))
    }
  }

  @Test
  fun selectStopInspection_firesCallbackAndRetainsProcess() {
    val testNotifier = TestProcessNotifier()
    val model = ProcessesModel(testNotifier) { listOf("B") }
    val fakeStream = createFakeStream()
    val processB = fakeStream.createFakeProcess("B", 101)
    val callbackFiredLatch = CountDownLatch(1)
    val selectProcessAction = SelectProcessAction(model) {
      model.stop()
      callbackFiredLatch.countDown()
    }

    testNotifier.fireConnected(processB) // Preferred

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children).hasLength(2)
    val device = children[0]
    assertThat(device.templateText).isEqualTo("FakeDevice")
    assertThat((device as ActionGroup).getChildren(null).map { it.templateText }).containsExactly("B")

    val stop = children[1]
    assertThat(stop.templateText).isEqualTo(AppInspectionBundle.message("action.stop.inspectors"))

    stop.actionPerformed(createFakeEvent())
    callbackFiredLatch.await()

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val refreshedChildren = selectProcessAction.getChildren(null)
    assertThat(refreshedChildren).hasLength(2)
    val refreshedDevice = refreshedChildren[0]
    val processes = (refreshedDevice as ActionGroup).getChildren(null)
    assertThat(processes).hasLength(2)
    assertThat(processes.map { it.templateText }).containsExactly("B", "B [DETACHED]")
  }
}
