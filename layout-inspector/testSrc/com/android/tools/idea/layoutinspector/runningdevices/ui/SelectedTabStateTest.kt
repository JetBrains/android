/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.runningdevices.verifyUiInjected
import com.android.tools.idea.layoutinspector.runningdevices.verifyUiRemoved
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.DeviceDisplayListener
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayOwner
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class SelectedTabStateTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var layoutInspector: LayoutInspector
  private lateinit var displayListeners: MutableList<DeviceDisplayListener>

  @Before
  fun setUp() {
    val rectMap =
      mapOf(
        ROOT to Rectangle(0, 0, 100, 100),
        VIEW1 to Rectangle(0, 0, 100, 50),
        VIEW2 to Rectangle(0, 0, 100, 50),
        VIEW3 to Rectangle(0, 50, 100, 50),
        VIEW4 to Rectangle(40, 40, 20, 20),
      )

    val model =
      model(displayViewRule.disposable) {
        view(ROOT, rectMap[ROOT]) {
          view(VIEW1, rectMap[VIEW1]) { view(VIEW2, rectMap[VIEW2]) { image() } }
          view(VIEW3, rectMap[VIEW3])
          view(VIEW4, rectMap[VIEW4])
        }
      }

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
        metrics = mock(),
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
        layoutInspectorModel = model,
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings(),
      )

    displayListeners = mutableListOf()
  }

  @After
  fun tearDown() {
    PropertiesComponent.getInstance().unsetValue(UI_CONFIGURATION_KEY)
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
    val tabsComponents1 = createTabComponents()
    val container1 = tabsComponents1.tabContentPanel.parent
    val selectedTabState1 = createSelectedTabState(tabsComponents1)

    selectedTabState1.enableLayoutInspector(UiConfig.VERTICAL)

    verifyUiInjected<LayoutInspectorRenderer>(
      UiConfig.VERTICAL,
      tabsComponents1.tabContentPanel,
      container1,
      tabsComponents1.displayList.value,
    )

    Disposer.dispose(tabsComponents1)

    verifyUiRemoved(tabsComponents1.tabContentPanel, container1, tabsComponents1.displayList.value)

    val tabsComponents2 = createTabComponents()
    val container2 = tabsComponents2.tabContentPanel.parent
    val selectedTabState2 = createSelectedTabState(tabsComponents2)

    selectedTabState2.enableLayoutInspector()

    verifyUiInjected<LayoutInspectorRenderer>(
      UiConfig.VERTICAL,
      tabsComponents2.tabContentPanel,
      container2,
      tabsComponents2.displayList.value,
    )
  }

  @Test
  @RunsInEdt
  fun testDynamicallyAddedDisplay() {
    val tabComponents = createTabComponents()
    val container = tabComponents.tabContentPanel.parent
    val selectedTabState = createSelectedTabState(tabComponents)

    waitForCondition(10.seconds) { selectedTabState.renderingComponents.isNotEmpty() }

    selectedTabState.enableLayoutInspector(UiConfig.HORIZONTAL)

    val newDisplay = displayViewRule.newEmulatorView()
    displayListeners.forEach { it.displayAdded(newDisplay) }

    assertThat(tabComponents.displayList.value).contains(newDisplay)

    verifyUiInjected<LayoutInspectorRenderer>(
      UiConfig.HORIZONTAL,
      selectedTabState.tabComponents.tabContentPanel,
      container,
      tabComponents.displayList.value,
    )

    displayListeners.forEach { it.displayRemoved(newDisplay) }

    assertThat(tabComponents.displayList.value).doesNotContain(newDisplay)

    verifyUiInjected<LayoutInspectorRenderer>(
      UiConfig.HORIZONTAL,
      selectedTabState.tabComponents.tabContentPanel,
      container,
      tabComponents.displayList.value,
    )
  }

  private fun testConfiguration(uiConfig: UiConfig) {
    val tabComponents = createTabComponents()
    val container = tabComponents.tabContentPanel.parent
    val selectedTabState = createSelectedTabState(tabComponents)

    waitForCondition(10.seconds) { selectedTabState.renderingComponents.isNotEmpty() }

    selectedTabState.enableLayoutInspector(uiConfig)

    verifyUiInjected<LayoutInspectorRenderer>(
      uiConfig,
      selectedTabState.tabComponents.tabContentPanel,
      container,
      tabComponents.displayList.value,
    )

    Disposer.dispose(tabComponents)

    verifyUiRemoved(
      selectedTabState.tabComponents.tabContentPanel,
      container,
      tabComponents.displayList.value,
    )
  }

  private fun createTabComponents(): TabComponents {
    val container = JPanel()
    val content = JPanel()
    container.add(content)

    val displayView1 = displayViewRule.newEmulatorView()
    content.add(displayView1)

    val displayView2 = displayViewRule.newEmulatorView()
    content.add(displayView2)

    return TabComponents(
      disposable = displayViewRule.disposable,
      tabContentPanel = content,
      displayOwner =
        object : DisplayOwner {
          override fun addDeviceDisplayListener(listener: DeviceDisplayListener) {
            displayListeners.add(listener)
          }

          override fun removeDeviceDisplayListener(listener: DeviceDisplayListener) {
            displayListeners.remove(listener)
          }
        },
    )
  }

  private fun createSelectedTabState(tabComponents: TabComponents): SelectedTabState {
    return SelectedTabState(
      disposable = tabComponents,
      project = displayViewRule.project,
      deviceId = DeviceId.ofPhysicalDevice("tab"),
      tabComponents = tabComponents,
      layoutInspector = layoutInspector,
    )
  }
}
