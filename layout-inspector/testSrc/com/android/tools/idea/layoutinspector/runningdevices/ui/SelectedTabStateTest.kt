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
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    val container = JPanel()
    val content = JPanel()
    container.add(content)
    val emulatorView = displayViewRule.newEmulatorView()

    val tabsComponents = TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState =
      SelectedTabState(
        displayViewRule.project,
        DeviceId.ofPhysicalDevice("tab2"),
        tabsComponents,
        layoutInspector
      )

    selectedTabState.enableLayoutInspector(UiConfig.HORIZONTAL)

    verifyUiInjected(
      UiConfig.HORIZONTAL,
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

  @Test
  @RunsInEdt
  fun testVerticalConfiguration() {
    val container = JPanel()
    val content = JPanel()
    container.add(content)
    val emulatorView = displayViewRule.newEmulatorView()

    val tabsComponents = TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState =
      SelectedTabState(
        displayViewRule.project,
        DeviceId.ofPhysicalDevice("tab2"),
        tabsComponents,
        layoutInspector
      )

    selectedTabState.enableLayoutInspector(UiConfig.VERTICAL)

    verifyUiInjected(
      UiConfig.VERTICAL,
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
