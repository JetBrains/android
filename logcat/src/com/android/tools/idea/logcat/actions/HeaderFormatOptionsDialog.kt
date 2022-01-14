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

import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.LogcatBundle.message
import com.android.tools.idea.logcat.messages.AppNameFormat
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.ProcessThreadFormat
import com.android.tools.idea.logcat.messages.TagFormat
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.messages.TimestampFormat
import com.android.tools.idea.logcat.util.createLogcatEditor
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
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.enableIf
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GridLayout
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import kotlin.reflect.KMutableProperty0

private const val MIN_TAG_LENGTH = 10
private const val MAX_TAG_LENGTH = 35
private const val MIN_APP_NAME_LENGTH = 10
private const val MAX_APP_NAME_LENGTH = 45
private const val GRID_COLUMN_GAP = 50

private val sampleZoneId = ZoneId.of("GMT")
private val sampleTimestamp = Instant.from(ZonedDateTime.of(2021, 10, 4, 11, 0, 14, 234000000, sampleZoneId))
private val sampleMessages = listOf(
  LogCatMessage(LogCatHeader(Log.LogLevel.DEBUG, 27217, 3814, "com.example.app1", "ExampleTag1", sampleTimestamp),
                "Sample logcat message 1."),
  LogCatMessage(LogCatHeader(Log.LogLevel.INFO, 27217, 3814, "com.example.app1", "ExampleTag1", sampleTimestamp),
                "Sample logcat message 2."),
  LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 24395, 24395, "com.example.app2", "ExampleTag2", sampleTimestamp),
                "Sample logcat message 3."),
  LogCatMessage(LogCatHeader(Log.LogLevel.ERROR, 24395, 24395, "com.example.app2", "ExampleTag2", sampleTimestamp),
                "Sample logcat multiline\nmessage."),
)

private val MAX_SAMPLE_DOCUMENT_TEXT_LENGTH =
  TimestampFormat.Style.DATETIME.width +
  ProcessThreadFormat.Style.BOTH.width +
  MAX_TAG_LENGTH + 1 +
  MAX_APP_NAME_LENGTH + 1 +
  3 + 1 +
  "Sample logcat message #.".length
private const val MAX_SAMPLE_DOCUMENT_BUFFER_SIZE = Int.MAX_VALUE

/**
 * A dialog for changing the formatting options.
 */
internal class HeaderFormatOptionsDialog(private val project: Project, formattingOptions: FormattingOptions) : Disposable {
  private var isShowTimestamp: Boolean = formattingOptions.timestampFormat.enabled
  private var timestampStyle: TimestampFormat.Style = formattingOptions.timestampFormat.style
  private var isShowPid: Boolean = formattingOptions.processThreadFormat.enabled
  private var isShowTid: Boolean = formattingOptions.processThreadFormat.run { style == ProcessThreadFormat.Style.BOTH }
  private var isShowTags: Boolean = formattingOptions.tagFormat.enabled
  private var tagsWidth: Int = formattingOptions.tagFormat.maxLength
  private var isShowRepeatingTags: Boolean = !formattingOptions.tagFormat.hideDuplicates
  private var isShowPackageNames: Boolean = formattingOptions.appNameFormat.enabled
  private var packageNamesWidth: Int = formattingOptions.appNameFormat.maxLength
  private var isShowRepeatingPackageNames: Boolean = !formattingOptions.appNameFormat.hideDuplicates

  @VisibleForTesting
  var sampleEditor = createLogcatEditor(project).apply {
    scrollPane.horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
  }

  private val sampleFormattingOptions = FormattingOptions()
  private val sampleMessageFormatter = MessageFormatter(sampleFormattingOptions, LogcatColors(), sampleZoneId)

  val dialogWrapper = dialog(
    project = project,
    title = message("logcat.header.options.title"),
    resizable = true,
    modality = DialogWrapper.IdeModalityType.PROJECT,
    panel = createPanel(),
  )

  init {
    Disposer.register(dialogWrapper.disposable, this)
    refreshSampleText()
    dialogWrapper.pack()
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(sampleEditor)
  }

  /**
   * Applies the state of the dialog to a [FormattingOptions] object.
   */
  fun applyTo(formattingOptions: FormattingOptions) {
    formattingOptions.apply {
      timestampFormat = TimestampFormat(timestampStyle, isShowTimestamp)
      processThreadFormat = ProcessThreadFormat(if (isShowTid) ProcessThreadFormat.Style.BOTH else ProcessThreadFormat.Style.PID, isShowPid)
      tagFormat = TagFormat(tagsWidth, !isShowRepeatingTags, isShowTags)
      appNameFormat = AppNameFormat(packageNamesWidth, !isShowRepeatingPackageNames, isShowPackageNames)
    }
  }

  private fun createPanel(): DialogPanel {
    return panel {
      row {
        component(JPanel())
          .constraints(growX, pushX)
          .applyToComponent {
            layout = GridLayout(0, 2).apply {
              hgap = GRID_COLUMN_GAP
            }
            add(panel { timestampGroup() })
            add(panel { processIdsGroup() })
            add(panel { tagsGroup() })
            add(panel { packageNamesGroup() })
          }
        footerGroup()
      }
    }
  }

  private fun LayoutBuilder.timestampGroup() {
    titledRow(message("logcat.header.options.timestamp.title")) {
      subRowIndent = 0
      row {
        val showTimestamp = liveCheckBox(message("logcat.header.options.timestamp.show"), ::isShowTimestamp)
          .constraints(growX, pushX)
        row {
          cell {
            label(message("logcat.header.options.timestamp.format"))
            val model = DefaultComboBoxModel(TimestampFormat.Style.values())
            val renderer = SimpleListCellRenderer.create<TimestampFormat.Style?>("") { it?.displayName }
            liveComboBox(model, ::timestampStyle, renderer)
          }
        }.enableIf(showTimestamp.selected)
      }
    }
  }

  private fun LayoutBuilder.processIdsGroup() {
    titledRow(message("logcat.header.options.process.ids.title")) {
      subRowIndent = 0
      row {
        val showPid = liveCheckBox(message("logcat.header.options.process.ids.show.pid"), ::isShowPid)
        row {
          liveCheckBox(message("logcat.header.options.process.ids.show.tid"), ::isShowTid)
            .enableIf(showPid.selected)
        }
      }
    }
  }

  private fun LayoutBuilder.tagsGroup() {
    titledRow(message("logcat.header.options.tags.title")) {
      subRowIndent = 0
      row {
        val showTags = liveCheckBox(message("logcat.header.options.tags.show"), ::isShowTags)
          .constraints(growX, pushX)
        row {
          cell {
            label(message("logcat.header.options.tags.width"))
            liveSpinner(::tagsWidth, MIN_TAG_LENGTH, MAX_TAG_LENGTH)
          }
        }.enableIf(showTags.selected)
        row {
          liveCheckBox(message("logcat.header.options.tags.show.repeated"), ::isShowRepeatingTags)
        }.enableIf(showTags.selected)
      }
    }
  }

  private fun LayoutBuilder.packageNamesGroup() {
    titledRow(message("logcat.header.options.packages.title")) {
      subRowIndent = 0
      row {
        val showPackageNames = liveCheckBox(message("logcat.header.options.packages.show"), ::isShowPackageNames)
        row {
          cell {
            label(message("logcat.header.options.packages.width"))
            liveSpinner(::packageNamesWidth, MIN_APP_NAME_LENGTH, MAX_APP_NAME_LENGTH)
          }
        }.enableIf(showPackageNames.selected)
        row {
          liveCheckBox(message("logcat.header.options.packages.show.repeated"), ::isShowRepeatingPackageNames)
        }.enableIf(showPackageNames.selected)
      }

    }
  }

  private fun LayoutBuilder.footerGroup() {
    row {
      component(sampleEditor.component).applyToComponent {
        border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun Cell.liveCheckBox(@NlsContexts.Checkbox text: String, prop: KMutableProperty0<Boolean>): CellBuilder<JBCheckBox> {
    return checkBox(text, prop).applyToComponent {
      addChangeListener {
        prop.set(isSelected)
        refreshSampleText()
      }
    }
  }

  private fun Cell.liveSpinner(prop: KMutableProperty0<Int>, minValue: Int, maxValue: Int): CellBuilder<JBIntSpinner> {
    return spinner(prop, minValue, maxValue).applyToComponent {
      addChangeListener {
        prop.set(number)
        refreshSampleText()
      }
    }
  }

  private inline fun <reified T : Any> Cell.liveComboBox(
    model: ComboBoxModel<T>,
    prop: KMutableProperty0<T>,
    renderer: ListCellRenderer<T?>,
  ): CellBuilder<ComboBox<T>> {
    return comboBox(model, prop, renderer).applyToComponent {
      addItemListener {
        prop.set(item)
        refreshSampleText()
      }
    }
  }

  private fun refreshSampleText() {
    applyTo(sampleFormattingOptions)
    val textAccumulator = TextAccumulator()
    sampleMessageFormatter.formatMessages(textAccumulator, sampleMessages)
    sampleEditor.document.setReadOnly(false)
    sampleEditor.document.setText("")
    DocumentAppender(project, sampleEditor.document, MAX_SAMPLE_DOCUMENT_BUFFER_SIZE).appendToDocument(textAccumulator)
    sampleEditor.document.insertString(sampleEditor.document.textLength, " ".repeat(MAX_SAMPLE_DOCUMENT_TEXT_LENGTH))
    sampleEditor.document.setReadOnly(true)
  }
}
