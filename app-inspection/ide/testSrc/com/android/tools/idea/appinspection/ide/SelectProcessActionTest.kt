package com.android.tools.idea.appinspection.ide

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.DisposableRule
import com.intellij.util.concurrency.EdtExecutorService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class SelectProcessActionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val ATTACH_HANDLER = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setPid(FakeTransportService.FAKE_PROCESS.pid)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("SelectProcessActionTest", transportService, transportService)!!

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val disposableRule = DisposableRule()

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
  }

  @Before
  fun setUp() {
    projectRule.mockService(ActionManager::class.java)
  }

  private fun fakeEvent(): AnActionEvent = AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT)

  @Test
  fun testNoProcesses() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))
    val model = AppInspectionProcessModel(discoveryHost) { listOf() }
    val selectProcessAction = SelectProcessAction(model)
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val children = selectProcessAction.getChildren(null)
    assertThat(children.size).isEqualTo(1)
    assertThat(children[0].templateText).isEqualTo("No devices detected")
  }

  @Test
  fun addsNonPreferredAndPreferredProcess_orderEnsured() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionTestUtils.createDiscoveryHost(executor, TransportClient(grpcServerRule.name))
    val processReadyLatch = CountDownLatch(2)

    val model = AppInspectionProcessModel(discoveryHost) { listOf("B") }
    val selectProcessAction = SelectProcessAction(model)
    discoveryHost.addProcessListener(EdtExecutorService.getInstance(), object : ProcessListener {
      override fun onProcessConnected(descriptor: ProcessDescriptor) {
        processReadyLatch.countDown()
      }

      override fun onProcessDisconnected(descriptor: ProcessDescriptor) {}
    })

    val processA = FakeTransportService.FAKE_PROCESS.toBuilder().setName("A").setPid(100).build()
    val processB = FakeTransportService.FAKE_PROCESS.toBuilder().setName("B").setPid(101).build()

    // Launch process A
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, processA)

    // Launch process B
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, processB)
    processReadyLatch.await()

    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    val devices = selectProcessAction.getChildren(null)
    assertThat(devices.size).isEqualTo(1)
    val device = devices[0]
    assertThat(device.templateText).isEqualTo("FakeDevice")

    val processes = (device as ActionGroup).getChildren(null)
    processes.forEach { it.update(fakeEvent()) }
    assertThat(processes.size).isEqualTo(3)

    // Preferred process B should be ahead of Non-preferred process A
    assertThat(processes[0].templateText).isEqualTo("B")
    assertThat(processes[1]).isInstanceOf(Separator::class.java)
    assertThat(processes[2].templateText).isEqualTo("A")
  }
}
