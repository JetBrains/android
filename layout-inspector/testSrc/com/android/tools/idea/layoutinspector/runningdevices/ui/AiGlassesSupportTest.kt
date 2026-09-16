/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.resource.data.Display
import com.android.tools.idea.layoutinspector.runningdevices.RunningDevicesStateObserver
import com.android.tools.idea.layoutinspector.runningdevices.TabInfo
import com.android.tools.idea.layoutinspector.runningdevices.addContent
import com.android.tools.idea.layoutinspector.runningdevices.getContent
import com.android.tools.idea.layoutinspector.runningdevices.removeContent
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.testing.ui.ToolWindowHeadlessManagerImpl
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@RunsInEdt
class AiGlassesSupportTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var fakeToolWindow: ToolWindow
  private lateinit var inspectorModel: InspectorModel

  @Before
  fun setUp() {
    fakeToolWindow = createFakeToolWindow(displayViewRule.project, displayViewRule.disposable, RUNNING_DEVICES_TOOL_WINDOW_ID)

    RunningDevicesStateObserver.getInstance(displayViewRule.project)
    fakeToolWindow.show()

    inspectorModel = model(displayViewRule.disposable) { view(ROOT, Rectangle(0, 0, 100, 100)) {} }
  }

  @Test
  fun testNoTabsEmitsInactive() = runTest {
    val results = mutableListOf<AiGlassesState>()
    val job = launch { aiGlassesDataFlow(displayViewRule.project, inspectorModel).toList(results) }

    advanceUntilIdle()
    assertThat(results).containsExactly(AiGlassesState.Inactive)
    job.cancel()
  }

  @Test
  fun testAddingAndRemovingHandheldTabDoesNotEmitActive() = runTest {
    val results = mutableListOf<AiGlassesState>()
    val job = launch { aiGlassesDataFlow(displayViewRule.project, inspectorModel).toList(results) }

    val tabInfo1 = addHandheldTab("handheld1")
    advanceUntilIdle()

    // We should still only have the initial Inactive state. Handheld tabs shouldn't trigger state changes for AI glasses.
    assertThat(results).containsExactly(AiGlassesState.Inactive)

    val tabInfo2 = addHandheldTab("handheld2")
    advanceUntilIdle()

    assertThat(results).containsExactly(AiGlassesState.Inactive)

    removeContent(fakeToolWindow, tabInfo1)
    advanceUntilIdle()

    // Removing the tab also shouldn't trigger anything.
    assertThat(results).containsExactly(AiGlassesState.Inactive)

    job.cancel()
  }

  @Test
  fun testOneAiGlassesTabAndOneSecondaryDisplayEmitsActive() = runTest {
    val results = mutableListOf<AiGlassesState>()
    val job = launch { aiGlassesDataFlow(displayViewRule.project, inspectorModel).toList(results) }

    advanceUntilIdle()

    addAppDisplay()
    val tabInfo = addGlassesTab("glasses1")

    advanceUntilIdle()

    assertThat(results).containsExactly(AiGlassesState.Inactive, AiGlassesState.Active(AiGlassesDisplayPair(tabInfo.displays.first(), 1)))

    job.cancel()
  }

  @Test
  fun testChangingHandheldTabDoesNotEmitWhenActive() = runTest {
    val results = mutableListOf<AiGlassesState>()
    val job = launch { aiGlassesDataFlow(displayViewRule.project, inspectorModel).toList(results) }

    advanceUntilIdle()

    addAppDisplay()
    val tabInfo = addGlassesTab("glasses1")

    advanceUntilIdle()

    assertThat(results).containsExactly(AiGlassesState.Inactive, AiGlassesState.Active(AiGlassesDisplayPair(tabInfo.displays.first(), 1)))

    addHandheldTab("handheld1")
    advanceUntilIdle()

    assertThat(results).containsExactly(AiGlassesState.Inactive, AiGlassesState.Active(AiGlassesDisplayPair(tabInfo.displays.first(), 1)))

    job.cancel()
  }

  @Test
  fun testMultipleAiGlassesTabsEmitsMultipleGlassesTabsError() = runTest {
    val results = mutableListOf<AiGlassesState>()
    val job = launch { aiGlassesDataFlow(displayViewRule.project, inspectorModel).toList(results) }

    advanceUntilIdle()

    val tab1 = addGlassesTab("glasses1")
    val tab2 = addGlassesTab("glasses2")

    val content1 = fakeToolWindow.getContent(tab1.deviceId)
    val content2 = fakeToolWindow.getContent(tab2.deviceId)

    ToolWindowHeadlessManagerImpl.split(content1, SwingConstants.BOTTOM)
    val bottomContentManager = content1.manager!!
    val topContentManager = content2.manager!!

    bottomContentManager.setSelectedContent(content1)
    topContentManager.setSelectedContent(content2)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    advanceUntilIdle()

    assertThat(results.last()).isEqualTo(AiGlassesState.Error.MultipleGlassesTabs)

    job.cancel()
  }

  @Test
  fun testOneAiGlassesTabAndMultipleSecondaryDisplaysEmitsMultipleSecondaryDisplaysError() = runTest {
    val results = mutableListOf<AiGlassesState>()
    val job = launch { aiGlassesDataFlow(displayViewRule.project, inspectorModel).toList(results) }

    advanceUntilIdle()

    addAppDisplay(secondaryDisplays = listOf(Display(1, Dimension(1000, 2000), 0), Display(2, Dimension(1000, 2000), 0)))

    addGlassesTab("glasses1")

    advanceUntilIdle()

    assertThat(results.last()).isEqualTo(AiGlassesState.Error.MultipleSecondaryDisplays)

    job.cancel()
  }

  private fun addHandheldTab(id: String): TabInfo {
    return addTab(id, DeviceType.HANDHELD)
  }

  private fun addGlassesTab(id: String): TabInfo {
    return addTab(id, DeviceType.AI_GLASSES)
  }

  private fun addTab(id: String, deviceType: DeviceType): TabInfo {
    val displayView = displayViewRule.newEmulatorDisplayView(displayId = Display.MAIN_DISPLAY_ID)
    val tab =
      TabInfo(
        deviceId = DeviceId.ofPhysicalDevice(id),
        content = BorderLayoutPanel(),
        container = JPanel(),
        displays = listOf(displayView),
        deviceType = deviceType,
      )

    addContent(fakeToolWindow, tab)
    return tab
  }

  /** Adds a primary display and list of secondary displays to the app */
  private fun addAppDisplay(secondaryDisplays: List<Display> = listOf(Display(1, Dimension(1000, 2000), 0))) {
    val mainDisplay = Display(Display.MAIN_DISPLAY_ID, Dimension(1000, 2000), 0)

    // Set up a secondary display in the inspector model
    inspectorModel.update(null, listOf(ROOT), 0)
    val displays = listOf(mainDisplay) + secondaryDisplays
    inspectorModel.resourceLookup.displays = displays
    // Dummy modification to trigger listener
    inspectorModel.windows.clear()
  }
}
