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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.stdui.CommonAction
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel.PROFILEABLE
import com.android.tools.profiler.proto.Memory.AllocationsInfo
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioMonitorStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.Utils.debuggableProcess
import com.android.tools.profilers.Utils.newDevice
import com.android.tools.profilers.Utils.onlineDevice
import com.android.tools.profilers.cpu.CpuCaptureArtifactView
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.ProfilingTechnology
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.HprofArtifactView
import com.android.tools.profilers.memory.LegacyAllocationsArtifactView
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@RunsInEdt
class SessionsViewTest {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)
  private val myIdeProfilerServices = FakeIdeProfilerServices()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("SessionsViewTestChannel", myTransportService, FakeEventService())
  @get:Rule val myEdtRule = EdtRule()


  private lateinit var myProfilers: StudioProfilers
  private lateinit var mySessionsManager: SessionsManager
  private lateinit var mySessionsView: SessionsView

  @Before
  fun setup() {
    myProfilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeProfilerServices, myTimer)
    mySessionsManager = myProfilers.sessionsManager
    mySessionsView = SessionsView(myProfilers, FakeIdeProfilerComponents())
  }

  @Ignore("b/123901060")
  @Test
  fun testSessionsListUpToDate() {
    val sessionsPanel = mySessionsView.sessionsPanel
    assertThat(sessionsPanel.componentCount).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = debuggableProcess { pid = 10 }
    val process2 = debuggableProcess { pid = 20 }

    myTimer.currentTimeNs = 1
    mySessionsManager.beginSession(device.deviceId, device, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var session1 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    var sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    assertThat(sessionItem0.artifact.session).isEqualTo(session1)

    mySessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    session1 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    assertThat(sessionItem0.artifact.session).isEqualTo(session1)

    myTimer.currentTimeNs = 2
    mySessionsManager.beginSession(device.deviceId, device, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session2 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    // Sessions are sorted in descending order.
    sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    var sessionItem1 = sessionsPanel.getComponent(1) as SessionItemView
    assertThat(sessionItem0.artifact.session).isEqualTo(session2)
    assertThat(sessionItem1.artifact.session).isEqualTo(session1)

    // Add the heap dump and CPU capture, expand the first session and make sure the artifacts are shown in the list
    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = Trace.TraceInfo.newBuilder()
      .setConfiguration(Trace.TraceConfiguration.newBuilder())
      .setFromTimestamp(cpuTraceTimestamp)
      .setToTimestamp(cpuTraceTimestamp + 1)
      .build()
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(heapDumpInfo.startTime, heapDumpInfo.startTime, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(session1.pid).build())
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(session2.pid).build())

    val cpuTraceEvent = Common.Event.newBuilder()
      .setGroupId(cpuTraceTimestamp)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(cpuTraceTimestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
    myTransportService.addEventToStream(device.deviceId, cpuTraceEvent.setPid(session1.pid).build())
    myTransportService.addEventToStream(device.deviceId, cpuTraceEvent.setPid(session2.pid).build())
    mySessionsManager.update()

    assertThat(sessionsPanel.componentCount).isEqualTo(6)
    sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem0 = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    val hprofItem0 = sessionsPanel.getComponent(2) as HprofArtifactView
    sessionItem1 = sessionsPanel.getComponent(3) as SessionItemView
    val cpuCaptureItem1 = sessionsPanel.getComponent(4) as CpuCaptureArtifactView
    val hprofItem1 = sessionsPanel.getComponent(5) as HprofArtifactView
    assertThat(sessionItem0.artifact.session).isEqualTo(session2)
    assertThat(hprofItem0.artifact.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.artifact.session).isEqualTo(session2)
    assertThat(sessionItem1.artifact.session).isEqualTo(session1)
    assertThat(hprofItem1.artifact.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.artifact.session).isEqualTo(session1)
  }

  @Test
  fun testProcessDropdownUpToDateForProfileables() {
    val device1 = onlineDevice { deviceId = NEW_DEVICE_ID_1; manufacturer = "Manufacturer1"; model = "Model1" }
    val device2 = onlineDevice { deviceId = NEW_DEVICE_ID_2; manufacturer = "Manufacturer2"; model = "Model2" }
    val process1 = debuggableProcess { pid = 10; deviceId = NEW_DEVICE_ID_1; name = "Process1"; exposureLevel = PROFILEABLE }
    val otherProcess1 = debuggableProcess { pid = 20; deviceId = NEW_DEVICE_ID_1; name = "Other1" }
    val otherProcess2 = debuggableProcess { pid = 30; deviceId = NEW_DEVICE_ID_2; name = "Other2"; exposureLevel = PROFILEABLE }
    // Process* is preferred, Other* should be in the other processes flyout.
    myProfilers.setPreferredProcess("Manufacturer1 Model1", "Process", null)

    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    var loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    assertThat(selectionAction.childrenActions[2].text).isEqualTo(SessionsView.NO_SUPPORTED_DEVICES)
    assertThat(selectionAction.childrenActions[2].isEnabled).isFalse()

    myTransportService.addDevice(device1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    var deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    assertThat(deviceAction1.childrenActions[0].text).isEqualTo(SessionsView.NO_DEBUGGABLE_OR_PROFILEABLE_PROCESSES)
    assertThat(deviceAction1.childrenActions[0].isEnabled).isFalse()

    myTransportService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device1, process1)
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(3)
    var processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10) (profileable)" }
    assertThat(processAction1.childrenActionCount).isEqualTo(0)

    myTransportService.addProcess(device1, otherProcess1)
    try {
      myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    } catch (e: NullPointerException) { /* Ignore error from HelpToolTip in test but not in production */ }
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(4)  // process1 + separator + "other debuggables" + "other profileables"
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10) (profileable)" }
    assertThat(deviceAction1.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    var processAction2 = deviceAction1.childrenActions
      .first { c -> c.text == "Other debuggable processes" }.childrenActions
      .first { c -> c.text == "Other1 (20)" }

    // Test the reverse case of having only "other" processes
    myTransportService.addDevice(device2)
    myTransportService.addProcess(device2, otherProcess2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(4)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(4)  // process1 + separator + "other debuggables" + "other profileables"
    processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1 (10) (profileable)" }
    assertThat(deviceAction1.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    processAction2 = deviceAction1.childrenActions
      .first { c -> c.text == "Other debuggable processes" }.childrenActions
      .first { c -> c.text == "Other1 (20)" }
    var deviceAction2 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(deviceAction2.isEnabled).isTrue()
    assertThat(deviceAction2.childrenActionCount).isEqualTo(2) // No separator + "no other debuggables" + "other profileables"
    var processAction3 = deviceAction2.childrenActions
      .first { c -> c.text == "Other profileable processes" }.childrenActions
      .first { c -> c.text == "Other2 (30)" }
  }

  @Test
  fun testUnsupportedDeviceDropdown() {
    val unsupportedReasonText = "Unsupported"
    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1; manufacturer = "Manufacturer1"; model = "Model1"; unsupportedReason = unsupportedReasonText }
    myTransportService.addDevice(device)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActionCount).isEqualTo(3)
    var loadAction = selectionAction.childrenActions.first { c -> c.text == "Load from file..." }
    assertThat(loadAction.isEnabled).isTrue()
    assertThat(loadAction.childrenActionCount).isEqualTo(0)
    assertThat(selectionAction.childrenActions[1]).isInstanceOf(CommonAction.SeparatorAction::class.java)
    var deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    assertThat(deviceAction1.childrenActions[0].text).isEqualTo(unsupportedReasonText)
    assertThat(deviceAction1.childrenActions[0].isEnabled).isFalse()
  }

  @Test
  fun testProcessDropdownHideDeadDevicesAndProcesses() {
    val deadDevice = newDevice(Common.Device.State.DISCONNECTED) { deviceId = NEW_DEVICE_ID_1; manufacturer = "Manufacturer1"; model = "Model1" }
    val onlineDevice = onlineDevice { deviceId = NEW_DEVICE_ID_2; manufacturer = "Manufacturer2"; model = "Model2" }
    val deadProcess1 = debuggableProcess { pid = 10; deviceId = NEW_DEVICE_ID_1; name = "Process1"; state = Common.Process.State.DEAD }
    val aliveProcess1 = debuggableProcess { pid = 20; deviceId = NEW_DEVICE_ID_1; name = "Process2" }
    val deadProcess2 = debuggableProcess { pid = 30; deviceId = NEW_DEVICE_ID_2; name = "Process3"; state = Common.Process.State.DEAD }
    val aliveProcess2 = debuggableProcess { pid = 40; deviceId = NEW_DEVICE_ID_2; name = "Process4" }
    val deadProcess3 = debuggableProcess { pid = 50; deviceId = NEW_DEVICE_ID_2; name = "Dead"; state = Common.Process.State.DEAD }
    // Also test processes that can be grouped in the fly-out menu.
    myProfilers.setPreferredProcess("Manufacturer2 Model2", "Process4", null)

    myTransportService.addDevice(deadDevice)
    myTransportService.addDevice(onlineDevice)
    myTransportService.addProcess(deadDevice, deadProcess1)
    myTransportService.addProcess(deadDevice, aliveProcess1)
    myTransportService.addProcess(onlineDevice, deadProcess2)
    myTransportService.addProcess(onlineDevice, aliveProcess2)
    myTransportService.addProcess(onlineDevice, deadProcess3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var selectionAction = mySessionsView.processSelectionAction
    assertThat(selectionAction.childrenActions.any { c -> c.text == "Manufacturer1 Model1" }).isFalse()
    val aliveDeviceAction = selectionAction.childrenActions.first { c -> c.text == "Manufacturer2 Model2" }
    assertThat(aliveDeviceAction.childrenActionCount).isEqualTo(3) // the process + no other debuggables + no other profileables
    val processAction1 = aliveDeviceAction.childrenActions.first { c -> c.text == "Process4 (40) (debuggable)" }
    assertThat(processAction1.childrenActionCount).isEqualTo(0)
  }

  @Test
  fun testDropdownActionsTriggerProcessChange() {
    val device1 = onlineDevice { deviceId = NEW_DEVICE_ID_1; manufacturer = "Manufacturer1"; model = "Model1" }
    val device2 = onlineDevice { deviceId = NEW_DEVICE_ID_2; manufacturer = "Manufacturer2"; model = "Model2" }
    val process1 = debuggableProcess { pid = 10; deviceId = NEW_DEVICE_ID_1; name = "Process1" }
    val process2 = debuggableProcess { pid = 20; deviceId = NEW_DEVICE_ID_1; name = "Process2" }
    val process3 = debuggableProcess { pid = 10; deviceId = NEW_DEVICE_ID_2; name = "Process3" }
    // Mark all process as preferred processes as we are not testing the other processes flyout here.
    myProfilers.setPreferredProcess(null, "Process", null)

    myTransportService.addDevice(device1)
    myTransportService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myTransportService.addProcess(device1, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var selectionAction = mySessionsView.processSelectionAction
    var processAction2 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer1 Model1" }.childrenActions
      .first { c -> c.text == "Process2 (20) (debuggable)" }
    processAction2.actionPerformed(ActionEvent(processAction2, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device1)
    assertThat(myProfilers.process).isEqualTo(process2)

    myTransportService.addDevice(device2)
    myTransportService.addProcess(device2, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    var processAction3 = selectionAction.childrenActions
      .first { c -> c.text == "Manufacturer2 Model2" }.childrenActions
      .first { c -> c.text == "Process3 (10) (debuggable)" }
    processAction3.actionPerformed(ActionEvent(processAction3, 0, ""))
    assertThat(myProfilers.device).isEqualTo(device2)
    assertThat(myProfilers.process).isEqualTo(process3)
  }

  @Test
  fun testStopProfiling() {
    val device1 = onlineDevice { deviceId = NEW_DEVICE_ID_1; manufacturer = "Manufacturer1"; model = "Model1" }
    val process1 = debuggableProcess { pid = 10; deviceId = NEW_DEVICE_ID_1; name = "Process1" }

    val stopProfilingButton = mySessionsView.stopProfilingButton
    assertThat(stopProfilingButton.isEnabled).isFalse()

    startSession(device1, process1)
    val session = myProfilers.session
    assertThat(stopProfilingButton.isEnabled).isTrue()
    assertThat(mySessionsManager.profilingSession).isNotEqualTo(Common.Session.getDefaultInstance())
    assertThat(mySessionsManager.profilingSession).isEqualTo(session)

    stopProfilingButton.doClick()
    assertThat(stopProfilingButton.isEnabled).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myProfilers.process).isNull()
    assertThat(mySessionsManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myProfilers.session.sessionId).isEqualTo(session.sessionId)
  }

  @Test
  fun testImportSessionsFromHprofFile() {
    val sessionsPanel = mySessionsView.sessionsPanel
    assertThat(sessionsPanel.componentCount).isEqualTo(0)

    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1 }
    val process1 = debuggableProcess { deviceId = NEW_DEVICE_ID_1; pid = 10 }
    startSession(device, process1)
    val session1 = mySessionsManager.selectedSession
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    assertThat((sessionsPanel.getComponent(0) as SessionItemView).artifact.session).isEqualTo(session1)

    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionStartEvent(1, 2, 1,
                                                                                       Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE,
                                                                                       1).build())
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 2, 2).build())
    mySessionsManager.update()
    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    assertThat(myProfilers.sessionsManager.selectedSessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
  }

  @Ignore("b/123901060")
  @Test
  fun testSessionItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = debuggableProcess { pid = 10 }
    val process2 = debuggableProcess { pid = 20 }
    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build()
    val cpuTraceInfo = Trace.TraceInfo.newBuilder()
      .setConfiguration(Trace.TraceConfiguration.newBuilder())
      .setFromTimestamp(20)
      .setToTimestamp(21)
      .build()

    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(heapDumpInfo.startTime, heapDumpInfo.startTime, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(process1.pid).build())
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(process2.pid).build())

    val cpuTraceEvent = Common.Event.newBuilder()
      .setGroupId(cpuTraceInfo.fromTimestamp)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(cpuTraceInfo.fromTimestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
    myTransportService.addEventToStream(device.deviceId, cpuTraceEvent.setPid(process1.pid).build())
    myTransportService.addEventToStream(device.deviceId, cpuTraceEvent.setPid(process2.pid).build())

    myTimer.currentTimeNs = 2
    mySessionsManager.beginSession(device.deviceId, device, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    mySessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session1 = mySessionsManager.selectedSession
    myTimer.currentTimeNs = 2
    mySessionsManager.beginSession(device.deviceId, device, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    mySessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session2 = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(6)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem0 = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    val hprofItem0 = sessionsPanel.getComponent(2) as HprofArtifactView
    var sessionItem1 = sessionsPanel.getComponent(3) as SessionItemView
    var cpuCaptureItem1 = sessionsPanel.getComponent(4) as CpuCaptureArtifactView
    var hprofItem1 = sessionsPanel.getComponent(5) as HprofArtifactView
    assertThat(sessionItem0.artifact.session).isEqualTo(session2)
    assertThat(hprofItem0.artifact.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.artifact.session).isEqualTo(session2)
    assertThat(sessionItem1.artifact.session).isEqualTo(session1)
    assertThat(hprofItem1.artifact.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.artifact.session).isEqualTo(session1)

    // Selecting on the second item should select the session.
    assertThat(mySessionsManager.selectedSession).isEqualTo(session2)
    ui.layout()
    ui.mouse.click(sessionItem1.bounds.x + 1, sessionItem1.bounds.y + 1)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)
  }

  @Ignore("b/123901060")
  @Test
  fun testSessionArtifactKeyboardSelect() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = debuggableProcess { pid = 10 }
    val process2 = debuggableProcess { pid = 20 }
    myTimer.currentTimeNs = 1
    mySessionsManager.beginSession(0, device, process1)
    mySessionsManager.endCurrentSession()
    val session1 = mySessionsManager.selectedSession
    myTimer.currentTimeNs = 2
    mySessionsManager.beginSession(0, device, process2)
    mySessionsManager.endCurrentSession()
    val session2 = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem0 = sessionsPanel.getComponent(0) as SessionItemView
    var sessionItem1 = sessionsPanel.getComponent(1) as SessionItemView

    // Make sure the second session item is selected
    assertThat(mySessionsManager.selectedSession).isEqualTo(session2)
    val ui = FakeUi(sessionsPanel)
    ui.keyboard.setFocus(sessionItem1)
    ui.keyboard.press(FakeKeyboard.Key.ENTER)
    ui.keyboard.release(FakeKeyboard.Key.ENTER)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session1)
    ui.keyboard.setFocus(sessionItem0)
    ui.keyboard.press(FakeKeyboard.Key.ENTER)
    ui.keyboard.release(FakeKeyboard.Key.ENTER)
    assertThat(mySessionsManager.selectedSession).isEqualTo(session2)
  }

  @Ignore("b/123901060")
  @Test
  fun testSessionArtifactKeyboardDelete() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = debuggableProcess { pid = 10 }
    val process2 = debuggableProcess { pid = 20 }
    myTimer.currentTimeNs = 1
    mySessionsManager.beginSession(0, device, process1)
    mySessionsManager.endCurrentSession()
    myTimer.currentTimeNs = 2
    mySessionsManager.beginSession(0, device, process2)
    mySessionsManager.endCurrentSession()

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    // Delete the ongoing session
    FakeUi(sessionsPanel).let { ui ->
      ui.keyboard.setFocus(sessionItem)
      ui.keyboard.press(FakeKeyboard.Key.BACKSPACE)
      ui.keyboard.release(FakeKeyboard.Key.BACKSPACE)
      assertThat(mySessionsManager.sessionArtifacts).hasSize(1)
      assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
      assertThat(mySessionsManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    }

    // Delete the remaining session
    assertThat(sessionsPanel.componentCount).isEqualTo(1)
    FakeUi(sessionsPanel).let { ui ->
      sessionItem = sessionsPanel.getComponent(0) as SessionItemView
      ui.layout()
      ui.keyboard.setFocus(sessionItem)
      ui.keyboard.press(FakeKeyboard.Key.BACKSPACE)
      ui.keyboard.release(FakeKeyboard.Key.BACKSPACE)
      assertThat(mySessionsManager.sessionArtifacts).hasSize(0)
    }
  }

  @Test
  fun testCpuCaptureItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1 }
    val process = debuggableProcess { pid = 10 }
    val traceInfoId = 13L

    val cpuTraceInfo = Trace.TraceInfo.newBuilder()
      .setTraceId(traceInfoId)
      .setFromTimestamp(TimeUnit.MINUTES.toNanos(1))
      .setToTimestamp(TimeUnit.MINUTES.toNanos(2))
      .setConfiguration(Trace.TraceConfiguration.newBuilder()
                          .setArtOptions(Trace.ArtOptions.newBuilder().setTraceMode(Trace.TraceMode.SAMPLED)))
      .build()

    myTransportService.addEventToStream(device.deviceId, Common.Event.newBuilder()
      .setGroupId(cpuTraceInfo.fromTimestamp)
      .setPid(process.pid)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(cpuTraceInfo.fromTimestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
      .build())
    myTransportService.addFile(traceInfoId.toString(), ByteString.copyFrom(Files.readAllBytes(resolveWorkspacePath(VALID_TRACE_PATH))))

    myTimer.currentTimeNs = 0
    mySessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    mySessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    val sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.isOngoing).isFalse()
    assertThat(cpuCaptureItem.artifact.name).isEqualTo(ProfilingTechnology.ART_SAMPLED.getName())
    assertThat(cpuCaptureItem.artifact.subtitle).isEqualTo("00:01:00.000")

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Makes sure current selection (before selecting an artifact) is the session we just began
    assertThat(mySessionsManager.selectedArtifactProto).isInstanceOf(Common.Session::class.java)
    // Selecting the CpuCaptureSessionArtifact should open CPU profiler and select the capture
    ui.layout()
    ui.mouse.click(cpuCaptureItem.bounds.x + 1, cpuCaptureItem.bounds.y + 1)
    // Move away again so we're not hovering
    ui.mouse.moveTo(-10, -10)
    assertThat(myProfilers.stage).isInstanceOf(CpuCaptureStage::class.java) // Makes sure CPU capture stage is now open
    assertThat(myProfilers.timeline.isStreaming).isFalse()
    // Makes sure artifact's proto selection is saved
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(cpuCaptureItem.artifact.artifactProto);

    // Make sure clicking the export label does not select the session.
    mySessionsManager.setSession(Common.Session.getDefaultInstance())
    assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    val exportLabel = cpuCaptureItem.exportLabel!!
    assertThat(exportLabel.isVisible).isFalse()
    ui.mouse.moveTo(cpuCaptureItem.bounds.x + 1, cpuCaptureItem.bounds.y + 1)
    ui.layout()
    assertThat(exportLabel.isVisible).isTrue()
    ui.mouse.click(cpuCaptureItem.bounds.x + exportLabel.bounds.x + 1, cpuCaptureItem.bounds.y + exportLabel.bounds.y + 1)
    assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    // Makes sure selected artifact's proto stayed the same
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(cpuCaptureItem.artifact.artifactProto);
  }

  @Test
  fun testCpuOngoingCaptureItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1 }
    val process = debuggableProcess { pid = 10 }
    val sessionStartNs = 1L

    // Sets an ongoing trace info in the service
    val cpuTraceInfo = Trace.TraceInfo.newBuilder()
      .setTraceId(1)
      .setConfiguration(Trace.TraceConfiguration.newBuilder()
                          .setAtraceOptions(Trace.AtraceOptions.getDefaultInstance()))
      .setFromTimestamp(sessionStartNs + 1)
      .setToTimestamp(-1)
      .build()
    myTransportService.addEventToStream(device.deviceId, Common.Event.newBuilder()
      .setGroupId(cpuTraceInfo.fromTimestamp)
      .setPid(process.getPid())
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(cpuTraceInfo.fromTimestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
      .build())
    myTimer.currentTimeNs = sessionStartNs
    mySessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    val sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    val cpuCaptureItem = sessionsPanel.getComponent(1) as CpuCaptureArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.session).isEqualTo(session)
    assertThat(cpuCaptureItem.artifact.isOngoing).isTrue()
    assertThat(cpuCaptureItem.artifact.name).isEqualTo(ProfilingTechnology.SYSTEM_TRACE.getName())
    assertThat(cpuCaptureItem.artifact.subtitle).isEqualTo(SessionArtifact.CAPTURING_SUBTITLE)
    assertThat(cpuCaptureItem.exportLabel).isNull()

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the CpuCaptureSessionArtifact should open CPU profiler and select the capture
    ui.layout()
    ui.mouse.click(cpuCaptureItem.bounds.x + 1, cpuCaptureItem.bounds.y + 1)
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java) // Makes sure CPU profiler stage is now open
    assertThat(myProfilers.timeline.isStreaming).isTrue()
    // Makes sure artifact's proto selection is saved
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(cpuCaptureItem.artifact.artifactProto);
    // Selects the CPUCaptureSessionArtifact again
    ui.layout()
    ui.mouse.click(cpuCaptureItem.bounds.x + 1, cpuCaptureItem.bounds.y + 1)
    // Makes sure selected artifact's proto stayed the same
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(cpuCaptureItem.artifact.artifactProto);
  }

  @Ignore("b/138573206")
  @Test
  fun testMemoryHeapDumpSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = debuggableProcess { pid = 10 }

    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(11).build()
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(heapDumpInfo.startTime, heapDumpInfo.startTime, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(process.pid).build())

    myTimer.currentTimeNs = 1
    mySessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    mySessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var hprofItem = sessionsPanel.getComponent(1) as HprofArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(hprofItem.artifact.session).isEqualTo(session)

    // Makes sure we're in monitor stage.
    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java)
    // Selecting on the HprofSessionArtifact should open Memory profiler and select the capture.
    ui.layout()
    ui.mouse.click(hprofItem.bounds.x + 1, hprofItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
    // Makes sure a HeapDumpCaptureObject is loaded.
    assertThat((myProfilers.stage as MainMemoryProfilerStage).captureSelection.selectedCapture).isInstanceOf(HeapDumpCaptureObject::class.java)

    // Make sure clicking the export label does not select the session.
    mySessionsManager.setSession(Common.Session.getDefaultInstance())
    assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    val exportLabel = hprofItem.exportLabel!!
    assertThat(exportLabel.isVisible).isFalse()
    ui.mouse.moveTo(hprofItem.bounds.x + 1, hprofItem.bounds.y + 1)
    ui.layout()
    assertThat(exportLabel.isVisible).isTrue()
    ui.mouse.click(hprofItem.bounds.x + exportLabel.bounds.x + 1, hprofItem.bounds.y + exportLabel.bounds.y + 1)
    assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testMemoryOngoingHeapDumpItemSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1 }
    val process = debuggableProcess { pid = 10 }

    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(Long.MAX_VALUE).build()
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(heapDumpInfo.startTime, heapDumpInfo.startTime, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(process.pid).build())

    mySessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var hprofItem = sessionsPanel.getComponent(1) as HprofArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(hprofItem.artifact.session).isEqualTo(session)
    assertThat(hprofItem.exportLabel).isNull()

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the HprofSessionArtifact should open Memory profiler.
    ui.layout()
    ui.mouse.click(hprofItem.bounds.x + 1, hprofItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
    // Makes sure that there is no capture selected.
    assertThat((myProfilers.stage as MainMemoryProfilerStage).captureSelection.selectedCapture).isNull()
    assertThat(myProfilers.timeline.isStreaming).isTrue()
    // Makes sure artifact's proto selection is saved
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(hprofItem.artifact.artifactProto);
    // Selects the HprofSessionArtifact again
    ui.layout()
    ui.mouse.click(hprofItem.bounds.x + 1, hprofItem.bounds.y + 1)
    // Makes sure selected artifact's proto stayed the same
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(hprofItem.artifact.artifactProto);
  }

  @Test
  fun testMemoryLegacyAllocationsSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1 }
    val process = debuggableProcess { pid = 10 }

    val allocationInfo = AllocationsInfo.newBuilder().setStartTime(10).setEndTime(11).setLegacy(true).setSuccess(true).build()
    myTransportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocationInfoData(allocationInfo.startTime, process.pid, allocationInfo).build())

    mySessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    val sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    val allocationItem = sessionsPanel.getComponent(1) as LegacyAllocationsArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(allocationItem.artifact.session).isEqualTo(session)

    // Makes sure we're in monitor stage.
    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java)
    // Selecting on the LegacyAllocationsSessionArtifact should open Memory profiler and select the capture.
    ui.layout()
    ui.mouse.click(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    // Move away again so we're not hovering
    ui.mouse.moveTo(-10, -10)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
    // Makes sure a LegacyAllocationCaptureObject is loaded.
    assertThat((myProfilers.stage as MainMemoryProfilerStage).captureSelection.selectedCapture).isInstanceOf(LegacyAllocationCaptureObject::class.java)
    // Makes sure artifact's proto selection is saved
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(allocationItem.artifact.artifactProto);

    // Make sure clicking the export label does not select the session.
    mySessionsManager.setSession(Common.Session.getDefaultInstance())
    assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    val exportLabel = allocationItem.exportLabel!!
    assertThat(exportLabel.isVisible).isFalse()
    ui.mouse.moveTo(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    ui.layout()
    assertThat(exportLabel.isVisible).isTrue()
    ui.mouse.click(allocationItem.bounds.x + exportLabel.bounds.x + 1, allocationItem.bounds.y + exportLabel.bounds.y + 1)
    assertThat(mySessionsManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    // Selects the LegacyAllocationsSessionArtifact again
    ui.layout()
    ui.mouse.click(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    // Makes sure selected artifact's proto stayed the same
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(allocationItem.artifact.artifactProto);
  }

  @Test
  fun testMemoryOngoingLegacyAllocationsSelection() {
    val sessionsPanel = mySessionsView.sessionsPanel
    sessionsPanel.setSize(200, 200)
    val ui = FakeUi(sessionsPanel)

    val device = onlineDevice { deviceId = NEW_DEVICE_ID_1 }
    val process = debuggableProcess { pid = 10 }

    val allocationInfo = AllocationsInfo.newBuilder().setStartTime(10).setEndTime(Long.MAX_VALUE).setLegacy(true).build()
    myTransportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocationInfoData(allocationInfo.startTime, process.pid, allocationInfo).build())

    mySessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val session = mySessionsManager.selectedSession

    assertThat(sessionsPanel.componentCount).isEqualTo(2)
    var sessionItem = sessionsPanel.getComponent(0) as SessionItemView
    var allocationItem = sessionsPanel.getComponent(1) as LegacyAllocationsArtifactView
    assertThat(sessionItem.artifact.session).isEqualTo(session)
    assertThat(allocationItem.artifact.session).isEqualTo(session)
    assertThat(allocationItem.exportLabel).isNull()

    assertThat(myProfilers.stage).isInstanceOf(StudioMonitorStage::class.java) // Makes sure we're in monitor stage
    // Selecting on the HprofSessionArtifact should open Memory profiler.
    ui.layout()
    ui.mouse.click(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    // Makes sure memory profiler stage is now open.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
    // Makes sure that there is no capture selected.
    assertThat((myProfilers.stage as MainMemoryProfilerStage).captureSelection.selectedCapture).isNull()
    assertThat(myProfilers.timeline.isStreaming).isTrue()
    // Makes sure artifact's proto selection is saved
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(allocationItem.artifact.artifactProto);
    // Selects the LegacyAllocationsSessionArtifact again
    ui.layout()
    ui.mouse.click(allocationItem.bounds.x + 1, allocationItem.bounds.y + 1)
    // Makes sure selected artifact's proto stayed the same
    assertThat(mySessionsManager.selectedArtifactProto).isEqualTo(allocationItem.artifact.artifactProto);
  }

  private fun startSession(device: Common.Device, process: Common.Process) {
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  companion object {
    const val VALID_TRACE_PATH = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"
    const val NEW_DEVICE_ID_1 = 1L
    const val NEW_DEVICE_ID_2 = 2L
  }
}