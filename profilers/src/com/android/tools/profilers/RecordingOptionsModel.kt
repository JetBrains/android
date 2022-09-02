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

import com.android.tools.adtui.model.AspectModel
import java.util.Collections
import javax.swing.DefaultComboBoxModel
import javax.swing.ListModel
import javax.swing.MutableComboBoxModel

class RecordingOptionsModel: AspectModel<RecordingOptionsModel.Aspect>() {
  var isRecording = false
    private set

  var selectedOption: RecordingOption? = null
    private set(newOption) {
      if (newOption != field) {
        require (!isRecording && newOption.isValid())
        field = newOption
        changed(Aspect.SELECTION_CHANGED)
      }
    }

  private val builtInOptionList = mutableListOf<RecordingOption>()
  val builtInOptions: List<RecordingOption> get() = Collections.unmodifiableList(builtInOptionList)
  val customConfigurationModel: MutableComboBoxModel<RecordingOption> = ConfigModel(emptyArray())

  // Map each option that's not currently ready to a message explaining why
  // This is also the source of truth regarding whether option is ready
  private val notReadyOptions = mutableMapOf<RecordingOption, String>()

  val isSelectedOptionBuiltIn get() = selectedOption in builtInOptionList
  val isSelectedOptionCustom get() = selectedOption in customConfigurationModel

  fun selectBuiltInOption(opt: RecordingOption) {
    require(opt in builtInOptionList)
    selectedOption = opt
  }

  fun selectCurrentCustomConfiguration() {
    selectedOption = customConfigurationModel.selectedItem as RecordingOption
  }

  /**
   * Select the first recording option when the [criteria] is met. If no option
   * meets the [criteria], the first built-in option is selected.
   */
  fun selectOptionBy(criteria: (recordingOption: RecordingOption) -> Boolean) {
    // Search in built-in options first.
    val builtInOption = builtInOptionList.firstOrNull(criteria)
    if (builtInOption != null) {
      selectBuiltInOption(builtInOption)
    }
    else {
      // Search in custom options.
      for (i in 0 until customConfigurationModel.size) {
        val customOption = customConfigurationModel.getElementAt(i)
        if (criteria(customOption)) {
          customConfigurationModel.selectedItem = customOption
          selectCurrentCustomConfiguration()
          return
        }
      }
      // If nothing is found, select the first built-in option.
      selectBuiltInOption(builtInOptionList[0])
    }
  }

  fun canStop() = isRecording && selectedOption?.stopAction != null
  fun canStart() = !isRecording && selectedOption != null && selectedOption !in notReadyOptions

  fun start() {
    require(canStart()) { "Cannot record while another session is on-going" }
    selectedOption!!.startAction.run()
    setRecording()
  }

  fun stop() {
    if (canStop()) {
      selectedOption?.stopAction?.run()
      setFinished()
    }
  }

  fun setRecording() {
    if (!isRecording) {
      isRecording = true
      changed(Aspect.RECORDING_CHANGED)
    }
  }

  fun setFinished() {
    if (isRecording) {
      isRecording = false
      changed(Aspect.RECORDING_CHANGED)
    }
  }

  fun addBuiltInOptions(vararg options: RecordingOption) {
    builtInOptionList.addAll(options)
    changed(Aspect.BUILT_IN_OPTIONS_CHANGED)
  }

  fun addConfigurations(vararg options: RecordingOption) {
    (customConfigurationModel as ConfigModel).addAll(options.toMutableList())
    // If no option is selected, select the first option by default. This prevents the combo box from having
    // an empty element selected by default.
    if (customConfigurationModel.size > 0 && customConfigurationModel.selectedItem == null) {
      customConfigurationModel.selectedItem = customConfigurationModel.getElementAt(0)
    }
  }

  fun clearConfigurations() {
    (customConfigurationModel as ConfigModel).removeAllElements()
  }

  fun setOptionNotReady(opt: RecordingOption, message: String) {
    require (opt in builtInOptions) { "Marking options not ready is only supported for builtin options for now" }
    notReadyOptions[opt] = message
    changed(Aspect.READY_OPTIONS_CHANGED)
  }

  fun setOptionReady(opt: RecordingOption) {
    if (opt in notReadyOptions) {
      notReadyOptions.remove(opt)
      changed(Aspect.READY_OPTIONS_CHANGED)
    }
  }

  fun getOptionNotReadyMessage(opt: RecordingOption) = notReadyOptions[opt]
  fun isOptionReady(opt: RecordingOption) = getOptionNotReadyMessage(opt) == null

  /**
   * Check if the recording option is recognized by this model
   */
  private fun RecordingOption?.isValid(): Boolean = this == null || this in builtInOptionList || this in customConfigurationModel

  private inner class ConfigModel(configs: Array<RecordingOption>): DefaultComboBoxModel<RecordingOption>(configs) {
    override fun addAll(c: MutableCollection<out RecordingOption>?) = trackEmptinessChanged { super.addAll(c) }
    override fun addAll(index: Int, c: MutableCollection<out RecordingOption>?) = trackEmptinessChanged { super.addAll(index, c) }
    override fun addElement(anObject: RecordingOption?) = trackEmptinessChanged { super.addElement(anObject) }
    override fun insertElementAt(anObject: RecordingOption?, index: Int) = trackEmptinessChanged { super.insertElementAt(anObject, index) }
    override fun removeAllElements() = trackEmptinessChanged { super.removeAllElements() }
    override fun removeElement(anObject: Any?) = trackEmptinessChanged { super.removeElement(anObject) }
    override fun removeElementAt(index: Int) = trackEmptinessChanged { super.removeElementAt(index) }
    private fun trackEmptinessChanged(run: () -> Any) = (size > 0).let {
      run()
      if (size > 0 != it)
        changed(Aspect.CONFIGURATIONS_EMPTINESS_CHANGED)
    }
  }

  enum class Aspect {
    RECORDING_CHANGED,
    SELECTION_CHANGED,
    BUILT_IN_OPTIONS_CHANGED,
    READY_OPTIONS_CHANGED,
    // only fired when the configuration list changes between empty <-> non-empty
    CONFIGURATIONS_EMPTINESS_CHANGED,
  }

  companion object {
    operator fun invoke(builtInRecordings: Array<RecordingOption>, configs: Array<RecordingOption> = emptyArray()) =
      RecordingOptionsModel().apply {
      addBuiltInOptions(*builtInRecordings)
        addConfigurations(*configs)
    }
  }
}

open class RecordingOption @JvmOverloads constructor(val title: String, val description: String,
                                                     val startAction: Runnable, val stopAction: Runnable? = null) {
  override fun toString() = title
}

// Singleton object for the prototype display value
// of the custom config dropdown.
object PrototypeDisplayRecordingOption: RecordingOption("", "", {})

private operator fun<T> ListModel<T>?.contains(item: T?): Boolean = this != null && (0 until size).any { getElementAt(it) == item }