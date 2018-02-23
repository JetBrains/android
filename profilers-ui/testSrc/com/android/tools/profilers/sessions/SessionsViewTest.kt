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
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeGrpcServer
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent

class SessionsViewTest {

  private val myProfilerService = FakeProfilerService(false)

  @get:Rule
  var myGrpcServer = FakeGrpcServer("StudioProfilerTestChannel", myProfilerService)

  private lateinit var myTimer: FakeTimer
  private lateinit var myProfilers: StudioProfilers
  private lateinit var mySessionsManager: SessionsManager
  private lateinit var mySessionsView: SessionsView

  @Before
  fun setup() {
    myTimer = FakeTimer()
    myProfilers = StudioProfilers(myGrpcServer.client, FakeIdeProfilerServices(), myTimer)
    mySessionsManager = myProfilers.sessionsManager
    mySessionsView = SessionsView(myProfilers)
  }

  @Test
  fun testSessionsListUpToDate() {
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    mySessionsManager.beginSession(device, process1)
    var session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session1)
    assertThat(mySessionsView.sessionsList.selectedIndex).isEqualTo(0)

    mySessionsManager.endCurrentSession()
    session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session1)
    assertThat(mySessionsView.sessionsList.selectedIndex).isEqualTo(0)

    mySessionsManager.beginSession(device, process2)
    val session2 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(2)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session2)
    assertThat(sessionArtifacts.getElementAt(1).session).isEqualTo(session1)
    assertThat(mySessionsView.sessionsList.selectedIndex).isEqualTo(0)
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
    assertThat(selectionAction.childrenActionCount).isEqualTo(1)
    var deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isFalse()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addProcess(device1, process1)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(1)
    deviceAction1 = selectionAction.childrenActions.first { c -> c.text == "Manufacturer1 Model1" }
    assertThat(deviceAction1.isSelected).isTrue()
    assertThat(deviceAction1.isEnabled).isTrue()
    assertThat(deviceAction1.childrenActionCount).isEqualTo(1)
    var processAction1 = deviceAction1.childrenActions.first { c -> c.text == "Process1" }
    assertThat(processAction1.isSelected).isTrue()
    assertThat(processAction1.childrenActionCount).isEqualTo(0)

    myProfilerService.addProcess(device1, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(selectionAction.childrenActionCount).isEqualTo(1)
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
    assertThat(selectionAction.childrenActionCount).isEqualTo(2)
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
}