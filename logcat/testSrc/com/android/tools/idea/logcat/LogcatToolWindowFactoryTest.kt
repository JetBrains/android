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

import com.android.adblib.testing.FakeAdbSession
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.filters.LogcatFilterColorSettingsPage
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColorSettingsPage
import com.android.tools.idea.logcat.messages.TagFormat
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
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl.MockToolWindow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify


@RunsInEdt
class LogcatToolWindowFactoryTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), disposableRule)

  private val settings = LogcatExperimentalSettings()
  private val mockProcessNameMonitor = mock<ProcessNameMonitor>()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(LogcatExperimentalSettings::class.java, settings, disposableRule.disposable)
  }

  @Test
  fun isApplicable() {
    assertThat(logcatToolWindowFactory().isApplicable(projectRule.project)).isTrue()
  }

  @Test
  fun isApplicable_legacy() {
    settings.logcatV2Enabled = false

    assertThat(logcatToolWindowFactory().isApplicable(projectRule.project)).isFalse()
  }

  @Test
  fun isApplicable_nonAndroidEnvironment() {
    val mockIdeInfo = spy(IdeInfo.getInstance())
    whenever(mockIdeInfo.isAndroidStudio).thenReturn(false)
    ApplicationManager.getApplication().replaceService(IdeInfo::class.java, mockIdeInfo, disposableRule.disposable)

    assertThat(logcatToolWindowFactory().isApplicable(projectRule.project)).isFalse()
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
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = null)

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
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = LogcatPanelConfig.toJson(logcatPanelConfig))

    // It's enough to assert on just one field in the config. We test more thoroughly in LogcatMainPanelTest
    assertThat(logcatMainPanel.formattingOptions).isEqualTo(logcatPanelConfig.formattingConfig.toFormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun createChildComponent_invalidState() {
    val logcatMainPanel = logcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = "invalid state")

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
    logcatToolWindowFactory(mockProcessNameMonitor).init(MockToolWindow(projectRule.project))

    verify(mockProcessNameMonitor).start()
  }

  private fun logcatToolWindowFactory(processNameMonitor: ProcessNameMonitor = mockProcessNameMonitor) =
    LogcatToolWindowFactory { FakeAdbSession() }.also {
      projectRule.project.registerServiceInstance(ProcessNameMonitor::class.java, processNameMonitor)
    }
}
