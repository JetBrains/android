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

import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

@RunsInEdt
class RunningDevicesStateObserverTest {

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val displayViewRule = EmulatorViewRule()

  private lateinit var fakeToolWindowManager: FakeToolWindowManager

  private lateinit var tab1: TabInfo
  private lateinit var tab2: TabInfo

  @Before
  fun setUp() {
    tab1 = TabInfo(TabId("tab1"), JPanel(), JPanel(), displayViewRule.newEmulatorView())
    tab2 = TabInfo(TabId("tab2"), JPanel(), JPanel(), displayViewRule.newEmulatorView())

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

    val runningDevicesStateObserver = RunningDevicesStateObserver(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val observedSelectedTabs = mutableListOf<TabId?>()
    val observedExistingTabs = mutableListOf<List<TabId>>()

    val listener = object : RunningDevicesStateObserver.Listener {
      override fun onSelectedTabChanged(tabId: TabId?) {
        observedSelectedTabs.add(tabId)
      }

      override fun onExistingTabsChanged(existingTabs: List<TabId>) {
        observedExistingTabs.add(existingTabs)
      }
    }

    runningDevicesStateObserver.addListener(listener)

    assertThat(observedSelectedTabs).containsExactly(tab1.tabId)
    assertThat(observedExistingTabs).containsExactly(
      listOf(tab1.tabId, tab2.tabId)
    )
  }

  @Test
  fun testListenerIsCalledWhenAddingAndRemovingContent() {
    val runningDevicesStateObserver = RunningDevicesStateObserver(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val observedSelectedTabs = mutableListOf<TabId?>()
    val observedExistingTabs = mutableListOf<List<TabId>>()

    val listener = object : RunningDevicesStateObserver.Listener {
      override fun onSelectedTabChanged(tabId: TabId?) {
        observedSelectedTabs.add(tabId)
      }

      override fun onExistingTabsChanged(existingTabs: List<TabId>) {
        observedExistingTabs.add(existingTabs)
      }
    }

    runningDevicesStateObserver.addListener(listener)

    fakeToolWindowManager.addContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.addContent(tab2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    fakeToolWindowManager.removeContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(observedSelectedTabs).containsExactly(null, tab1.tabId, tab2.tabId)
    assertThat(observedExistingTabs).containsExactly(
      emptyList<TabId>(),
      listOf(tab1.tabId),
      listOf(tab1.tabId, tab2.tabId),
      listOf(tab2.tabId)
    )
  }

  @Test
  fun testListenerIsCalledWhenSelectedTabChanges() {
    val runningDevicesStateObserver = RunningDevicesStateObserver(displayViewRule.project)
    runningDevicesStateObserver.update(true)

    val observedSelectedTabs = mutableListOf<TabId?>()
    val observedExistingTabs = mutableListOf<List<TabId>>()

    val listener = object : RunningDevicesStateObserver.Listener {
      override fun onSelectedTabChanged(tabId: TabId?) {
        observedSelectedTabs.add(tabId)
      }

      override fun onExistingTabsChanged(existingTabs: List<TabId>) {
        observedExistingTabs.add(existingTabs)
      }
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

    assertThat(observedSelectedTabs).containsExactly(null, tab1.tabId, tab2.tabId, tab1.tabId)
    assertThat(observedExistingTabs).containsExactly(
      emptyList<TabId>(),
      listOf(tab1.tabId),
      listOf(tab1.tabId, tab2.tabId),
    )
  }
}