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

import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.swing.FakeUi
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
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.RootPanelRenderer
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
        BorderLayoutPanel(),
        JPanel(),
        spy(displayViewRule.newEmulatorView()),
      )
    tab2 =
      TabInfo(
        DeviceId.ofPhysicalDevice("tab2"),
        BorderLayoutPanel(),
        JPanel(),
        spy(displayViewRule.newEmulatorView()),
      )
    fakeToolWindowManager = FakeToolWindowManager(displayViewRule.project, listOf(tab1, tab2))

    // replace ToolWindowManager with fake one
    displayViewRule.project.replaceService(
      ToolWindowManager::class.java,
      fakeToolWindowManager,
      displayViewRule.disposable,
    )
    // Initiate state observer singleton.
    RunningDevicesStateObserver.getInstance(displayViewRule.project)

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
        metrics = mock(),
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
        layoutInspectorModel =
          model(displayViewRule.disposable) { view(ROOT, Rectangle(0, 0, 100, 100)) {} },
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings(),
      )

    whenever(mockLayoutInspectorProjectService.getLayoutInspector()).thenAnswer { layoutInspector }
    displayViewRule.project.replaceService(
      LayoutInspectorProjectService::class.java,
      mockLayoutInspectorProjectService,
      displayViewRule.disposable,
    )

    fakeToolWindowManager.toolWindow.show()
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnOff() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    verifyUiRemoved(tab1)
  }

  @Test
  @RunsInEdt
  fun testHideToolWindowRemovesUi() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.addContent(tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    fakeToolWindowManager.setSelectedContent(tab1)

    fakeToolWindowManager.toolWindow.show()
    waitForCondition(2, TimeUnit.SECONDS) { fakeToolWindowManager.toolWindow.isVisible }

    verifyUiRemoved(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)

    fakeToolWindowManager.toolWindow.hide()
    waitForCondition(2, TimeUnit.SECONDS) { !fakeToolWindowManager.toolWindow.isVisible }

    // Make sure that the UI is removed when the tool window is hidden.
    verifyUiRemoved(tab1)

    fakeToolWindowManager.toolWindow.show()
    waitForCondition(2, TimeUnit.SECONDS) { fakeToolWindowManager.toolWindow.isVisible }

    // The UI should be re-inject from scratch when the tool window is visible again.
    verifyUiInjected(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTimesForSameTab() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    verifyUiRemoved(tab1)
  }

  @Test
  @RunsInEdt
  @Ignore("b/401493980")
  fun testToggleLayoutInspectorOffMultipleTimesForSameTab() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    verifyUiRemoved(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTabs() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    verifyUiRemoved(tab1)
    verifyUiInjected(tab2)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    verifyUiRemoved(tab1)
    verifyUiInjected(tab2)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, false)

    verifyUiRemoved(tab2)
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

    verifyUiInjected(tab1)

    // adding a new tab that doesn't have Layout Inspector enabled
    fakeToolWindowManager.addContent(tab2)

    verifyUiInjected(tab1)
    verifyUiRemoved(tab2)
  }

  @Test
  @RunsInEdt
  fun testWorkbenchIsInjectedWhenSelectedTabChanges() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    fakeToolWindowManager.setSelectedContent(tab2)
    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    verifyUiRemoved(tab1)
    verifyUiInjected(tab2)

    fakeToolWindowManager.setSelectedContent(tab1)

    verifyUiInjected(tab1)
    verifyUiRemoved(tab2)
  }

  @Test
  @RunsInEdt
  fun testSelectedTabIsRemoved() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(4)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    fakeToolWindowManager.setSelectedContent(tab2)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(1)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(4)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    verifyUiRemoved(tab1)
    verifyUiInjected(tab2)

    fakeToolWindowManager.setSelectedContent(tab1)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(4)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    verifyUiInjected(tab1)
    verifyUiRemoved(tab2)

    fakeToolWindowManager.removeContent(tab1)

    verifyUiRemoved(tab1)
    assertThat(layoutInspector.deviceModel?.selectedDevice).isNull()

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(4)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    verifyUiInjected(tab2)

    fakeToolWindowManager.removeContent(tab2)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(1)
  }

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

    layoutInspector.inspectorClientSettings.inLiveMode = false
    assertThat(layoutInspector.inspectorClientSettings.inLiveMode).isFalse()

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    assertThat(layoutInspector.inspectorClientSettings.inLiveMode).isFalse()

    layoutInspector.processModel?.selectedProcess = MODERN_DEVICE.createProcess()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(layoutInspector.inspectorClientSettings.inLiveMode).isTrue()
  }

  @Test
  @RunsInEdt
  fun testDeepInspectEnablesClickIntercept() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    val layoutInspectorRenderer =
      tab1.displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>().first()

    val toolbar =
      tab1.container.allChildren().filterIsInstance<ActionToolbar>().first {
        it.component.name == "LayoutInspector.MainToolbar"
      }
    FakeUi(
      toolbar.component,
      createFakeWindow = true,
      parentDisposable = displayViewRule.disposable,
    )

    waitForCondition(10.seconds) {
      toolbar.actions.filterIsInstance<ToggleDeepInspectAction>().any()
    }
    val toggleDeepInspectAction =
      toolbar.actions.filterIsInstance<ToggleDeepInspectAction>().first()
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

    verifyUiInjected(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector)
      .containsExactly(tab1.deviceId)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    verifyUiRemoved(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testGlobalStateIsUpdatedOnDispose() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector)
      .containsExactly(tab1.deviceId)

    Disposer.dispose(layoutInspectorManager)

    verifyUiRemoved(tab1)
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

  @Test
  @RunsInEdt
  fun testEnableLiAcrossTabsFromMultipleContentManagers() {
    val secondContentManager = FakeContentManager()
    Disposer.register(displayViewRule.disposable, secondContentManager)

    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.addContent(tab1)

    val fakeComponent = FakeRunningDevicesComponent(tab2)
    val fakeContent = FakeContent(displayViewRule.disposable, secondContentManager, fakeComponent)
    secondContentManager.addContent(fakeContent)
    secondContentManager.setSelectedContent(fakeContent)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    fakeToolWindowManager.setSelectedContent(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)

    verifyUiInjected(tab1)

    layoutInspectorManager.enableLayoutInspector(tab2.deviceId, true)

    verifyUiRemoved(tab1)
    verifyUiInjected(tab2)
  }

  @Test
  @RunsInEdt
  fun testDisable() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, true)
    verifyUiInjected(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector)
      .containsExactly(tab1.deviceId)

    layoutInspectorManager.disable()

    verifyUiRemoved(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }
}

private fun verifyUiInjected(tabInfo: TabInfo) {
  verifyUiInjected<RootPanelRenderer>(
    UiConfig.HORIZONTAL,
    tabInfo.content,
    tabInfo.container,
    tabInfo.displayView,
  )
}

private fun verifyUiRemoved(tabInfo: TabInfo) {
  verifyUiRemoved(tabInfo.content, tabInfo.container, tabInfo.displayView)
}
