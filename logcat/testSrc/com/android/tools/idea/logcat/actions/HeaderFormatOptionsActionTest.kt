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
package com.android.tools.idea.logcat.actions

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.logcat.messages.AppNameFormat
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.BOTH
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.messages.TimestampFormat.NO_TIMESTAMP
import com.android.tools.idea.logcat.messages.TimestampFormat.TIME
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.DialogWrapper
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
 * Tests for [HeaderFormatOptionsAction]
 */
@RunsInEdt
class HeaderFormatOptionsActionTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
  }

  @Test
  fun presentation() {
    val action = HeaderFormatOptionsAction(projectRule.project, FormattingOptions()) { }

    assertThat(action.templatePresentation.text).isEqualTo("Header Format")
    assertThat(action.templatePresentation.description).isEqualTo("Configure header formatting options")
    assertThat(action.templatePresentation.icon).isSameAs(AllIcons.General.LayoutEditorPreview)
  }

  @Test
  fun actionPerformed_dialogInitialized() {
    val formattingOptions = FormattingOptions(TIME, BOTH, TagFormat(), AppNameFormat())
    var isShowTimestamp = false
    var isShowDate = true
    val action = HeaderFormatOptionsAction(projectRule.project, formattingOptions) {}

    createModalDialogAndInteractWithIt(action::performAction) { dialogWrapper ->
      val showTimestamp = dialogWrapper.getCheckBox("Show timestamp")
      val showDate = dialogWrapper.getCheckBox("Show date")
      isShowTimestamp = showTimestamp.isSelected
      isShowDate = showDate.isSelected
    }

    // More comprehensive dialog tests are in HeaderFormatOptionsDialogTest
    assertThat(isShowTimestamp).isTrue()
    assertThat(isShowDate).isFalse()
  }

  @Test
  fun actionPerformed_ok() {
    val formattingOptions = FormattingOptions(TIME, BOTH, TagFormat(), AppNameFormat())
    var refreshCount = 0
    val action = HeaderFormatOptionsAction(projectRule.project, formattingOptions) { refreshCount++ }

    createModalDialogAndInteractWithIt(action::performAction) { dialogWrapper ->
      dialogWrapper.getCheckBox("Show timestamp").isSelected = false
      dialogWrapper.clickDefaultButton()
    }

    assertThat(formattingOptions.timestampFormat).isEqualTo(NO_TIMESTAMP)
    assertThat(refreshCount).isEqualTo(1)
  }

  @Test
  fun actionPerformed_cancel() {
    val formattingOptions = FormattingOptions(TIME, BOTH, TagFormat(), AppNameFormat())
    var refreshCount = 0
    val action = HeaderFormatOptionsAction(projectRule.project, formattingOptions) { refreshCount++ }

    createModalDialogAndInteractWithIt(action::performAction) { dialogWrapper ->
      dialogWrapper.getCheckBox("Show timestamp").isSelected = false
      dialogWrapper.doCancelAction()
    }

    assertThat(formattingOptions.timestampFormat).isEqualTo(TIME)
    assertThat(refreshCount).isEqualTo(0)
  }
}

private fun DialogWrapper.getCheckBox(text: String) =
  TreeWalker(rootPane).descendants().filterIsInstance<JCheckBox>().first { it.text == text }

private fun AnAction.performAction() {
  actionPerformed(TestActionEvent())
}
