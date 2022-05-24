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

import com.android.ddmlib.Log.LogLevel.DEBUG
import com.android.ddmlib.Log.LogLevel.ERROR
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.messages.AppNameFormat
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.ProcessThreadFormat
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.PID
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.messages.TimestampFormat
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.TIME
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.PROJECT
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.dialog
import org.jetbrains.annotations.VisibleForTesting
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.BASELINE
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeEvent

private const val MIN_TAG_LENGTH = 10
private const val MAX_TAG_LENGTH = 35
private const val MIN_APP_NAME_LENGTH = 10
private const val MAX_APP_NAME_LENGTH = 45
private const val GAP = 40

private val sampleZoneId = ZoneId.of("GMT")
private val sampleTimestamp = Instant.from(ZonedDateTime.of(2021, 10, 4, 11, 0, 14, 234000000, sampleZoneId))
private val sampleMessages = listOf(
  LogCatMessage(LogCatHeader(DEBUG, 27217, 3814, "com.example.app1", "ExampleTag1", sampleTimestamp), "Sample logcat message 1."),
  LogCatMessage(LogCatHeader(INFO, 27217, 3814, "com.example.app1", "ExampleTag1", sampleTimestamp), "Sample logcat message 2."),
  LogCatMessage(LogCatHeader(WARN, 24395, 24395, "com.example.app2", "ExampleTag2", sampleTimestamp), "Sample logcat message 3."),
  LogCatMessage(LogCatHeader(ERROR, 24395, 24395, "com.example.app2", "ExampleTag2", sampleTimestamp), "Sample logcat multiline\nmessage."),
)

private const val MAX_SAMPLE_DOCUMENT_BUFFER_SIZE = Int.MAX_VALUE

/**
 * A dialog for changing the formatting options.
 */
internal class HeaderFormatOptionsDialog(private val project: Project, formattingOptions: FormattingOptions) : Disposable {
  private val showDateCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.show.date"),
    formattingOptions.timestampFormat.style == DATETIME)

  private val showTimestampCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.show.timestamp"),
    formattingOptions.timestampFormat.enabled,
    showDateCheckBox)

  private val showThreadIdCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.show.tid"),
    formattingOptions.processThreadFormat.style == BOTH)

  private val showProcessIdsCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.show.pids"),
    formattingOptions.processThreadFormat.enabled,
    showThreadIdCheckBox)

  private val hideDuplicateTagsCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.hide.duplicate.tags"),
    formattingOptions.tagFormat.hideDuplicates)

  private val tagSpinnerModel = SpinnerNumberModel(formattingOptions.tagFormat.maxLength, MIN_TAG_LENGTH, MAX_TAG_LENGTH, /* stepSize=*/ 1)
  private val tagWidthLabel = JLabel(LogcatBundle.message("logcat.header.options.tag.width"))

  @VisibleForTesting
  val tagWidthSpinner = JSpinner(tagSpinnerModel)

  private val showTagCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.show.tag"),
    formattingOptions.tagFormat.enabled,
    hideDuplicateTagsCheckBox,
    tagWidthLabel,
    tagWidthSpinner)

  private val hideDuplicateAppNamesCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.hide.duplicate.appnames"),
    formattingOptions.appNameFormat.hideDuplicates)

  private val appNameSpinnerModel =
    SpinnerNumberModel(formattingOptions.appNameFormat.maxLength, MIN_APP_NAME_LENGTH, MAX_APP_NAME_LENGTH, /* stepSize=*/ 1)
  private val appNameWidthLabel = JLabel(LogcatBundle.message("logcat.header.options.appname.width"))

  @VisibleForTesting
  val appNameWidthSpinner = JSpinner(appNameSpinnerModel)

  private val showAppNameCheckBox = createCheckbox(
    LogcatBundle.message("logcat.header.options.show.appname"),
    formattingOptions.appNameFormat.enabled,
    hideDuplicateAppNamesCheckBox,
    appNameWidthLabel,
    appNameWidthSpinner)

  private val sampleFormattingOptions = FormattingOptions()
  private val sampleMessageFormatter = MessageFormatter(sampleFormattingOptions, LogcatColors(), sampleZoneId)

  @VisibleForTesting
  var sampleEditor = createLogcatEditor(project)

  val dialogWrapper = dialog(
    project = project,
    title = LogcatBundle.message("logcat.header.options.title"),
    resizable = true,
    modality = PROJECT,
    panel = createPanel(),
  )

  init {
    Disposer.register(dialogWrapper.disposable, this)
  }

  /**
   * Applies the state of the dialog to a [FormattingOptions] object.
   */
  fun applyTo(formattingOptions: FormattingOptions) {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(if (showDateCheckBox.isSelected) DATETIME else TIME, showTimestampCheckBox.isSelected)
      processThreadFormat = ProcessThreadFormat(if (showThreadIdCheckBox.isSelected) BOTH else PID, showProcessIdsCheckBox.isSelected)
      tagFormat = TagFormat(tagSpinnerModel.value as Int, hideDuplicateTagsCheckBox.isSelected, showTagCheckBox.isSelected)
      appNameFormat = AppNameFormat(
        appNameSpinnerModel.value as Int, hideDuplicateAppNamesCheckBox.isSelected, showAppNameCheckBox.isSelected)
    }
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(sampleEditor)
  }

  private fun createPanel(): JComponent {
    val panel: JComponent = JPanel(null)
    val layout = GroupLayout(panel).apply {
      autoCreateContainerGaps = true
      autoCreateGaps = true
    }

    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addComponent(showTimestampCheckBox)
        .addGroup(
          layout.createSequentialGroup()
            .addGap(GAP)
            .addComponent(showDateCheckBox)
        )
        .addComponent(showProcessIdsCheckBox)
        .addGroup(
          layout.createSequentialGroup()
            .addGap(GAP)
            .addComponent(showThreadIdCheckBox)
        )
        .addComponent(showTagCheckBox)
        .addGroup(
          layout.createSequentialGroup()
            .addGap(GAP)
            .addGroup(
              layout.createParallelGroup()
                .addComponent(hideDuplicateTagsCheckBox)
                .addGroup(
                  layout.createSequentialGroup()
                    .addComponent(tagWidthLabel)
                    .addComponent(tagWidthSpinner)
                )
            )
        )
        .addComponent(showAppNameCheckBox)
        .addGroup(
          layout.createSequentialGroup()
            .addGap(GAP)
            .addGroup(
              layout.createParallelGroup()
                .addComponent(hideDuplicateAppNamesCheckBox)
                .addGroup(
                  layout.createSequentialGroup()
                    .addComponent(appNameWidthLabel)
                    .addComponent(appNameWidthSpinner)
                )
            )
        )
        .addComponent(sampleEditor.contentComponent)
    )

    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addComponent(showTimestampCheckBox)
        .addComponent(showDateCheckBox)
        .addComponent(showProcessIdsCheckBox)
        .addComponent(showThreadIdCheckBox)
        .addComponent(showTagCheckBox)
        .addComponent(hideDuplicateTagsCheckBox)
        .addGroup(
          layout.createParallelGroup(BASELINE)
            .addComponent(tagWidthLabel)
            .addComponent(tagWidthSpinner)
        )
        .addComponent(showAppNameCheckBox)
        .addComponent(hideDuplicateAppNamesCheckBox)
        .addGroup(
          layout.createParallelGroup(BASELINE)
            .addComponent(appNameWidthLabel)
            .addComponent(appNameWidthSpinner)
        )
        .addComponent(sampleEditor.contentComponent)
    )

    val changeListener: (e: ChangeEvent) -> Unit = {
      refreshSampleText()
      dialogWrapper.pack()
    }
    showTimestampCheckBox.addChangeListener(changeListener)
    showDateCheckBox.addChangeListener(changeListener)
    showProcessIdsCheckBox.addChangeListener(changeListener)
    showThreadIdCheckBox.addChangeListener(changeListener)
    showTagCheckBox.addChangeListener(changeListener)
    hideDuplicateTagsCheckBox.addChangeListener(changeListener)
    tagWidthSpinner.addChangeListener(changeListener)
    showAppNameCheckBox.addChangeListener(changeListener)
    hideDuplicateAppNamesCheckBox.addChangeListener(changeListener)
    appNameSpinnerModel.addChangeListener(changeListener)

    refreshSampleText()
    panel.layout = layout
    return panel
  }

  private fun createCheckbox(text: String, value: Boolean, vararg children: JComponent) = JCheckBox(text).apply {
    children.forEach { it.isEnabled = value }
    addItemListener {
      children.forEach { it.isEnabled = isSelected }
    }
    isSelected = value
  }


  private fun refreshSampleText() {
    applyTo(sampleFormattingOptions)
    val textAccumulator = TextAccumulator()
    sampleMessageFormatter.formatMessages(textAccumulator, sampleMessages)
    sampleEditor.document.setReadOnly(false)
    sampleEditor.document.setText("")
    DocumentAppender(project, sampleEditor.document, MAX_SAMPLE_DOCUMENT_BUFFER_SIZE).appendToDocument(textAccumulator)
    sampleEditor.document.setReadOnly(true)
  }
}
