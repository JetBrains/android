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
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.ide.ui.RecentProcess
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.tree.InspectorTreeSettings
import com.android.tools.idea.layoutinspector.ui.DeviceViewContentPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.InspectorRenderSettings
import com.android.tools.idea.layoutinspector.util.ComponentUtil
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
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
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.anyString
import java.io.ByteArrayOutputStream
import java.io.PrintStream
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
    private val listener: ToolWindowManagerListener
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

  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)
  private val inspectionRule = AppInspectionInspectorRule(projectRule)
  private val inspectorRule = LayoutInspectorRule(listOf(LegacyClientProvider({ projectRule.testRootDisposable })), projectRule) {
    it.name == LEGACY_PROCESS.name
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectionRule).around(inspectorRule)!!

  @Before
  fun setUp() {
    val devices = listOf(MODERN_DEVICE, OLDER_LEGACY_DEVICE, LEGACY_DEVICE)
    devices.forEach { device ->
      inspectorRule.adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString())
    }
  }

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
    inspectorRule.attachDevice(LEGACY_DEVICE)
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

  @Test
  fun toolWindowFactoryCreatesCorrectSettings() {
    ApplicationManager.getApplication().replaceService(TransportService::class.java, mock(), projectRule.testRootDisposable)
    projectRule.replaceService(AppInspectionDiscoveryService::class.java, mock())
    whenever(AppInspectionDiscoveryService.instance.apiServices).thenReturn(inspectionRule.inspectionService.apiServices)
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(inspectorRule.project)
    LayoutInspectorToolWindowFactory().createToolWindowContent(inspectorRule.project, toolWindow)
    val component = toolWindow.contentManager.selectedContent?.component!!
    waitForCondition(5L, TimeUnit.SECONDS) {
      ComponentUtil.flatten(component).firstIsInstanceOrNull<DeviceViewPanel>() != null
    }
    val inspector = DataManager.getDataProvider(ComponentUtil.flatten(component).firstIsInstance<WorkBench<*>>())?.getData(
      LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector
    assertThat(inspector.treeSettings).isInstanceOf(InspectorTreeSettings::class.java)
    val contentPanel = ComponentUtil.flatten(component).firstIsInstance<DeviceViewContentPanel>()
    assertThat(contentPanel.renderSettings).isInstanceOf(InspectorRenderSettings::class.java)
  }
}

@Ignore("b/205981893")
class LayoutInspectorToolWindowFactoryDisposeTest {

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val adbRule = FakeAdbRule()

  @Test
  fun testResetSelectedProcessAfterProjectIsClosed() = runBlocking {
    val device = MODERN_DEVICE
    adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString())
    ApplicationManager.getApplication().replaceService(AppInspectionDiscoveryService::class.java, mock(), disposableRule.disposable)
    val service = AppInspectionDiscoveryService.instance
    val discovery = TestProcessDiscovery()
    val apiServices: AppInspectionApiServices = mock()
    val target: AppInspectionTarget = mock()
    whenever(service.apiServices).thenReturn(apiServices)
    whenever(service.apiServices.processDiscovery).thenReturn(discovery)
    whenever(apiServices.attachToProcess(eq(MODERN_PROCESS), anyString())).thenReturn(target)
    whenever(apiServices.launchInspector(any())).thenReturn(mock())
    whenever(target.getLibraryVersions(any())).thenReturn(emptyList())

    // In this test we want to close the project BEFORE the tear down of this test method.
    // Existing project rules do not allow this since they assume the project is closed in the tear down.
    // Create and close the project explicitly instead:
    val project = createProject()
    val defaultError = System.err
    try {
      val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
      LayoutInspectorToolWindowFactory().createToolWindowContent(project, toolWindow)
      val component = toolWindow.contentManager.selectedContent?.component!!
      waitForCondition(25L, TimeUnit.SECONDS) {
        ComponentUtil.flatten(component).firstIsInstanceOrNull<DeviceViewPanel>() != null
      }
      val deviceViewPanel = ComponentUtil.flatten(component).firstIsInstance<DeviceViewPanel>()
      val deviceViewContentPanel = ComponentUtil.flatten(deviceViewPanel).firstIsInstance<DeviceViewContentPanel>()
      val processes = deviceViewPanel.processesModel!!
      RecentProcess.set(project, RecentProcess(adbRule.bridge.devices.first(), MODERN_PROCESS.name))

      val modelUpdatedLatch = ReportingCountDownLatch(1)
      deviceViewContentPanel.inspectorModel.modificationListeners.add { _, _, _ ->  modelUpdatedLatch.countDown() }
      discovery.fireConnected(MODERN_PROCESS)
      modelUpdatedLatch.await(1L, TimeUnit.SECONDS)

      // In this test we want to close the project BEFORE the tear down of this test method.
      // Existing project rules do not allow this since they assume the project is closed in the tear down.
      // Create and close the project explicitly instead:
      runInEdtAndWait {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }

      // Collect standard error output to look for AlreadyDisposedExceptions
      val bytes = ByteArrayOutputStream()
      System.setErr(PrintStream(bytes))

      closeProject(project)

      // This should not cause already disposed errors:
      processes.selectedProcess = null

      // The already disposed errors happens on various worker threads. Wait a tiny bit...
      Thread.sleep(20)
      val errors = bytes.toString()
      assertThat(errors).named(errors).doesNotContain("AlreadyDisposedException")
    }
    finally {
      System.setErr(defaultError)
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
