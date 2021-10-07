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
import com.android.tools.idea.logcat.messages.ProcessThreadFormat
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.PID
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.messages.TimestampFormat
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.TIME
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JLabel

/**
 * Tests for [HeaderFormatOptionsDialog]
 */
@RunsInEdt
class HeaderFormatOptionsDialogTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val formattingOptions = FormattingOptions(
    TimestampFormat(DATETIME, enabled = true),
    ProcessThreadFormat(BOTH, enabled = true),
    TagFormat(maxLength = 20, hideDuplicates = true, enabled = true),
    AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true))

  private val dialog by lazy { HeaderFormatOptionsDialog(projectRule.project, formattingOptions) }
  private val showTimestampCheckBox by lazy { dialog.getCheckBox("Show timestamp") }
  private val showDateCheckBox by lazy { dialog.getCheckBox("Show date") }
  private val showProcessIdsCheckBox by lazy { dialog.getCheckBox("Show process ids") }
  private val showThreadIdCheckBox by lazy { dialog.getCheckBox("Show thread id") }
  private val showTagCheckBox by lazy { dialog.getCheckBox("Show tag") }
  private val hideDuplicateTagsCheckBox by lazy { dialog.getCheckBox("Hide duplicate tags") }
  private val tagWidthLabel by lazy { dialog.getLabel("Tag width:") }
  private val showAppNameCheckBox by lazy { dialog.getCheckBox("Show package name") }
  private val hideDuplicateAppNamesCheckBox by lazy { dialog.getCheckBox("Hide duplicate package names") }
  private val appNameWidthLabel by lazy { dialog.getLabel("Package name width:") }

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
  }

  @Test
  fun initialState_timestampDisabled() {
    formattingOptions.timestampFormat = TimestampFormat(DATETIME, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTimestampCheckBox.isSelected).isFalse()
      assertThat(showDateCheckBox.isEnabled).isFalse()
      assertThat(showDateCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun initialState_timestampDateTime() {
    formattingOptions.timestampFormat = TimestampFormat(DATETIME)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTimestampCheckBox.isSelected).isTrue()
      assertThat(showDateCheckBox.isEnabled).isTrue()
      assertThat(showDateCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun initialState_timestampTime() {
    formattingOptions.timestampFormat = TimestampFormat(DATETIME)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTimestampCheckBox.isSelected).isTrue()
      assertThat(showDateCheckBox.isEnabled).isTrue()
      assertThat(showDateCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun toggle_timestamp() {
    formattingOptions.timestampFormat = TimestampFormat(enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showTimestampCheckBox.isSelected = true
      assertThat(showDateCheckBox.isEnabled).isTrue()
      showTimestampCheckBox.isSelected = false
      assertThat(showDateCheckBox.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_timestamp() {
    formattingOptions.timestampFormat = TimestampFormat(enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToTimestamp {
        showTimestampCheckBox.isSelected = true
        showDateCheckBox.isSelected = true
      }).isEqualTo(TimestampFormat(DATETIME, enabled = true))

      assertThat(dialog.applyToTimestamp {
        showTimestampCheckBox.isSelected = false
      }).isEqualTo(TimestampFormat(DATETIME, enabled = false))

      assertThat(dialog.applyToTimestamp {
        showTimestampCheckBox.isSelected = true
        showDateCheckBox.isSelected = false
      }).isEqualTo(TimestampFormat(TIME, enabled = true))
    }
  }

  @Test
  fun initialState_idsDisabled() {
    formattingOptions.processThreadFormat = ProcessThreadFormat(BOTH, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showProcessIdsCheckBox.isSelected).isFalse()
      assertThat(showThreadIdCheckBox.isEnabled).isFalse()
      assertThat(showThreadIdCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun initialState_idsBoth() {
    formattingOptions.processThreadFormat = ProcessThreadFormat(BOTH)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showProcessIdsCheckBox.isSelected).isTrue()
      assertThat(showThreadIdCheckBox.isEnabled).isTrue()
      assertThat(showThreadIdCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun initialState_idsPid() {
    formattingOptions.processThreadFormat = ProcessThreadFormat(PID)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showProcessIdsCheckBox.isSelected).isTrue()
      assertThat(showThreadIdCheckBox.isEnabled).isTrue()
      assertThat(showThreadIdCheckBox.isSelected).isFalse()
    }
  }

  @Test
  fun toggle_ids() {
    formattingOptions.processThreadFormat = ProcessThreadFormat(enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showProcessIdsCheckBox.isSelected = true
      assertThat(showThreadIdCheckBox.isEnabled).isTrue()
      showProcessIdsCheckBox.isSelected = false
      assertThat(showThreadIdCheckBox.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_ids() {
    formattingOptions.processThreadFormat = ProcessThreadFormat(PID, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToIds {
        showProcessIdsCheckBox.isSelected = true
        showThreadIdCheckBox.isSelected = true
      }).isEqualTo(ProcessThreadFormat(BOTH, enabled = true))

      assertThat(dialog.applyToIds {
        showProcessIdsCheckBox.isSelected = false
      }).isEqualTo(ProcessThreadFormat(BOTH, enabled = false))

      assertThat(dialog.applyToIds {
        showProcessIdsCheckBox.isSelected = true
        showThreadIdCheckBox.isSelected = false
      }).isEqualTo(ProcessThreadFormat(PID, enabled = true))
    }
  }

  @Test
  fun initialState_tagDisabled() {
    formattingOptions.tagFormat = TagFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTagCheckBox.isSelected).isFalse()
      assertThat(hideDuplicateTagsCheckBox.isEnabled).isFalse()
      assertThat(hideDuplicateTagsCheckBox.isSelected).isFalse()
      assertThat(tagWidthLabel.isEnabled).isFalse()
      assertThat(dialog.tagWidthSpinner.isEnabled).isFalse()
      assertThat(dialog.tagWidthSpinner.value).isEqualTo(10)
    }
  }

  @Test
  fun initialState_tagEnabled() {
    formattingOptions.tagFormat = TagFormat(maxLength = 20, hideDuplicates = true, enabled = true)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTagCheckBox.isSelected).isTrue()
      assertThat(hideDuplicateTagsCheckBox.isEnabled).isTrue()
      assertThat(hideDuplicateTagsCheckBox.isSelected).isTrue()
      assertThat(tagWidthLabel.isEnabled).isTrue()
      assertThat(dialog.tagWidthSpinner.isEnabled).isTrue()
      assertThat(dialog.tagWidthSpinner.value).isEqualTo(20)
    }
  }

  @Test
  fun toggle_tag() {
    formattingOptions.tagFormat = TagFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showTagCheckBox.isSelected = true
      assertThat(hideDuplicateTagsCheckBox.isEnabled).isTrue()
      assertThat(tagWidthLabel.isEnabled).isTrue()
      assertThat(dialog.tagWidthSpinner.isEnabled).isTrue()

      showTagCheckBox.isSelected = false
      assertThat(hideDuplicateTagsCheckBox.isEnabled).isFalse()
      assertThat(tagWidthLabel.isEnabled).isFalse()
      assertThat(dialog.tagWidthSpinner.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_tag() {
    formattingOptions.tagFormat = TagFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToTag {
        showTagCheckBox.isSelected = true
        hideDuplicateTagsCheckBox.isSelected = true
        dialog.tagWidthSpinner.value = 20
      }).isEqualTo(TagFormat(maxLength = 20, hideDuplicates = true, enabled = true))

      assertThat(dialog.applyToTag {
        showTagCheckBox.isSelected = false
        hideDuplicateTagsCheckBox.isSelected = false
        dialog.tagWidthSpinner.value = 10
      }).isEqualTo(TagFormat(maxLength = 10, hideDuplicates = false, enabled = false))
    }
  }

  @Test
  fun initialState_appNameDisabled() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showAppNameCheckBox.isSelected).isFalse()
      assertThat(hideDuplicateAppNamesCheckBox.isEnabled).isFalse()
      assertThat(hideDuplicateAppNamesCheckBox.isSelected).isFalse()
      assertThat(appNameWidthLabel.isEnabled).isFalse()
      assertThat(dialog.appNameWidthSpinner.isEnabled).isFalse()
      assertThat(dialog.appNameWidthSpinner.value).isEqualTo(10)
    }
  }

  @Test
  fun initialState_appNameEnabled() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showAppNameCheckBox.isSelected).isTrue()
      assertThat(hideDuplicateAppNamesCheckBox.isEnabled).isTrue()
      assertThat(hideDuplicateAppNamesCheckBox.isSelected).isTrue()
      assertThat(appNameWidthLabel.isEnabled).isTrue()
      assertThat(dialog.appNameWidthSpinner.isEnabled).isTrue()
      assertThat(dialog.appNameWidthSpinner.value).isEqualTo(20)
    }
  }

  @Test
  fun toggle_appName() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showAppNameCheckBox.isSelected = true
      assertThat(hideDuplicateAppNamesCheckBox.isEnabled).isTrue()
      assertThat(appNameWidthLabel.isEnabled).isTrue()
      assertThat(dialog.appNameWidthSpinner.isEnabled).isTrue()

      showAppNameCheckBox.isSelected = false
      assertThat(hideDuplicateAppNamesCheckBox.isEnabled).isFalse()
      assertThat(appNameWidthLabel.isEnabled).isFalse()
      assertThat(dialog.appNameWidthSpinner.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_appName() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToAppName {
        showAppNameCheckBox.isSelected = true
        hideDuplicateAppNamesCheckBox.isSelected = true
        dialog.appNameWidthSpinner.value = 20
      }).isEqualTo(AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true))

      assertThat(dialog.applyToAppName {
        showAppNameCheckBox.isSelected = false
        hideDuplicateAppNamesCheckBox.isSelected = false
        dialog.appNameWidthSpinner.value = 10
      }).isEqualTo(AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false))
    }
  }

  @Test
  fun sampleText() {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(TIME, enabled = false)
      processThreadFormat = ProcessThreadFormat(PID, enabled = false)
      tagFormat = TagFormat(maxLength = 23, hideDuplicates = false, enabled = false)
      appNameFormat = AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false)
    }
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.sampleEditor.document.text).isEqualTo("""
        D  Sample logcat message 1.
        I  Sample logcat message 2.
        W  Sample logcat message 3.
        E  Sample logcat message 4.
      """.trimIndent().prependIndent(" ").plus("\n"))

      showTimestampCheckBox.isSelected = true
      showProcessIdsCheckBox.isSelected = true
      showTagCheckBox.isSelected = true
      showAppNameCheckBox.isSelected = true

      assertThat(dialog.sampleEditor.document.text).isEqualTo("""
        11:00:14.234 27217 ExampleTag1             com.example.app1                     D  Sample logcat message 1.
        11:00:14.234 27217 ExampleTag1             com.example.app1                     I  Sample logcat message 2.
        11:00:14.234 24395 ExampleTag2             com.example.app2                     W  Sample logcat message 3.
        11:00:14.234 24395 ExampleTag2             com.example.app2                     E  Sample logcat message 4.

      """.trimIndent())

      // TODO(b/202206947): Add an assert that pack() is called when the dialog is interacted with so we know it resizes itself to
      //  accommodate the new text.
    }
  }
}

private fun HeaderFormatOptionsDialog.getCheckBox(text: String) =
  TreeWalker(dialogWrapper.rootPane).descendants().filterIsInstance<JCheckBox>().first { it.text == text }

private fun HeaderFormatOptionsDialog.getLabel(text: String) =
  TreeWalker(dialogWrapper.rootPane).descendants().filterIsInstance<JLabel>().first { it.text == text }

private fun HeaderFormatOptionsDialog.applyToOptions(body: () -> Unit): FormattingOptions {
  body()
  val formattingOptions = FormattingOptions()
  applyTo(formattingOptions)
  return formattingOptions
}

private fun HeaderFormatOptionsDialog.applyToTimestamp(body: () -> Unit) = applyToOptions(body).timestampFormat

private fun HeaderFormatOptionsDialog.applyToIds(body: () -> Unit) = applyToOptions(body).processThreadFormat

private fun HeaderFormatOptionsDialog.applyToTag(body: () -> Unit) = applyToOptions(body).tagFormat

private fun HeaderFormatOptionsDialog.applyToAppName(body: () -> Unit) = applyToOptions(body).appNameFormat
