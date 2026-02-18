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
import com.android.tools.idea.layoutinspector.DEVICE_1
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.resource.data.Display
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.EmbeddedRendererPanel
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.OnDeviceRendererPanel
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.testing.ui.FakeToolWindow
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
  private lateinit var xrTab: TabInfo

  private lateinit var fakeToolWindow: FakeToolWindow
  private lateinit var fakeForegroundProcessDetection: FakeForegroundProcessDetection
  private lateinit var layoutInspectorManager: LayoutInspectorManager

  @Before
  fun setUp() {
    tab1 =
      TabInfo(
        deviceId = DeviceId.ofPhysicalDevice("tab1"),
        content = BorderLayoutPanel(),
        container = JPanel(),
        displays =
          listOf(
            displayViewRule.newEmulatorDisplayView(displayId = Display.MAIN_DISPLAY_ID),
            displayViewRule.newEmulatorDisplayView(displayId = 1),
          ),
      )
    tab2 =
      TabInfo(
        deviceId = DeviceId.ofPhysicalDevice("tab2"),
        content = BorderLayoutPanel(),
        container = JPanel(),
        displays =
          listOf(
            displayViewRule.newEmulatorDisplayView(displayId = Display.MAIN_DISPLAY_ID),
            displayViewRule.newEmulatorDisplayView(displayId = 1),
          ),
      )
    xrTab =
      TabInfo(
        deviceId = DeviceId.ofPhysicalDevice("tab3"),
        content = BorderLayoutPanel(),
        container = JPanel(),
        displays = listOf(displayViewRule.newEmulatorDisplayView(avdCreator = { path -> FakeEmulator.createXrHeadsetAvd(path) })),
      )
    fakeToolWindow = createFakeToolWindow(displayViewRule.project, displayViewRule.disposable, RUNNING_DEVICES_TOOL_WINDOW_ID)
    addContent(fakeToolWindow, tab1)
    addContent(fakeToolWindow, tab2)
    addContent(fakeToolWindow, xrTab)

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
        layoutInspectorModel = model(displayViewRule.disposable) { view(ROOT, Rectangle(0, 0, 100, 100)) {} },
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings(),
      )

    whenever(mockLayoutInspectorProjectService.getLayoutInspector()).thenAnswer { layoutInspector }
    displayViewRule.project.replaceService(
      LayoutInspectorProjectService::class.java,
      mockLayoutInspectorProjectService,
      displayViewRule.disposable,
    )

    fakeToolWindow.show()

    withEmbeddedLayoutInspector { layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project) }
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnOff() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    enableLayoutInspector(tab1, false)

    verifyUiRemoved(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnOffXr() = withEmbeddedLayoutInspector {
    enableLayoutInspector(xrTab, true)

    verifyUiInjected<OnDeviceRendererPanel>(xrTab)

    enableLayoutInspector(xrTab, false)

    verifyUiRemoved(xrTab)
  }

  @Test
  @RunsInEdt
  fun testHideToolWindowRemovesUi() = withEmbeddedLayoutInspector {
    addContent(fakeToolWindow, tab1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    setSelectedContent(fakeToolWindow, tab1)

    fakeToolWindow.show()
    waitForCondition(2, TimeUnit.SECONDS) { fakeToolWindow.isVisible }

    verifyUiRemoved(tab1)

    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    fakeToolWindow.hide()
    waitForCondition(2, TimeUnit.SECONDS) { !fakeToolWindow.isVisible }

    // Make sure that the UI is removed when the tool window is hidden.
    verifyUiRemoved(tab1)

    fakeToolWindow.show()
    waitForCondition(2, TimeUnit.SECONDS) { fakeToolWindow.isVisible }

    // The UI should be re-inject from scratch when the tool window is visible again.
    verifyUiInjected<EmbeddedRendererPanel>(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTimesForSameTab() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    enableLayoutInspector(tab1, false)

    verifyUiRemoved(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOffMultipleTimesForSameTab() = withEmbeddedLayoutInspector {
    setSelectedContent(fakeToolWindow, tab1)
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    enableLayoutInspector(tab1, false)
    enableLayoutInspector(tab1, false)

    verifyUiRemoved(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTabs() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    enableLayoutInspector(tab2, true)

    verifyUiRemoved(tab1)
    verifyUiInjected<EmbeddedRendererPanel>(tab2)

    layoutInspectorManager.enableLayoutInspector(tab1.deviceId, false)

    verifyUiRemoved(tab1)
    verifyUiInjected<EmbeddedRendererPanel>(tab2)

    enableLayoutInspector(tab2, false)

    verifyUiRemoved(tab2)
  }

  @Test
  @RunsInEdt
  fun testSelectedTabDoesNotChange() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    // adding a new tab that doesn't have Layout Inspector enabled
    addContent(fakeToolWindow, tab2)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)
    verifyUiRemoved(tab2)
  }

  @Test
  @RunsInEdt
  fun testWorkbenchIsInjectedWhenSelectedTabChanges() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    enableLayoutInspector(tab2, true)

    verifyUiRemoved(tab1)
    verifyUiInjected<EmbeddedRendererPanel>(tab2)

    setSelectedContent(fakeToolWindow, tab1)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)
    verifyUiRemoved(tab2)
  }

  @Test
  @RunsInEdt
  fun testSelectedTabIsRemoved() = withEmbeddedLayoutInspector {
    setSelectedContent(fakeToolWindow, tab1)
    enableLayoutInspector(tab1, true)

    // Displays are added asynchronously. Wait for them to be added.
    tab1.displays.forEach { display ->
      waitForCondition(2.seconds) { display.component.allChildren().filterIsInstance<LayoutInspectorRenderer>().isNotEmpty() }
    }

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(6)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    setSelectedContent(fakeToolWindow, tab2)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(1)

    enableLayoutInspector(tab2, true)

    // Displays are added asynchronously. Wait for them to be added.
    tab2.displays.forEach { display ->
      waitForCondition(2.seconds) { display.component.allChildren().filterIsInstance<LayoutInspectorRenderer>().isNotEmpty() }
    }

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(6)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    verifyUiRemoved(tab1)
    verifyUiInjected<EmbeddedRendererPanel>(tab2)

    setSelectedContent(fakeToolWindow, tab1)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(6)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)
    verifyUiRemoved(tab2)

    removeContent(fakeToolWindow, tab1)

    verifyUiRemoved(tab1)
    assertThat(layoutInspector.deviceModel?.selectedDevice).isNull()

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(6)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(3)

    verifyUiInjected<EmbeddedRendererPanel>(tab2)

    removeContent(fakeToolWindow, tab2)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.processModel?.selectedProcessListeners).hasSize(1)
  }

  @Test
  @RunsInEdt
  fun testDeepInspectIsDisabledOnProcessChange() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    val toolbar =
      tab1.container.allChildren().filterIsInstance<ActionToolbar>().first { it.component.name == "LayoutInspector.MainToolbar" }
    FakeUi(toolbar.component, createFakeWindow = true, parentDisposable = displayViewRule.disposable)

    waitForCondition(2.seconds) { toolbar.actions.filterIsInstance<ToggleDeepInspectAction>().any() }
    val toggleDeepInspectAction = toolbar.actions.filterIsInstance<ToggleDeepInspectAction>().first()
    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction))).isFalse()

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))
    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction))).isTrue()

    tab1.displays.forEach { display ->
      val renderer = display.component.allChildren().filterIsInstance<EmbeddedRendererPanel>().first()
      waitForCondition(2.seconds) { renderer.interceptClicks }
    }

    layoutInspector.processModel?.selectedProcess = DEVICE_1.createProcess()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction))).isFalse()

    tab1.displays.forEach { display ->
      val renderer = display.component.allChildren().filterIsInstance<EmbeddedRendererPanel>().first()
      waitForCondition(2.seconds) { !renderer.interceptClicks }
    }
  }

  @Test
  @RunsInEdt
  fun testEnableLiveUpdatesOnProcessChange() = withEmbeddedLayoutInspector {
    layoutInspector.inspectorClientSettings.inLiveMode = false
    assertThat(layoutInspector.inspectorClientSettings.inLiveMode).isFalse()

    enableLayoutInspector(tab1, true)

    assertThat(layoutInspector.inspectorClientSettings.inLiveMode).isFalse()

    layoutInspector.processModel?.selectedProcess = DEVICE_1.createProcess()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(layoutInspector.inspectorClientSettings.inLiveMode).isTrue()
  }

  @Test
  @RunsInEdt
  fun testDeepInspectEnablesClickIntercept() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    val toolbar =
      tab1.container.allChildren().filterIsInstance<ActionToolbar>().first { it.component.name == "LayoutInspector.MainToolbar" }
    FakeUi(toolbar.component, createFakeWindow = true, parentDisposable = displayViewRule.disposable)

    waitForCondition(10.seconds) { toolbar.actions.filterIsInstance<ToggleDeepInspectAction>().any() }
    val toggleDeepInspectAction = toolbar.actions.filterIsInstance<ToggleDeepInspectAction>().first()
    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction))).isFalse()
    tab1.displays.forEach { display ->
      val renderer = display.component.allChildren().filterIsInstance<EmbeddedRendererPanel>().first()
      assertThat(renderer.interceptClicks).isFalse()
    }

    toggleDeepInspectAction.actionPerformed(createTestActionEvent(toggleDeepInspectAction))

    assertThat(toggleDeepInspectAction.isSelected(createTestActionEvent(toggleDeepInspectAction))).isTrue()
    tab1.displays.forEach { display ->
      val renderer = display.component.allChildren().filterIsInstance<EmbeddedRendererPanel>().first()
      waitForCondition(2.seconds) { renderer.interceptClicks }
    }

    enableLayoutInspector(tab1, false)
  }

  @Test
  @RunsInEdt
  fun testGlobalStateIsUpdated() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).containsExactly(tab1.deviceId)

    enableLayoutInspector(tab1, false)

    verifyUiRemoved(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testGlobalStateIsUpdatedOnDispose() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).containsExactly(tab1.deviceId)

    Disposer.dispose(layoutInspectorManager)

    verifyUiRemoved(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testWorkbenchIsDisposedWhenLIIsDisabled() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)

    var isWorkbenchDisposed = false
    val workbench = tab1.container.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()
    Disposer.register(workbench) { isWorkbenchDisposed = true }

    val isRendererDisposed = mutableListOf<Boolean>()

    tab1.displays.forEach { display ->
      // Displays are added asynchronously. Wait for them to be added.
      waitForCondition(2.seconds) { display.component.allChildren().filterIsInstance<LayoutInspectorRenderer>().isNotEmpty() }

      val renderer = display.component.allChildren().filterIsInstance<LayoutInspectorRenderer>().first()
      Disposer.register(renderer) { isRendererDisposed.add(true) }
    }

    enableLayoutInspector(tab1, false)

    assertThat(isWorkbenchDisposed).isTrue()
    assertThat(isRendererDisposed.size).isEqualTo(tab1.displays.size)
    assertThat(isRendererDisposed.all { it }).isTrue()
  }

  @Test
  @RunsInEdt
  fun testAssertStartStopForegroundProcessDetection() = withEmbeddedLayoutInspector {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    enableLayoutInspector(tab1, true)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(1)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(0)

    setSelectedContent(fakeToolWindow, tab2)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(1)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(1)

    enableLayoutInspector(tab2, true)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(2)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(1)

    setSelectedContent(fakeToolWindow, tab1)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(3)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(2)

    removeContent(fakeToolWindow, tab1)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(4)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(3)

    removeContent(fakeToolWindow, tab2)

    assertThat(fakeForegroundProcessDetection.startInvokeCounter).isEqualTo(4)
    assertThat(fakeForegroundProcessDetection.stopInvokeCounter).isEqualTo(4)
  }

  @Test
  @RunsInEdt
  fun testEnableLiAcrossTabsFromMultipleContentManagers() = withEmbeddedLayoutInspector {
    val secondContentManager = FakeContentManager()
    Disposer.register(displayViewRule.disposable, secondContentManager)

    addContent(fakeToolWindow, tab1)

    val fakeComponent = FakeRunningDevicesComponent(tab2)
    val fakeContent = FakeContent(displayViewRule.disposable, secondContentManager, fakeComponent)
    secondContentManager.addContent(fakeContent)
    secondContentManager.setSelectedContent(fakeContent)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    setSelectedContent(fakeToolWindow, tab1)

    enableLayoutInspector(tab1, true)

    verifyUiInjected<EmbeddedRendererPanel>(tab1)

    enableLayoutInspector(tab2, true)

    verifyUiRemoved(tab1)
    verifyUiInjected<EmbeddedRendererPanel>(tab2)
  }

  @Test
  @RunsInEdt
  fun testDisable() = withEmbeddedLayoutInspector {
    enableLayoutInspector(tab1, true)
    verifyUiInjected<EmbeddedRendererPanel>(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).containsExactly(tab1.deviceId)

    layoutInspectorManager.disable()

    verifyUiRemoved(tab1)
    assertThat(LayoutInspectorManagerGlobalState.tabsWithLayoutInspector).isEmpty()
  }

  private fun enableLayoutInspector(tabInfo: TabInfo, enable: Boolean) {
    setSelectedContent(fakeToolWindow, tabInfo)
    layoutInspectorManager.enableLayoutInspector(tabInfo.deviceId, enable)
  }
}

private inline fun <reified T : LayoutInspectorRenderer> verifyUiInjected(tabInfo: TabInfo) {
  verifyUiInjected<T>(UiConfig.HORIZONTAL, tabInfo.content, tabInfo.container, tabInfo.displays)
}

private fun verifyUiRemoved(tabInfo: TabInfo) {
  verifyUiRemoved(tabInfo.content, tabInfo.container, tabInfo.displays)
}
