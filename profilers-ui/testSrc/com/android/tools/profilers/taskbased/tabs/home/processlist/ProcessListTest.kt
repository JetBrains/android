/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.home.processlist


import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.compose.JewelTestTheme
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class ProcessListTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridViewTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var processListModel: ProcessListModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    processListModel = ProcessListModel(myProfilers) {}
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme`() {
    singleWindowApplication(
      title = "Testing TaskGridView",
    ) {
      populateVisualTestData()
      JewelTestTheme (darkMode = false) {
        ProcessList(processListModel)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme`() {
    singleWindowApplication(
      title = "Testing TaskGridView",
    ) {
      val device1 = TaskModelTestUtils.createDevice("FakeDevice1", Common.Device.State.ONLINE, "12", 24)
      TaskModelTestUtils.addDeviceWithProcess(device1, TaskModelTestUtils.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                        device1.deviceId), myTransportService, myTimer)

      // Assert FakeDevice1 was auto-selected.
      assertThat(processListModel.selectedDevice.value).isEqualTo(device1)

      val device2 = TaskModelTestUtils.createDevice("FakeDevice2", Common.Device.State.ONLINE, "12", 24)
      TaskModelTestUtils.addDeviceWithProcess(device2, TaskModelTestUtils.createProcess(20, "FakeProcess2", Common.Process.State.ALIVE,
                                                                                        device2.deviceId), myTransportService, myTimer)
      TaskModelTestUtils.addDeviceWithProcess(device2, TaskModelTestUtils.createProcess(40, "FakeProcess3", Common.Process.State.ALIVE,
                                                                                        device2.deviceId), myTransportService, myTimer)


      JewelTestTheme (darkMode = true) {
        ProcessList(processListModel)
      }
    }
  }

  @Test
  fun `device dropdown selection sets and renders the selected device's processes`() {
    composeTestRule.setContent {
      JewelTestTheme {
        ProcessList(processListModel)
      }
    }

    // Assert no device selection is registered in the data model.
    assertThat(processListModel.selectedDevice.value).isNull()

    // Assert that no processes are present for the selected device in data model or in UI (as there is no device selection yet).
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(0)
    assertThat(processListModel.getSelectedDeviceProcesses()).hasSize(0)

    // FakeDevice1 is added first and will this be auto-selected.
    val device1 = TaskModelTestUtils.createDevice("FakeDevice1", Common.Device.State.ONLINE, "12", 24)
    TaskModelTestUtils.addDeviceWithProcess(device1, TaskModelTestUtils.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                      device1.deviceId), myTransportService, myTimer)

    // Select the device
    processListModel.onDeviceSelection(device1)
    // Assert FakeDevice1 was auto-selected.
    assertThat(processListModel.selectedDevice.value).isNotNull()
    assertThat(processListModel.selectedDevice.value!!.device).isEqualTo(device1)
    // Selecting FakeDevice1 should populate the process list with 1 processes both in UI and in data model.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    assertThat(processListModel.getSelectedDeviceProcesses()).hasSize(1)
  }

  @Test
  fun `process selection reflects in data model`() {
    val device = TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 24)
    composeTestRule.setContent {
      JewelTestTheme {
        TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                         device.deviceId), myTransportService, myTimer)
        TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(40, "FakeProcess2", Common.Process.State.ALIVE,
                                                                                         device.deviceId), myTransportService, myTimer)
        ProcessList(processListModel)
      }
    }

    // Select the device
    processListModel.onDeviceSelection(device)

    // Selection of FakeDevice should populate the process list with 2 processes both in UI and in data model.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(2)
    assertThat(processListModel.getSelectedDeviceProcesses()).hasSize(2)

    // Assert no process is selected in data model.
    assertThat(processListModel.selectedProcess.value).isEqualTo(Common.Process.getDefaultInstance())

    // Select first process in dropdown.
    composeTestRule.onAllNodesWithTag("ProcessListRow")[0].assertExists()
    composeTestRule.onAllNodesWithTag("ProcessListRow")[0].performClick()

    // Assert process selection is registered in data model.
    assertThat(processListModel.selectedProcess.value.name).isEqualTo("FakeProcess1")
  }

  @Test
  fun testNoDevicesTitleAndMessage() {
    composeTestRule.setContent {
      JewelTestTheme {
        ProcessList(processListModel)
      }
    }

    processListModel.setSelectedDevicesCount(0)
    // Because there is zero selected devices, "No Devices" should be the text set for where the selected device name usually is.
    // Also, in the area the process table usually is should be a message to the user explaining that zero devices are selected.
    composeTestRule.onNodeWithText(TaskBasedUxStrings.NO_DEVICE_SELECTED_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.NO_DEVICE_SELECTED_MESSAGE).assertIsDisplayed()
  }

  @Test
  fun testMultipleDevicesTitleAndMessage() {
    composeTestRule.setContent {
      JewelTestTheme {
        ProcessList(processListModel)
      }
    }

    processListModel.setSelectedDevicesCount(6)
    // Because there is six selected devices, "Multiple Devices (6)" should be the text set for where the selected device name usually is.
    // Also, in the area the process table usually is should be a message to the user explaining that multiple devices are selected.
    composeTestRule.onNodeWithText("${TaskBasedUxStrings.MULTIPLE_DEVICES_SELECTED_TITLE} (6)").assertIsDisplayed()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.MULTIPLE_DEVICES_SELECTED_MESSAGE).assertIsDisplayed()
  }

  private fun populateVisualTestData() {
    assertThat(processListModel.deviceList.value).isEmpty()
    val device = TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 24)
    TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                     device.deviceId), myTransportService, myTimer)
    TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(40, "FakeProcess2", Common.Process.State.ALIVE,
                                                                                     device.deviceId), myTransportService, myTimer)
  }
}