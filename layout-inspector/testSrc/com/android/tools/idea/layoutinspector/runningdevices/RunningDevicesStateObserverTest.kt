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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.idea.layoutinspector.TestScopeRule
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.StreamingDeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class RunningDevicesStateObserverTest {

  private val edtRule = EdtRule()

  private val displayViewRule = EmulatorViewRule()

  @get:Rule val rule = RuleChain(TestScopeRule(), displayViewRule, edtRule)

  private lateinit var fakeToolWindow: ToolWindowEx

  private lateinit var tab1: TabInfo
  private lateinit var tab2: TabInfo

  @Before
  fun setUp() {
    tab1 =
      TabInfo(StreamingDeviceId.ofPhysicalDevice("tab1"), BorderLayoutPanel(), JPanel(), listOf(displayViewRule.newEmulatorDisplayView()))
    tab2 =
      TabInfo(StreamingDeviceId.ofPhysicalDevice("tab2"), BorderLayoutPanel(), JPanel(), listOf(displayViewRule.newEmulatorDisplayView()))

    fakeToolWindow = createFakeToolWindow(displayViewRule.project, displayViewRule.disposable, RUNNING_DEVICES_TOOL_WINDOW_ID)
  }

  @Test
  fun testListenerIsCalledWithExistingState() {
    val runningDevicesStateObserver = RunningDevicesStateObserver.getInstance(displayViewRule.project)

    fakeToolWindow.show()

    addContent(fakeToolWindow, tab1)
    addContent(fakeToolWindow, tab2)

    val observedSelectedTabs = mutableListOf<List<StreamingDeviceId>>()
    val observedExistingTabs = mutableListOf<List<StreamingDeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>) {
          observedSelectedTabs.add(selectedTabs)
        }

        override fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>) {
          observedExistingTabs.add(existingTabs)
        }
      }

    runningDevicesStateObserver.addListener(listener)

    assertThat(observedSelectedTabs).containsExactly(listOf(tab1.streamingDeviceId))
    assertThat(observedExistingTabs).containsExactly(listOf(tab1.streamingDeviceId, tab2.streamingDeviceId))
  }

  @Test
  fun testListenerIsCalledWhenAddingAndRemovingContent() {
    val runningDevicesStateObserver = RunningDevicesStateObserver.getInstance(displayViewRule.project)

    fakeToolWindow.show()

    val observedSelectedTabs = mutableListOf<List<StreamingDeviceId>>()
    val observedExistingTabs = mutableListOf<List<StreamingDeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>) {
          observedSelectedTabs.add(selectedTabs)
        }

        override fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>) {
          observedExistingTabs.add(existingTabs)
        }
      }

    runningDevicesStateObserver.addListener(listener)

    addContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    addContent(fakeToolWindow, tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    removeContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs)
      .containsExactly(emptyList<StreamingDeviceId>(), listOf(tab1.streamingDeviceId), listOf(tab2.streamingDeviceId))
    assertThat(observedExistingTabs)
      .containsExactly(
        emptyList<StreamingDeviceId>(),
        listOf(tab1.streamingDeviceId),
        listOf(tab1.streamingDeviceId, tab2.streamingDeviceId),
        listOf(tab2.streamingDeviceId),
      )
  }

  @Test
  fun testListenerIsCalledWhenSelectedTabChanges() {
    val runningDevicesStateObserver = RunningDevicesStateObserver.getInstance(displayViewRule.project)

    val observedSelectedTabs = mutableListOf<List<StreamingDeviceId>>()
    val observedExistingTabs = mutableListOf<List<StreamingDeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>) {
          observedSelectedTabs.add(selectedTabs)
        }

        override fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>) {
          observedExistingTabs.add(existingTabs)
        }
      }

    fakeToolWindow.show()

    runningDevicesStateObserver.addListener(listener)

    fakeToolWindow.show()

    addContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    addContent(fakeToolWindow, tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    setSelectedContent(fakeToolWindow, tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    setSelectedContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    setSelectedContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs)
      .containsExactly(
        emptyList<StreamingDeviceId>(),
        listOf(tab1.streamingDeviceId),
        listOf(tab2.streamingDeviceId),
        listOf(tab1.streamingDeviceId),
      )
    assertThat(observedExistingTabs)
      .containsExactly(
        emptyList<StreamingDeviceId>(),
        listOf(tab1.streamingDeviceId),
        listOf(tab1.streamingDeviceId, tab2.streamingDeviceId),
      )
  }

  @Test
  fun testToolWindowStateChange() {
    val runningDevicesStateObserver = RunningDevicesStateObserver.getInstance(displayViewRule.project)

    val observedSelectedTabs = mutableListOf<List<StreamingDeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>) {
          observedSelectedTabs.add(selectedTabs)
        }

        override fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>) {}
      }

    runningDevicesStateObserver.addListener(listener)

    fakeToolWindow.show()
    fakeToolWindow.hide()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs).containsExactly(emptyList<StreamingDeviceId>())

    addContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    setSelectedContent(fakeToolWindow, tab1)

    fakeToolWindow.show()
    fakeToolWindow.hide()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs)
      .containsExactly(emptyList<StreamingDeviceId>(), listOf(tab1.streamingDeviceId), emptyList<StreamingDeviceId>())
  }

  @Test
  fun testObserverIsNotifiedOfExistingTabsAtCreation() {
    addContent(fakeToolWindow, tab1)
    addContent(fakeToolWindow, tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val observedSelectedTabs = mutableListOf<List<StreamingDeviceId>>()
    val observedExistingTabs = mutableListOf<List<StreamingDeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabsChanged(selectedTabs: List<StreamingDeviceId>) {
          observedSelectedTabs.add(selectedTabs)
        }

        override fun onExistingTabsChanged(existingTabs: List<StreamingDeviceId>) {
          observedExistingTabs.add(existingTabs)
        }
      }

    val runningDevicesStateObserver = RunningDevicesStateObserver.getInstance(displayViewRule.project)
    runningDevicesStateObserver.addListener(listener)
    // Show to trigger ToolWindowManagerListener.stateChanged
    fakeToolWindow.show()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs).containsExactly(emptyList<StreamingDeviceId>(), listOf(tab1.streamingDeviceId))
    assertThat(observedExistingTabs).containsExactly(emptyList<StreamingDeviceId>(), listOf(tab1.streamingDeviceId, tab2.streamingDeviceId))
  }
}
