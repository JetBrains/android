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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.verifyUiInjected
import com.android.tools.idea.layoutinspector.runningdevices.verifyUiRemoved
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

class SelectedTabStateTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var layoutInspector: LayoutInspector

  @Before
  fun setUp() {
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(displayViewRule.disposable, processModel)
    val notificationModel = NotificationModel(displayViewRule.project)

    val coroutineScope = AndroidCoroutineScope(displayViewRule.disposable)
    val launcher =
      InspectorClientLauncher(
        processModel,
        emptyList(),
        displayViewRule.project,
        notificationModel,
        coroutineScope,
        displayViewRule.disposable,
      )

    val fakeForegroundProcessDetection = FakeForegroundProcessDetection()

    layoutInspector =
      LayoutInspector(
        coroutineScope = coroutineScope,
        processModel = processModel,
        deviceModel = deviceModel,
        foregroundProcessDetection = fakeForegroundProcessDetection,
        inspectorClientSettings = InspectorClientSettings(displayViewRule.project),
        launcher = launcher,
        layoutInspectorModel = model {},
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings()
      )
  }

  @Test
  @RunsInEdt
  fun testHorizontalConfiguration() {
    testConfiguration(UiConfig.HORIZONTAL)
  }

  @Test
  @RunsInEdt
  fun testHorizontalSwapConfiguration() {
    testConfiguration(UiConfig.HORIZONTAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testVerticalConfiguration() {
    testConfiguration(UiConfig.VERTICAL)
  }

  @Test
  @RunsInEdt
  fun testVerticalSwapConfiguration() {
    testConfiguration(UiConfig.VERTICAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testLeftVerticalConfiguration() {
    testConfiguration(UiConfig.LEFT_VERTICAL)
  }

  @Test
  @RunsInEdt
  fun testLeftVerticalSwapConfiguration() {
    testConfiguration(UiConfig.LEFT_VERTICAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testRightVerticalConfiguration() {
    testConfiguration(UiConfig.RIGHT_VERTICAL)
  }

  @Test
  @RunsInEdt
  fun testRightVerticalSwapConfiguration() {
    testConfiguration(UiConfig.RIGHT_VERTICAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testUiConfigIsRestored() {
    val container = JPanel()
    val content = JPanel()
    container.add(content)
    val emulatorView = displayViewRule.newEmulatorView()

    val tabsComponents1 =
      TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState1 =
      SelectedTabState(
        displayViewRule.project,
        DeviceId.ofPhysicalDevice("tab"),
        tabsComponents1,
        layoutInspector
      )

    selectedTabState1.enableLayoutInspector(UiConfig.VERTICAL)

    verifyUiInjected(
      UiConfig.VERTICAL,
      tabsComponents1.tabContentPanel,
      tabsComponents1.tabContentPanelContainer,
      emulatorView
    )

    Disposer.dispose(tabsComponents1)

    verifyUiRemoved(
      tabsComponents1.tabContentPanel,
      tabsComponents1.tabContentPanelContainer,
      emulatorView
    )

    val tabsComponents2 =
      TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState2 =
      SelectedTabState(
        displayViewRule.project,
        DeviceId.ofPhysicalDevice("tab"),
        tabsComponents2,
        layoutInspector
      )

    selectedTabState2.enableLayoutInspector()

    verifyUiInjected(
      UiConfig.VERTICAL,
      tabsComponents2.tabContentPanel,
      tabsComponents2.tabContentPanelContainer,
      emulatorView
    )
  }

  private fun testConfiguration(uiConfig: UiConfig) {
    val container = JPanel()
    val content = JPanel()
    container.add(content)
    val emulatorView = displayViewRule.newEmulatorView()

    val tabsComponents = TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState =
      SelectedTabState(
        displayViewRule.project,
        DeviceId.ofPhysicalDevice("tab"),
        tabsComponents,
        layoutInspector
      )

    selectedTabState.enableLayoutInspector(uiConfig)

    verifyUiInjected(
      uiConfig,
      tabsComponents.tabContentPanel,
      tabsComponents.tabContentPanelContainer,
      emulatorView
    )

    Disposer.dispose(tabsComponents)

    verifyUiRemoved(
      tabsComponents.tabContentPanel,
      tabsComponents.tabContentPanelContainer,
      emulatorView
    )
  }
}
