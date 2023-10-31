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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.actions.GearAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.ui.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.SingleDeviceSelectProcessAction
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JPanel
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy

class LayoutInspectorManagerTest {

  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val edtRule = EdtRule()

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var layoutInspector: LayoutInspector
  private lateinit var notificationModel: NotificationModel

  private lateinit var tab1: TabInfo
  private lateinit var tab2: TabInfo

  private lateinit var fakeToolWindowManager: FakeToolWindowManager
  private lateinit var fakeForegroundProcessDetection: FakeForegroundProcessDetection

  @Before
  fun setUp() {
    tab1 =
      TabInfo(
        DeviceId.ofPhysicalDevice("tab1"),
        JPanel(),
        JPanel(),
        spy(displayViewRule.newEmulatorView())
      )
    tab2 =
      TabInfo(
        DeviceId.ofPhysicalDevice("tab2"),
        JPanel(),
        JPanel(),
        spy(displayViewRule.newEmulatorView())
      )
    fakeToolWindowManager = FakeToolWindowManager(displayViewRule.project, listOf(tab1, tab2))

    // replace ToolWindowManager with fake one
    displayViewRule.project.replaceService(
      ToolWindowManager::class.java,
      fakeToolWindowManager,
      displayViewRule.disposable
    )

    val mockLayoutInspectorProjectService = mock<LayoutInspectorProjectService>()

    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(displayViewRule.disposable, processModel)
    notificationModel = NotificationModel(displayViewRule.project)

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

    fakeForegroundProcessDetection = FakeForegroundProcessDetection()

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

    whenever(mockLayoutInspectorProjectService.getLayoutInspector()).thenAnswer { layoutInspector }
    displayViewRule.project.replaceService(
      LayoutInspectorProjectService::class.java,
      mockLayoutInspectorProjectService,
      displayViewRule.disposable
    )

    RunningDevicesStateObserver.getInstance(displayViewRule.project).update(true)
  }

  @After
  fun tearDown() {
    RunningDevicesStateObserver.getInstance(displayViewRule.project).update(false)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnOff() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTimesForSameTab() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOffMultipleTimesForSameTab() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTabs() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, false)

    assertDoesNotHaveWorkbench(tab2)
  }

  @Test
  @RunsInEdt
  fun testHasDataProvider() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    // Verify workbench has access to data provider
    val workbench =
      tab1.container.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()
    val dataProvider = DataManager.getDataProvider(workbench)
    val layoutInspector1 = dataProvider!!.getData(LAYOUT_INSPECTOR_DATA_KEY.name)
    assertThat(layoutInspector1).isEqualTo(layoutInspector)

    // Verify toolbar has access to data provider
    val toolbar =
      tab1.container.allChildren().filterIsInstance<JComponent>().first {
        it.name == "EmbeddedLayoutInspector.Toolbar"
      }
    val dataProvider2 = DataManager.getDataProvider(toolbar)
    val layoutInspector2 = dataProvider2!!.getData(LAYOUT_INSPECTOR_DATA_KEY.name)
    assertThat(layoutInspector2).isEqualTo(layoutInspector)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    val dataContext3 = DataManager.getDataProvider(workbench)
    assertThat(dataContext3).isNull()

    val dataContext4 = DataManager.getDataProvider(toolbar)
    assertThat(dataContext4).isNull()
  }

  @Test
  @RunsInEdt
  fun testSelectedTabDoesNotChange() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)

    // adding a new tab that doesn't have Layout Inspector enabled
    fakeToolWindowManager.addContent(tab2)

    assertHasWorkbench(tab1)
    assertDoesNotHaveWorkbench(tab2)
  }

  @Test
  @RunsInEdt
  fun testWorkbenchIsInjectedWhenSelectedTabChanges() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    fakeToolWindowManager.setSelectedContent(tab2)
    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    fakeToolWindowManager.setSelectedContent(tab1)

    assertHasWorkbench(tab1)
    assertDoesNotHaveWorkbench(tab2)
  }

  @Test
  @RunsInEdt
  fun testSelectedTabIsRemoved() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(3)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    fakeToolWindowManager.setSelectedContent(tab2)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(1)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(3)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    fakeToolWindowManager.setSelectedContent(tab1)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(3)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    assertHasWorkbench(tab1)
    assertDoesNotHaveWorkbench(tab2)

    fakeToolWindowManager.removeContent(tab1)

    assertDoesNotHaveWorkbench(tab1)
    assertThat(layoutInspector.deviceModel?.selectedDevice).isNull()

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(3)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    assertHasWorkbench(tab2)

    fakeToolWindowManager.removeContent(tab2)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(1)
  }

  @Test
  @RunsInEdt
  fun testViewIsRefreshedOnSelectionChange() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)
    var refreshCount = 0

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    val layoutInspectorRenderer =
      tab1.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>().first()
    layoutInspectorRenderer.addListener { refreshCount += 1 }

    layoutInspector.inspectorModel.setSelection(ViewNode("node1"), SelectionOrigin.COMPONENT_TREE)
    assertThat(refreshCount).isEqualTo(1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    layoutInspector.inspectorModel.setSelection(ViewNode("node2"), SelectionOrigin.COMPONENT_TREE)
    assertThat(refreshCount).isEqualTo(1)
  }

  @Ignore("b/287075342")
  @Test
  @RunsInEdt
  fun testDeepInspectIsDisabledOnProcessChange() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    val layoutInspectorRenderer =
      tab1.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>().first()
    assertThat(layoutInspectorRenderer.interceptClicks).isFalse()

    layoutInspectorRenderer.interceptClicks = true
    layoutInspector.processModel?.selectedProcess = MODERN_DEVICE.createProcess()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(layoutInspectorRenderer.interceptClicks).isFalse()
  }

  @Test
  @RunsInEdt
  fun testEnableLiveUpdatesOnProcessChange() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspector.inspectorClientSettings.isCapturingModeOn = false
    assertThat(layoutInspector.inspectorClientSettings.isCapturingModeOn).isFalse()

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertThat(layoutInspector.inspectorClientSettings.isCapturingModeOn).isFalse()

    layoutInspector.processModel?.selectedProcess = MODERN_DEVICE.createProcess()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(layoutInspector.inspectorClientSettings.isCapturingModeOn).isTrue()
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorShowsWarningFirstTime() = withEmbeddedLayoutInspector {
    PropertiesComponent.getInstance().unsetValue(SHOW_EXPERIMENTAL_WARNING_KEY)

    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)
    val notifications1 = notificationModel.notifications
    assertThat(notifications1).hasSize(1)
    val firstNotification = notifications1.single()
    assertThat(firstNotification.message)
      .isEqualTo(
        "(Experimental) Layout Inspector is now embedded within the Running Devices window"
      )
    assertThat(firstNotification.actions[0].name).isEqualTo("Don't Show Again")
    assertThat(firstNotification.actions[1].name).isEqualTo("Opt-out")

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)
    val notifications2 = notificationModel.notifications
    assertThat(notifications2).hasSize(1)

    val doNotShowAgain = firstNotification.actions[0]
    doNotShowAgain.invoke(firstNotification)

    val notifications3 = notificationModel.notifications
    assertThat(notifications3).hasSize(0)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    val notifications4 = notificationModel.notifications
    assertThat(notifications4).hasSize(0)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testDeepInspectEnablesClickIntercept() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    val layoutInspectorRenderer =
      tab1.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>().first()

    val toolbars =
      tab1.container.allChildren().filterIsInstance<ActionToolbar>().first {
        it.component.name == "LayoutInspector.MainToolbar"
      }

    val toggleDeepInspectAction =
      toolbars.actions.filterIsInstance<ToggleDeepInspectAction>().first()
    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction)))
      .isFalse()
    assertThat(layoutInspectorRenderer.interceptClicks).isFalse()

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))

    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction)))
      .isTrue()
    assertThat(layoutInspectorRenderer.interceptClicks).isTrue()

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)
  }

  @Test
  @RunsInEdt
  fun testGlobalStateIsUpdated() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector)
      .containsExactly(tab1.deviceId)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertDoesNotHaveWorkbench(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testGlobalStateIsUpdatedOnDispose() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertHasWorkbench(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector)
      .containsExactly(tab1.deviceId)

    Disposer.dispose(layoutInspectorManager)

    assertDoesNotHaveWorkbench(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testWorkbenchIsDisposedWhenLIIsDisabled() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    var isWorkbenchDisposed = false
    val workbench =
      tab1.container.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()
    Disposer.register(workbench) { isWorkbenchDisposed = true }
    var isRendererDisposed = false
    val renderer =
      tab1.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>().first()
    Disposer.register(renderer) { isRendererDisposed = true }

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    assertThat(isWorkbenchDisposed).isTrue()
    assertThat(isRendererDisposed).isTrue()
  }

  @Test
  @RunsInEdt
  fun testAssertStartStopForegroundProcessDetection() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(1)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(0)

    fakeToolWindowManager.setSelectedContent(tab2)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(1)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(1)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(2)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(1)

    fakeToolWindowManager.setSelectedContent(tab1)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(3)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(2)

    fakeToolWindowManager.removeContent(tab1)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(4)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(3)

    fakeToolWindowManager.removeContent(tab2)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(4)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(4)
  }

  private fun assertHasWorkbench(tabInfo: TabInfo) {
    assertThat(tabInfo.content.allParents().filterIsInstance<Splitter>()).hasSize(1)
    assertThat(tabInfo.container.allChildren().filterIsInstance<WorkBench<LayoutInspector>>())
      .hasSize(1)

    val splitter = tabInfo.content.allParents().filterIsInstance<Splitter>().first()

    assertThat(splitter.allChildren().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(1)

    val workbench = splitter.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()
    assertThat(workbench.isFocusCycleRoot).isFalse()

    val toolbars =
      tabInfo.container.allChildren().filterIsInstance<ActionToolbar>().filter {
        it.component.name == "LayoutInspector.MainToolbar"
      }

    assertThat(toolbars).hasSize(1)

    assertThat(toolbars.first().actions.filterIsInstance<SingleDeviceSelectProcessAction>())
      .hasSize(1)
    assertThat(toolbars.first().actions.filterIsInstance<ToggleDeepInspectAction>()).hasSize(1)
    assertThat(toolbars.first().actions.filterIsInstance<GearAction>()).hasSize(1)

    assertThat(
        tabInfo.container.allChildren().filter { it.name == "LayoutInspectorToolbarTitleLabel" }
      )
      .hasSize(1)

    // Assert the other gear actions from the side panels are not visible
    assertThat(
        workbench
          .allChildren()
          .filterIsInstance<JPanel>()
          .flatMap { it.allChildren() }
          .filterIsInstance<ActionToolbar>()
          .flatMap { it.component.allChildren() }
          .filterIsInstance<ActionButton>()
          .filter { it.presentation.text.contains("More Options") }
      )
      .isEmpty()

    // Assert the hide actions from the side panels are not visible
    assertThat(
        workbench
          .allChildren()
          .filterIsInstance<JPanel>()
          .flatMap { it.allChildren() }
          .filterIsInstance<ActionToolbar>()
          .flatMap { it.component.allChildren() }
          .filterIsInstance<ActionButton>()
          .filter { it.presentation.text.contains("Hide") }
      )
      .isEmpty()

    val inspectorBanner = tabInfo.container.allChildren().filterIsInstance<InspectorBanner>()

    assertThat(inspectorBanner).hasSize(1)

    assertThat(tabInfo.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>())
      .hasSize(1)
  }

  private fun assertDoesNotHaveWorkbench(tabInfo: TabInfo) {
    assertThat(tabInfo.content.allParents().filterIsInstance<Splitter>()).hasSize(0)
    assertThat(tabInfo.container.allChildren().filterIsInstance<WorkBench<LayoutInspector>>())
      .hasSize(0)
    assertThat(tabInfo.content.parent).isEqualTo(tabInfo.container)

    val toolbars =
      tabInfo.container.allChildren().filterIsInstance<ActionToolbar>().filter {
        it.component.name == "LayoutInspector.MainToolbar"
      }

    assertThat(toolbars).hasSize(0)

    val inspectorBanner = tabInfo.container.allChildren().filterIsInstance<InspectorBanner>()

    assertThat(inspectorBanner).hasSize(0)

    assertThat(tabInfo.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>())
      .hasSize(0)
  }

  private fun Component.allParents(): List<Container> {
    val parents = mutableListOf<Container>()
    var component = this
    while (component.parent != null) {
      parents.add(component.parent)
      component = component.parent
    }
    return parents
  }

  private fun Container.allChildren(): List<Component> {
    val children = mutableListOf<Component>()
    for (component in components) {
      children.add(component)
      if (component is Container) {
        children.addAll(component.allChildren())
      }
    }
    return children
  }
}
