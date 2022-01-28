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

import com.android.testutils.MockitoKt
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.util.findComponentWithLabel
import com.android.tools.idea.logcat.util.getButton
import com.android.tools.idea.logcat.util.getCheckBox
import com.android.tools.idea.logcat.util.logcatEvents
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatDialogEvent.Preset
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JComboBox

/**
 * Tests for [LogcatFormatPresetsDialog]
 */
@RunsInEdt
class LogcatFormatPresetsDialogTest {
  private val projectRule = ProjectRule()

  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), usageTrackerRule)

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
  }

  private val applyAction = MockitoKt.mock<LogcatFormatDialogBase.ApplyAction>()

  @Test
  fun initialize() {
    val args = listOf(STANDARD to STANDARD, STANDARD to COMPACT, COMPACT to STANDARD, COMPACT to COMPACT)
    for ((initialFormatting, defaultFormatting) in args) {
      val dialog = LogcatFormatPresetsDialog(projectRule.project, initialFormatting, defaultFormatting, applyAction)
      createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
        val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
        val setAsDefaultCheckBox = it.getCheckBox("Use as default view for new windows")
        val tagsCheckBox = it.getCheckBox("Show tags")
        assertThat(styleComboBox.selectedItem).isEqualTo(initialFormatting)
        assertThat(setAsDefaultCheckBox.isSelected).isEqualTo(initialFormatting == defaultFormatting)
        assertThat(tagsCheckBox.isSelected).isEqualTo(initialFormatting.formattingOptions.tagFormat.enabled)
      }
    }
  }

  @Test
  fun changingView_changesComponents() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
      val tagsCheckBox = it.getCheckBox("Show tags")

      styleComboBox.selectedItem = COMPACT

      assertThat(tagsCheckBox.isSelected).isFalse()
    }
  }

  @Test
  fun changingView_changesSetAsDefault_whenIsDefault() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
      val setAsDefaultCheckBox = it.getCheckBox("Use as default view for new windows")

      styleComboBox.selectedItem = COMPACT

      assertThat(setAsDefaultCheckBox.isSelected).isFalse()
    }
  }

  @Test
  fun changingView_changesSetAsDefault_whenIsNotDefault() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, COMPACT, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
      val setAsDefaultCheckBox = it.getCheckBox("Use as default view for new windows")

      styleComboBox.selectedItem = COMPACT

      assertThat(setAsDefaultCheckBox.isSelected).isTrue()
    }
  }

  @Test
  fun changingView_changesSetAsDefault_whenCheckboxInteracted() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, COMPACT, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
      val setAsDefaultCheckBox = it.getCheckBox("Use as default view for new windows")

      setAsDefaultCheckBox.isSelected = true
      styleComboBox.selectedItem = STANDARD

      assertThat(setAsDefaultCheckBox.isSelected).isFalse()
    }
  }

  @Test
  fun changingView_changesSampleText() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
      val tagsCheckBox = it.getCheckBox("Show tags")

      tagsCheckBox.isSelected = false
      styleComboBox.selectedItem = COMPACT
      tagsCheckBox.isSelected = true

      assertThat(dialog.standardFormattingOptions.tagFormat.enabled).isFalse()
      assertThat(dialog.compactFormattingOptions.tagFormat.enabled).isTrue()
    }
  }

  @Test
  fun setAsDefaultCheckBox() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, COMPACT, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")
      val setAsDefaultCheckBox = it.getCheckBox("Use as default view for new windows")

      setAsDefaultCheckBox.isSelected = true
      assertThat(dialog.defaultFormatting).isEqualTo(COMPACT)

      styleComboBox.selectedItem = STANDARD
      setAsDefaultCheckBox.isSelected = true
      assertThat(dialog.defaultFormatting).isEqualTo(STANDARD)
    }
  }

  @Test
  fun changingView_savesStyles() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val styleComboBox = it.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View")

      styleComboBox.selectedItem = COMPACT

      assertThat(dialog.sampleEditor.document.text.lines()[0]).isEqualTo("11:00:14.234  D  Sample logcat message 1.")
    }
  }

  @Test
  fun initialize_applyButtonIsDisabled() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.getButton("Apply").isEnabled).isFalse()
    }
  }

  @Test
  fun makeChanges_enablesApplyButton() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getCheckBox("Show timestamp").isSelected = false

      assertThat(it.getButton("Apply").isEnabled).isTrue()
    }
  }

  @Test
  fun clickOk_activatesApplyAction() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getButton("OK").doClick()

      Mockito.verify(applyAction).onApply(dialog)
    }
  }

  @Test
  fun clickCancel_doesNotActivateApplyAction() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getButton("Cancel").doClick()

      Mockito.verify(applyAction, Mockito.never()).onApply(dialog)
    }
  }

  @Test
  fun clickApply_activatesApplyAction() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      // We need to make a change in order to enable the Apply button
      it.getCheckBox("Show timestamp").isSelected = false

      it.getButton("Apply").doClick()

      Mockito.verify(applyAction).onApply(dialog)
    }
  }

  @Test
  fun clickApply_disabledApplyButton() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      // We need to make a change in order to enable the Apply button
      it.getCheckBox("Show timestamp").isSelected = false
      val applyButton = it.getButton("Apply")

      applyButton.doClick()

      assertThat(applyButton.isEnabled).isFalse()
    }
  }

  @Test
  fun clickOk_logsUsage() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      dialogWrapper.getButton("OK").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialog })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialogEvent.newBuilder()
            .setIsApplyButtonUsed(false)
            .setIsShowTimestamp(true)
            .setIsShowDate(true)
            .setIsShowProcessId(true)
            .setIsShowThreadId(true)
            .setIsShowTags(true)
            .setIsShowRepeatedTags(true)
            .setTagWidth(23)
            .setIsShowPackages(true)
            .setIsShowRepeatedPackages(true)
            .setPackageWidth(35)
            .setPreset(Preset.STANDARD)
            .setIsDefaultPreset(true)
            .build())
    }
  }

  @Test
  fun clickOk_afterChanges_logsUsage() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      dialogWrapper.findComponentWithLabel<JComboBox<FormattingOptions.Style>>("View").selectedItem = COMPACT
      dialogWrapper.getButton("OK").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialog })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialogEvent.newBuilder()
            .setIsApplyButtonUsed(false)
            .setIsShowTimestamp(true)
            .setIsShowDate(false)
            .setIsShowProcessId(false)
            .setIsShowThreadId(true)
            .setIsShowTags(false)
            .setIsShowRepeatedTags(true)
            .setTagWidth(23)
            .setIsShowPackages(false)
            .setIsShowRepeatedPackages(true)
            .setPackageWidth(35)
            .setPreset(Preset.COMPACT)
            .setIsDefaultPreset(false)
            .build())
    }
  }

  @Test
  fun clickApply_logsUsage() {
    val dialog = LogcatFormatPresetsDialog(projectRule.project, STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      // We need to make a change in order to enable the Apply button
      dialogWrapper.getCheckBox("Show timestamp").isSelected = false

      dialogWrapper.getButton("Apply").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialog })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialogEvent.newBuilder()
            .setIsApplyButtonUsed(true)
            .setIsShowTimestamp(false)
            .setIsShowDate(true)
            .setIsShowProcessId(true)
            .setIsShowThreadId(true)
            .setIsShowTags(true)
            .setIsShowRepeatedTags(true)
            .setTagWidth(23)
            .setIsShowPackages(true)
            .setIsShowRepeatedPackages(true)
            .setPackageWidth(35)
            .setPreset(Preset.STANDARD)
            .setIsDefaultPreset(true)
            .build())
    }
  }
}
