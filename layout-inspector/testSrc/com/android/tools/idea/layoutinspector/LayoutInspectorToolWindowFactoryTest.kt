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

import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorConfigurable
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.sdk.AndroidProjectChecker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import java.util.concurrent.CountDownLatch
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private val MODERN_PROCESS = DEVICE_1.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

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

  private class FakeToolWindow(project: Project, private val listener: ToolWindowManagerListener) :
    ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
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
  private val appInspectionRule = AppInspectionInspectorRule(projectRule)
  private val layoutInspectorRule =
    LayoutInspectorRule(clientProviders = listOf(appInspectionRule.createInspectorClientProvider()), projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(appInspectionRule).around(layoutInspectorRule)!!

  @Before
  fun setUp() {
    layoutInspectorRule.attachDevice(DEVICE_1)
  }

  @Test
  fun isApplicableReturnsFalseWhenEnabledInRunningDevices() = withEmbeddedLayoutInspector {
    assertThat(LayoutInspectorToolWindowFactory().isApplicable(projectRule.project)).isFalse()

    enableEmbeddedLayoutInspector = false

    assertThat(LayoutInspectorToolWindowFactory().isApplicable(projectRule.project)).isTrue()
  }

  @Test
  fun launcherDisabledWhenToolWindowIsMinimized() {
    val listener = LayoutInspectorToolWindowManagerListener(layoutInspectorRule.launcher)
    val toolWindow = FakeToolWindow(layoutInspectorRule.project, listener)

    toolWindow.show()
    assertThat(toolWindow.visible).isTrue()

    toolWindow.hide()
    assertThat(toolWindow.visible).isFalse()
    assertThat(layoutInspectorRule.launcher.enabled).isFalse()

    toolWindow.show()
    assertThat(layoutInspectorRule.launcher.enabled).isTrue()
  }

  @Test
  fun testCollapseToolWindowShowsInspectionNotificationWhenInspectorIsRunning() {
    val listener = LayoutInspectorToolWindowManagerListener(layoutInspectorRule.launcher)

    val toolWindow = FakeToolWindow(layoutInspectorRule.project, listener)

    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isEmpty()

    toolWindow.show()

    // Attach process
    layoutInspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    layoutInspectorRule.processes.selectedProcess = MODERN_PROCESS
    waitForCondition(2.seconds) { layoutInspectorRule.launcher.activeClient.isConnected }

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
    val listener = LayoutInspectorToolWindowManagerListener(layoutInspectorRule.launcher)
    val toolWindow = FakeToolWindow(layoutInspectorRule.project, listener)

    toolWindow.show()

    // Attach process
    layoutInspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    layoutInspectorRule.processes.selectedProcess = MODERN_PROCESS
    waitForCondition(2.seconds) { layoutInspectorRule.launcher.activeClient.isConnected }

    toolWindow.hide()
    assertThat(layoutInspectorRule.inspectorClient.isConnected).isTrue()

    layoutInspectorRule.processNotifier.fireDisconnected(MODERN_PROCESS)
    assertThat(layoutInspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Layout Inspector" } ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass).isEqualTo(AndroidProjectChecker::class.qualifiedName)
  }

  @Test
  fun testRegisterLayoutInspectorToolWindow() {
    val coroutineScope = projectRule.testRootDisposable.createCoroutineScope()
    val fakeForegroundProcessDetection = FakeForegroundProcessDetection()

    val layoutInspector =
      LayoutInspector(
        coroutineScope = coroutineScope,
        processModel = mock(),
        deviceModel = mock(),
        foregroundProcessDetection = fakeForegroundProcessDetection,
        inspectorClientSettings = InspectorClientSettings(projectRule.project),
        launcher = mock(),
        layoutInspectorModel = mock(),
        notificationModel = mock(),
        treeSettings = mock(),
        executor = MoreExecutors.directExecutor(),
      )

    val stopInspectorLatch = CountDownLatch(1)
    layoutInspector.stopInspectorListeners.add { stopInspectorLatch.countDown() }

    val mockLayoutInspectorProjectService = mock<LayoutInspectorProjectService>()
    whenever(mockLayoutInspectorProjectService.getLayoutInspector()).thenAnswer { layoutInspector }
    projectRule.project.replaceService(
      LayoutInspectorProjectService::class.java,
      mockLayoutInspectorProjectService,
      projectRule.testRootDisposable,
    )

    val mockLayoutInspectorManager = mock<LayoutInspectorManager>()
    projectRule.project.replaceService(LayoutInspectorManager::class.java, mockLayoutInspectorManager, projectRule.testRootDisposable)

    // Verify that the tool window is null to begin with.
    val layoutInspectorToolWindow1 = ToolWindowManager.getInstance(projectRule.project).getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
    assertThat(layoutInspectorToolWindow1).isNull()

    registerLayoutInspectorToolWindow(projectRule.project)

    stopInspectorLatch.await()
    verify(mockLayoutInspectorManager).disable()

    // Verify that the tool window was added.
    val layoutInspectorToolWindow2 = ToolWindowManager.getInstance(projectRule.project).getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
    assertThat(layoutInspectorToolWindow2).isNotNull()

    unregisterLayoutInspectorToolWindow(projectRule.project)

    // Verify that the tool window has been removed.
    val layoutInspectorToolWindow3 = ToolWindowManager.getInstance(projectRule.project).getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
    assertThat(layoutInspectorToolWindow3).isNull()
  }

  @Test
  fun testEmbeddedLayoutInspectorBanner() {
    val originalService = ApplicationManager.getApplication().getService(ShowSettingsUtil::class.java)
    val mockService = mock<ShowSettingsUtil>()
    ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, mockService, projectRule.testRootDisposable)

    val notificationModel = NotificationModel(projectRule.project)

    val testScheduler = TestCoroutineScheduler()
    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val setShouldShowBannerInvocations = mutableListOf<Boolean>()
    var activateEmbeddedLiInvocationsCounter = 0
    showEmbeddedLayoutInspectorBanner(
      project = projectRule.project,
      notificationModel = notificationModel,
      scope = scope,
      shouldShowBanner = { true },
      setShouldShowBanner = { setShouldShowBannerInvocations.add(it) },
      activateEmbeddedLayoutInspector = { activateEmbeddedLiInvocationsCounter += 1 },
    )

    whenever(mockService.showSettingsDialog(eq(projectRule.project), eq(LayoutInspectorConfigurable::class.java))).then {
      // Wait for the coroutine to start listening to embeddedLayoutInspectorChanges
      testScheduler.advanceUntilIdle()
      // Simulate the user enabling the setting in the ui
      LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = true
    }

    val notification = notificationModel.notifications.first()
    assertThat(notification.id).isEqualTo(BANNER_STRING_ID)

    val doNotShowAgainAction = notification.actions.find { it.name == LayoutInspectorBundle.message("do.not.show.again") }
    doNotShowAgainAction!!.invoke(notification)

    assertThat(setShouldShowBannerInvocations).containsExactly(false)

    val enableAction = notification.actions.find { it.name == LayoutInspectorBundle.message("enable") }
    enableAction!!.invoke(notification)
    testScheduler.advanceUntilIdle()

    verify(mockService).showSettingsDialog(eq(projectRule.project), eq(LayoutInspectorConfigurable::class.java))
    waitForCondition(10.seconds) { activateEmbeddedLiInvocationsCounter == 1 }
    assertThat(activateEmbeddedLiInvocationsCounter).isEqualTo(1)

    // clean up by restoring the original service
    ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, originalService, projectRule.testRootDisposable)
  }

  @Test
  fun testEmbeddedLayoutInspectorBannerNotShown() {
    val notificationModel = NotificationModel(projectRule.project)

    showEmbeddedLayoutInspectorBanner(
      project = projectRule.project,
      notificationModel = notificationModel,
      scope = projectRule.testRootDisposable.createCoroutineScope(),
      shouldShowBanner = { false },
      setShouldShowBanner = {},
      activateEmbeddedLayoutInspector = {},
    )

    assertThat(notificationModel.notifications).isEmpty()
  }

  @Test
  fun testEmbeddedLayoutInspectorSwitchRemovesBanner() {
    val originalService = ApplicationManager.getApplication().getService(ShowSettingsUtil::class.java)
    val mockService = mock<ShowSettingsUtil>()
    ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, mockService, projectRule.testRootDisposable)

    // Ensure we start with embedded inspector disabled
    LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = false
    try {
      val notificationModel = NotificationModel(projectRule.project)

      val testScheduler = TestCoroutineScheduler()
      val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

      var activateEmbeddedLiInvocationsCounter = 0
      showEmbeddedLayoutInspectorBanner(
        project = projectRule.project,
        notificationModel = notificationModel,
        scope = scope,
        shouldShowBanner = { true },
        setShouldShowBanner = {},
        activateEmbeddedLayoutInspector = { activateEmbeddedLiInvocationsCounter += 1 },
      )

      whenever(mockService.showSettingsDialog(eq(projectRule.project), eq(LayoutInspectorConfigurable::class.java))).then {
        // Wait for the coroutine to start listening to embeddedLayoutInspectorChanges
        testScheduler.advanceUntilIdle()
        // Simulate the user enabling the setting in the ui
        LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = true
      }

      val notification = notificationModel.notifications.first()
      assertThat(notification.id).isEqualTo(BANNER_STRING_ID)

      val enableAction = notification.actions.find { it.name == LayoutInspectorBundle.message("enable") }
      enableAction!!.invoke(notification)
      testScheduler.advanceUntilIdle()

      verify(mockService).showSettingsDialog(eq(projectRule.project), eq(LayoutInspectorConfigurable::class.java))
      waitForCondition(10.seconds) { activateEmbeddedLiInvocationsCounter == 1 }

      // The notification should be removed after enabling
      assertThat(notificationModel.notifications).isEmpty()
    } finally {
      LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = false
      ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, originalService, projectRule.testRootDisposable)
    }
  }
}
