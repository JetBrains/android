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

import com.android.tools.idea.logcat.LogcatBundle.message
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.FORMAT_DIALOG
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ItemEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import javax.swing.event.ChangeEvent
import kotlin.reflect.KMutableProperty0

private const val MIN_TAG_LENGTH = 10
private const val MAX_TAG_LENGTH = 35
private const val MIN_APP_NAME_LENGTH = 10
private const val MAX_APP_NAME_LENGTH = 45

private val sampleZoneId = ZoneId.of("GMT")
private val sampleTimestamp = Instant.from(ZonedDateTime.of(2021, 10, 4, 11, 0, 14, 234000000, sampleZoneId))
private val sampleMessages = listOf(
  LogcatMessage(LogcatHeader(LogLevel.DEBUG, 27217, 3814, "com.example.app1", "", "ExampleTag1", sampleTimestamp),
                "Sample logcat message 1."),
  LogcatMessage(LogcatHeader(LogLevel.INFO, 27217, 3814, "com.example.app1", "", "ExampleTag1", sampleTimestamp),
                "Sample logcat message 2."),
  LogcatMessage(LogcatHeader(LogLevel.WARN, 24395, 24395, "com.example.app2", "", "ExampleTag2", sampleTimestamp),
                "Sample logcat message 3."),
  LogcatMessage(LogcatHeader(LogLevel.ERROR, 24395, 24395, "com.example.app2", "", "ExampleTag2", sampleTimestamp),
                "Sample logcat multiline\nmessage."),
)

private val MAX_SAMPLE_DOCUMENT_TEXT_LENGTH =
  DATETIME.width +
  ProcessThreadFormat.Style.BOTH.width +
  MAX_TAG_LENGTH + 1 +
  MAX_APP_NAME_LENGTH + 1 +
  3 + 1 +
  "Sample logcat message #.".length
private const val MAX_SAMPLE_DOCUMENT_BUFFER_SIZE = Int.MAX_VALUE

/**
 * A base class for a Formatting Options dialog.
 */
internal abstract class LogcatFormatDialogBase(
  private val project: Project,
  private val applyAction: ApplyAction
) : Disposable {
  private val components = mutableListOf<JComponent>()

  private lateinit var showTimestampCheckbox: JBCheckBox
  private lateinit var timestampStyleComboBox: ComboBox<TimestampFormat.Style>
  private lateinit var showPidCheckbox: JBCheckBox
  private lateinit var includeTidCheckbox: JBCheckBox
  private lateinit var showTagCheckbox: JBCheckBox
  private lateinit var tagWidthSpinner: JBIntSpinner
  private lateinit var showRepeatedTagsCheckbox: JBCheckBox
  private lateinit var showPackageCheckbox: JBCheckBox
  private lateinit var packageWidthSpinner: JBIntSpinner
  private lateinit var showRepeatedPackagesCheckbox: JBCheckBox
  private lateinit var showLevelCheckbox: JBCheckBox

  @VisibleForTesting
  var sampleEditor = createLogcatEditor(project).apply {
    scrollPane.horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
  }

  private val sampleFormattingOptions = FormattingOptions()
  private val sampleMessageFormatter = MessageFormatter(LogcatColors(), sampleZoneId)

  val dialogWrapper: DialogWrapper by lazy {
    createDialogWrapper().apply {
      Disposer.register(disposable, this@LogcatFormatDialogBase)
      onComponentsChanged(isUserAction = false)
      pack()
    }
  }

  abstract fun createDialogWrapper(): DialogWrapper

  final override fun dispose() {
    EditorFactory.getInstance().releaseEditor(sampleEditor)
  }

  /**
   * Applies the state of the dialog to a [FormattingOptions] object.
   */
  fun applyToFormattingOptions(formattingOptions: FormattingOptions) {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(timestampStyleComboBox.item, showTimestampCheckbox.isSelected)
      processThreadFormat = ProcessThreadFormat(
        if (includeTidCheckbox.isSelected) ProcessThreadFormat.Style.BOTH else ProcessThreadFormat.Style.PID,
        showPidCheckbox.isSelected)
      tagFormat = TagFormat(tagWidthSpinner.number, !showRepeatedTagsCheckbox.isSelected, showTagCheckbox.isSelected)
      appNameFormat = AppNameFormat(packageWidthSpinner.number, !showRepeatedPackagesCheckbox.isSelected, showPackageCheckbox.isSelected)
      levelFormat = LevelFormat(showLevelCheckbox.isSelected)
    }
  }

  @VisibleForTesting
  fun applyToComponents(currentOptions: FormattingOptions) {
    showTimestampCheckbox.isSelected = currentOptions.timestampFormat.enabled
    timestampStyleComboBox.selectedItem = currentOptions.timestampFormat.style
    showPidCheckbox.isSelected = currentOptions.processThreadFormat.enabled
    includeTidCheckbox.isSelected = currentOptions.processThreadFormat.style == ProcessThreadFormat.Style.BOTH
    showTagCheckbox.isSelected = currentOptions.tagFormat.enabled
    tagWidthSpinner.number = currentOptions.tagFormat.maxLength
    showRepeatedTagsCheckbox.isSelected = !currentOptions.tagFormat.hideDuplicates
    showPackageCheckbox.isSelected = currentOptions.appNameFormat.enabled
    packageWidthSpinner.number = currentOptions.appNameFormat.maxLength
    showRepeatedPackagesCheckbox.isSelected = !currentOptions.appNameFormat.hideDuplicates
    showLevelCheckbox.isSelected = currentOptions.levelFormat.enabled
  }

  protected fun createPanel(formattingOptions: FormattingOptions): DialogPanel {
    val panel = panel {
      createComponents(this, formattingOptions)
    }
    val refresh: () -> Unit = { onComponentsChanged(isUserAction = true) }
    val itemEventListener: (e: ItemEvent) -> Unit = { refresh() }
    val changeEventListener: (e: ChangeEvent) -> Unit = { refresh() }

    components.forEach {
      when (it) {
        is JBCheckBox -> it.addItemListener(itemEventListener)
        is JBIntSpinner -> it.addChangeListener(changeEventListener)
        is ComboBox<*> -> it.addItemListener(itemEventListener)
      }
    }

    return panel
  }

  protected open fun createComponents(layoutBuilder: Panel, formattingOptions: FormattingOptions) {
    //.align(AlignY.TOP) is not required while group panels next to each other are the same height
    layoutBuilder.panel {
      row {
        panel {
          timestampGroup(formattingOptions.timestampFormat)
        }.gap(RightGap.COLUMNS)
          .resizableColumn()
        panel {
          processIdGroup(formattingOptions.processThreadFormat)
        }.resizableColumn()
      }.layout(RowLayout.PARENT_GRID)

      row {
        panel {
          tagGroup(formattingOptions.tagFormat)
        }.gap(RightGap.COLUMNS)
          .resizableColumn()
        panel {
          packageNameGroup(formattingOptions.appNameFormat)
        }.resizableColumn()
      }.layout(RowLayout.PARENT_GRID)

      levelGroup(formattingOptions.levelFormat.enabled)
      footerGroup()
    }
  }

  protected fun onApply(isApplyButton: Boolean) {
    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(FORMAT_DIALOG)
        .setFormatDialogApplied(getLogcatFormatDialogEvent().setIsApplyButtonUsed(isApplyButton))
    )
    applyAction.onApply(this)
  }

  protected open fun getLogcatFormatDialogEvent(): LogcatUsageEvent.LogcatFormatDialog.Builder =
    LogcatUsageEvent.LogcatFormatDialog.newBuilder()
      .setConfiguration(LogcatFormatConfiguration.newBuilder()
                          .setIsShowTimestamp(showTimestampCheckbox.isSelected)
                          .setIsShowDate(timestampStyleComboBox.item == DATETIME)
                          .setIsShowProcessId(showPidCheckbox.isSelected)
                          .setIsShowThreadId(includeTidCheckbox.isSelected)
                          .setIsShowTags(showTagCheckbox.isSelected)
                          .setIsShowRepeatedTags(showRepeatedTagsCheckbox.isSelected)
                          .setTagWidth(tagWidthSpinner.number)
                          .setIsShowPackages(showPackageCheckbox.isSelected)
                          .setIsShowRepeatedPackages(showRepeatedPackagesCheckbox.isSelected)
                          .setPackageWidth(packageWidthSpinner.number)
        // TODO(aalbert): Add usage for Show Levels
      )

  private fun Panel.timestampGroup(format: TimestampFormat) {
    group(message("logcat.header.options.timestamp.title")) {
      lateinit var showTimestamp: Cell<JBCheckBox>
      row { showTimestamp = checkBox(message("logcat.header.options.timestamp.show"), format.enabled, ::showTimestampCheckbox) }
      indent {
        row {
          panel {
            row(message("logcat.header.options.timestamp.format")) {
              val model = DefaultComboBoxModel(TimestampFormat.Style.values())
              val renderer = SimpleListCellRenderer.create<TimestampFormat.Style?>("") { it?.displayName }
              comboBox(model, renderer, format.style, ::timestampStyleComboBox)
            }
          }
        }.enabledIf(showTimestamp.selected)
      }
    }
  }

  private fun Panel.processIdGroup(format: ProcessThreadFormat) {
    group(message("logcat.header.options.process.id.title")) {
      lateinit var showPid: Cell<JBCheckBox>
      row { showPid = checkBox(message("logcat.header.options.process.id.show.pid"), format.enabled, ::showPidCheckbox) }
      indent {
        row {
          val includeTid = format.style == ProcessThreadFormat.Style.BOTH
          checkBox(message("logcat.header.options.process.id.show.tid"), includeTid, ::includeTidCheckbox)
            .enabledIf(showPid.selected)
        }
      }
    }
  }

  private fun Panel.tagGroup(format: TagFormat) {
    group(message("logcat.header.options.tag.title")) {

      lateinit var showTags: Cell<JBCheckBox>
      row { showTags = checkBox(message("logcat.header.options.tag.show"), format.enabled, ::showTagCheckbox) }
      indent {
        row {
          panel {
            row(message("logcat.header.options.tag.width")) {
              spinner(format.maxLength, MIN_TAG_LENGTH, MAX_TAG_LENGTH, ::tagWidthSpinner)
            }
          }
        }.enabledIf(showTags.selected)
        row {
          checkBox(message("logcat.header.options.tag.show.repeated"), !format.hideDuplicates, ::showRepeatedTagsCheckbox)
        }.enabledIf(showTags.selected)
      }
    }
  }

  private fun Panel.packageNameGroup(format: AppNameFormat) {
    group(message("logcat.header.options.package.title")) {

      lateinit var showPackageNames: Cell<JBCheckBox>
      row {
        showPackageNames = checkBox(message("logcat.header.options.package.show"), format.enabled, ::showPackageCheckbox)
      }
      indent {
        row {
          panel {
            row(message("logcat.header.options.package.width")) {
              spinner(format.maxLength, MIN_APP_NAME_LENGTH, MAX_APP_NAME_LENGTH, ::packageWidthSpinner)
            }
          }
        }.enabledIf(showPackageNames.selected)
        row {
          checkBox(message("logcat.header.options.package.show.repeated"), !format.hideDuplicates, ::showRepeatedPackagesCheckbox)
        }.enabledIf(showPackageNames.selected)
      }
    }
  }

  private fun Panel.levelGroup(showLevels: Boolean) {
    group(message("logcat.header.options.level.title")) {
      row {
        checkBox(message("logcat.header.options.level.show"), showLevels, ::showLevelCheckbox)
      }
    }
  }

  private fun Panel.footerGroup() {
    row {
      cell(sampleEditor.component).applyToComponent {
        border = JBUI.Borders.customLine(NamedColorUtil.getBoundsColor())
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun Row.checkBox(
    @NlsContexts.Checkbox text:
    String, value: Boolean,
    componentSetter: KMutableProperty0<JBCheckBox>
  ) = checkBox(text).apply {
    selected(value)
    componentSetter.set(component)
    components.add(component)
  }

  private fun Row.spinner(
    value: Int,
    minValue: Int,
    maxValue: Int,
    componentSetter: KMutableProperty0<JBIntSpinner>,
  ) = spinner(minValue..maxValue).apply {
    //TODO: replace with proper setter
    bindIntValue(getter = { value }, setter = {})
    componentSetter.set(component)
    components.add(component)
  }

  private inline fun <reified T : Any> Row.comboBox(
    model: ComboBoxModel<T>,
    renderer: ListCellRenderer<T?>,
    value: T,
    componentSetter: KMutableProperty0<ComboBox<T>>,
  ) = comboBox(model, renderer).apply {
    //TODO: replace with proper setter
    bindItem(getter = { value }, setter = {})
    componentSetter.set(this.component)
    components.add(component)
  }

  protected open fun onComponentsChanged(isUserAction: Boolean) {
    applyToFormattingOptions(sampleFormattingOptions)
    val textAccumulator = TextAccumulator()
    sampleMessageFormatter.formatMessages(sampleFormattingOptions, textAccumulator, sampleMessages)
    sampleEditor.document.setReadOnly(false)
    try {
      sampleEditor.document.setText("")
      DocumentAppender(project, sampleEditor.document, MAX_SAMPLE_DOCUMENT_BUFFER_SIZE).appendToDocument(textAccumulator)
      sampleEditor.document.insertString(sampleEditor.document.textLength, " ".repeat(MAX_SAMPLE_DOCUMENT_TEXT_LENGTH))
    }
    finally {
      sampleEditor.document.setReadOnly(true)
    }
  }

  interface ApplyAction {
    fun onApply(logcatFormatDialogBase: LogcatFormatDialogBase)
  }
}
