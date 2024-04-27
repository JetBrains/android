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

import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

@RunsInEdt
class RunningDevicesStateObserverTest {

  @get:Rule val edtRule = EdtRule()

  private val scheduler = TestCoroutineScheduler()
  private val dispatcher = StandardTestDispatcher(scheduler)
  private val testScope = TestScope(dispatcher)

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var fakeToolWindowManager: FakeToolWindowManager

  private lateinit var tab1: TabInfo
  private lateinit var tab2: TabInfo

  @Before
  fun setUp() {
    tab1 =
      TabInfo(
        DeviceId.ofPhysicalDevice("tab1"),
        JPanel(),
        JPanel(),
        displayViewRule.newEmulatorView()
      )
    tab2 =
      TabInfo(
        DeviceId.ofPhysicalDevice("tab2"),
        JPanel(),
        JPanel(),
        displayViewRule.newEmulatorView()
      )

    fakeToolWindowManager = FakeToolWindowManager(displayViewRule.project, emptyList())

    // replace ToolWindowManager with fake one
    displayViewRule.project.replaceService(
      ToolWindowManager::class.java,
      fakeToolWindowManager,
      displayViewRule.disposable
    )
  }

  @Test
  fun testListenerIsCalledWithExistingState() {
    fakeToolWindowManager.addContent(tab1)
    fakeToolWindowManager.addContent(tab2)

    val runningDevicesStateObserver =
      RunningDevicesStateObserver.getInstance(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val observedSelectedTabs = mutableListOf<DeviceId?>()
    val observedExistingTabs = mutableListOf<List<DeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabChanged(tabId: DeviceId?) {
          observedSelectedTabs.add(tabId)
        }

        override fun onExistingTabsChanged(existingTabs: List<DeviceId>) {
          observedExistingTabs.add(existingTabs)
        }

        override fun onToolWindowHidden() {}

        override fun onToolWindowShown(selectedDeviceId: DeviceId?) {}
      }

    runningDevicesStateObserver.addListener(listener)

    println("Selected tabs: $observedSelectedTabs")
    println("Existing tabs: $observedExistingTabs")
    assertThat(observedSelectedTabs).containsExactly(tab1.deviceId)
    assertThat(observedExistingTabs).containsExactly(listOf(tab1.deviceId, tab2.deviceId))
  }

  @Test
  fun testListenerIsCalledWhenAddingAndRemovingContent() {
    val runningDevicesStateObserver =
      RunningDevicesStateObserver.getInstance(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val observedSelectedTabs = mutableListOf<DeviceId?>()
    val observedExistingTabs = mutableListOf<List<DeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabChanged(tabId: DeviceId?) {
          observedSelectedTabs.add(tabId)
        }

        override fun onExistingTabsChanged(existingTabs: List<DeviceId>) {
          observedExistingTabs.add(existingTabs)
        }

        override fun onToolWindowHidden() {}

        override fun onToolWindowShown(selectedDeviceId: DeviceId?) {}
      }

    runningDevicesStateObserver.addListener(listener)

    fakeToolWindowManager.addContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.addContent(tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.removeContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs).containsExactly(null, tab1.deviceId, tab2.deviceId)
    assertThat(observedExistingTabs)
      .containsExactly(
        emptyList<DeviceId>(),
        listOf(tab1.deviceId),
        listOf(tab1.deviceId, tab2.deviceId),
        listOf(tab2.deviceId)
      )
  }

  @Test
  fun testListenerIsCalledWhenSelectedTabChanges() {
    val runningDevicesStateObserver =
      RunningDevicesStateObserver.getInstance(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val observedSelectedTabs = mutableListOf<DeviceId?>()
    val observedExistingTabs = mutableListOf<List<DeviceId>>()

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabChanged(tabId: DeviceId?) {
          observedSelectedTabs.add(tabId)
        }

        override fun onExistingTabsChanged(existingTabs: List<DeviceId>) {
          observedExistingTabs.add(existingTabs)
        }

        override fun onToolWindowHidden() {}

        override fun onToolWindowShown(selectedDeviceId: DeviceId?) {}
      }

    runningDevicesStateObserver.addListener(listener)

    fakeToolWindowManager.addContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.addContent(tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.setSelectedContent(tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.setSelectedContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.setSelectedContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs)
      .containsExactly(null, tab1.deviceId, tab2.deviceId, tab1.deviceId)
    assertThat(observedExistingTabs)
      .containsExactly(
        emptyList<DeviceId>(),
        listOf(tab1.deviceId),
        listOf(tab1.deviceId, tab2.deviceId),
      )
  }

  @Test
  fun testToolWindowStateChange() {
    val runningDevicesStateObserver =
      RunningDevicesStateObserver.getInstance(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val toolWindowOpenDeviceIds = mutableListOf<DeviceId?>()
    var toolWindowClosedCount = 0

    val listener =
      object : RunningDevicesStateObserver.Listener {
        override fun onSelectedTabChanged(deviceId: DeviceId?) {}

        override fun onExistingTabsChanged(existingTabs: List<DeviceId>) {}

        override fun onToolWindowHidden() {
          toolWindowClosedCount += 1
        }

        override fun onToolWindowShown(selectedDeviceId: DeviceId?) {
          toolWindowOpenDeviceIds.add(selectedDeviceId)
        }
      }

    runningDevicesStateObserver.addListener(listener)

    fakeToolWindowManager.toolWindow.show()
    fakeToolWindowManager.toolWindow.hide()

    assertThat(toolWindowOpenDeviceIds).containsExactly(null)
    assertThat(toolWindowClosedCount).isEqualTo(1)

    fakeToolWindowManager.addContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    fakeToolWindowManager.setSelectedContent(tab1)

    fakeToolWindowManager.toolWindow.show()
    fakeToolWindowManager.toolWindow.hide()

    assertThat(toolWindowOpenDeviceIds).containsExactly(null, tab1.deviceId)
    assertThat(toolWindowClosedCount).isEqualTo(2)
  }
}
