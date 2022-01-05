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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.TagFormat
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LogcatToolWindowFactoryTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @After
  fun tearDown() {
    StudioFlags.LOGCAT_V2_ENABLE.clearOverride()
  }

  @Test
  fun shouldBeAvailable_isFalse() {
    assertThat(LogcatToolWindowFactory().shouldBeAvailable(projectRule.project)).isFalse()
  }

  @Test
  fun shouldBeAvailable_obeysFlag_true() {
    StudioFlags.LOGCAT_V2_ENABLE.override(true)
    assertThat(LogcatToolWindowFactory().shouldBeAvailable(projectRule.project)).isTrue()
  }

  @Test
  fun shouldBeAvailable_obeysFlag_false() {
    StudioFlags.LOGCAT_V2_ENABLE.override(false)
    assertThat(LogcatToolWindowFactory().shouldBeAvailable(projectRule.project)).isFalse()
  }

  @Test
  fun generateTabName_noPreExistingNames() {
    assertThat(LogcatToolWindowFactory().generateTabName(emptySet())).isEqualTo("Logcat")
  }

  @Test
  fun generateTabName_defaultNameAlreadyUsed() {
    assertThat(LogcatToolWindowFactory().generateTabName(setOf("Logcat"))).isEqualTo("Logcat (2)")
  }

  @Test
  fun createChildComponent_isLogcatMainPanel() {
    val childComponent = LogcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = null)

    assertThat(childComponent).isInstanceOf(LogcatMainPanel::class.java)
    Disposer.dispose(childComponent as Disposable)
  }

  @Test
  fun createChildComponent_parsesState() {
    val logcatPanelConfig = LogcatPanelConfig("device", FormattingOptions(tagFormat = TagFormat(15)), "filter")

    val logcatMainPanel = LogcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = Gson().toJson(logcatPanelConfig))

    // It's enough to assert on just one field in the config. We test more thoroughly in LogcatMainPanelTest
    assertThat(logcatMainPanel.formattingOptions).isEqualTo(logcatPanelConfig.formattingOptions)
    Disposer.dispose(logcatMainPanel)
  }

  @Test
  fun createChildComponent_invalidState() {
    val logcatMainPanel = LogcatToolWindowFactory()
      .createChildComponent(projectRule.project, ActionGroup.EMPTY_GROUP, clientState = "invalid state")

    assertThat(logcatMainPanel.formattingOptions).isEqualTo(FormattingOptions())
    Disposer.dispose(logcatMainPanel)
  }
}