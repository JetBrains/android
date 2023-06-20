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
import com.intellij.openapi.ui.panel.PanelBuilder
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.UI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.MutableComboBoxModel

/**
 * This class implements the UI for selecting and performing recording options.
 * If custom configuration isn't supported, parameter `editConfig` should be `null`,
 * then the UI will also be simpler without button/menu for custom configurations.
 */
class RecordingOptionsView @JvmOverloads constructor(private val recordingModel: RecordingOptionsModel,
                                                     // TODO unified UI for add/edit config instead of arbitrary callback?
                                                     editConfig: ((MutableComboBoxModel<RecordingOption>) -> Unit)? = null)
  : JBPanel<RecordingOptionsView>(GridBagLayout()) {
  private val observer = AspectObserver()

  private val btnGroup = ButtonGroup()

  @VisibleForTesting
  var builtInRadios = makeBuiltInRadios()
    private set

  @VisibleForTesting
  val configComponents = editConfig?.let {
    ConfigComponentGroup(
      JButton(EDIT_CONFIG).apply { addActionListener { editConfig(recordingModel.customConfigurationModel) }},
      radioButton("").apply { addActionListener { recordingModel.selectCurrentCustomConfiguration() }},
      ProfilerCombobox(recordingModel.customConfigurationModel).apply {
        // Sets prototype value to minimum width option to compute width of dropdown.
        // Now dropdown width is always constrained/overriden by parent width as parent is always wider.
        prototypeDisplayValue = PrototypeDisplayRecordingOption
      }
    )
  }

  @VisibleForTesting
  val startStopButton = JButton(START).apply { addActionListener { onStartStopButtonPressed() }}

  @VisibleForTesting
  val optionRows = FlexibleGrid().also {
    it.set(makeRows())
    addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = it.adapt(width, height)
    })
  }

  @VisibleForTesting
  val allRadios get() = if (configComponents != null) builtInRadios + configComponents.radio else builtInRadios

  init {
    val btnRow =
      JBPanel<Nothing>(FlowLayout(FlowLayout.LEADING)).apply {
        configComponents?.let { add(it.button) }
        add(startStopButton)
      }

    val content = JBPanel<Nothing>(BorderLayout()).apply {
      add(optionRows, BorderLayout.CENTER)
      add(btnRow, BorderLayout.SOUTH)
    }
    add(content, GridBagConstraints())

    recordingModel.addDependency(observer)
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
      configComponents?.apply {
        button.isEnabled = true
      }
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

  private fun makeRows(): List<Pair<JComponent, String>> {
    val builtInRows = builtInRadios zip recordingModel.builtInOptions.map {it.description}
    return configComponents?.let {
      val configRadioWrapper = JBPanel<Nothing>(BorderLayout()).apply {
        add(it.radio, BorderLayout.LINE_START)
        add(it.menu, BorderLayout.CENTER)
      }
      builtInRows + (configRadioWrapper to ADD_CONFIG_DESC)
    } ?: builtInRows
  }

  private fun makeBuiltInRadios() = recordingModel.builtInOptions.map { opt ->
    radioButton(opt.title).apply { addActionListener { recordingModel.selectBuiltInOption(opt) } }
  }

  private fun onSelectionChanged() = when {
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

  private fun onRecordingChanged() = with (startStopButton) { when {
    !recordingModel.isRecording -> { text = START    ; isEnabled = true ; setOptionsEnabled(true)  }
    recordingModel.canStop()    -> { text = STOP     ; isEnabled = true ; setOptionsEnabled(false) }
    else                        -> { text = RECORDING; isEnabled = false; setOptionsEnabled(false) }
  }}

  private fun onOptionReadinessChanged() =
    (builtInRadios zip recordingModel.builtInOptions).forEach { (radio, opt) ->
      radio.set(isEnabled &&
                !recordingModel.isRecording &&
                recordingModel.isOptionReady(opt), recordingModel.getOptionNotReadyMessage(opt))
    }

  private fun onStartStopButtonPressed() = when {
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

  private fun radioButton(text: String) = JRadioButton(text).apply(btnGroup::add)

  companion object {
    const val ADD_CONFIG_DESC = "Load saved custom profiling configurations"
    const val START = "Record"
    const val STOP = "Stop"
    const val RECORDING = "Recording"
    const val EDIT_CONFIG = "Edit Configurations"
    const val DEFAULT_COLUMN_WIDTH = 250
  }

  data class ConfigComponentGroup(val button: JButton, val radio: JRadioButton, val menu: ProfilerCombobox<RecordingOption>)
}

/**
 * This class implements the grid of recording options and their descriptions that can adapt to available width/height.
 * The layout that contains it is responsible for calling the `width` method.
 */
@VisibleForTesting
class FlexibleGrid : JBPanel<FlexibleGrid>() {
  @VisibleForTesting var doubleColumnWidth = 0
  @VisibleForTesting var doubleColumnHeight = 0
  @VisibleForTesting var singleColumnWidth = 0
  @VisibleForTesting var singleColumnHeight = 0
  private var rows = listOf<Pair<JComponent, String>>()

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

  fun set(rows: List<Pair<JComponent, String>>) {
    this.rows = rows
    makeWideView().preferredSize.let {
      doubleColumnWidth = it.width
      doubleColumnHeight = it.height
    }
    makeTallView().preferredSize.let {
      singleColumnWidth = it.width
      singleColumnHeight = it.height
    }
    refresh()
  }

  fun adapt(width: Int, height: Int) {
    mode = when {
      width >= doubleColumnWidth && height >= doubleColumnHeight -> Mode.Wide
      width >= singleColumnWidth && height >= singleColumnHeight -> Mode.Tall
      else -> Mode.Compact
    }
  }

  private fun refresh() {
    removeAll()
    add(when (mode) {
      Mode.Wide -> makeWideView()
      Mode.Tall -> makeTallView()
      Mode.Compact -> makeCompactView()
    })
    revalidate()
    repaint()
  }

  private fun makeWideView() = makePanelWithRows {
    (ctrl, desc) -> UI.PanelFactory.panel(ctrl).withComment(desc).moveCommentRight()
  }.splitColumns().createPanel()

  private fun makeTallView() = makePanelWithRows {
    (ctrl, desc) -> UI.PanelFactory.panel(ctrl).withComment(desc)
  }.createPanel()

  private fun makeCompactView() = makePanelWithRows {
    (ctrl, desc) -> UI.PanelFactory.panel(ctrl).withTooltip(desc)
  }.createPanel()

  private fun makePanelWithRows(makeRow: (Pair<JComponent, String>) -> PanelBuilder) =
    UI.PanelFactory.grid().apply { rows.forEach { add(makeRow(it)) } }

  @VisibleForTesting enum class Mode { Wide, Tall, Compact }

  companion object {
    const val DESC_HEIGHT = 50
    const val CTRL_HEIGHT = 25
  }
}

private fun JRadioButton.set(enabled: Boolean, tooltip: String?) {
  isEnabled = enabled
  toolTipText = tooltip
}