/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.model.AspectObserver
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.MutableComboBoxModel

/**
 * This class implements the UI for selecting and performing recording options. If custom configuration isn't supported, parameter
 * `editConfig` should be `null`, then the UI will also be simpler without button/menu for custom configurations.
 */
class RecordingOptionsView
@JvmOverloads
constructor(
  private val recordingModel: RecordingOptionsModel,
  // TODO unified UI for add/edit config instead of arbitrary callback?
  editConfig: ((MutableComboBoxModel<RecordingOption>) -> Unit)? = null,
) : JBPanel<RecordingOptionsView>() {
  private val observer = AspectObserver()

  @VisibleForTesting
  var builtInRadios: List<JRadioButton> = makeBuiltInRadios()
    private set

  @VisibleForTesting
  val configComponents: ConfigComponentGroup? =
    editConfig?.let {
      ConfigComponentGroup(
        JButton(EDIT_CONFIG).apply { addActionListener { editConfig(recordingModel.customConfigurationModel) } },
        JRadioButton("").apply { addActionListener { recordingModel.selectCurrentCustomConfiguration() } },
        ProfilerCombobox(recordingModel.customConfigurationModel).apply {
          // Sets prototype value to minimum width option to compute width of dropdown.
          // Now dropdown width is always constrained/overriden by parent width as parent is always wider.
          prototypeDisplayValue = PrototypeDisplayRecordingOption
        },
      )
    }

  @VisibleForTesting val startStopButton: JButton = JButton(START).apply { addActionListener { onStartStopButtonPressed() } }

  @VisibleForTesting
  val optionRows: FlexibleGrid =
    FlexibleGrid().also {
      it.set(makeRows())
      addComponentListener(
        object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent?) = it.adapt(width, height)
        }
      )
    }

  @VisibleForTesting
  val allRadios: List<JRadioButton>
    get() = if (configComponents != null) builtInRadios + configComponents.radio else builtInRadios

  init {
    val btnRow =
      JBPanel<Nothing>(FlowLayout(FlowLayout.LEADING)).apply {
        configComponents?.let { add(it.button) }
        add(startStopButton)
      }

    val content =
      JBPanel<Nothing>(BorderLayout()).apply {
        add(optionRows, BorderLayout.CENTER)
        add(btnRow, BorderLayout.SOUTH)
      }

    layout = GridLayout().apply {
      respectMinimumSize = true
    }

    val builder = RowsGridBuilder(this)
    builder
      .resizableRow()
      .cell(content, horizontalAlign = HorizontalAlign.CENTER, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)

    recordingModel
      .addDependency(observer)
      .onChange(RecordingOptionsModel.Aspect.RECORDING_CHANGED, ::onRecordingChanged)
      .onChange(RecordingOptionsModel.Aspect.SELECTION_CHANGED, ::onSelectionChanged)
      .onChange(RecordingOptionsModel.Aspect.BUILT_IN_OPTIONS_CHANGED, ::onBuiltInOptionsChanged)
      .onChange(RecordingOptionsModel.Aspect.CONFIGURATIONS_EMPTINESS_CHANGED, ::resetConfigMenu)
      .onChange(RecordingOptionsModel.Aspect.READY_OPTIONS_CHANGED, ::onOptionReadinessChanged)

    onRecordingChanged()
    onSelectionChanged()
    onOptionReadinessChanged()
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    // Set enabled status according to model
    if (enabled) {
      onRecordingChanged()
      onSelectionChanged()
      onOptionReadinessChanged()
      configComponents?.apply { button.isEnabled = true }
    }
    // Disable everything
    else {
      startStopButton.isEnabled = false
      builtInRadios.forEach { it.isEnabled = false }
      configComponents?.apply {
        menu.isEnabled = false
        radio.isEnabled = false
        button.isEnabled = false
      }
    }
  }

  private fun makeRows(): List<OptionRow> {
    val result = builtInRadios.mapIndexed { index, button -> OptionRow(button, null, recordingModel.builtInOptions[index].description) }
      .toMutableList()
    configComponents?.let {
      result.add(OptionRow(it.radio, it.menu, ADD_CONFIG_DESC))
    }
    return result
  }

  private fun makeBuiltInRadios() =
    recordingModel.builtInOptions.map { opt ->
      JRadioButton(opt.title).apply { addActionListener { recordingModel.selectBuiltInOption(opt) } }
    }

  private fun onSelectionChanged() =
    when {
      recordingModel.isSelectedOptionBuiltIn -> {
        startStopButton.isEnabled = !recordingModel.isRecording || recordingModel.canStop()
        builtInRadios[recordingModel.builtInOptions.indexOf(recordingModel.selectedOption)].isSelected = true
      }
      recordingModel.isSelectedOptionCustom -> {
        startStopButton.isEnabled = !recordingModel.isRecording || recordingModel.canStop()
        configComponents!!.radio.isSelected = true
        configComponents.menu.selectedItem = recordingModel.selectedOption!!
      }
      else -> {
        startStopButton.isEnabled = false
        allRadios.forEach { it.isSelected = false }
      }
    }

  private fun onRecordingChanged() =
    with(startStopButton) {
      when {
        recordingModel.isLoading -> {
          text = LOADING
          isEnabled = false
          setOptionsEnabled(false)
        }
        !recordingModel.isRecording -> {
          text = START
          isEnabled = true
          setOptionsEnabled(true)
        }
        recordingModel.canStop() -> {
          text = STOP
          isEnabled = true
          setOptionsEnabled(false)
        }
        else -> {
          text = RECORDING
          isEnabled = false
          setOptionsEnabled(false)
        }
      }
    }

  private fun onOptionReadinessChanged() =
    (builtInRadios zip recordingModel.builtInOptions).forEach { (radio, opt) ->
      radio.set(isEnabled && !recordingModel.isRecording && recordingModel.isOptionReady(opt), recordingModel.getOptionNotReadyMessage(opt))
    }

  private fun onStartStopButtonPressed() =
    when {
      !recordingModel.isRecording && recordingModel.canStart() -> recordingModel.start()
      recordingModel.isRecording && recordingModel.canStop() -> recordingModel.stop()
      else -> throw IllegalStateException("Start/stop unexpectedly enabled")
    }

  private fun setOptionsEnabled(enabled: Boolean) {
    (builtInRadios zip recordingModel.builtInOptions).forEach { (radio, opt) ->
      radio.set(enabled && recordingModel.isOptionReady(opt), recordingModel.getOptionNotReadyMessage(opt))
    }
    allRadios.forEach { it.isEnabled = enabled }
    configComponents?.apply {
      menu.isEnabled = enabled
      radio.isEnabled = enabled
    }
    resetConfigMenu()
  }

  private fun onBuiltInOptionsChanged() {
    builtInRadios = makeBuiltInRadios()
    optionRows.set(makeRows())
  }

  private fun resetConfigMenu() {
    configComponents?.apply {
      val enabled = !recordingModel.isRecording && recordingModel.customConfigurationModel.size > 0
      radio.isEnabled = enabled
      menu.isEnabled = enabled
    }
  }

  companion object {
    const val ADD_CONFIG_DESC = "Load saved custom profiling configurations"
    const val START = "Record"
    const val STOP = "Stop"
    const val RECORDING = "Recording"
    const val LOADING = "Loading"
    const val EDIT_CONFIG = "Edit Configurations"
  }

  data class ConfigComponentGroup(val button: JButton, val radio: JRadioButton, val menu: ProfilerCombobox<RecordingOption>)
}

/**
 * This class implements the grid of recording options and their descriptions that can adapt to available width/height. The layout that
 * contains it is responsible for calling the `width` method.
 */
@VisibleForTesting
class FlexibleGrid : JBPanel<FlexibleGrid>() {
  @VisibleForTesting var doubleColumnWidth: Int = 0
  @VisibleForTesting var doubleColumnHeight: Int = 0
  @VisibleForTesting var singleColumnWidth: Int = 0
  @VisibleForTesting var singleColumnHeight: Int = 0
  private var rows = listOf<OptionRow>()

  @VisibleForTesting
  var mode = Mode.Wide
    set(newMode) {
      if (newMode != field) {
        field = newMode
        refresh()
      }
    }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    refresh()
  }

  internal fun set(rows: List<OptionRow>) {
    this.rows = rows
    minimumSize = createView(rows, Mode.Compact).minimumSize
    createView(rows, Mode.Wide).preferredSize.let {
      doubleColumnWidth = it.width
      doubleColumnHeight = it.height
    }
    createView(rows, Mode.Tall).preferredSize.let {
      singleColumnWidth = it.width
      singleColumnHeight = it.height
    }
    refresh()
  }

  fun adapt(width: Int, height: Int) {
    mode =
      when {
        width >= doubleColumnWidth && height >= doubleColumnHeight -> Mode.Wide
        width >= singleColumnWidth && height >= singleColumnHeight -> Mode.Tall
        else -> Mode.Compact
      }
  }

  private fun refresh() {
    removeAll()
    add(createView(rows, mode))
    revalidate()
    repaint()
  }

  @VisibleForTesting
  enum class Mode {
    Wide,
    Tall,
    Compact,
  }
}

private fun createView(rows: List<OptionRow>, viewMode: FlexibleGrid.Mode): JComponent {
  return panel {
    buttonsGroup {
      for (row in rows) {
        row {
          val radioButtonCell = cell(row.radioButton)
            .gap(RightGap.SMALL)
          val additionalCell = row.additional?.let {
            cell(it)
              .resizableColumn()
              .align(AlignX.FILL)
          }

          when (viewMode) {
            FlexibleGrid.Mode.Wide -> comment(row.desc, maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
            FlexibleGrid.Mode.Tall -> radioButtonCell.comment(row.desc, maxLineLength = 50)
            FlexibleGrid.Mode.Compact -> (additionalCell ?: radioButtonCell).contextHelp(row.desc)
          }
        }
      }
    }
  }
}

private fun JRadioButton.set(enabled: Boolean, tooltip: String?) {
  isEnabled = enabled
  toolTipText = tooltip
}

internal data class OptionRow(val radioButton: JRadioButton, val additional: JComponent?, val desc: String)
