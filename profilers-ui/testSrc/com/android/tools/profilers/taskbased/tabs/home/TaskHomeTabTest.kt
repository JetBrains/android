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
package com.android.tools.profilers.com.android.tools.profilers.taskbased.tabs.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.com.android.tools.profilers.JewelThemedComposableWrapper
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.selections.deviceprocesses.ProcessListModelTest
import com.android.tools.profilers.taskbased.tabs.home.TaskHomeTab
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class TaskHomeTabTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridViewTestChannel", myTransportService, FakeEventService())

  private val myComponents = FakeIdeProfilerComponents()

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var taskHomeTabModel: TaskHomeTabModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    taskHomeTabModel = TaskHomeTabModel(myProfilers)
    ideProfilerServices.enableTaskBasedUx(true)
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach { myProfilers.addTaskHandler(it.key, it.value) }
    assertThat(taskHomeTabModel.processListModel.deviceToProcesses.value).isEmpty()
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme`() {
    singleWindowApplication(
      title = "Testing TaskHomeTab",
    ) {
      JewelThemedComposableWrapper(isDark = false) {
        TaskHomeTab(taskHomeTabModel, myComponents)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme`() {
    singleWindowApplication(
      title = "Testing TaskHomeTab",
    ) {
      JewelThemedComposableWrapper(isDark = true) {
        TaskHomeTab(taskHomeTabModel, myComponents)
      }
    }
  }

  @Test
  fun `selecting device, process, and task enable start profiler task button`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = true) {
        TaskHomeTab(taskHomeTabModel, myComponents)
      }
    }

    // Populate the device and process selections.
    val device = ProcessListModelTest.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 24)
    ProcessListModelTest.addDeviceWithProcess(device, ProcessListModelTest.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                         device.deviceId), myTransportService, myTimer)
    ProcessListModelTest.addDeviceWithProcess(device, ProcessListModelTest.createProcess(40, "FakeProcess2", Common.Process.State.ALIVE,
                                                                                         device.deviceId), myTransportService, myTimer)

    // Assert that no processes are present for the selected device.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(0)

    // Select the device. We must use parent as `selectableItem` api does not allow us to append a test tag to the Modifier.
    composeTestRule.onNodeWithTag("DeviceSelectionDropdown").assertHasClickAction()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdown").performClick()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().assertExists()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().assertHasClickAction()
    composeTestRule.onNodeWithTag("DeviceSelectionDropdownItem", useUnmergedTree = true).onParent().performClick()

    // Selecting the FakeDevice should populate the process list with 2 processes.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(2)
    assertThat(taskHomeTabModel.processListModel.getSelectedDeviceProcesses()).hasSize(2)

    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice.model).isEqualTo("FakeDevice")

    // Select a process.
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().assertHasClickAction()
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().performClick()

    // Make sure process selection was registered in data model.
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("FakeProcess1")

    // Make sure at this point, the start profiler task button is still disabled.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertIsNotEnabled()

    // Make task selection.
    composeTestRule.onNodeWithText("Heap Dump").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
    composeTestRule.onNodeWithText("Heap Dump").performClick()

    // Make sure task selection was registered in data model.
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.HEAP_DUMP)

    // Make sure at this point, the start profiler task button is now enabled as device, process, and task selections were all valid.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertIsEnabled()
  }
}