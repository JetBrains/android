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

import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.testing.FakeAdbSession
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.filters.LogcatFilterColorSettingsPage
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColorSettingsPage
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.logcat.testing.TestDevice
import com.android.tools.idea.logcat.testing.setupCommandsForDevice
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.ShowLogcatListener.DeviceInfo.PhysicalDeviceInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl.MockToolWindow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit


@RunsInEdt
class LogcatToolWindowFactoryTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), disposableRule)

  private val project get() = projectRule.project
  private val disposable get() = disposableRule.disposable
  private val settings = LogcatExperimentalSettings()
  private val mockProcessNameMonitor = mock<ProcessNameMonitor>()
  private val fakeAdbSession = FakeAdbSession()
  private val fakeLogcatService = FakeLogcatService()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(LogcatExperimentalSettings::class.java, settings, disposable)
    project.replaceService(LogcatService::class.java, fakeLogcatService, disposable)
  }

  @Test
  fun isApplicable() {
    assertThat(logcatToolWindowFactory().isApplicable(project)).isTrue()
  }

  @Test
  fun isApplicable_legacy() {
    settings.logcatV2Enabled = false

    assertThat(logcatToolWindowFactory().isApplicable(project)).isFalse()
  }

  @Test
  fun isApplicable_nonAndroidEnvironment() {
    val mockIdeInfo = spy(IdeInfo.getInstance())
    whenever(mockIdeInfo.isAndroidStudio).thenReturn(false)
    ApplicationManager.getApplication().replaceService(IdeInfo::class.java, mockIdeInfo, disposableRule.disposable)

    assertThat(logcatToolWindowFactory().isApplicable(project)).isFalse()
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
    val childComponent = logcatToolWindowFactory()
      .createChildComponent(project, ActionGroup.EMPTY_GROUP, clientState = null)

    assertThat(childComponent).isInstanceOf(LogcatMainPanel::class.java)
    Disposer.dispose(childComponent as Disposable)
  }

  @Test
  fun createChildComponent_parsesState() {
    val logcatPanelConfig = LogcatPanelConfig(
      device = null,
      FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(15))),
      "filter",
      isSoftWrap = false)

    val logcatMainPanel = logcatToolWindowFactory()
      .createChildComponent(project, ActionGroup.EMPTY_GROUP, clientState = LogcatPanelConfig.toJson(logcatPanelConfig))

    // It's enough to assert on just one field in the config. We test more thoroughly in LogcatMainPanelTest
    assertThat(logcatMainPanel.formattingOptions).isEqualTo(logcatPanelConfig.formattingConfig.toFormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun createChildComponent_invalidState() {
    val logcatMainPanel = logcatToolWindowFactory()
      .createChildComponent(project, ActionGroup.EMPTY_GROUP, clientState = "invalid state")

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun colorSettingsPagesRegistration() {
    // We have to use a mock because there is no clean way to clean up ColorSettingsPages
    val mockColorSettingsPages = mock<ColorSettingsPages>()
    ApplicationManager.getApplication().replaceService(ColorSettingsPages::class.java, mockColorSettingsPages, disposableRule.disposable)

    logcatToolWindowFactory()
    AndroidLogcatToolWindowFactory()

    verify(mockColorSettingsPages).registerPage(any(LogcatColorSettingsPage::class.java))
    verify(mockColorSettingsPages).registerPage(any(LogcatFilterColorSettingsPage::class.java))
    verify(mockColorSettingsPages, never()).registerPage(any(AndroidLogcatColorPage::class.java))
  }

  @Test
  fun colorSettingsPagesRegistration_legacy() {
    // We have to use a mock because there is no clean way to clean up ColorSettingsPages
    val mockColorSettingsPages = mock<ColorSettingsPages>()
    ApplicationManager.getApplication().replaceService(ColorSettingsPages::class.java, mockColorSettingsPages, disposableRule.disposable)
    settings.logcatV2Enabled = false

    logcatToolWindowFactory()
    AndroidLogcatToolWindowFactory()

    verify(mockColorSettingsPages, never()).registerPage(any(LogcatColorSettingsPage::class.java))
    verify(mockColorSettingsPages, never()).registerPage(any(LogcatFilterColorSettingsPage::class.java))
    verify(mockColorSettingsPages).registerPage(any(AndroidLogcatColorPage::class.java))
  }

  @Test
  fun startsProcessNameMonitor() {
    logcatToolWindowFactory(mockProcessNameMonitor).init(MockToolWindow(project))

    verify(mockProcessNameMonitor).start()
  }

  @Test
  fun showLogcat_opensLogcatPanel() {
    val toolWindow = MockToolWindow(project)
    logcatToolWindowFactory().init(toolWindow)
    val device = TestDevice("device1", DeviceState.ONLINE, "11", 30, "manufacturer1", "model1")
    fakeAdbSession.deviceServices.setupCommandsForDevice(device)
    fakeAdbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber("device1"), "logcat -v long -v epoch", "")

    project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(
      PhysicalDeviceInfo("device1", "11", 30, "Google", "Pixel"),
      "com.test")

    waitForCondition { toolWindow.contentManager.contentCount == 1 }

    val content = toolWindow.contentManager.contents.first()
    val logcatMainPanel: LogcatMainPanel = TreeWalker(content.component).descendants().filterIsInstance<LogcatMainPanel>().first()
    waitForCondition {
      logcatMainPanel.headerPanel.getSelectedDevice() != null
    }
    assertThat(content.tabName).isEqualTo("com.test (device1)")
    assertThat(logcatMainPanel.headerPanel.getSelectedDevice()?.deviceId).isEqualTo("device1")
    assertThat(logcatMainPanel.headerPanel.filter).isEqualTo("package:com.test")
  }

  private fun logcatToolWindowFactory(
    processNameMonitor: ProcessNameMonitor = mockProcessNameMonitor,
    adbSession: FakeAdbSession = fakeAdbSession,
  ): LogcatToolWindowFactory {
    project.registerOrReplaceServiceInstance(ProcessNameMonitor::class.java, processNameMonitor, disposable)
    project.registerOrReplaceServiceInstance(AdbLibService::class.java, TestAdbLibService(adbSession), disposable)
    return LogcatToolWindowFactory()
  }
}

private fun waitForCondition(condition: () -> Boolean) = waitForCondition(TIMEOUT_SEC, TimeUnit.SECONDS, condition)
