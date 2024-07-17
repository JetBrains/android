package com.android.tools.idea.logcat.messages

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions.Companion.DEFAULT_COMPACT
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions.Companion.DEFAULT_STANDARD
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.LogcatFormatDialog.ApplyAction
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.PID
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.TIME
import com.android.tools.idea.logcat.util.getButton
import com.android.tools.idea.logcat.util.getCheckBox
import com.android.tools.idea.logcat.util.logcatEvents
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration.Preset
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSpinner
import kotlin.test.fail
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/** Tests for [LogcatFormatDialog] */
@RunsInEdt
class LogcatFormatDialogTest {
  private val projectRule = ProjectRule()
  private val usageTrackerRule = UsageTrackerRule()
  private val disposableRule = DisposableRule()
  private val mockApplyAction = mock<ApplyAction>()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      EdtRule(),
      usageTrackerRule,
      disposableRule,
      HeadlessDialogRule(),
      ApplicationServiceRule(
        AndroidLogcatFormattingOptions::class.java,
        AndroidLogcatFormattingOptions(),
      ),
    )

  @After
  fun tearDown() {
    STANDARD.formattingOptions.apply {
      timestampFormat = DEFAULT_STANDARD.timestampFormat
      processThreadFormat = DEFAULT_STANDARD.processThreadFormat
      tagFormat = DEFAULT_STANDARD.tagFormat
      appNameFormat = DEFAULT_STANDARD.appNameFormat
      levelFormat = DEFAULT_STANDARD.levelFormat
    }
    COMPACT.formattingOptions.apply {
      timestampFormat = DEFAULT_COMPACT.timestampFormat
      processThreadFormat = DEFAULT_COMPACT.processThreadFormat
      tagFormat = DEFAULT_COMPACT.tagFormat
      appNameFormat = DEFAULT_COMPACT.appNameFormat
      levelFormat = DEFAULT_COMPACT.levelFormat
    }
  }

  @Test
  fun initialState_timestampDisabled() {
    STANDARD.formattingOptions.timestampFormat = TimestampFormat(DATETIME, enabled = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showTimestamp().isSelected).isFalse()
      assertThat(it.timestampFormat().isEnabled).isFalse()
      assertThat(it.timestampFormat().selectedItem).isEqualTo(DATETIME)
    }
  }

  @Test
  fun initialState_timestampDateTime() {
    STANDARD.formattingOptions.timestampFormat = TimestampFormat(DATETIME, enabled = true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showTimestamp().isSelected).isTrue()
      assertThat(it.timestampFormat().isEnabled).isTrue()
      assertThat(it.timestampFormat().selectedItem).isEqualTo(DATETIME)
    }
  }

  @Test
  fun initialState_timestampTime() {
    STANDARD.formattingOptions.timestampFormat = TimestampFormat(TIME, enabled = true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showTimestamp().isSelected).isTrue()
      assertThat(it.timestampFormat().isEnabled).isTrue()
      assertThat(it.timestampFormat().selectedItem).isEqualTo(TIME)
    }
  }

  @Test
  fun toggle_timestamp() {
    STANDARD.formattingOptions.timestampFormat = TimestampFormat(enabled = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showTimestamp().isSelected = true
      assertThat(it.timestampFormat().isEnabled).isTrue()
      it.showTimestamp().isSelected = false
      assertThat(it.timestampFormat().isEnabled).isFalse()
    }
  }

  @Test
  fun apply_timestamp() {
    STANDARD.formattingOptions.timestampFormat = TimestampFormat(enabled = false)
    val options = FormattingOptions()
    val dialog =
      logcatFormatDialogNew(
        initialFormatting = STANDARD,
        applyAction = { standardOptions, _, _ ->
          options.timestampFormat = standardOptions.timestampFormat
        },
      )

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.showTimestamp().isSelected = true
      it.timestampFormat().selectedItem = DATETIME
      ui.clickApply()
      assertThat(options.timestampFormat).isEqualTo(TimestampFormat(DATETIME, enabled = true))

      it.showTimestamp().isSelected = false
      ui.clickApply()
      assertThat(options.timestampFormat).isEqualTo(TimestampFormat(DATETIME, enabled = false))

      it.showTimestamp().isSelected = true
      it.timestampFormat().selectedItem = TIME
      ui.clickApply()
      assertThat(options.timestampFormat).isEqualTo(TimestampFormat(TIME, enabled = true))
    }
  }

  @Test
  fun initialState_idsDisabled() {
    STANDARD.formattingOptions.processThreadFormat = ProcessThreadFormat(BOTH, enabled = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showPid().isSelected).isFalse()
      assertThat(it.showTid().isEnabled).isFalse()
      assertThat(it.showTid().isSelected).isTrue()
    }
  }

  @Test
  fun initialState_idsBoth() {
    STANDARD.formattingOptions.processThreadFormat = ProcessThreadFormat(BOTH, enabled = true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showPid().isSelected).isTrue()
      assertThat(it.showTid().isEnabled).isTrue()
      assertThat(it.showTid().isSelected).isTrue()
    }
  }

  @Test
  fun initialState_idsPid() {
    STANDARD.formattingOptions.processThreadFormat = ProcessThreadFormat(PID, enabled = true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showPid().isSelected).isTrue()
      assertThat(it.showTid().isEnabled).isTrue()
      assertThat(it.showTid().isSelected).isFalse()
    }
  }

  @Test
  fun toggle_ids() {
    STANDARD.formattingOptions.processThreadFormat = ProcessThreadFormat(BOTH, enabled = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showPid().isSelected = true
      assertThat(it.showTid().isEnabled).isTrue()

      it.showPid().isSelected = false
      assertThat(it.showTid().isEnabled).isFalse()
    }
  }

  @Test
  fun apply_ids() {
    STANDARD.formattingOptions.processThreadFormat = ProcessThreadFormat(PID, enabled = false)
    val options = FormattingOptions()
    val dialog =
      logcatFormatDialogNew(
        initialFormatting = STANDARD,
        applyAction = { standardOptions, _, _ ->
          options.processThreadFormat = standardOptions.processThreadFormat
        },
      )

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.showPid().isSelected = true
      it.showTid().isSelected = true
      ui.clickApply()
      assertThat(options.processThreadFormat).isEqualTo(ProcessThreadFormat(BOTH, enabled = true))

      it.showPid().isSelected = false
      ui.clickApply()
      assertThat(options.processThreadFormat).isEqualTo(ProcessThreadFormat(BOTH, enabled = false))

      it.showPid().isSelected = true
      it.showTid().isSelected = false
      ui.clickApply()
      assertThat(options.processThreadFormat).isEqualTo(ProcessThreadFormat(PID, enabled = true))
    }
  }

  @Test
  fun initialState_tagDisabled() {
    STANDARD.formattingOptions.tagFormat =
      TagFormat(maxLength = 10, hideDuplicates = false, enabled = false, colorize = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showTag().isSelected).isFalse()
      assertThat(it.showRepeatedTags().isEnabled).isFalse()
      assertThat(it.showRepeatedTags().isSelected).isTrue()
      assertThat(it.colorize().isEnabled).isFalse()
      assertThat(it.colorize().isSelected).isFalse()
      assertThat(it.tagWidthLabel().isEnabled).isFalse()
      assertThat(it.tagWidth().isEnabled).isFalse()
      assertThat(it.tagWidth().value).isEqualTo(10)
    }
  }

  @Test
  fun initialState_tagEnabled() {
    STANDARD.formattingOptions.tagFormat =
      TagFormat(maxLength = 20, hideDuplicates = true, enabled = true, colorize = true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showTag().isSelected).isTrue()
      assertThat(it.showRepeatedTags().isEnabled).isTrue()
      assertThat(it.showRepeatedTags().isSelected).isFalse()
      assertThat(it.colorize().isEnabled).isTrue()
      assertThat(it.colorize().isSelected).isTrue()
      assertThat(it.tagWidthLabel().isEnabled).isTrue()
      assertThat(it.tagWidth().isEnabled).isTrue()
      assertThat(it.tagWidth().value).isEqualTo(20)
    }
  }

  @Test
  fun toggle_tag() {
    STANDARD.formattingOptions.tagFormat =
      TagFormat(maxLength = 10, hideDuplicates = false, enabled = false, colorize = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showTag().isSelected = true
      assertThat(it.tagWidth().isEnabled).isTrue()
      assertThat(it.tagWidthLabel().isEnabled).isTrue()
      assertThat(it.showRepeatedTags().isEnabled).isTrue()
      assertThat(it.colorize().isEnabled).isTrue()

      it.showTag().isSelected = false
      assertThat(it.tagWidth().isEnabled).isFalse()
      assertThat(it.tagWidthLabel().isEnabled).isFalse()
      assertThat(it.showRepeatedTags().isEnabled).isFalse()
      assertThat(it.colorize().isEnabled).isFalse()
    }
  }

  @Test
  fun apply_tag() {
    STANDARD.formattingOptions.tagFormat =
      TagFormat(maxLength = 10, hideDuplicates = false, enabled = false, colorize = false)
    val options = FormattingOptions()
    val dialog =
      logcatFormatDialogNew(
        initialFormatting = STANDARD,
        applyAction = { standardOptions, _, _ -> options.tagFormat = standardOptions.tagFormat },
      )

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.showTag().isSelected = true
      it.showRepeatedTags().isSelected = false
      it.colorize().isSelected = true
      it.tagWidth().value = 20
      ui.clickApply()
      assertThat(options.tagFormat)
        .isEqualTo(
          TagFormat(maxLength = 20, hideDuplicates = true, enabled = true, colorize = true)
        )

      it.showTag().isSelected = false
      it.showRepeatedTags().isSelected = true
      it.colorize().isSelected = false
      it.tagWidth().value = 10
      ui.clickApply()
      assertThat(options.tagFormat)
        .isEqualTo(
          TagFormat(maxLength = 10, hideDuplicates = false, enabled = false, colorize = false)
        )
    }
  }

  @Test
  fun initialState_packageDisabled() {
    STANDARD.formattingOptions.appNameFormat =
      AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showPackage().isSelected).isFalse()
      assertThat(it.showRepeatedPackages().isEnabled).isFalse()
      assertThat(it.showRepeatedPackages().isSelected).isTrue()
      assertThat(it.packageWidthLabel().isEnabled).isFalse()
      assertThat(it.packageWidth().isEnabled).isFalse()
      assertThat(it.packageWidth().value).isEqualTo(10)
    }
  }

  @Test
  fun initialState_packageEnabled() {
    STANDARD.formattingOptions.appNameFormat =
      AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showPackage().isSelected).isTrue()
      assertThat(it.showRepeatedPackages().isEnabled).isTrue()
      assertThat(it.showRepeatedPackages().isSelected).isFalse()
      assertThat(it.packageWidthLabel().isEnabled).isTrue()
      assertThat(it.packageWidth().isEnabled).isTrue()
      assertThat(it.packageWidth().value).isEqualTo(20)
    }
  }

  @Test
  fun toggle_package() {
    STANDARD.formattingOptions.appNameFormat =
      AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showPackage().isSelected = true
      assertThat(it.packageWidth().isEnabled).isTrue()
      assertThat(it.packageWidthLabel().isEnabled).isTrue()
      assertThat(it.showRepeatedPackages().isEnabled).isTrue()

      it.showPackage().isSelected = false
      assertThat(it.packageWidth().isEnabled).isFalse()
      assertThat(it.packageWidthLabel().isEnabled).isFalse()
      assertThat(it.showRepeatedPackages().isEnabled).isFalse()
    }
  }

  @Test
  fun apply_package() {
    STANDARD.formattingOptions.appNameFormat =
      AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false)
    val options = FormattingOptions()
    val dialog =
      logcatFormatDialogNew(
        initialFormatting = STANDARD,
        applyAction = { standardOptions, _, _ ->
          options.appNameFormat = standardOptions.appNameFormat
        },
      )

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.showPackage().isSelected = true
      it.showRepeatedPackages().isSelected = false
      it.packageWidth().value = 20
      ui.clickApply()
      assertThat(options.appNameFormat)
        .isEqualTo(AppNameFormat(maxLength = 20, hideDuplicates = true, enabled = true))

      it.showPackage().isSelected = false
      it.showRepeatedPackages().isSelected = true
      it.packageWidth().value = 10
      ui.clickApply()
      assertThat(options.appNameFormat)
        .isEqualTo(AppNameFormat(maxLength = 10, hideDuplicates = false, enabled = false))
    }
  }

  @Test
  fun initialState_levelsDisabled() {
    STANDARD.formattingOptions.levelFormat = LevelFormat(false)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showLevel().isSelected).isFalse()
    }
  }

  @Test
  fun initialState_levelsEnabled() {
    STANDARD.formattingOptions.levelFormat = LevelFormat(true)
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.showLevel().isSelected).isTrue()
    }
  }

  @Test
  fun apply_levels() {
    STANDARD.formattingOptions.levelFormat = LevelFormat(false)
    val options = FormattingOptions()
    val dialog =
      logcatFormatDialogNew(
        initialFormatting = STANDARD,
        applyAction = { standardOptions, _, _ -> options.levelFormat = standardOptions.levelFormat },
      )

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.showLevel().isSelected = true
      ui.clickApply()
      assertThat(options.levelFormat.enabled).isTrue()

      it.showLevel().isSelected = false
      ui.clickApply()
      assertThat(options.levelFormat.enabled).isFalse()
    }
  }

  @Test
  fun previewText() {
    STANDARD.formattingOptions.apply {
      timestampFormat = TimestampFormat(TIME, enabled = false)
      processThreadFormat = ProcessThreadFormat(PID, enabled = false)
      tagFormat = TagFormat(maxLength = 23, hideDuplicates = false, enabled = false)
      appNameFormat = AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false)
      levelFormat = LevelFormat(false)
    }
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.previewEditor.document.text.trimEnd())
        .isEqualTo(
          """
          Sample logcat message 1.
          Sample logcat message 2.
          Sample logcat message 3.
          Sample logcat multiline
          message.
        """
            .trimIndent()
        )

      it.showTimestamp().isSelected = true
      it.showPid().isSelected = true
      it.showTag().isSelected = true
      it.showPackage().isSelected = true
      it.showLevel().isSelected = true

      assertThat(dialog.previewEditor.document.text.trimEnd())
        .isEqualTo(
          """
        11:00:14.234 27217 ExampleTag1             com.example.app1                     D  Sample logcat message 1.
        11:00:14.234 27217 ExampleTag1             com.example.app1                     I  Sample logcat message 2.
        11:00:14.234 24395 ExampleTag2             com.example.app2                     W  Sample logcat message 3.
        11:00:14.234 24395 ExampleTag2             com.example.app2                     E  Sample logcat multiline
                                                                                           message.
      """
            .trimIndent()
        )
    }
  }

  @Test
  fun previewText_sameMaxLength() {
    STANDARD.formattingOptions.apply {
      timestampFormat = TimestampFormat(TIME, enabled = false)
      processThreadFormat = ProcessThreadFormat(PID, enabled = false)
      tagFormat = TagFormat(maxLength = 23, hideDuplicates = false, enabled = false)
      appNameFormat = AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false)
    }
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val maxLineLength = dialog.previewEditor.document.text.lines().maxOf(String::length)

      it.showTimestamp().isSelected = true
      it.showPid().isSelected = true
      it.showTag().isSelected = true
      it.showPackage().isSelected = true
      it.showLevel().isSelected = true

      assertThat(dialog.previewEditor.document.text.lines().maxOf(String::length))
        .isEqualTo(maxLineLength)
    }
  }

  @Test
  fun clickOk_activatesApplyAction() {
    val applyAction = mock<ApplyAction>()
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD, applyAction = applyAction)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getButton("OK").doClick()

      verify(applyAction).apply(any(), any(), any())
    }
  }

  @Test
  fun clickCancel_doesNotActivateApplyAction() {
    val applyAction = mock<ApplyAction>()
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD, applyAction = applyAction)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.getButton("Cancel").doClick()

      verify(applyAction, never()).apply(any(), any(), any())
    }
  }

  @Test
  fun clickOk_logsUsage() {
    STANDARD.formattingOptions.apply {
      timestampFormat = TimestampFormat(DATETIME, enabled = true)
      processThreadFormat = ProcessThreadFormat(ProcessThreadFormat.Style.BOTH, enabled = true)
      tagFormat = TagFormat(maxLength = 23, hideDuplicates = false, enabled = true)
      appNameFormat = AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = true)
      levelFormat = LevelFormat(enabled = true)
    }
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      dialogWrapper.getButton("OK").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialogApplied })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialog.newBuilder()
            .setIsApplyButtonUsed(false)
            .setConfiguration(
              LogcatUsageEvent.LogcatFormatConfiguration.newBuilder()
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
                .build()
            )
            .build()
        )
    }
  }

  @Test
  fun clickOk_afterChanges_logsUsage() {
    val dialog = logcatFormatDialogNew(initialFormatting = STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { it ->
      it.formattingStyle().selectedItem = COMPACT
      it.showTimestamp().isSelected = false
      it.timestampFormat().selectedItem = TIME
      it.showPid().isSelected = false
      it.showTid().isSelected = false
      it.showTag().isSelected = false
      it.showRepeatedTags().isSelected = true
      it.tagWidth().value = 10
      it.showPackage().isSelected = false
      it.showRepeatedPackages().isSelected = true
      it.packageWidth().value = 10
      it.getButton("OK").doClick()

      assertThat(usageTrackerRule.logcatEvents().map { it.formatDialogApplied })
        .containsExactly(
          LogcatUsageEvent.LogcatFormatDialog.newBuilder()
            .setIsApplyButtonUsed(false)
            .setConfiguration(
              LogcatUsageEvent.LogcatFormatConfiguration.newBuilder()
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
                .setPreset(Preset.COMPACT)
                .build()
            )
            .build()
        )
    }
  }

  @Test
  fun initialize_presets() {
    val args =
      listOf(STANDARD to STANDARD, STANDARD to COMPACT, COMPACT to STANDARD, COMPACT to COMPACT)
    for ((initialFormatting, defaultFormatting) in args) {
      val dialog = logcatFormatDialogNew(initialFormatting, defaultFormatting)
      createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
        val tagsCheckBox = it.getCheckBox("Show tag")
        assertThat(it.formattingStyle().selectedItem).isEqualTo(initialFormatting)
        assertThat(it.setAsDefault().isSelected).isEqualTo(initialFormatting == defaultFormatting)
        assertThat(tagsCheckBox.isSelected)
          .isEqualTo(initialFormatting.formattingOptions.tagFormat.enabled)
      }
    }
  }

  @Test
  fun changingPreset_changesComponents() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.formattingStyle().selectedItem = COMPACT

      assertThat(it.showTag().isSelected).isFalse()
    }
  }

  @Test
  fun changingPreset_changesSetAsDefault_whenIsDefault() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.formattingStyle().selectedItem = COMPACT

      assertThat(it.setAsDefault().isSelected).isFalse()
    }
  }

  @Test
  fun changingPreset_changesSetAsDefault_whenIsNotDefault() {
    val dialog = logcatFormatDialogNew(STANDARD, COMPACT)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.formattingStyle().selectedItem = COMPACT

      assertThat(it.setAsDefault().isSelected).isTrue()
    }
  }

  @Test
  fun changingPreset_changesSetAsDefault_whenCheckboxInteracted() {
    val dialog = logcatFormatDialogNew(COMPACT, STANDARD)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.setAsDefault().isSelected = true
      it.formattingStyle().selectedItem = STANDARD

      assertThat(it.setAsDefault().isSelected).isFalse()
    }
  }

  @Test
  fun changingView_changesPreviewText() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.formattingStyle().selectedItem = COMPACT

      assertThat(dialog.previewEditor.document.text.trimEnd())
        .isEqualTo(
          """
          11:00:14.234  D  Sample logcat message 1.
          11:00:14.234  I  Sample logcat message 2.
          11:00:14.234  W  Sample logcat message 3.
          11:00:14.234  E  Sample logcat multiline
                           message.
      """
            .trimIndent()
        )
    }
  }

  @Test
  fun setAsDefaultCheckBox() {
    var style: FormattingOptions.Style? = null
    val dialog =
      logcatFormatDialogNew(
        COMPACT,
        STANDARD,
        applyAction = { _, _, defaultStyle -> style = defaultStyle },
      )
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.setAsDefault().isSelected = true
      ui.clickApply()
      assertThat(style).isEqualTo(COMPACT)

      it.formattingStyle().selectedItem = STANDARD
      it.setAsDefault().isSelected = true
      ui.clickApply()
      assertThat(style).isEqualTo(STANDARD)
    }
  }

  @Test
  fun changingView_savesStyles() {
    var standard: FormattingOptions? = null
    var compact: FormattingOptions? = null

    val dialog =
      logcatFormatDialogNew(
        STANDARD,
        STANDARD,
        applyAction = { standardOptions, compactOptions, _ ->
          standard = standardOptions
          compact = compactOptions
        },
      )

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      val ui = FakeUi(it.rootPane)

      it.showTag().isSelected = false
      it.formattingStyle().selectedItem = COMPACT
      it.showTag().isSelected = true
      ui.clickApply()

      assertThat(standard?.tagFormat?.enabled).isFalse()
      assertThat(compact?.tagFormat?.enabled).isTrue()
    }
  }

  @Test
  fun initialize_applyButtonIsDisabled() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(it.getButton("Apply").isEnabled).isFalse()
    }
  }

  @Test
  fun makeChanges_enablesApplyButton() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showTimestamp().isSelected = false

      assertThat(it.getButton("Apply").isEnabled).isTrue()
    }
  }

  @Test
  fun setAsDefault_enablesApplyButton() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.setAsDefault().isSelected = false

      assertThat(it.getButton("Apply").isEnabled).isTrue()
    }
  }

  @Test
  fun clickApply_activatesApplyAction() {
    val applyAction = mock<ApplyAction>()
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD, applyAction)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      // We need to make a change in order to enable the Apply button
      it.showTimestamp().isSelected = false

      it.getButton("Apply").doClick()

      verify(applyAction).apply(any(), any(), any())
    }
  }

  @Test
  fun clickApply_disabledApplyButton() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      // We need to make a change in order to enable the Apply button
      it.showTimestamp().isSelected = false
      val applyButton = it.getButton("Apply")

      applyButton.doClick()

      assertThat(applyButton.isEnabled).isFalse()
    }
  }

  @Test
  fun restoreDefault_standard() {
    val dialog = logcatFormatDialogNew(STANDARD, STANDARD)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showTimestamp().isSelected = false

      it.getButton("Restore default").doClick()

      assertThat(it.showTimestamp().isSelected).isTrue()
    }
  }

  @Test
  fun restoreDefault_compact() {
    val dialog = logcatFormatDialogNew(COMPACT, COMPACT)
    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      it.showTimestamp().isSelected = false

      it.getButton("Restore default").doClick()

      assertThat(it.showTimestamp().isSelected).isTrue()
    }
  }

  private fun logcatFormatDialogNew(
    initialFormatting: FormattingOptions.Style = STANDARD,
    defaultFormatting: FormattingOptions.Style = STANDARD,
    applyAction: ApplyAction = mockApplyAction,
  ): LogcatFormatDialog =
    LogcatFormatDialog(projectRule.project, initialFormatting, defaultFormatting, applyAction)

  private inline fun <reified T : JComponent> DialogWrapper.findComponent(name: String): T {
    return TreeWalker(rootPane).descendants().filterIsInstance<T>().find { it.name == name }
      ?: fail("${T::class.simpleName} named $name was not found")
  }

  private fun FakeUi.clickApply() =
    clickOn(getComponent<JButton> { button -> button.text == "Apply" })

  private fun DialogWrapper.formattingStyle() =
    findComponent<JComboBox<FormattingOptions.Style>>("formattingStyle")

  private fun DialogWrapper.setAsDefault() = findComponent<JCheckBox>("setAsDefault")

  private fun DialogWrapper.showTimestamp() = findComponent<JCheckBox>("showTimestamp")

  private fun DialogWrapper.timestampFormat() =
    findComponent<JComboBox<TimestampFormat.Style>>("timestampFormat")

  private fun DialogWrapper.showPid() = findComponent<JCheckBox>("showPid")

  private fun DialogWrapper.showTid() = findComponent<JCheckBox>("showTid")

  private fun DialogWrapper.showTag() = findComponent<JCheckBox>("showTag")

  private fun DialogWrapper.tagWidth() = findComponent<JSpinner>("tagWidth")

  private fun DialogWrapper.tagWidthLabel() = findComponent<JLabel>("tagWidthLabel")

  private fun DialogWrapper.showRepeatedTags() = findComponent<JCheckBox>("showRepeatedTags")

  private fun DialogWrapper.colorize() = findComponent<JCheckBox>("colorizeTags")

  private fun DialogWrapper.showPackage() = findComponent<JCheckBox>("showPackage")

  private fun DialogWrapper.packageWidth() = findComponent<JSpinner>("packageWidth")

  private fun DialogWrapper.packageWidthLabel() = findComponent<JLabel>("packageWidthLabel")

  private fun DialogWrapper.showRepeatedPackages() =
    findComponent<JCheckBox>("showRepeatedPackages")

  private fun DialogWrapper.showLevel() = findComponent<JCheckBox>("showLevel")
}
