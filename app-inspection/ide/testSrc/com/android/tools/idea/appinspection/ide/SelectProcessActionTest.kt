package com.android.tools.idea.appinspection.ide

import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
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

class SelectProcessActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    projectRule.mockService(ActionManager::class.java)
  }

  private fun createFakeStream(): Common.Stream {
    return Common.Stream.newBuilder()
      .setDevice(FakeTransportService.FAKE_DEVICE)
      .build()
  }
  private fun Common.Stream.createFakeProcess(name: String? = null, pid: Int = 0): ProcessDescriptor {
    return ProcessDescriptor(this, FakeTransportService.FAKE_PROCESS.toBuilder()
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
    assertThat(devices.size).isEqualTo(1)
    val device = devices[0]
    assertThat(device.templateText).isEqualTo("FakeDevice")

    val processes = (device as ActionGroup).getChildren(null)
    processes.forEach { it.update(createFakeEvent()) }
    assertThat(processes.size).isEqualTo(3)

    // Preferred process B should be ahead of Non-preferred process A
    assertThat(processes[0].templateText).isEqualTo("B")
    assertThat(processes[1]).isInstanceOf(Separator::class.java)
    assertThat(processes[2].templateText).isEqualTo("A")
  }
}
