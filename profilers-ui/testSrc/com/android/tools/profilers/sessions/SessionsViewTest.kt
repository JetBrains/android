/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent

class SessionsViewTest {

  private val myProfilerService = FakeProfilerService(false)
  private val myMemoryService = FakeMemoryService()
  private val myCpuService = FakeCpuService()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
      "SessionsViewTestChannel",
      myProfilerService,
      myMemoryService,
      myCpuService,
      FakeEventService(),
      FakeNetworkService.newBuilder().build()
  )

  private lateinit var myTimer: FakeTimer
  private lateinit var myProfilers: StudioProfilers
  private lateinit var mySessionsManager: SessionsManager
  private lateinit var mySessionsView: SessionsView

  @Before
  fun setup() {
    myTimer = FakeTimer()
    myProfilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), myTimer)
    mySessionsManager = myProfilers.sessionsManager
    mySessionsView = SessionsView(myProfilers, FakeIdeProfilerComponents())
  }

  @Test
  fun testSessionsListUpToDate() {
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process1)
    var session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    var sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session1)

    mySessionsManager.endCurrentSession()
    session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session1)

    myProfilerService.setTimestampNs(2)
    mySessionsManager.beginSession(device, process2)
    val session2 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(2)
    // Sessions are sorted in descending order.
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    var sessionItem1 = sessionArtifacts.getElementAt(1) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)

    // Add the heap dump and CPU capture, expand the first session and make sure the artifacts are shown in the list
    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)
    sessionItem0.isExpanded = true

    assertThat(sessionArtifacts.size).isEqualTo(4)
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    val hprofItem = sessionArtifacts.getElementAt(1) as HprofSessionArtifact
    val cpuCaptureItem = sessionArtifacts.getElementAt(2) as CpuCaptureSessionArtifact
    sessionItem1 = sessionArtifacts.getElementAt(3) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(hprofItem.session).isEqualTo(session2)
    assertThat(cpuCaptureItem.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)
  }

  @Test
  fun testProcessDropdownUpToDate() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val device2 = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Process2").setState(Common.Process.State.ALIVE).build()
    val process3 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(2).setName("Process3").setState(Common.Process.State.ALIVE).build()

    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActionCount).isEqualTo(0)

    myProfilerService.addDevice(device1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(2)
    var deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Loading from file..." }
    assertThat(deviceAction1.isSelected).isFalse()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addDevice(device1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(2)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isFalse()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(2)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    var processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1" }
    assertThat(processAction1.isSelected).isTrue()
    assertThat(processAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addProcess(device1, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(2)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(2)
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1" }
    assertThat(processAction1.isSelected).isTrue()
    var processAction2 = deviceAction1.childrenActions.first { c -> c.text == "Process2" }
    assertThat(processAction2.isSelected).isFalse()

    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(2)
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1" }
    assertThat(processAction1.isSelected).isTrue()
    processAction2 = deviceAction1.childrenActions.first { c -> c.text == "Process2" }
    assertThat(processAction2.isSelected).isFalse()
    var deviceAction2 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(deviceAction2.isSelected).isFalse()
    assertThat(deviceAction2.isEnabled).isTrue()
    assertThat(deviceAction2.childrenActionCount).isEqualTo(1)
    var processAction3 = deviceAction2.childrenActions.first { c -> c.text == "Process3" }
    assertThat(processAction3.isSelected).isFalse()
  }

  @Test
  fun testDropdownActionsTriggerProcessChange() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val device2 = Common.Device.newBuilder()
      .setDeviceId(2).setManufacturer("Manufacturer2").setModel("Model2").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder()
      .setPid(20).setDeviceId(1).setName("Process2").setState(Common.Process.State.ALIVE).build()
    val process3 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(2).setName("Process3").setState(Common.Process.State.ALIVE).build()

    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myProfilers.device).isEqualTo(device1)
    assertThat(myProfilers.process).isEqualTo(process1)

    myProfilerService.addProcess(device1, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var selectionAction = mySessionsView.processSelectionAction
    var processAction2 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer1 Model1" }.childrenActions
      .first { c -> c.text == "Process2" }
    processAction2.actionPerformed(ActionEvent(processAction2, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device1)
    assertThat(myProfilers.process).isEqualTo(process2)

    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var processAction3 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer2 Model2" }.childrenActions
      .first { c -> c.text == "Process3" }
    processAction3.actionPerformed(ActionEvent(processAction3, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device2)
    assertThat(myProfilers.process).isEqualTo(process3)
  }

  @Test
  fun testStopProfiling() {
    val device1 = Common.Device.newBuilder()
      .setDeviceId(1).setManufacturer("Manufacturer1").setModel("Model1").setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder()
      .setPid(10).setDeviceId(1).setName("Process1").setState(Common.Process.State.ALIVE).build()

    val stopProfilingButton = mySessionsView.stopProfilingButton
    assertThat(stopProfilingButton.isEnabled).isFalse()

    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(stopProfilingButton.isEnabled).isTrue()
    assertThat(mySessionsManager.profilingSession).isNotEqualTo(Common.Session.getDefaultInstance())

    stopProfilingButton.doClick()
    assertThat(stopProfilingButton.isEnabled).isFalse()
    assertThat(mySessionsManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testImportSessionsFromHprofFile() {
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    mySessionsManager.beginSession(device, process1)
    val session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session1)

    val session = mySessionsManager.createImportedSession("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
    mySessionsManager.update();
    mySessionsManager.setSession(session)
    assertThat(sessionArtifacts.size).isEqualTo(2)

    val selectedSession = mySessionsManager.selectedSession
    assertThat(session).isEqualTo(selectedSession)
    assertThat(myProfilers.selectedSessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
  }

  @Test
  fun testSessionItemMouseInteraction() {
    mySessionsView.sessionsList.ui = HeadlessListUI()
    mySessionsView.sessionsList.setSize(100, 100)
    val ui = FakeUi(mySessionsView.sessionsList)
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(20).setToTimestamp(21).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)

    myProfilerService.setTimestampNs(1)
    mySessionsManager.beginSession(device, process1)
    mySessionsManager.endCurrentSession()
    val session1 = mySessionsManager.selectedSession
    myProfilerService.setTimestampNs(2)
    mySessionsManager.beginSession(device, process2)
    mySessionsManager.endCurrentSession()
    val session2 = mySessionsManager.selectedSession

    assertThat(sessionArtifacts.size).isEqualTo(2)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    var sessionItem1 = sessionArtifacts.getElementAt(1) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)

    // Clicking on the second session should select it.
    assertThat(mySessionsManager.selectedSession).isEqualTo(session2)
    ui.layout()
    ui.mouse.click(50, 50)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)

    // Clicking on the arrow region should expand but not select.
    ui.layout()
    ui.mouse.click(10, 10)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)
    assertThat(sessionArtifacts.size).isEqualTo(4)
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    val hprofItem = sessionArtifacts.getElementAt(1) as HprofSessionArtifact
    val cpuCaptureItem = sessionArtifacts.getElementAt(2) as CpuCaptureSessionArtifact
    sessionItem1 = sessionArtifacts.getElementAt(3) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(hprofItem.session).isEqualTo(session2)
    assertThat(cpuCaptureItem.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)

    // Clicking again should collapse the session.
    ui.layout()
    ui.mouse.click(10, 10)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)
    assertThat(sessionArtifacts.size).isEqualTo(2)
    sessionItem0 = sessionArtifacts.getElementAt(0) as SessionItem
    sessionItem1 = sessionArtifacts.getElementAt(1) as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem1.session).isEqualTo(session1)
  }
}