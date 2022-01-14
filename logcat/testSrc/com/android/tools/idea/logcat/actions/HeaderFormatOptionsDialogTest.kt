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
import com.intellij.ui.JBIntSpinner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import kotlin.test.fail

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
  private val timestampFormatComboBox by lazy { dialog.findComponentWithLabel<JComboBox<TimestampFormat.Style>>("Format:") }
  private val showProcessIdsCheckBox by lazy { dialog.getCheckBox("Show process id") }
  private val showThreadIdCheckBox by lazy { dialog.getCheckBox("Include thread id") }
  private val showTagCheckBox by lazy { dialog.getCheckBox("Show tags") }
  private val showRepeatedTagsCheckBox by lazy { dialog.getCheckBox("Show repeated tags") }
  private val tagWidthLabel by lazy { dialog.getLabel("Tag column width:") }
  private val tagWidthSpinner by lazy { dialog.findComponentWithLabel<JBIntSpinner>("Tag column width:") }
  private val showAppNameCheckBox by lazy { dialog.getCheckBox("Show package names") }
  private val showRepeatedAppNamesCheckBox by lazy { dialog.getCheckBox("Show repeated package names") }
  private val appNameWidthLabel by lazy { dialog.getLabel("Package column width:") }
  private val appNameWidthSpinner by lazy { dialog.findComponentWithLabel<JBIntSpinner>("Package column width:") }

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
  }

  @Test
  fun initialState_timestampDisabled() {
    formattingOptions.timestampFormat = TimestampFormat(DATETIME, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTimestampCheckBox.isSelected).isFalse()
      assertThat(timestampFormatComboBox.isEnabled).isFalse()
      assertThat(timestampFormatComboBox.selectedItem).isEqualTo(DATETIME)
    }
  }

  @Test
  fun initialState_timestampDateTime() {
    formattingOptions.timestampFormat = TimestampFormat(DATETIME)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTimestampCheckBox.isSelected).isTrue()
      assertThat(timestampFormatComboBox.isEnabled).isTrue()
      assertThat(timestampFormatComboBox.selectedItem).isEqualTo(DATETIME)
    }
  }

  @Test
  fun initialState_timestampTime() {
    formattingOptions.timestampFormat = TimestampFormat(TIME)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTimestampCheckBox.isSelected).isTrue()
      assertThat(timestampFormatComboBox.isEnabled).isTrue()
      assertThat(timestampFormatComboBox.selectedItem).isEqualTo(TIME)
    }
  }

  @Test
  fun toggle_timestamp() {
    formattingOptions.timestampFormat = TimestampFormat(enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showTimestampCheckBox.isSelected = true
      assertThat(timestampFormatComboBox.isEnabled).isTrue()
      showTimestampCheckBox.isSelected = false
      assertThat(timestampFormatComboBox.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_timestamp() {
    formattingOptions.timestampFormat = TimestampFormat(enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToTimestamp {
        showTimestampCheckBox.isSelected = true
        timestampFormatComboBox.selectedItem = DATETIME
      }).isEqualTo(TimestampFormat(DATETIME, enabled = true))

      assertThat(dialog.applyToTimestamp {
        showTimestampCheckBox.isSelected = false
      }).isEqualTo(TimestampFormat(DATETIME, enabled = false))

      assertThat(dialog.applyToTimestamp {
        showTimestampCheckBox.isSelected = true
        timestampFormatComboBox.selectedItem = TIME
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
      assertThat(showRepeatedTagsCheckBox.isEnabled).isFalse()
      assertThat(showRepeatedTagsCheckBox.isSelected).isTrue()
      assertThat(tagWidthLabel.isEnabled).isFalse()
      assertThat(tagWidthSpinner.isEnabled).isFalse()
      assertThat(tagWidthSpinner.value).isEqualTo(10)
    }
  }

  @Test
  fun initialState_tagEnabled() {
    formattingOptions.tagFormat = TagFormat(maxLength = 20, hideDuplicates = true, enabled = true)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showTagCheckBox.isSelected).isTrue()
      assertThat(showRepeatedTagsCheckBox.isEnabled).isTrue()
      assertThat(showRepeatedTagsCheckBox.isSelected).isFalse()
      assertThat(tagWidthLabel.isEnabled).isTrue()
      assertThat(tagWidthSpinner.isEnabled).isTrue()
      assertThat(tagWidthSpinner.value).isEqualTo(20)
    }
  }

  @Test
  fun toggle_tag() {
    formattingOptions.tagFormat = TagFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showTagCheckBox.isSelected = true
      assertThat(showRepeatedTagsCheckBox.isEnabled).isTrue()
      assertThat(tagWidthLabel.isEnabled).isTrue()
      assertThat(tagWidthSpinner.isEnabled).isTrue()

      showTagCheckBox.isSelected = false
      assertThat(showRepeatedTagsCheckBox.isEnabled).isFalse()
      assertThat(tagWidthLabel.isEnabled).isFalse()
      assertThat(tagWidthSpinner.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_tag() {
    formattingOptions.tagFormat = TagFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToTag {
        showTagCheckBox.isSelected = true
        showRepeatedTagsCheckBox.isSelected = false
        tagWidthSpinner.value = 20
      }).isEqualTo(TagFormat(maxLength = 20, hideDuplicates = true, enabled = true))

      assertThat(dialog.applyToTag {
        showTagCheckBox.isSelected = false
        showRepeatedTagsCheckBox.isSelected = true
        tagWidthSpinner.value = 10
      }).isEqualTo(TagFormat(maxLength = 10, hideDuplicates = false, enabled = false))
    }
  }

  @Test
  fun initialState_appNameDisabled() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showAppNameCheckBox.isSelected).isFalse()
      assertThat(showRepeatedAppNamesCheckBox.isEnabled).isFalse()
      assertThat(showRepeatedAppNamesCheckBox.isSelected).isTrue()
      assertThat(appNameWidthLabel.isEnabled).isFalse()
      assertThat(appNameWidthSpinner.isEnabled).isFalse()
      assertThat(appNameWidthSpinner.value).isEqualTo(10)
    }
  }

  @Test
  fun initialState_appNameEnabled() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showAppNameCheckBox.isSelected).isTrue()
      assertThat(showRepeatedAppNamesCheckBox.isEnabled).isTrue()
      assertThat(showRepeatedAppNamesCheckBox.isSelected).isFalse()
      assertThat(appNameWidthLabel.isEnabled).isTrue()
      assertThat(appNameWidthSpinner.isEnabled).isTrue()
      assertThat(appNameWidthSpinner.value).isEqualTo(20)
    }
  }

  @Test
  fun toggle_appName() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showAppNameCheckBox.isSelected = true
      assertThat(showRepeatedAppNamesCheckBox.isEnabled).isTrue()
      assertThat(appNameWidthLabel.isEnabled).isTrue()
      assertThat(appNameWidthSpinner.isEnabled).isTrue()

      showAppNameCheckBox.isSelected = false
      assertThat(showRepeatedAppNamesCheckBox.isEnabled).isFalse()
      assertThat(appNameWidthLabel.isEnabled).isFalse()
      assertThat(appNameWidthSpinner.isEnabled).isFalse()
    }
  }

  @Test
  fun apply_appName() {
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToAppName {
        showAppNameCheckBox.isSelected = true
        showRepeatedAppNamesCheckBox.isSelected = false
        appNameWidthSpinner.value = 20
      }).isEqualTo(AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true))

      assertThat(dialog.applyToAppName {
        showAppNameCheckBox.isSelected = false
        showRepeatedAppNamesCheckBox.isSelected = true
        appNameWidthSpinner.value = 10
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
      assertThat(dialog.sampleEditor.document.text.trimEnd()).isEqualTo("""
        D  Sample logcat message 1.
        I  Sample logcat message 2.
        W  Sample logcat message 3.
        E  Sample logcat multiline
           message.
      """.trimIndent().prependIndent(" "))

      showTimestampCheckBox.isSelected = true
      showProcessIdsCheckBox.isSelected = true
      showTagCheckBox.isSelected = true
      showAppNameCheckBox.isSelected = true

      assertThat(dialog.sampleEditor.document.text.trimEnd()).isEqualTo("""
        11:00:14.234 27217 ExampleTag1             com.example.app1                     D  Sample logcat message 1.
        11:00:14.234 27217 ExampleTag1             com.example.app1                     I  Sample logcat message 2.
        11:00:14.234 24395 ExampleTag2             com.example.app2                     W  Sample logcat message 3.
        11:00:14.234 24395 ExampleTag2             com.example.app2                     E  Sample logcat multiline
                                                                                           message.
      """.trimIndent())
    }
  }

  @Test
  fun sampleText_sameMaxLength() {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(TIME, enabled = false)
      processThreadFormat = ProcessThreadFormat(PID, enabled = false)
      tagFormat = TagFormat(maxLength = 23, hideDuplicates = false, enabled = false)
      appNameFormat = AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false)
    }
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val maxLineLength = dialog.sampleEditor.document.text.lines().maxOf(String::length)

      showTimestampCheckBox.isSelected = true
      showProcessIdsCheckBox.isSelected = true
      showTagCheckBox.isSelected = true
      showAppNameCheckBox.isSelected = true

      assertThat(dialog.sampleEditor.document.text.lines().maxOf(String::length)).isEqualTo(maxLineLength)
    }
  }
}

private inline fun <reified T> HeaderFormatOptionsDialog.findComponentWithLabel(text: String): T {
  val components = TreeWalker(dialogWrapper.rootPane).descendants().toList()
  val labelIndex = components.indexOfFirst { it is JLabel && it.text == text }
  if (labelIndex < 0) {
    fail("${T::class.simpleName} with label '$text' not found")
  }
  val component = components[labelIndex + 1]

  return component as? T
         ?: fail("Component with label '$text' is a ${component::class.simpleName} but was expecting a ${T::class.simpleName}")
}

private fun HeaderFormatOptionsDialog.getCheckBox(text: String) =
  TreeWalker(dialogWrapper.rootPane).descendants().filterIsInstance<JCheckBox>()
    .firstOrNull { it.text == text } ?: fail("Checkbox with label '$text' not found")

private fun HeaderFormatOptionsDialog.getLabel(text: String) =
  TreeWalker(dialogWrapper.rootPane).descendants().filterIsInstance<JLabel>()
    .firstOrNull { it.text == text } ?: fail("Label '$text' not found")

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
