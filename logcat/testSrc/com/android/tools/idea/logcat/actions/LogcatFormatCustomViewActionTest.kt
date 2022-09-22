/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.messages.AppNameFormat
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.ProcessThreadFormat
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.messages.TimestampFormat
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox

/**
 * Tests for [LogcatFormatCustomViewAction]
 */
@RunsInEdt
class LogcatFormatCustomViewActionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), disposableRule)

  private val fakeLogcatPresenter = FakeLogcatPresenter()

  @Before
  fun setUp() {
    enableHeadlessDialogs(disposableRule.disposable)
  }

  @Test
  fun presentation() {
    val action = logcatFormatCustomViewAction()

    assertThat(action.templatePresentation.text).isEqualTo("Custom View")
    assertThat(action.templatePresentation.description).isNull()
    assertThat(action.templatePresentation.icon).isNull()
  }

  @Test
  fun actionPerformed_dialogInitialized() {
    fakeLogcatPresenter.formattingOptions = FormattingOptions(processThreadFormat = ProcessThreadFormat(ProcessThreadFormat.Style.PID))
    var isShowProcessId = false
    var isShowThreadId = true
    val action = logcatFormatCustomViewAction(fakeLogcatPresenter)

    createModalDialogAndInteractWithIt(action::performAction) { dialogWrapper ->
      val showProcessId = dialogWrapper.getCheckBox("Show process id")
      val showThreadId = dialogWrapper.getCheckBox("Include thread id")
      isShowProcessId = showProcessId.isSelected
      isShowThreadId = showThreadId.isSelected
    }

    // More comprehensive dialog tests are in HeaderFormatOptionsDialogTest
    assertThat(isShowProcessId).isTrue()
    assertThat(isShowThreadId).isFalse()
  }

  @Test
  fun actionPerformed_ok() {
    fakeLogcatPresenter.formattingOptions = FormattingOptions(
      TimestampFormat(TimestampFormat.Style.TIME, enabled = true),
      ProcessThreadFormat(ProcessThreadFormat.Style.BOTH),
      TagFormat(),
      AppNameFormat())
    val action = logcatFormatCustomViewAction(fakeLogcatPresenter)

    createModalDialogAndInteractWithIt(action::performAction) { dialogWrapper ->
      dialogWrapper.getCheckBox("Show timestamp").isSelected = false
      dialogWrapper.clickDefaultButton()
    }

    assertThat(fakeLogcatPresenter.formattingOptions.timestampFormat)
      .isEqualTo(TimestampFormat(TimestampFormat.Style.TIME, enabled = false))
  }

  @Test
  fun actionPerformed_cancel() {
    fakeLogcatPresenter.formattingOptions = FormattingOptions(
      TimestampFormat(TimestampFormat.Style.TIME, enabled = true),
      ProcessThreadFormat(ProcessThreadFormat.Style.BOTH),
      TagFormat(),
      AppNameFormat())
    val action = logcatFormatCustomViewAction(fakeLogcatPresenter)

    createModalDialogAndInteractWithIt(action::performAction) { dialogWrapper ->
      dialogWrapper.getCheckBox("Show timestamp").isSelected = false
      dialogWrapper.doCancelAction()
    }

    assertThat(fakeLogcatPresenter.formattingOptions.timestampFormat).isEqualTo(TimestampFormat(TimestampFormat.Style.TIME, enabled = true))
  }

  @Test
  fun isSelected() {
    fakeLogcatPresenter.formattingOptions = STANDARD.formattingOptions
    assertThat(logcatFormatCustomViewAction(fakeLogcatPresenter).isSelected()).isFalse()

    fakeLogcatPresenter.formattingOptions = COMPACT.formattingOptions
    assertThat(logcatFormatCustomViewAction(fakeLogcatPresenter).isSelected()).isFalse()

    fakeLogcatPresenter.formattingOptions = FormattingOptions(TimestampFormat(enabled = false))
    assertThat(logcatFormatCustomViewAction(fakeLogcatPresenter).isSelected()).isTrue()
  }

  private fun logcatFormatCustomViewAction(
    logcatPresenter: LogcatPresenter = fakeLogcatPresenter,
  ) = LogcatFormatCustomViewAction(projectRule.project, logcatPresenter)
}

private fun DialogWrapper.getCheckBox(text: String) =
  TreeWalker(rootPane).descendants().filterIsInstance<JCheckBox>().first { it.text == text }

private fun AnAction.performAction() {
  actionPerformed(TestActionEvent())
}
