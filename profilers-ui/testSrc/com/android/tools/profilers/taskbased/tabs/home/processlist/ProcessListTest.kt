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
package com.android.tools.profilers.com.android.tools.profilers.taskbased.tabs.home.processlist


import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.com.android.tools.profilers.JewelThemedComposableWrapper
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.taskbased.selections.deviceprocesses.ProcessListModelTest
import com.android.tools.profilers.taskbased.tabs.home.processlist.ProcessList
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
    assertThat(processListModel.deviceToProcesses.value).isEmpty()
    val device = ProcessListModelTest.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 24)
    ProcessListModelTest.addDeviceWithProcess(device, ProcessListModelTest.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                         device.deviceId), myTransportService, myTimer)
    ProcessListModelTest.addDeviceWithProcess(device, ProcessListModelTest.createProcess(40, "FakeProcess2", Common.Process.State.ALIVE,
                                                                                         device.deviceId), myTransportService, myTimer)
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme`() {
    singleWindowApplication(
      title = "Testing TaskGridView",
    ) {
      JewelThemedComposableWrapper(isDark = false) {
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
      JewelThemedComposableWrapper(isDark = true) {
        ProcessList(processListModel)
      }
    }
  }

  @Test
  fun `device dropdown selection sets and renders the selected device's processes`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = true) {
        ProcessList(processListModel)
      }
    }

    // Assert no device selection is registered in the data model.
    assertThat(processListModel.selectedDevice.value).isEqualTo(Common.Device.getDefaultInstance())

    // Assert that no processes are present for the selected device in data model or in UI (as there is no device selection yet).
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(0)
    assertThat(processListModel.getSelectedDeviceProcesses()).hasSize(0)

    // Select the device. We must use parent as `selectableItem` api does not allow us to append a test tag to the Modifier.
    composeTestRule.onNodeWithTag("DeviceSelectionDropdown").assertHasClickAction()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdown").performClick()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().assertExists()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().assertHasClickAction()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().performClick()

    // Selecting the FakeDevice should populate the process list with 2 processes both in UI and in data model.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(2)
    assertThat(processListModel.getSelectedDeviceProcesses()).hasSize(2)

    // Make sure device selection is also registered in data model.
    assertThat(processListModel.selectedDevice.value.model).isEqualTo("FakeDevice")
  }

  @Test
  fun `process selection reflects in data model`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = true) {
        ProcessList(processListModel)
      }
    }

    // Select the device.
    composeTestRule.onNodeWithTag("DeviceSelectionDropdown").assertHasClickAction()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdown").performClick()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().assertExists()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().assertHasClickAction()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().performClick()

    // Selecting the FakeDevice should populate the process list with 2 processes both in UI and in data model.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(2)
    assertThat(processListModel.getSelectedDeviceProcesses()).hasSize(2)

    // Assert no process is selected in data model.
    assertThat(processListModel.selectedProcess.value).isEqualTo(Common.Process.getDefaultInstance())

    // Select first process in dropdown.
    composeTestRule.onAllNodesWithTag("ProcessListRow")[0].assertHasClickAction()
    composeTestRule.onAllNodesWithTag("ProcessListRow")[0].performClick()

    // Assert process selection is registered in data model.
    assertThat(processListModel.selectedProcess.value.name).isEqualTo("FakeProcess1")
  }
}