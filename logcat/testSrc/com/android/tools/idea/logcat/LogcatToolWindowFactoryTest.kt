/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.testing.FakeProcessNameMonitor
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.devices.DeviceComboBoxDeviceTrackerFactory
import com.android.tools.idea.logcat.devices.DeviceFinder
import com.android.tools.idea.logcat.devices.FakeDeviceComboBoxDeviceTracker
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.ShowLogcatListener.DeviceInfo.PhysicalDeviceInfo
import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl.MockToolWindow
import com.intellij.util.io.delete
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LogcatToolWindowFactoryTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  private val deviceTracker = FakeDeviceComboBoxDeviceTracker()
  private val fakeLogcatService = FakeLogcatService()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ProjectServiceRule(
        projectRule,
        DeviceComboBoxDeviceTrackerFactory::class.java,
        DeviceComboBoxDeviceTrackerFactory { deviceTracker },
      ),
      ProjectServiceRule(projectRule, LogcatService::class.java, fakeLogcatService),
      EdtRule(),
      disposableRule,
    )

  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  @Before
  fun setUp() {
    project.replaceService(LogcatService::class.java, this.fakeLogcatService, disposable)
  }

  @Test
  fun isApplicable() {
    assertThat(logcatToolWindowFactory().isApplicable(project)).isTrue()
  }

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Logcat" }
        ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidEnvironmentChecker::class.qualifiedName)
  }

  @Test
  fun generateTabName_noPreExistingNames() {
    assertThat(logcatToolWindowFactory().generateTabName(emptySet())).isEqualTo("Logcat")
  }

  @Test
  fun generateTabName_defaultNameAlreadyUsed() {
    assertThat(logcatToolWindowFactory().generateTabName(setOf("Logcat"))).isEqualTo("Logcat (2)")
  }

  @Test
  fun createChildComponent_isLogcatMainPanel() {
    val childComponent =
      logcatToolWindowFactory()
        .createChildComponent(project, ActionGroup.EMPTY_GROUP, clientState = null)

    assertThat(childComponent).isInstanceOf(LogcatMainPanel::class.java)
    Disposer.dispose(childComponent as Disposable)
  }

  @Test
  fun createChildComponent_parsesState() {
    val logcatPanelConfig =
      LogcatPanelConfig(
        device = null,
        file = null,
        formattingConfig = FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(15))),
        "filter",
        filterMatchCase = true,
        isSoftWrap = false,
      )

    val logcatMainPanel =
      logcatToolWindowFactory()
        .createChildComponent(
          project,
          ActionGroup.EMPTY_GROUP,
          clientState = LogcatPanelConfig.toJson(logcatPanelConfig),
        )

    // It's enough to assert on just one field in the config. We test more thoroughly in
    // LogcatMainPanelTest
    assertThat(logcatMainPanel.formattingOptions)
      .isEqualTo(logcatPanelConfig.formattingConfig.toFormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun createChildComponent_invalidState() {
    val logcatMainPanel =
      logcatToolWindowFactory()
        .createChildComponent(project, ActionGroup.EMPTY_GROUP, clientState = "invalid state")

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun startsProcessNameMonitor() {
    val mockProcessNameMonitor = mock<ProcessNameMonitor>()
    val latch = CountDownLatch(1)
    var threadName: String? = null
    whenever(mockProcessNameMonitor.start()).then {
      threadName = Thread.currentThread().name
      latch.countDown()
    }

    logcatToolWindowFactory(mockProcessNameMonitor).init(MockToolWindow(project))

    latch.await(2, TimeUnit.SECONDS)
    assertThat(threadName).startsWith("ApplicationImpl pooled thread")
  }

  @Test
  fun showLogcat_opensLogcatPanel() {
    val toolWindow = MockToolWindow(project)
    logcatToolWindowFactory().init(toolWindow)
    val device = Device.createPhysical("device1", true, "11", 30, "Google", "Pixel")
    project.replaceService(DeviceFinder::class.java, DeviceFinder { device }, disposable)
    deviceTracker.addDevices(device)

    project.messageBus
      .syncPublisher(ShowLogcatListener.TOPIC)
      .showLogcat(PhysicalDeviceInfo("device1", "11", 30, 30, "Google", "Pixel"), "com.test")

    waitForCondition { toolWindow.contentManager.contentCount == 1 }

    val content = toolWindow.contentManager.contents.first()
    val logcatMainPanel: LogcatMainPanel =
      TreeWalker(content.component).descendants().filterIsInstance<LogcatMainPanel>().first()
    waitForCondition { logcatMainPanel.headerPanel.deviceComboBox.getSelectedDevice() != null }
    assertThat(content.tabName).isEqualTo("com.test (device1)")
    assertThat(logcatMainPanel.headerPanel.deviceComboBox.getSelectedDevice()?.deviceId)
      .isEqualTo("device1")
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:com.test")
  }

  @Test
  fun showLogcatFile_opensLogcatPanel() {
    // Use a real file because the path gets serialized and deserilized and depends on the file
    // system being used.
    val path = Files.createTempFile("logcat", "txt")
    Disposer.register(disposable) { path.delete() }
    val toolWindow = MockToolWindow(project)
    logcatToolWindowFactory().init(toolWindow)

    project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcatFile(path, "name")
    waitForCondition { toolWindow.contentManager.contentCount == 1 }

    val content = toolWindow.contentManager.contents.first()
    val logcatMainPanel: LogcatMainPanel =
      TreeWalker(content.component).descendants().filterIsInstance<LogcatMainPanel>().first()
    waitForCondition { logcatMainPanel.headerPanel.deviceComboBox.getSelectedFile() != null }
    assertThat(content.tabName).isEqualTo("name")
    assertThat(logcatMainPanel.headerPanel.deviceComboBox.getSelectedFile()).isEqualTo(path)
  }

  @Test
  fun clearLogcat_clearsLogcat() {
    val toolWindow = MockToolWindow(project)
    logcatToolWindowFactory().init(toolWindow)

    project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat("serial")

    waitForCondition { fakeLogcatService.clearRequests.contains("serial") }
  }

  private fun logcatToolWindowFactory(
    processNameMonitor: ProcessNameMonitor = FakeProcessNameMonitor()
  ): LogcatToolWindowFactory {
    project.registerOrReplaceServiceInstance(
      ProcessNameMonitor::class.java,
      processNameMonitor,
      disposable,
    )
    return LogcatToolWindowFactory()
  }
}
