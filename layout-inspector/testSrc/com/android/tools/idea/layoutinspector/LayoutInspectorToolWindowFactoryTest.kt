/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.ide.ui.RecentProcess
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.tree.InspectorTreeSettings
import com.android.tools.idea.layoutinspector.ui.DeviceViewContentPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorDeviceViewSettings
import com.android.tools.idea.layoutinspector.util.ComponentUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.TransportService
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.DataManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit

private val MODERN_PROCESS = MODERN_DEVICE.createProcess()
private val LEGACY_PROCESS = LEGACY_DEVICE.createProcess()
private val OLDER_LEGACY_PROCESS = OLDER_LEGACY_DEVICE.createProcess()

class LayoutInspectorToolWindowFactoryTest {

  private class FakeToolWindowManager(project: Project, private val toolWindow: ToolWindow) : ToolWindowHeadlessManagerImpl(project) {
    var notificationText = ""

    override fun getToolWindow(id: String?): ToolWindow {
      return toolWindow
    }

    override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
      notificationText = options.htmlBody
    }
  }

  private class FakeToolWindow(
    project: Project,
    private val listener: LayoutInspectorToolWindowManagerListener
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    var shouldBeAvailable = true
    var visible = false
    val manager = FakeToolWindowManager(project, this)

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      shouldBeAvailable = available
    }

    override fun isAvailable() = shouldBeAvailable

    override fun show(runnable: Runnable?) {
      visible = true
      listener.stateChanged(manager)
    }

    override fun hide(runnable: Runnable?) {
      visible = false
      listener.stateChanged(manager)
    }

    override fun isVisible(): Boolean {
      return visible
    }
  }

  private val disposableRule = DisposableRule()

  private val inspectorRule = LayoutInspectorRule(LegacyClientProvider(disposableRule.disposable), projectRule = AndroidProjectRule.inMemory().initAndroid(false)) {
    it.name == LEGACY_PROCESS.name
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectorRule).around(disposableRule)!!


  @Test
  fun clientOnlyLaunchedIfWindowIsNotMinimized() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.launcher)
    val toolWindow = FakeToolWindow(inspectorRule.project, listener)

    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.visible).isFalse()
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()

    toolWindow.show()
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun testShowInspectionNotificationWhenInspectorIsRunning() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.launcher)

    val toolWindow = FakeToolWindow(inspectorRule.project, listener)

    // bubble isn't shown when inspection not running
    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isEmpty()

    // Attach to a fake process.
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)

    // Check bubble is shown.
    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isNotEmpty()

    // Message is shown each time.
    toolWindow.manager.notificationText = ""
    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isNotEmpty()
  }

  @Test
  fun clientCanBeDisconnectedWhileMinimized() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.launcher)
    val toolWindow = FakeToolWindow(inspectorRule.project, listener)

    toolWindow.show()
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    toolWindow.hide()
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    inspectorRule.processNotifier.fireDisconnected(LEGACY_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun testCreateProcessesModel() {
    val factory = LayoutInspectorToolWindowFactory()
    val model = factory.createProcessesModel(inspectorRule.project, inspectorRule.processNotifier, MoreExecutors.directExecutor())
    // Verify that devices older than M will be included in the processes model:
    inspectorRule.processNotifier.fireConnected(OLDER_LEGACY_PROCESS)
    assertThat(model.processes).hasSize(1)
    // An M device as well:
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)
    assertThat(model.processes).hasSize(2)
    // And newer devices as well:
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(model.processes).hasSize(3)
  }
}

class LayoutInspectorToolWindowFactorySettingsTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val adbRule = FakeAdbRule()

  @Test
  fun toolWindowFactoryCreatesCorrectSettings() {
    ApplicationManager.getApplication().replaceService(TransportService::class.java, mock(), disposableRule.disposable)
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project)
    LayoutInspectorToolWindowFactory().createToolWindowContent(projectRule.project, toolWindow)
    val component = toolWindow.contentManager.selectedContent?.component!!
    waitForCondition(5L, TimeUnit.SECONDS) {
      ComponentUtil.flatten(component).firstIsInstanceOrNull<DeviceViewPanel>() != null
    }
    val inspector = DataManager.getDataProvider(ComponentUtil.flatten(component).firstIsInstance<WorkBench<*>>())?.getData(
      LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector
    assertThat(inspector.treeSettings).isInstanceOf(InspectorTreeSettings::class.java)
    val contentPanel = ComponentUtil.flatten(component).firstIsInstance<DeviceViewContentPanel>()
    assertThat(contentPanel.viewSettings).isInstanceOf(InspectorDeviceViewSettings::class.java)
  }
}

class LayoutInspectorToolWindowFactoryDisposeTest {

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val adbRule = FakeAdbRule()

  @Test
  fun testResetSelectedProcessAfterProjectIsClosed() {
    val device = MODERN_DEVICE
    adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString(),
                         DeviceState.HostConnectionType.USB)
    ApplicationManager.getApplication().replaceService(AppInspectionDiscoveryService::class.java, mock(), disposableRule.disposable)
    val service = AppInspectionDiscoveryService.instance
    val discovery = TestProcessDiscovery()
    `when`(service.apiServices).thenReturn(mock())
    `when`(service.apiServices.processDiscovery).thenReturn(discovery)

    // In this test we want to close the project BEFORE the tear down of this test method.
    // Existing project rules do not allow this since they assume the project is closed in the tear down.
    // Create and close the project explicitly instead:
    val project = createProject()
    try {
      val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
      LayoutInspectorToolWindowFactory().createToolWindowContent(project, toolWindow)
      val component = toolWindow.contentManager.selectedContent?.component!!
      waitForCondition(25L, TimeUnit.SECONDS) {
        ComponentUtil.flatten(component).firstIsInstanceOrNull<DeviceViewPanel>() != null
      }
      val deviceViewPanel = ComponentUtil.flatten(component).firstIsInstance<DeviceViewPanel>()
      val processes = deviceViewPanel.processes!!
      RecentProcess.set(project, RecentProcess(adbRule.bridge.devices.first(), MODERN_PROCESS.name))
      discovery.fireConnected(MODERN_PROCESS)

      // In this test we want to close the project BEFORE the tear down of this test method.
      // Existing project rules do not allow this since they assume the project is closed in the tear down.
      // Create and close the project explicitly instead:
      runInEdtAndWait {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }

      closeProject(project)

      // This should not cause already disposed errors:
      processes.selectedProcess = null
    }
    finally {
      if (project.isOpen) {
        closeProject(project)
      }
    }
  }

  private fun createProject(): ProjectEx {
    val projectFile = TemporaryDirectory.generateTemporaryPath("project_dispose_project${ProjectFileType.DOT_DEFAULT_EXTENSION}")
    val options = createTestOpenProjectOptions(runPostStartUpActivities = false).copy(preloadServices = false)
    return (ProjectManager.getInstance() as TestProjectManager).openProject(projectFile, options) as ProjectEx
  }

  private fun closeProject(project: Project) {
    runInEdtAndWait {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      Disposer.dispose(project)
    }
  }
}
