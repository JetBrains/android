/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.PID
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFormatConfiguration.Preset
import com.intellij.CommonBundle
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ActionEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

private const val MIN_TAG_LENGTH = 10
private const val MAX_TAG_LENGTH = 120
private const val MIN_APP_NAME_LENGTH = 10
private const val MAX_APP_NAME_LENGTH = 120
private const val MAX_PROCESS_NAME_LENGTH = 120
private val previewZoneId = ZoneId.of("GMT")
private val previewFormatter = MessageFormatter(LogcatColors(), previewZoneId)
private val previewTimestamp =
  Instant.from(ZonedDateTime.of(2021, 10, 4, 11, 0, 14, 234000000, previewZoneId))
private val previewMessages =
  listOf(
    LogcatMessage(
      LogcatHeader(
        LogLevel.DEBUG,
        27217,
        3814,
        "com.example.app1",
        "com.example.app1:process",
        "ExampleTag1",
        previewTimestamp,
      ),
      "Sample logcat message 1.",
    ),
    LogcatMessage(
      LogcatHeader(
        LogLevel.INFO,
        27217,
        3814,
        "com.example.app1",
        "com.example.app1:process",
        "ExampleTag1",
        previewTimestamp,
      ),
      "Sample logcat message 2.",
    ),
    LogcatMessage(
      LogcatHeader(
        LogLevel.WARN,
        24395,
        24395,
        "com.example.app2",
        "com.example.app2:process",
        "ExampleTag2",
        previewTimestamp,
      ),
      "Sample logcat message 3.",
    ),
    LogcatMessage(
      LogcatHeader(
        LogLevel.ERROR,
        24395,
        24395,
        "com.example.app2",
        "com.example.app2:process",
        "ExampleTag2",
        previewTimestamp,
      ),
      "Sample logcat multiline\nmessage.",
    ),
  )
private val MAX_SAMPLE_DOCUMENT_TEXT_LENGTH =
  TimestampFormat.Style.DATETIME.width +
    BOTH.width +
    MAX_TAG_LENGTH +
    1 +
    MAX_APP_NAME_LENGTH +
    1 +
    MAX_PROCESS_NAME_LENGTH +
    1 +
    3 +
    1 +
    "Sample logcat message #.".length
private const val MAX_PREVIEW_DOCUMENT_BUFFER_SIZE = Int.MAX_VALUE

internal class LogcatFormatDialog(
  private val project: Project,
  initialFormatting: FormattingOptions.Style,
  private var defaultFormatting: FormattingOptions.Style,
  private val applyAction: ApplyAction,
) {

  private val standardFormattingOptions = STANDARD.formattingOptions.copy()
  private val compactFormattingOptions = COMPACT.formattingOptions.copy()

  private val propertyGraph = PropertyGraph()
  private var doNotApplyComponentsToOptions: Boolean = false

  private var formattingStyle = propertyGraph.property(initialFormatting)
  private var setAsDefault = propertyGraph.property(initialFormatting == defaultFormatting)

  // Timestamp
  private val showTimestamp =
    observableProperty(initialFormatting.formattingOptions.timestampFormat.enabled)
  private var timestampStyle =
    observableProperty(initialFormatting.formattingOptions.timestampFormat.style)

  // Tag
  private val showTag = observableProperty(initialFormatting.formattingOptions.tagFormat.enabled)
  private var tagWidth = observableProperty(initialFormatting.formattingOptions.tagFormat.maxLength)
  private var tagShowDuplicates =
    observableProperty(!initialFormatting.formattingOptions.tagFormat.hideDuplicates)
  private var tagColorize =
    observableProperty(initialFormatting.formattingOptions.tagFormat.colorize)

  // Level
  private var showLevel =
    observableProperty(initialFormatting.formattingOptions.levelFormat.enabled)

  // PID/TID
  private var showPid =
    observableProperty(initialFormatting.formattingOptions.processThreadFormat.enabled)
  private var showTid =
    observableProperty(initialFormatting.formattingOptions.processThreadFormat.style == BOTH)

  // Package
  private var showPackage =
    observableProperty(initialFormatting.formattingOptions.appNameFormat.enabled)
  private var packageWidth =
    observableProperty(initialFormatting.formattingOptions.appNameFormat.maxLength)
  private var packageShowDuplicates =
    observableProperty(!initialFormatting.formattingOptions.appNameFormat.hideDuplicates)

  // Process name
  private var showProcess =
    observableProperty(initialFormatting.formattingOptions.processNameFormat.enabled)
  private var processWidth =
    observableProperty(initialFormatting.formattingOptions.processNameFormat.maxLength)
  private var processShowDuplicates =
    observableProperty(!initialFormatting.formattingOptions.processNameFormat.hideDuplicates)

  // Preview area
  @VisibleForTesting
  var previewEditor =
    createLogcatEditor(project).apply {
      scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
    }

  private var applyButton = ApplyButton()

  val dialogWrapper: DialogWrapper = createDialogWrapper(project)

  private fun createDialogWrapper(project: Project): DialogWrapper {
    val dialog = MyDialogWrapper(project, createPanel())
    onComponentsChanged()
    applyButton.isEnabled = false
    dialog.pack()
    Disposer.register(dialog.disposable, ::dispose)
    return dialog
  }

  init {
    formattingStyle.afterChange {
      val (previousOptions, currentOptions) =
        when (it) {
          STANDARD -> compactFormattingOptions to standardFormattingOptions
          COMPACT -> standardFormattingOptions to compactFormattingOptions
        }
      applyComponentsToOptions(previousOptions)
      // Do not apply changes to the current style while we manually change update the components.
      doNotApplyComponentsToOptions = true
      try {
        applyOptionsToComponents(currentOptions)
      } finally {
        doNotApplyComponentsToOptions = false
      }
      onComponentsChanged()

      setAsDefault.set(it == defaultFormatting)
    }
    setAsDefault.afterChange {
      applyButton.isEnabled = true
      val currentStyle = formattingStyle.get()
      defaultFormatting =
        when {
          it -> currentStyle
          currentStyle == STANDARD -> COMPACT
          else -> STANDARD
        }
    }
  }

  private fun onApply(isApplyButton: Boolean) {
    LogcatUsageTracker.log(
      LogcatUsageEvent.newBuilder()
        .setType(LogcatUsageEvent.Type.FORMAT_DIALOG)
        .setFormatDialogApplied(getLogcatFormatDialogEvent().setIsApplyButtonUsed(isApplyButton))
    )
    applyAction.apply(
      standardFormattingOptions.copy(),
      compactFormattingOptions.copy(),
      defaultFormatting,
    )
  }

  private fun createPanel(): DialogPanel {
    return panel {
      headerPanel()
      centerPanel()
      footerPanel()
    }
  }

  private fun Panel.headerPanel() {
    panel {
      row(LogcatBundle.message("logcat.format.presets.dialog.view")) {
        comboBox(FormattingOptions.Style.values().toList())
          .bindItem(formattingStyle)
          .named("formattingStyle")
        checkBox(LogcatBundle.message("logcat.format.presets.dialog.default"))
          .bindSelected(setAsDefault)
          .named("setAsDefault")
        cell(
          ActionLink(LogcatBundle.message("logcat.format.presets.dialog.restore.default")) {
            restoreDefault()
          }
        )
      }
    }
  }

  private fun Panel.centerPanel() {
    row {
        panel {
            timestampGroup()
            tagGroup()
            levelGroup()
          }
          .gap(RightGap.COLUMNS)
          .align(AlignY.TOP)
          .resizableColumn()
        panel {
            pidGroup()
            packageGroup()
            processGroup()
          }
          .align(AlignY.TOP)
          .resizableColumn()
      }
      .bottomGap(BottomGap.MEDIUM)
  }

  private fun Panel.footerPanel() {
    row {
      cell(previewEditor.component).applyToComponent {
        border = JBUI.Borders.customLine(NamedColorUtil.getBoundsColor())
      }
    }
  }

  private fun Panel.timestampGroup() {
    group(LogcatBundle.message("logcat.header.options.timestamp.title"), indent = false) {
      row {
        checkBox(LogcatBundle.message("logcat.header.options.timestamp.show"))
          .named("showTimestamp")
          .bindSelected(showTimestamp)
      }
      indent {
          row(LogcatBundle.message("logcat.header.options.timestamp.format")) {
            comboBox(TimestampFormat.Style.values().toList())
              .bindItem(timestampStyle)
              .named("timestampFormat")
          }
        }
        .enabledIf(showTimestamp)
    }
  }

  private fun Panel.tagGroup() {
    group(LogcatBundle.message("logcat.header.options.tag.title"), indent = false) {
      row {
        checkBox(LogcatBundle.message("logcat.header.options.tag.show"))
          .bindSelected(showTag)
          .named("showTag")
      }
      indent {
          row {
            label(LogcatBundle.message("logcat.header.options.tag.width")).named("tagWidthLabel")
            spinner(IntRange(MIN_TAG_LENGTH, MAX_TAG_LENGTH), step = 1)
              .bindIntValue(tagWidth)
              .named("tagWidth")
          }
          row {
            checkBox(LogcatBundle.message("logcat.header.options.tag.show.repeated"))
              .bindSelected(tagShowDuplicates)
              .named("showRepeatedTags")
          }
          row {
            checkBox(LogcatBundle.message("logcat.header.options.tag.colorize"))
              .bindSelected(tagColorize)
              .named("colorizeTags")
          }
        }
        .enabledIf(showTag)
    }
  }

  private fun Panel.levelGroup() {
    group(LogcatBundle.message("logcat.header.options.level.title"), indent = false) {
      row {
        checkBox(LogcatBundle.message("logcat.header.options.level.show"))
          .bindSelected(showLevel)
          .named("showLevel")
      }
    }
  }

  private fun Panel.pidGroup() {
    group(LogcatBundle.message("logcat.header.options.process.id.title"), indent = false) {
      row {
        checkBox(LogcatBundle.message("logcat.header.options.process.id.show.pid"))
          .bindSelected(showPid)
          .named("showPid")
      }
      indent {
          row {
            checkBox(LogcatBundle.message("logcat.header.options.process.id.show.tid"))
              .bindSelected(showTid)
              .named("showTid")
          }
        }
        .enabledIf(showPid)
    }
  }

  private fun Panel.packageGroup() {
    group(LogcatBundle.message("logcat.header.options.package.title"), indent = false) {
      row {
        checkBox(LogcatBundle.message("logcat.header.options.package.show"))
          .bindSelected(showPackage)
          .named("showPackage")
      }
      indent {
          row {
            label(LogcatBundle.message("logcat.header.options.package.width"))
              .named("packageWidthLabel")
            spinner(IntRange(MIN_APP_NAME_LENGTH, MAX_APP_NAME_LENGTH), step = 1)
              .bindIntValue(packageWidth)
              .named("packageWidth")
          }
          row {
            checkBox(LogcatBundle.message("logcat.header.options.package.show.repeated"))
              .bindSelected(packageShowDuplicates)
              .named("showRepeatedPackages")
          }
        }
        .enabledIf(showPackage)
    }
  }

  private fun Panel.processGroup() {
    group(LogcatBundle.message("logcat.header.options.process.title"), indent = false) {
      row {
        checkBox(LogcatBundle.message("logcat.header.options.process.show"))
          .bindSelected(showProcess)
          .named("showPackage")
      }
      indent {
          row {
            label(LogcatBundle.message("logcat.header.options.process.width"))
              .named("packageWidthLabel")
            spinner(IntRange(MIN_APP_NAME_LENGTH, MAX_APP_NAME_LENGTH), step = 1)
              .bindIntValue(processWidth)
              .named("processNameWidth")
          }
          row {
            checkBox(LogcatBundle.message("logcat.header.options.process.show.repeated"))
              .bindSelected(processShowDuplicates)
              .named("showRepeatedProcessNames")
          }
        }
        .enabledIf(showProcess)
    }
  }

  private fun applyComponentsToOptions(options: FormattingOptions) {
    if (doNotApplyComponentsToOptions) {
      return
    }
    options.apply {
      timestampFormat = TimestampFormat(timestampStyle.get(), showTimestamp.get())
      processThreadFormat = ProcessThreadFormat(if (showTid.get()) BOTH else PID, showPid.get())
      tagFormat =
        TagFormat(tagWidth.get(), !tagShowDuplicates.get(), showTag.get(), tagColorize.get())
      appNameFormat =
        AppNameFormat(packageWidth.get(), !packageShowDuplicates.get(), showPackage.get())
      processNameFormat =
        ProcessNameFormat(processWidth.get(), !processShowDuplicates.get(), showProcess.get())
      levelFormat = LevelFormat(showLevel.get())
    }
  }

  private fun applyOptionsToComponents(options: FormattingOptions) {
    options.timestampFormat.let {
      showTimestamp.set(it.enabled)
      timestampStyle.set(it.style)
    }
    options.processThreadFormat.let {
      showPid.set(it.enabled)
      showTid.set(it.style == BOTH)
    }
    options.tagFormat.let {
      showTag.set(it.enabled)
      tagWidth.set(it.maxLength)
      tagShowDuplicates.set(!it.hideDuplicates)
      tagColorize.set(it.colorize)
    }
    options.appNameFormat.let {
      showPackage.set(it.enabled)
      packageWidth.set(it.maxLength)
      packageShowDuplicates.set(!it.hideDuplicates)
    }
    options.processNameFormat.let {
      showProcess.set(it.enabled)
      processWidth.set(it.maxLength)
      processShowDuplicates.set(!it.hideDuplicates)
    }
    options.levelFormat.let { showLevel.set(it.enabled) }
  }

  private fun onComponentsChanged() {
    val options = FormattingOptions()
    applyComponentsToOptions(options)
    val textAccumulator = TextAccumulator()
    previewFormatter.formatMessages(options, textAccumulator, previewMessages)
    try {
      previewEditor.document.setReadOnly(false)
      previewEditor.document.setText("")
      DocumentAppender(project, previewEditor.document, MAX_PREVIEW_DOCUMENT_BUFFER_SIZE)
        .appendToDocument(textAccumulator)
      previewEditor.document.insertString(
        previewEditor.document.textLength,
        " ".repeat(MAX_SAMPLE_DOCUMENT_TEXT_LENGTH),
      )
    } finally {
      previewEditor.document.setReadOnly(true)
    }

    applyComponentsToOptions(
      if (formattingStyle.get() == STANDARD) standardFormattingOptions else compactFormattingOptions
    )
    applyButton.isEnabled = true
  }

  private fun dispose() {
    EditorFactory.getInstance().releaseEditor(previewEditor)
  }

  private fun getLogcatFormatDialogEvent(): LogcatUsageEvent.LogcatFormatDialog.Builder =
    LogcatUsageEvent.LogcatFormatDialog.newBuilder()
      .setConfiguration(
        LogcatUsageEvent.LogcatFormatConfiguration.newBuilder()
          .setIsShowTimestamp(showTimestamp.get())
          .setIsShowDate(timestampStyle.get() == TimestampFormat.Style.DATETIME)
          .setIsShowProcessId(showPid.get())
          .setIsShowThreadId(showTid.get())
          .setIsShowTags(showTag.get())
          .setIsShowRepeatedTags(tagShowDuplicates.get())
          .setTagWidth(tagWidth.get())
          // TODO(aalbert): Add usage for Tag Colorize
          .setIsShowPackages(showPackage.get())
          .setIsShowRepeatedPackages(packageShowDuplicates.get())
          .setPackageWidth(packageWidth.get())
          .setPreset(if (formattingStyle.get() == STANDARD) Preset.STANDARD else Preset.COMPACT)
        // TODO(aalbert): Add usage for Show Levels
      )

  private fun restoreDefault() {
    val formattingOptions =
      when (formattingStyle.get()) {
        STANDARD -> AndroidLogcatFormattingOptions.DEFAULT_STANDARD
        COMPACT -> AndroidLogcatFormattingOptions.DEFAULT_COMPACT
      }
    applyOptionsToComponents(formattingOptions)
  }

  private fun <T> observableProperty(initial: T) =
    propertyGraph.property(initial).apply { afterChange { onComponentsChanged() } }

  fun interface ApplyAction {
    fun apply(
      standardOptions: FormattingOptions,
      compactOptions: FormattingOptions,
      defaultStyle: FormattingOptions.Style,
    )
  }

  /**
   * We need to extend DialogWrapper ourselves rather than use `components.dialog()` because we need
   * to add an `Apply` button and there seems no easy way to do this with the `components.dialog()`
   * version.
   *
   * It does provide a way to set the actions but in a way that completely replaces the default `OK`
   * and `Cancel` buttons. There seems to be no way to reuse them.
   */
  private inner class MyDialogWrapper(project: Project, private val panel: JComponent) :
    DialogWrapper(project, null, true, IdeModalityType.IDE) {
    override fun createCenterPanel(): JComponent = panel

    init {
      title = LogcatBundle.message("logcat.header.options.title")
      isResizable = true
      init()
    }

    override fun doOKAction() {
      onApply(isApplyButton = false)
      super.doOKAction()
    }

    override fun createActions(): Array<Action> = super.createActions() + applyButton
  }

  private inner class ApplyButton : AbstractAction(CommonBundle.getApplyButtonText()) {
    override fun actionPerformed(e: ActionEvent) {
      onApply(isApplyButton = true)
      isEnabled = false
    }
  }
}

private fun <T : JComponent> Cell<T>.named(name: String) = applyToComponent { this.name = name }

/**
 * Kotlin DSL 2 doesn't have `ObservableMutableProperty` binding for spinners.
 *
 * https://youtrack.jetbrains.com/issue/IDEA-320805/Kotlin-DSL-2-Doesnt-Have-ObservableMutableProperty-Binding-For-Spinners
 */
private fun <T : JBIntSpinner> Cell<T>.bindIntValue(
  property: ObservableMutableProperty<Int>
): Cell<T> {
  applyToComponent {
    value = property.get()
    val mutex = AtomicBoolean()
    property.afterChange { mutex.lockOrSkip { value = it } }
    addChangeListener { mutex.lockOrSkip { property.set(value as Int) } }
  }
  return this
}
