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
package com.android.tools.idea.logcat.messages

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.PID
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.TIME
import com.android.tools.idea.logcat.util.findComponentWithLabel
import com.android.tools.idea.logcat.util.getButton
import com.android.tools.idea.logcat.util.getCheckBox
import com.android.tools.idea.logcat.util.getLabel
import com.android.tools.idea.logcat.util.logcatEvents
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.JBIntSpinner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import javax.swing.JComboBox

/**
 * Tests for [LogcatFormatDialog]
 */
@RunsInEdt
class LogcatFormatDialogTest {
  private val projectRule = ProjectRule()
  private val usageTrackerRule = UsageTrackerRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), usageTrackerRule, disposableRule)

  private val formattingOptions = FormattingOptions(
    TimestampFormat(DATETIME, enabled = true),
    ProcessThreadFormat(BOTH, enabled = true),
    TagFormat(maxLength = 20, hideDuplicates = true, enabled = true),
    AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true))

  private val applyAction = mock<LogcatFormatDialogBase.ApplyAction>()
  private val dialog by lazy { LogcatFormatDialog(projectRule.project, formattingOptions, applyAction) }
  private val showTimestampCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show timestamp") }
  private val timestampFormatComboBox by lazy { dialog.dialogWrapper.findComponentWithLabel<JComboBox<TimestampFormat.Style>>("Format:") }
  private val showProcessIdsCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show process id") }
  private val showThreadIdCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Include thread id") }
  private val showTagCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show tag") }
  private val showRepeatedTagsCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show repeated tags") }
  private val tagWidthLabel by lazy { dialog.dialogWrapper.getLabel("Tag column width:") }
  private val tagWidthSpinner by lazy { dialog.dialogWrapper.findComponentWithLabel<JBIntSpinner>("Tag column width:") }
  private val showAppNameCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show package name") }
  private val showRepeatedAppNamesCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show repeated package names") }
  private val appNameWidthLabel by lazy { dialog.dialogWrapper.getLabel("Package column width:") }
  private val appNameWidthSpinner by lazy { dialog.dialogWrapper.findComponentWithLabel<JBIntSpinner>("Package column width:") }
  private val showLevelsCheckBox by lazy { dialog.dialogWrapper.getCheckBox("Show level") }

  @Before
  fun setUp() {
    enableHeadlessDialogs(disposableRule.disposable)
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
  fun initialState_levelsDisabled() {
    formattingOptions.levelFormat = LevelFormat(false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showLevelsCheckBox.isSelected).isFalse()
    }
  }

  @Test
  fun initialState_levelsEnabled() {
    formattingOptions.levelFormat = LevelFormat(true)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(showLevelsCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun toggle_levels() {
    formattingOptions.levelFormat = LevelFormat(false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      showLevelsCheckBox.isSelected = true
      assertThat(showLevelsCheckBox.isSelected).isTrue()

      showLevelsCheckBox.isSelected = false
      assertThat(showLevelsCheckBox.isSelected).isFalse()

    }
  }

  @Test
  fun apply_levels() {
    formattingOptions.levelFormat = LevelFormat(false)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.applyToLevels {
        showLevelsCheckBox.isSelected = true
      }).isEqualTo(LevelFormat(true))

      assertThat(dialog.applyToLevels {
        showLevelsCheckBox.isSelected = false
      }).isEqualTo(LevelFormat(false))
    }
  }


  @Test
  fun sampleText() {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(TIME, enabled = false)
      processThreadFormat = ProcessThreadFormat(PID, enabled = false)
      tagFormat = TagFormat(maxLength = 23, hideDuplicates = false, enabled = false)
      appNameFormat = AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false)
      levelFormat = LevelFormat(false)
    }
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.sampleEditor.document.text.trimEnd()).isEqualTo("""
       Sample logcat message 1.
       Sample logcat message 2.
       Sample logcat message 3.
       Sample logcat multiline
       message.
      """.trimIndent())

      showTimestampCheckBox.isSelected = true
      showProcessIdsCheckBox.isSelected = true
      showTagCheckBox.isSelected = true
      showAppNameCheckBox.isSelected = true
      showLevelsCheckBox.isSelected = true


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
      showLevelsCheckBox.isSelected = true

      assertThat(dialog.sampleEditor.document.text.lines().maxOf(String::length)).isEqualTo(maxLineLength)
    }
  }

  @Test
  fun clickOk_activatesApplyAction() {
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getButton("OK").doClick()

      verify(applyAction).onApply(dialog)
    }
  }

  @Test
  fun clickCancel_doesNotActivateApplyAction() {
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getButton("Cancel").doClick()

      verify(applyAction, never()).onApply(dialog)
    }
  }

  @Test
  fun clickOk_logsUsage() {
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      dialogWrapper.getButton("OK").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialogApplied })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialog.newBuilder()
            .setIsApplyButtonUsed(false)
            .setConfiguration(
              LogcatFormatConfiguration.newBuilder()
                .setIsShowTimestamp(true)
                .setIsShowDate(true)
                .setIsShowProcessId(true)
                .setIsShowThreadId(true)
                .setIsShowTags(true)
                .setIsShowRepeatedTags(false)
                .setTagWidth(20)
                .setIsShowPackages(true)
                .setIsShowRepeatedPackages(false)
                .setPackageWidth(20)
                .build())
            .build())

    }
  }

  @Test
  fun clickOk_afterChanges_logsUsage() {
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      showTimestampCheckBox.isSelected = false
      timestampFormatComboBox.selectedItem = TIME
      showProcessIdsCheckBox.isSelected = false
      showThreadIdCheckBox.isSelected = false
      showTagCheckBox.isSelected = false
      showRepeatedTagsCheckBox.isSelected = true
      tagWidthSpinner.number = 10
      showAppNameCheckBox.isSelected = false
      showRepeatedAppNamesCheckBox.isSelected = true
      appNameWidthSpinner.number = 10
      dialogWrapper.getButton("OK").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialogApplied })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialog.newBuilder()
            .setIsApplyButtonUsed(false)
            .setConfiguration(
              LogcatFormatConfiguration.newBuilder()
                .setIsShowTimestamp(false)
                .setIsShowDate(false)
                .setIsShowProcessId(false)
                .setIsShowThreadId(false)
                .setIsShowTags(false)
                .setIsShowRepeatedTags(true)
                .setTagWidth(10)
                .setIsShowPackages(false)
                .setIsShowRepeatedPackages(true)
                .setPackageWidth(10)
                .build())
            .build())
    }
  }
}

private fun LogcatFormatDialog.applyToOptions(body: () -> Unit): FormattingOptions {
  body()
  val formattingOptions = FormattingOptions()
  applyToFormattingOptions(formattingOptions)
  return formattingOptions
}

private fun LogcatFormatDialog.applyToTimestamp(body: () -> Unit) = applyToOptions(body).timestampFormat

private fun LogcatFormatDialog.applyToIds(body: () -> Unit) = applyToOptions(body).processThreadFormat

private fun LogcatFormatDialog.applyToTag(body: () -> Unit) = applyToOptions(body).tagFormat

private fun LogcatFormatDialog.applyToAppName(body: () -> Unit) = applyToOptions(body).appNameFormat

private fun LogcatFormatDialog.applyToLevels(body: () -> Unit) = applyToOptions(body).levelFormat
